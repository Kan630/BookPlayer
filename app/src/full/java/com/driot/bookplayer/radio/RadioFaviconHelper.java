package com.driot.bookplayer.radio;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.myLogD;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.myLogW;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.RadioStation;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.utils.Tonio;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Map;

public class RadioFaviconHelper {

    public static final boolean VERBOSE_DEBUG = true;
    private static void myLogDD(String txt) { if (VERBOSE_DEBUG) myLogD(txt); }

    private static final int MAX_FAVICON_SIZE_BYTES = 20_480; // 20 KB
    private static final int FAVICON_PX = 256;

    public static void loadRadioFavicon(RadioStation s, ImageView favicon, int replacementResource,
                                        Map<String, String> faviconCache) {
        favicon.setTag(s.stationuuid);
        Context context = favicon.getContext().getApplicationContext();

        // Cache hit — load directly
        if (faviconCache.containsKey(s.stationuuid)) {
            String cachedUrl = faviconCache.get(s.stationuuid);
            if (cachedUrl != null) {
                GlideLoader.load(favicon, cachedUrl, replacementResource);
            } else {
                GlideLoader.clear(favicon, replacementResource);
            }
            return;
        }

        // No cache — reset to default while resolving
        GlideLoader.clear(favicon, replacementResource);

        if (!TextUtils.isEmpty(s.favicon)) {
            // Station has a declared favicon — try it first, fall back on failure
            GlideLoader.load(favicon, s.favicon, replacementResource, new RequestListener<>() {
                @Override
                public boolean onResourceReady(android.graphics.drawable.Drawable r, Object model,
                                               Target<android.graphics.drawable.Drawable> t,
                                               DataSource ds, boolean first) {
                    faviconCache.put(s.stationuuid, s.favicon);
                    downloadAndSaveFavicon(context, s.stationuuid, s.favicon, true);
                    return false;
                }

                @Override
                public boolean onLoadFailed(GlideException e, Object model,
                                            Target<android.graphics.drawable.Drawable> t, boolean first) {
                    resolveAndCache(context, s, favicon, replacementResource, faviconCache);
                    return true;
                }
            });
        } else {
            resolveAndCache(context, s, favicon, replacementResource, faviconCache);
        }
    }

    /** Runs the fallback chain and loads the result into the view (if it hasn't been recycled). */
    private static void resolveAndCache(Context context, RadioStation s, ImageView favicon,
                                        int replacementResource, Map<String, String> faviconCache) {
        FaviconResolver.resolve(s.name, s.country, s.homepage, url -> {
            faviconCache.put(s.stationuuid, url); // null is a valid "nothing found" marker
            if (url != null && favicon.getTag().equals(s.stationuuid)) {
                GlideLoader.load(favicon, url, replacementResource);
                downloadAndSaveFavicon(context, s.stationuuid, url, true);
            }
        });
    }

    public static void resolveAndPersistFavicon(Context context, RadioStation s) {
        // Already saved as a local file
        if (!TextUtils.isEmpty(s.favicon) && !s.favicon.startsWith("http")) {
            File f = new File(s.favicon);
            if (f.exists() && f.length() > 0) return;
        }

        // Has a network URL — download and save to normal (non-cached) folder
        if (!TextUtils.isEmpty(s.favicon) && s.favicon.startsWith("http")) {
            downloadAndSaveFavicon(context, s.stationuuid, s.favicon, false);
            return;
        }

        // No URL yet — resolve first, then download
        FaviconResolver.resolve(s.name, s.country, s.homepage, url -> {
            if (url != null) downloadAndSaveFavicon(context, s.stationuuid, url, false);
        });
    }

    // -------------------------------------------------------------------------
    // Disk persistence
    // -------------------------------------------------------------------------

    /**
     * Downloads {@code url} on a background thread, scales to {@value FAVICON_PX}×{@value FAVICON_PX},
     * compresses to JPEG under {@value MAX_FAVICON_SIZE_BYTES} bytes, and saves to the image folder.
     * For non-cached saves the DB favicon field is updated to the local file path.
     */
    private static void downloadAndSaveFavicon(Context context, String uuid, String url,
                                               boolean isCached) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                File dir = StorageHelper.getImageFolder(context, isCached);
                File outFile = new File(dir, uuid + ".jpg");

                if (outFile.exists() && outFile.length() > 0) {
                    myLogDD("downloadAndSaveFavicon already on disk: " + outFile.getAbsolutePath());
                    return;
                }

                Bitmap bmp = Glide.with(context)
                        .asBitmap()
                        .load(url)
                        .submit(FAVICON_PX, FAVICON_PX)
                        .get();

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                int quality = 85;
                do {
                    baos.reset();
                    bmp.compress(Bitmap.CompressFormat.JPEG, quality, baos);
                    quality -= 10;
                } while (baos.size() > MAX_FAVICON_SIZE_BYTES && quality > 20);

                dir.mkdirs();
                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    fos.write(baos.toByteArray());
                }

                myLogDD("downloadAndSaveFavicon saved " + outFile.getName()
                        + " (" + Tonio.getReadableSize(baos.size()) + ", quality=" + (quality + 10) + ")");

                if (!isCached) {
                    AppDatabase.getDatabase(context)
                            .radioStationDao()
                            .updateFavicon(uuid, outFile.getAbsolutePath());
                }

            } catch (Exception e) {
                myLogW("downloadAndSaveFavicon failed for " + uuid + ": " + e.getMessage());
            }
        });
    }

    // -------------------------------------------------------------------------
    // Drawables
    // -------------------------------------------------------------------------

    public static ColorDrawable getDefaultFaviconDrawable(Context context) {
        int nightMode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        int color = (nightMode == Configuration.UI_MODE_NIGHT_YES) ? Color.BLACK : Color.WHITE;
        return new ColorDrawable(color);
    }
}
