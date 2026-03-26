package com.driot.bookplayer.radio;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.myLogD;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.myLogW;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.RadioStation;
import com.driot.bookplayer.global.Var;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;
import java.util.function.Consumer;

public class RadioFaviconHelper {

    public static final boolean VERBOSE_DEBUG = false;
    private static void myLogDD(String txt) { if (VERBOSE_DEBUG) myLogD(txt); }

    public static void loadRadioFavicon(RadioStation s, ImageView favicon, int replacementResource,
                                        Map<String, String> faviconCache) {
        favicon.setTag(s.stationuuid);

        // If we already resolved a URL for this station, load it directly
        if (faviconCache.containsKey(s.stationuuid)) {
            String cachedUrl = faviconCache.get(s.stationuuid);
            if (cachedUrl != null) {
                Glide.with(favicon)
                        .load(cachedUrl)
                        .placeholder(getDefaultFaviconDrawable(favicon.getContext()))
                        .error(getErrorDrawable(favicon.getContext(), replacementResource))
                        .into(favicon);
            } else {
                Glide.with(favicon).clear(favicon);
                favicon.setImageDrawable(getErrorDrawable(favicon.getContext(), replacementResource));
            }
            return;
        }

        // Not yet resolved — clear immediately to avoid showing stale image from recycled view
        Glide.with(favicon).clear(favicon);
        favicon.setImageDrawable(getErrorDrawable(favicon.getContext(), replacementResource));

        if (TextUtils.isEmpty(s.favicon)) {
            if (!TextUtils.isEmpty(s.homepage)) {
                fetchOgImage(s.homepage, Var.RADIO_OG_IMAGE_TIMEOUT_MS, url -> {
                    if (!favicon.getTag().equals(s.stationuuid)) return;
                    if (url != null) {
                        myLogDD("[" + s.name + "] => no favicon, using og image");
                        faviconCache.put(s.stationuuid, url);
                        Glide.with(favicon).load(url)
                                .placeholder(getDefaultFaviconDrawable(favicon.getContext()))
                                .error(getErrorDrawable(favicon.getContext(), replacementResource))
                                .into(favicon);
                    } else {
                        myLogDD("[" + s.name + "] => no favicon, falling back to google favicon");
                        String googleUrl = "https://www.google.com/s2/favicons?sz=256&domain=" + s.homepage;
                        faviconCache.put(s.stationuuid, googleUrl);
                        Glide.with(favicon).load(googleUrl)
                                .placeholder(getDefaultFaviconDrawable(favicon.getContext()))
                                .error(getErrorDrawable(favicon.getContext(), replacementResource))
                                .into(favicon);
                    }
                });
            } else {
                myLogDD("[" + s.name + "] => no favicon, no homepage, trying DuckDuckGo fallback");
                fetchFallbackBySearch(s.name, s.country, url -> {
                    if (!favicon.getTag().equals(s.stationuuid)) return;
                    if (url != null) {
                        myLogDD("[" + s.name + "] => DuckDuckGo fallback found");
                        faviconCache.put(s.stationuuid, url);
                        Glide.with(favicon).load(url)
                                .placeholder(getDefaultFaviconDrawable(favicon.getContext()))
                                .error(getErrorDrawable(favicon.getContext(), replacementResource))
                                .into(favicon);
                    } else {
                        myLogDD("[" + s.name + "] => DuckDuckGo failed, trying iTunes fallback");
                        fetchFallbackImage(s.name, s.country, url2 -> {
                            if (!favicon.getTag().equals(s.stationuuid)) return;
                            if (url2 != null) {
                                myLogDD("[" + s.name + "] => iTunes fallback found");
                                faviconCache.put(s.stationuuid, url2);
                                Glide.with(favicon).load(url2)
                                        .placeholder(getDefaultFaviconDrawable(favicon.getContext()))
                                        .error(getErrorDrawable(favicon.getContext(), replacementResource))
                                        .into(favicon);
                            } else {
                                myLogDD("[" + s.name + "] => no image found at all, using default");
                                faviconCache.put(s.stationuuid, null);
                            }
                        });
                    }
                });
            }
        } else {
            Glide.with(favicon)
                    .load(s.favicon)
                    .placeholder(getDefaultFaviconDrawable(favicon.getContext()))
                    .error(getErrorDrawable(favicon.getContext(), replacementResource))
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                    Target<Drawable> target, boolean isFirstResource) {
                            if (!TextUtils.isEmpty(s.homepage)) {
                                fetchOgImage(s.homepage, Var.RADIO_OG_IMAGE_TIMEOUT_MS, url -> {
                                    if (!favicon.getTag().equals(s.stationuuid)) return;
                                    String loadUrl = url != null ? url
                                            : "https://www.google.com/s2/favicons?sz=256&domain=" + s.homepage;
                                    faviconCache.put(s.stationuuid, loadUrl);
                                    Glide.with(favicon).load(loadUrl)
                                            .placeholder(getDefaultFaviconDrawable(favicon.getContext()))
                                            .error(getErrorDrawable(favicon.getContext(), replacementResource))
                                            .into(favicon);
                                });
                            } else {
                                faviconCache.put(s.stationuuid, null);
                            }
                            return true;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model,
                                                       Target<Drawable> target, DataSource dataSource,
                                                       boolean isFirstResource) {
                            faviconCache.put(s.stationuuid, s.favicon);
                            return false;
                        }
                    })
                    .into(favicon);
        }
    }
    //RADIO favicon fetcher
    private static void fetchOgImage(String homeUrl, int timeout_ms, Consumer<String> callback) {
        new Thread(() -> {
            try {
                org.jsoup.nodes.Document doc = org.jsoup.Jsoup.connect(homeUrl).timeout(timeout_ms).get();
                String img = doc.select("meta[property=og:image]").attr("content");
                new Handler(Looper.getMainLooper()).post(() ->
                        callback.accept(img.isEmpty() ? null : img)
                );
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() ->
                        callback.accept(null)
                );
            }
        }).start();
    }
    private static Drawable getErrorDrawable(Context context, int replacementResource) {
        if (replacementResource != 0) {
            return AppCompatResources.getDrawable(context, replacementResource);
        }
        return getDefaultFaviconDrawable(context);
    }
    public static ColorDrawable getDefaultFaviconDrawable(Context context) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.windowBackground, typedValue, true);
        // Check if dark theme
        int nightMode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        int color = (nightMode == Configuration.UI_MODE_NIGHT_YES)
                ? Color.BLACK
                : Color.WHITE;
        return new ColorDrawable(color);
    }

    private static void fetchFallbackImage(String stationName, String country, Consumer<String> callback) {
        new Thread(() -> {
            try {
                String query = URLEncoder.encode("radio " + stationName + " " + country, "UTF-8");
                String apiUrl = "https://itunes.apple.com/search?term=" + query + "&media=music&limit=1";
                HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject json = new JSONObject(sb.toString());
                if (json.getInt("resultCount") > 0) {
                    // artworkUrl100 → replace with higher res
                    String url = json.getJSONArray("results")
                            .getJSONObject(0)
                            .getString("artworkUrl100")
                            .replace("100x100", "600x600");
                    new Handler(Looper.getMainLooper()).post(() -> callback.accept(url));
                } else {
                    new Handler(Looper.getMainLooper()).post(() -> callback.accept(null));
                }
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> callback.accept(null));
            }
        }).start();
    }
    private static void fetchFallbackBySearch(String stationName, String country, Consumer<String> callback) {
        new Thread(() -> {
            try {
                // Strip leading "radio" prefix if present — Wikipedia titles don't have it
                String cleanName = stationName.replaceAll("(?i)^radio\\s+", "").trim();

                // Try 1: exact name only (e.g. "France Inter")
                String url1 = "https://en.wikipedia.org/w/api.php?action=query&titles="
                        + URLEncoder.encode(cleanName, "UTF-8")
                        + "&prop=pageimages&format=json&pithumbsize=300";

                String imageUrl = tryWikipediaQuery(url1);

                // Try 2: name + country if first failed (e.g. "France Musique France")
                if (imageUrl == null) {
                    String url2 = "https://en.wikipedia.org/w/api.php?action=query&titles="
                            + URLEncoder.encode(cleanName + " " + country, "UTF-8")
                            + "&prop=pageimages&format=json&pithumbsize=300";
                    imageUrl = tryWikipediaQuery(url2);
                }

                final String result = imageUrl;
                new Handler(Looper.getMainLooper()).post(() -> callback.accept(result));

            } catch (Exception e) {
                myLogDD("Wikipedia exception: " + e.getMessage());
                new Handler(Looper.getMainLooper()).post(() -> callback.accept(null));
            }
        }).start();
    }

    private static String tryWikipediaQuery(String apiUrl) {
        try {
            myLogDD("Wikipedia query: " + apiUrl);
            HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "RadioApp/1.0");
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            String raw = sb.toString();
            myLogDD("Wikipedia response: " + raw);

            JSONObject pages = new JSONObject(raw)
                    .getJSONObject("query")
                    .getJSONObject("pages");

            JSONObject page = pages.getJSONObject(pages.keys().next());

            // missing or invalid = not found
            if (page.has("missing") || page.has("invalid")) return null;

            JSONObject thumbnail = page.optJSONObject("thumbnail");
            if (thumbnail != null) {
                String imageUrl = thumbnail.optString("source", "");
                myLogDD("Wikipedia imageUrl: " + imageUrl);
                return imageUrl.isEmpty() ? null : imageUrl;
            }
        } catch (Exception e) {
            myLogDD("Wikipedia tryQuery exception: " + e.getMessage());
        }
        return null;
    }

    public static void resolveAndPersistFavicon(Context context, RadioStation s) {
        // Already has a valid remote URL → nothing to do
        if (!TextUtils.isEmpty(s.favicon) && s.favicon.startsWith("http")) return;

        // Already has a valid local file → nothing to do
        if (!TextUtils.isEmpty(s.favicon) && !s.favicon.startsWith("http")) {
            File f = new File(s.favicon);
            if (f.exists() && f.length() > 0) return;
        }

        // No favicon, or local file is missing/broken → resolve
        if (!TextUtils.isEmpty(s.homepage)) {
            fetchOgImage(s.homepage, Var.RADIO_OG_IMAGE_TIMEOUT_MS, url -> {
                if (url != null) {
                    myLogDD("[" + s.name + "] => persisting og image: " + url);
                    persistFaviconUrl(context, s, url);
                } else {
                    String googleUrl = "https://www.google.com/s2/favicons?sz=256&domain=" + s.homepage;
                    myLogDD("[" + s.name + "] => persisting google favicon: " + googleUrl);
                    persistFaviconUrl(context, s, googleUrl);
                }
            });
        } else {
            fetchFallbackBySearch(s.name, s.country, url -> {
                if (url != null) {
                    myLogDD("[" + s.name + "] => persisting Wikipedia url: " + url);
                    persistFaviconUrl(context, s, url);
                } else {
                    fetchFallbackImage(s.name, s.country, url2 -> {
                        if (url2 != null) {
                            myLogDD("[" + s.name + "] => persisting iTunes url: " + url2);
                            persistFaviconUrl(context, s, url2);
                        }
                    });
                }
            });
        }
    }

    private static void persistFaviconUrl(Context context, RadioStation s, String url) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                AppDatabase.getDatabase(context.getApplicationContext())
                        .radioStationDao()
                        .updateFavicon(s.stationuuid, url);
                s.favicon = url; // keep in-memory object in sync
            } catch (Exception e) {
                myLogW("persistFaviconUrl failed: " + e.getMessage());
            }
        });
    }

}
