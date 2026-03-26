package com.driot.bookplayer.radio;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.driot.bookplayer.global.Var;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.function.Consumer;

/**
 * Resolves a favicon URL for a radio station through a prioritized fallback chain:
 *   1. OG image from homepage
 *   2. Google favicon service (requires homepage)
 *   3. Wikipedia page image (by station name + country)
 *   4. iTunes artwork
 *
 * All callbacks are delivered on the main thread.
 * Call {@link #resolve(String, String, String, Consumer)} to start resolution.
 */
public class FaviconResolver {

    private static final boolean VERBOSE_DEBUG = RadioFaviconHelper.VERBOSE_DEBUG;
    private static void log(String msg) { if (VERBOSE_DEBUG) com.driot.bookplayer.utils.log.LoggerStaticHelper.myLogD(msg); }

    private final String stationName;
    private final String country;
    private final String homepage;
    private final Consumer<String> callback; // null = not found

    private FaviconResolver(String stationName, String country, String homepage, Consumer<String> callback) {
        this.stationName = stationName;
        this.country     = country;
        this.homepage    = homepage;
        this.callback    = callback;
    }

    public static void resolve(String stationName, String country, String homepage,
                               Consumer<String> callback) {
        new FaviconResolver(stationName, country, homepage, callback).start();
    }

    private void start() {
        if (!TextUtils.isEmpty(homepage)) {
            tryOgImage();
        } else {
            tryWikipedia();
        }
    }

    // Step 1 — OG image from the station's homepage
    private void tryOgImage() {
        fetchOgImage(homepage, Var.RADIO_OG_IMAGE_TIMEOUT_MS, url -> {
            if (url != null) {
                log("[" + stationName + "] => og image found");
                callback.accept(url);
            } else {
                tryGoogleFavicon();
            }
        });
    }

    // Step 2 — Google favicon service (fallback when og:image is absent)
    private void tryGoogleFavicon() {
        String url = "https://www.google.com/s2/favicons?sz=256&domain=" + homepage;
        log("[" + stationName + "] => using google favicon");
        callback.accept(url); // Google always returns something; treat as terminal
    }

    // Step 3 — Wikipedia page image (used when there is no homepage at all)
    private void tryWikipedia() {
        fetchFallbackBySearch(stationName, country, url -> {
            if (url != null) {
                log("[" + stationName + "] => Wikipedia image found");
                callback.accept(url);
            } else {
                tryItunes();
            }
        });
    }

    // Step 4 — iTunes artwork (last resort)
    private void tryItunes() {
        fetchFallbackImage(stationName, country, url -> {
            if (url != null) {
                log("[" + stationName + "] => iTunes image found");
            } else {
                log("[" + stationName + "] => no image found at all");
            }
            callback.accept(url); // may be null — caller handles it
        });
    }

    // -------------------------------------------------------------------------
    // Network helpers (all post result to main thread)
    // -------------------------------------------------------------------------

    static void fetchOgImage(String homeUrl, int timeoutMs, Consumer<String> callback) {
        new Thread(() -> {
            String result = null;
            try {
                org.jsoup.nodes.Document doc = org.jsoup.Jsoup.connect(homeUrl).timeout(timeoutMs).get();
                String img = doc.select("meta[property=og:image]").attr("content");
                if (!img.isEmpty()) result = img;
            } catch (Exception ignored) {}
            final String r = result;
            new Handler(Looper.getMainLooper()).post(() -> callback.accept(r));
        }).start();
    }

    private static void fetchFallbackImage(String stationName, String country, Consumer<String> callback) {
        new Thread(() -> {
            String result = null;
            try {
                String query   = URLEncoder.encode("radio " + stationName + " " + country, "UTF-8");
                String apiUrl  = "https://itunes.apple.com/search?term=" + query + "&media=music&limit=1";
                String raw     = httpGet(apiUrl, 5000);
                JSONObject json = new JSONObject(raw);
                if (json.getInt("resultCount") > 0) {
                    result = json.getJSONArray("results")
                            .getJSONObject(0)
                            .getString("artworkUrl100")
                            .replace("100x100", "600x600");
                }
            } catch (Exception ignored) {}
            final String r = result;
            new Handler(Looper.getMainLooper()).post(() -> callback.accept(r));
        }).start();
    }

    private static void fetchFallbackBySearch(String stationName, String country, Consumer<String> callback) {
        new Thread(() -> {
            String result = null;
            try {
                String cleanName = stationName.replaceAll("(?i)^radio\\s+", "").trim();
                result = tryWikipediaQuery(cleanName);
                if (result == null) result = tryWikipediaQuery(cleanName + " " + country);
            } catch (Exception e) {
                log("Wikipedia exception: " + e.getMessage());
            }
            final String r = result;
            new Handler(Looper.getMainLooper()).post(() -> callback.accept(r));
        }).start();
    }

    private static String tryWikipediaQuery(String title) {
        try {
            String apiUrl = "https://en.wikipedia.org/w/api.php?action=query&titles="
                    + URLEncoder.encode(title, "UTF-8")
                    + "&prop=pageimages&format=json&pithumbsize=300";
            log("Wikipedia query: " + apiUrl);
            String raw = httpGet(apiUrl, 5000);
            log("Wikipedia response: " + raw);

            JSONObject pages = new JSONObject(raw).getJSONObject("query").getJSONObject("pages");
            JSONObject page  = pages.getJSONObject(pages.keys().next());
            if (page.has("missing") || page.has("invalid")) return null;

            JSONObject thumbnail = page.optJSONObject("thumbnail");
            if (thumbnail != null) {
                String url = thumbnail.optString("source", "");
                log("Wikipedia imageUrl: " + url);
                return url.isEmpty() ? null : url;
            }
        } catch (Exception e) {
            log("Wikipedia tryQuery exception: " + e.getMessage());
        }
        return null;
    }

    /** Minimal HTTP GET — reads full response body as a string. */
    private static String httpGet(String apiUrl, int timeoutMs) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setRequestProperty("User-Agent", "RadioApp/1.0");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }
}