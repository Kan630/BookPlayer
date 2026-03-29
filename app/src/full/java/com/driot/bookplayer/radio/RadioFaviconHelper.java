package com.driot.bookplayer.radio;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.myLogD;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.myLogW;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class RadioFaviconHelper {

    public static final boolean VERBOSE_DEBUG = false;
    private static void myLogDD(String txt) { if (VERBOSE_DEBUG) myLogD(txt); }

    private static final int MAX_FAVICON_SIZE_BYTES = 20_480; // 20 KB
    private static final int FAVICON_PX = 256;

    /**
     * Guards against queuing the same UUID more than once concurrently.
     * Without this, a station visible multiple times in a recycler scroll can trigger
     * 3 parallel downloads that all receive the same Glide-cached Bitmap, then one
     * thread recycles it while the others are still using it → SIGABRT.
     */
    private static final Set<String> inProgressDownloads =
            Collections.synchronizedSet(new HashSet<>());
    /** Favicons at or below this size are treated as "small" and centered on a padded canvas. */
    private static final int FAVICON_SMALL_THRESHOLD_PX = 64;
    private static final int FAVICON_SMALL_BOX_PX = 64; // inner box size inside the 256×256 canvas

    public static void loadRadioFavicon(RadioStation s, ImageView favicon, int replacementResource,
                                        Map<String, String> faviconCache) {
        favicon.setTag(s.stationuuid);
        Context context = favicon.getContext().getApplicationContext();

        // Step 1 — memory cache hit
        if (faviconCache.containsKey(s.stationuuid)) {
            String cachedUrl = faviconCache.get(s.stationuuid);
            myLogDD("step 1/mem-cache: [" + s.name + "] hit => " + cachedUrl);
            if (cachedUrl != null) {
                GlideLoader.load(favicon, cachedUrl, replacementResource);
            } else {
                GlideLoader.clear(favicon, replacementResource);
            }
            return;
        }

        // Step 1b — disk cache hit: load local file immediately, no network needed
        File diskFile = new File(StorageHelper.getImageFolder(context, true),
                "radio_cover_" + s.stationuuid + ".jpg");
        if (diskFile.exists() && diskFile.length() > 0) {
            String localPath = diskFile.getAbsolutePath();
            myLogDD("step 1b/disk-cache: [" + s.name + "] hit => " + diskFile.getName());
            faviconCache.put(s.stationuuid, localPath);
            GlideLoader.load(favicon, localPath, replacementResource);
            return;
        }

        // No cache — reset to default while resolving
        GlideLoader.clear(favicon, replacementResource);

        if (!TextUtils.isEmpty(s.favicon) && !"null".equals(s.favicon)) {
            // Step 2 — try the station's declared favicon
            myLogDD("step 2/station-favicon: [" + s.name + "] trying " + s.favicon);
            GlideLoader.load(favicon, s.favicon, replacementResource, new RequestListener<>() {
                @Override
                public boolean onResourceReady(android.graphics.drawable.Drawable r, Object model,
                                               Target<android.graphics.drawable.Drawable> t,
                                               DataSource ds, boolean first) {
                    myLogDD("step 2/station-favicon: [" + s.name + "] loaded OK");
                    faviconCache.put(s.stationuuid, s.favicon);
                    downloadAndSaveFavicon(context, s.stationuuid, s.name, s.favicon, true);
                    return false;
                }

                @Override
                public boolean onLoadFailed(GlideException e, Object model,
                                            Target<android.graphics.drawable.Drawable> t, boolean first) {
                    myLogDD("step 2/station-favicon: [" + s.name + "] failed, starting fallback chain");
                    resolveAndCache(context, s, favicon, replacementResource, faviconCache);
                    return true;
                }
            });
        } else {
            // Step 3 — no declared favicon, run the full resolver chain
            myLogDD("step 3/resolve: [" + s.name + "] no declared favicon, starting fallback chain");
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
                downloadAndSaveFavicon(context, s.stationuuid, s.name, url, true);
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
            downloadAndSaveFavicon(context, s.stationuuid, s.name, s.favicon, false);
            return;
        }

        // No URL yet — resolve first, then download
        FaviconResolver.resolve(s.name, s.country, s.homepage, url -> {
            if (url != null) downloadAndSaveFavicon(context, s.stationuuid, s.name, url, false);
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
    private static void downloadAndSaveFavicon(Context context, String uuid, String stationName,
                                               String url, boolean isCached) {
        // --- Deduplication gate (main-thread call site) ----------------------------
        // A station visible multiple times during a fast scroll can enqueue several
        // concurrent jobs for the same UUID. All of them would receive the same
        // Glide-cached Bitmap; the first job to finish recycles it and the others
        // crash with "Error, cannot access an invalid/free'd bitmap here!".
        if (!inProgressDownloads.add(uuid)) {
            myLogDD("step D/skip: [" + stationName + "] already in progress, skipping duplicate");
            return;
        }

        AppDatabase.databaseWriteExecutor.execute(() -> {
            // NEVER call downloaded.recycle() — Glide may hand the same cached Bitmap
            // instance to concurrent callers. The dedup gate above ensures only one
            // thread reaches this point per UUID, so we are safe; just let Glide's
            // own ref-counting/pool manage the downloaded bitmap's lifetime.
            Bitmap downloaded = null;
            Bitmap bmp = null;

            try {
                File dir = StorageHelper.getImageFolder(context, isCached);
                File outFile = new File(dir, "radio_cover_" + uuid + ".jpg");

                if (outFile.exists() && outFile.length() > 0) {
                    myLogDD("step D/skip: [" + stationName + "] already on disk: " + outFile.getName());
                    return;
                }

                // SVG files are not decodable by Glide without an extra library — skip them.
                // Only reject true SVGs; Wikimedia thumbnails look like "logo.svg/300px-logo.svg.png"
                // and are actually PNGs, so check the path ending, not a substring.
                String urlPath = url.split("\\?")[0].toLowerCase();
                if (urlPath.endsWith(".svg")) {
                    myLogDD("step D/skip: [" + stationName + "] SVG not supported — " + url);
                    return;
                }

                // Download capped at FAVICON_PX. Glide does NOT upscale, so a 16×16 favicon
                // comes back as 16×16 — which lets us detect it and apply the gray canvas.
                downloaded = Glide.with(context)
                        .asBitmap()
                        .load(url)
                        .submit(FAVICON_PX, FAVICON_PX)
                        .get();

                if (downloaded == null) {
                    myLogW("step D/fail: [" + stationName + "] Glide returned null — " + url);
                    return;
                }

                boolean isSmall = downloaded.getWidth() <= FAVICON_SMALL_THRESHOLD_PX
                        || downloaded.getHeight() <= FAVICON_SMALL_THRESHOLD_PX;
                myLogDD("step D/dl: [" + stationName + "] downloaded " + downloaded.getWidth()
                        + "×" + downloaded.getHeight() + (isSmall ? " (small → gray canvas)" : ""));

                bmp = isSmall
                        ? centerOnGrayCanvas(downloaded)
                        : Bitmap.createScaledBitmap(downloaded, FAVICON_PX, FAVICON_PX, true);

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

                myLogDD("step D/saved: [" + stationName + "] " + outFile.getName()
                        + " (" + Tonio.getReadableSize(baos.size()) + ", quality=" + (quality + 10) + ")");

                if (!isCached) {
                    AppDatabase.getDatabase(context)
                            .radioStationDao()
                            .updateFavicon(uuid, outFile.getAbsolutePath());
                }

            } catch (Throwable e) {
                // Catch Throwable (not just Exception) so OutOfMemoryError from bitmap
                // operations doesn't escape and crash the process silently.
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                myLogW("step D/fail: [" + stationName + "] " + cause.getMessage() + " — " + url);
            } finally {
                inProgressDownloads.remove(uuid);
                // Recycle OUR bitmap (createScaledBitmap / centerOnGrayCanvas result).
                // Guard against bmp == downloaded (createScaledBitmap returns the same
                // instance when the source is already the right size — don't double-free).
                // downloaded itself is intentionally NOT recycled; Glide manages it.
                if (bmp != null && bmp != downloaded && !bmp.isRecycled()) bmp.recycle();
            }
        });
    }

    /**
     * Places {@code favicon} (scaled to fit {@value FAVICON_SMALL_BOX_PX}×{@value FAVICON_SMALL_BOX_PX})
     * centered on a {@value FAVICON_PX}×{@value FAVICON_PX} gray canvas.
     * Used for tiny favicons (e.g. Google favicon service) to avoid blurry upscaling.
     */
    private static Bitmap centerOnGrayCanvas(Bitmap favicon) {
        Bitmap canvas = Bitmap.createBitmap(FAVICON_PX, FAVICON_PX, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(canvas);
        c.drawColor(0xFF808080); // mid-gray background

        float scale = Math.min(
                (float) FAVICON_SMALL_BOX_PX / favicon.getWidth(),
                (float) FAVICON_SMALL_BOX_PX / favicon.getHeight());
        int dstW = Math.round(favicon.getWidth() * scale);
        int dstH = Math.round(favicon.getHeight() * scale);
        int left = (FAVICON_PX - dstW) / 2;
        int top  = (FAVICON_PX - dstH) / 2;

        c.drawBitmap(favicon,
                null,
                new RectF(left, top, left + dstW, top + dstH),
                new Paint(Paint.FILTER_BITMAP_FLAG));
        return canvas;
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
