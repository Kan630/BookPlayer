package com.driot.bookplayer.net;

import android.content.Context;

import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.objects.CoverResult;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

public class PixabayProxyProvider implements CoverSearchProvider {
    private final String baseUrl; // e.g. "https://pixabay.driot.com"
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
            myLogD("Pixabay request: " + urlStr);

            okhttp3.Request.Builder reqBuilder = new okhttp3.Request.Builder()
                    .url(urlStr)
                    .header("User-Agent", Var.USER_AGENT_BOOKPLAYER);
            if (appToken != null && !appToken.isEmpty()) reqBuilder.header("x-app-auth", appToken);

            try (okhttp3.Response resp = NetUtils.sharedClient().newCall(reqBuilder.build()).execute()) {
                if (!resp.isSuccessful()) {
                    myLogW("Pixabay HTTP " + resp.code() + " for " + urlStr);
                    return out;
                }
                String json = resp.body().string();
                JSONObject root = new JSONObject(json);
                JSONArray hits = root.optJSONArray("hits");
                if (hits == null) return out;

                for (int i = 0; i < hits.length() && out.size() < max; i++) {
                    JSONObject h = hits.getJSONObject(i);
                    String large = opt(h, "largeImageURL");
                    String web   = opt(h, "webformatURL");
                    String prev  = opt(h, "previewURL");
                    String image = firstHttps(large, web, prev);
                    if (image == null) continue;
                    String title = h.optString("tags", query);
                    out.add(new CoverResult(title, image, "Pixabay"));
                }
            }
        } catch (Exception e) {
            myLogEE(e, "Pixabay search failed");
        }
        return out;
    }

    private static String opt(JSONObject o, String k) {
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
