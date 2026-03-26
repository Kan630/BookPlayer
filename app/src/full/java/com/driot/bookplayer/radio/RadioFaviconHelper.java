package com.driot.bookplayer.radio;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.myLogD;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.myLogW;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.widget.ImageView;

import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.RadioStation;

import java.io.File;
import java.util.Map;

public class RadioFaviconHelper {

    public static final boolean VERBOSE_DEBUG = false;
    private static void myLogDD(String txt) { if (VERBOSE_DEBUG) myLogD(txt); }

    public static void loadRadioFavicon(RadioStation s, ImageView favicon, int replacementResource,
                                        Map<String, String> faviconCache) {
        favicon.setTag(s.stationuuid);

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
                    return false;
                }

                @Override
                public boolean onLoadFailed(GlideException e, Object model,
                                            Target<android.graphics.drawable.Drawable> t, boolean first) {
                    resolveAndCache(s, favicon, replacementResource, faviconCache);
                    return true;
                }
            });
        } else {
            resolveAndCache(s, favicon, replacementResource, faviconCache);
        }
    }

    /** Runs the fallback chain and loads the result into the view (if it hasn't been recycled). */
    private static void resolveAndCache(RadioStation s, ImageView favicon, int replacementResource,
                                        Map<String, String> faviconCache) {
        FaviconResolver.resolve(s.name, s.country, s.homepage, url -> {
            faviconCache.put(s.stationuuid, url); // null is a valid "nothing found" marker
            if (url != null && favicon.getTag().equals(s.stationuuid)) {
                GlideLoader.load(favicon, url, replacementResource);
            }
        });
    }

    public static void resolveAndPersistFavicon(Context context, RadioStation s) {
        if (!TextUtils.isEmpty(s.favicon) && s.favicon.startsWith("http")) return;
        if (!TextUtils.isEmpty(s.favicon)) {
            File f = new File(s.favicon);
            if (f.exists() && f.length() > 0) return;
        }

        FaviconResolver.resolve(s.name, s.country, s.homepage, url -> {
            if (url != null) persistFaviconUrl(context, s, url);
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

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    private static void persistFaviconUrl(Context context, RadioStation s, String url) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                AppDatabase.getDatabase(context.getApplicationContext())
                        .radioStationDao()
                        .updateFavicon(s.stationuuid, url);
                s.favicon = url;
            } catch (Exception e) {
                myLogW("persistFaviconUrl failed: " + e.getMessage());
            }
        });
    }
}