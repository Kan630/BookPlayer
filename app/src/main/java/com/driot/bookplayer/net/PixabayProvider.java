package com.driot.bookplayer.net;

import android.content.Context;

import com.driot.bookplayer.objects.CoverResult;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import androidx.annotation.Nullable;

public class PixabayProvider implements CoverSearchProvider {
    private final String apiKey;
    public PixabayProvider(String apiKey) { this.apiKey = apiKey; }

    @Override
    public List<CoverResult> search(Context ctx, String query, int max) {
        ArrayList<CoverResult> out = new ArrayList<>();

        // --- Preflight logging
        if (apiKey == null || apiKey.trim().isEmpty()) {
            myLogW("PixabayProvider: missing API key");
            return out;
        }
        final String keyTrim = apiKey.trim();
        myLogD("PixabayProvider: starting search; q=\"" + query + "\", max=" + max
                + ", key=" + obfuscateKey(keyTrim));

        HttpURLConnection conn = null;
        long t0 = System.currentTimeMillis();

        try {
            String q = java.net.URLEncoder.encode(query, "UTF-8");
            int perPage = Math.max(20, max);
            String urlStr = "https://pixabay.com/api/"
                    + "?key=" + keyTrim
                    + "&q=" + q
                    + "&image_type=photo"
                    + "&safesearch=true"
                    + "&per_page=" + perPage
                    + "&lang=en";

            myLogD("PixabayProvider URL: " + redactKeyInUrl(urlStr));

            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "BookPlayer/1.0 (+https://example.invalid)");

            int code = conn.getResponseCode();
            myLogD("PixabayProvider HTTP " + code + " (" + (System.currentTimeMillis() - t0) + "ms)");

            InputStream in = null;
            if (code >= 200 && code < 300) {
                in = conn.getInputStream();
            } else {
                // Read error body to understand why (e.g., invalid key / blocked / rate limit)
                InputStream err = conn.getErrorStream();
                String errBody = (err != null) ? NetUtils.readUtf8(err) : null;
                myLogW("PixabayProvider error body: " + truncate(errBody, 500));
                return out;
            }

            String json = NetUtils.readUtf8(in);
            // Optional: small peek into the JSON to debug structure
            myLogD("PixabayProvider raw json (truncated): " + truncate(json, 400));

            JSONObject root = new JSONObject(json);
            int totalHits = root.optInt("totalHits", -1);
            myLogD("PixabayProvider totalHits=" + totalHits);

            JSONArray hits = root.optJSONArray("hits");
            if (hits == null || hits.length() == 0) {
                myLogD("PixabayProvider: no hits");
                return out;
            }

            for (int i = 0; i < hits.length() && out.size() < max; i++) {
                JSONObject h = hits.getJSONObject(i);

                String large = optStringOrNull(h, "largeImageURL");
                String web   = optStringOrNull(h, "webformatURL");
                String prev  = optStringOrNull(h, "previewURL");

                String imageUrl = pickFirstHttps(large, web, prev);
                if (imageUrl == null) continue;

                String title = h.optString("tags", query);
                out.add(new CoverResult(title, imageUrl, "Pixabay"));

                if (i == 0) {
                    myLogD("PixabayProvider first image URL: " + imageUrl);
                }
            }

            myLogD("PixabayProvider: returning " + out.size() + " results (elapsed "
                    + (System.currentTimeMillis() - t0) + "ms)");

        } catch (Exception e) {
            myLogEE(e, "Pixabay search failed");
        } finally {
            if (conn != null) conn.disconnect();
        }

        return out;
    }

    private static String pickFirstHttps(String... urls) {
        if (urls == null) return null;
        for (String u : urls) {
            if (u == null || u.isEmpty()) continue;
            if (u.startsWith("//")) u = "https:" + u;
            if (u.startsWith("http://")) u = "https://" + u.substring(7);
            if (u.startsWith("https://")) return u;
        }
        return null;
    }

    private static String obfuscateKey(String key) {
        if (key == null) return "null";
        int n = key.length();
        if (n <= 8) return "***";
        return key.substring(0, 4) + "..." + key.substring(n - 4);
    }

    private static String redactKeyInUrl(String url) {
        if (url == null) return null;
        int i = url.indexOf("key=");
        if (i < 0) return url;
        int amp = url.indexOf('&', i);
        String masked = "key=" + obfuscateKey(paramValue(url, i + 4, amp));
        return url.substring(0, i) + masked + (amp >= 0 ? url.substring(amp) : "");
    }

    private static String paramValue(String s, int start, int end) {
        if (s == null) return "";
        if (end < 0 || end > s.length()) end = s.length();
        if (start < 0 || start > end) return "";
        return s.substring(start, end);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return (s.length() <= max) ? s : (s.substring(0, max) + "…");
    }

    // Utility: treat missing/empty as null
    private static @Nullable String optStringOrNull(JSONObject o, String key) {
        // optString never returns null; we convert "" to null to keep our logic clean
        String s = o.optString(key, "");
        return (s.isEmpty() ? null : s);
    }
}
