package com.driot.bookplayer.net;

import android.content.Context;

import com.driot.bookplayer.objects.CoverResult;

import java.util.ArrayList;
import java.util.List;

public class PixabayProxyProvider implements CoverSearchProvider {
    private final String baseUrl; // e.g. "https://bookplayer-proxy...workers.dev/pixabay"
    private final String appToken;

    public PixabayProxyProvider(String baseUrl, String appToken) {
        this.baseUrl = baseUrl;
        this.appToken = appToken;
    }

    @Override
    public List<CoverResult> search(Context ctx, String query, int max) {
        ArrayList<CoverResult> out = new ArrayList<>();
        try {
            String q = java.net.URLEncoder.encode(query, "UTF-8");
            int perPage = Math.max(20, max);
            String urlStr = baseUrl + "?q=" + q + "&per_page=" + perPage + "&lang=en";

            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(urlStr).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "BookPlayer/1.0 (+android)");
            if (appToken != null && !appToken.isEmpty()) conn.setRequestProperty("x-app-auth", appToken);

            int code = conn.getResponseCode();
            java.io.InputStream in = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String json = NetUtils.readUtf8(in);
            if (code < 200 || code >= 300) return out;

            org.json.JSONObject root = new org.json.JSONObject(json);
            org.json.JSONArray hits = root.optJSONArray("hits");
            if (hits == null) return out;

            for (int i = 0; i < hits.length() && out.size() < max; i++) {
                org.json.JSONObject h = hits.getJSONObject(i);
                String large = opt(h, "largeImageURL");
                String web   = opt(h, "webformatURL");
                String prev  = opt(h, "previewURL");
                String image = firstHttps(large, web, prev);
                if (image == null) continue;
                String title = h.optString("tags", query);
                out.add(new CoverResult(title, image, "Pixabay"));
            }
        } catch (Exception ignore) {}
        return out;
    }

    private static String opt(org.json.JSONObject o, String k) {
        String s = o.optString(k, "");
        return s.isEmpty() ? null : s;
    }
    private static String firstHttps(String... urls) {
        if (urls == null) return null;
        for (String u : urls) {
            if (u == null || u.isEmpty()) continue;
            if (u.startsWith("//")) u = "https:" + u;
            if (u.startsWith("http://")) u = "https://" + u.substring(7);
            if (u.startsWith("https://")) return u;
        }
        return null;
    }
}
