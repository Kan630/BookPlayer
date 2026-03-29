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

    /** Audio stream file extensions — no point scraping these as web pages. */
    private static final String[] STREAM_EXTENSIONS =
            { ".mp3", ".m3u8", ".m3u", ".aac", ".ogg", ".flac", ".wav", ".pls", ".xspf" };

    private static boolean isStreamUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase().split("\\?")[0]; // strip query params before checking
        for (String ext : STREAM_EXTENSIONS) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }

    private void start() {
        if (!TextUtils.isEmpty(homepage) && !isStreamUrl(homepage)) {
            tryOgImage();
        } else {
            if (isStreamUrl(homepage))
                log("[" + stationName + "] step 1/OG: skipped — homepage is a stream URL");
            tryWikipedia();
        }
    }

    // Step 1 — OG image from the station's homepage
    private void tryOgImage() {
        log("[" + stationName + "] step 1/OG: trying og:image from " + homepage);
        fetchOgImage(homepage, Var.RADIO_OG_IMAGE_TIMEOUT_MS, url -> {
            if (url != null) {
                log("[" + stationName + "] step 1/OG: found => " + url);
                callback.accept(url);
            } else {
                log("[" + stationName + "] step 1/OG: not found, trying google favicon");
                tryGoogleFavicon();
            }
        });
    }

    // Step 2 — Google favicon service; probed via HEAD before accepting (returns 404 for some domains)
    private void tryGoogleFavicon() {
        String host = homepage;
        try { host = new java.net.URL(homepage).getHost(); } catch (Exception ignored) {}
        final String faviconUrl = "https://www.google.com/s2/favicons?sz=256&domain=" + host;
        log("[" + stationName + "] step 2/GF: probing => " + faviconUrl);
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new java.net.URL(faviconUrl).openConnection();
                conn.setRequestMethod("HEAD");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.setRequestProperty("User-Agent", Var.USER_AGENT_BOOKPLAYER);
                int code = conn.getResponseCode();
                conn.disconnect();
                if (code == 200) {
                    log("[" + stationName + "] step 2/GF: found (HTTP 200) => " + faviconUrl);
                    new Handler(Looper.getMainLooper()).post(() -> callback.accept(faviconUrl));
                } else {
                    log("[" + stationName + "] step 2/GF: HTTP " + code + ", trying wikipedia");
                    new Handler(Looper.getMainLooper()).post(this::tryWikipedia);
                }
            } catch (Exception e) {
                log("[" + stationName + "] step 2/GF: error (" + e.getMessage() + "), trying wikipedia");
                new Handler(Looper.getMainLooper()).post(this::tryWikipedia);
            }
        }).start();
    }

    // Step 3 — Wikipedia page image (used when there is no homepage at all)
    private void tryWikipedia() {
        log("[" + stationName + "] step 3/WP: trying wikipedia");
        fetchFallbackBySearch(stationName, country, url -> {
            if (url != null) {
                log("[" + stationName + "] step 3/WP: found => " + url);
                callback.accept(url);
            } else {
                log("[" + stationName + "] step 3/WP: not found, trying iTunes");
                tryItunes();
            }
        });
    }

    // Step 4 — iTunes artwork (last resort)
    private void tryItunes() {
        log("[" + stationName + "] step 4/ITN: trying iTunes");
        fetchFallbackImage(stationName, country, url -> {
            if (url != null) {
                log("[" + stationName + "] step 4/ITN: found => " + url);
            } else {
                log("[" + stationName + "] step 4/ITN: not found — no image at all");
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
        conn.setRequestProperty("User-Agent", Var.USER_AGENT_BOOKPLAYER);
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