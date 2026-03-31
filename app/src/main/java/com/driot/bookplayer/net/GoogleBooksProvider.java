// com.driot.bookplayer.net.GoogleBooksProvider.java
package com.driot.bookplayer.net;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.objects.CoverResult;

import java.util.ArrayList;
import java.util.List;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

public class GoogleBooksProvider implements CoverSearchProvider {

    @Override public String getName() { return "Google Books"; }

    @Override
    public List<CoverResult> search(Context ctx, String query, int max) {
        ArrayList<CoverResult> out = new ArrayList<>();
        try {
            String q = java.net.URLEncoder.encode("intitle:" + query, "UTF-8");
            String urlStr = "https://www.googleapis.com/books/v1/volumes?q=" + q + "&maxResults=20";

            okhttp3.Request req = new okhttp3.Request.Builder()
                    .url(urlStr)
                    .header("User-Agent", Var.USER_AGENT_BOOKPLAYER)
                    .build();

            try (okhttp3.Response resp = NetUtils.sharedClient().newCall(req).execute()) {
                if (!resp.isSuccessful()) {
                    myLogW("GoogleBooks search returned response code: " + resp.code());
                    return out;
                }
                String json = resp.body().string();
                JSONObject root = new JSONObject(json);
                JSONArray items = root.optJSONArray("items");
                if (items == null) return out;

                for (int i = 0; i < items.length() && out.size() < max; i++) {
                    JSONObject volume = items.getJSONObject(i).optJSONObject("volumeInfo");
                    if (volume == null) continue;
                    JSONObject imageLinks = volume.optJSONObject("imageLinks");
                    if (imageLinks == null) continue;

                    String img = pickBestImageUrl(imageLinks);
                    if (img == null) continue;

                    String title = volume.optString("title", query);
                    out.add(new CoverResult(title, img, "GoogleBooks"));
                }
            }
        } catch (Exception e) {
            myLogEE(e, "GoogleBooks search failed");
        }
        return out;
    }

    private static String pickBestImageUrl(JSONObject imageLinks) {
        String[] keys = { "extraLarge", "large", "medium", "small", "thumbnail", "smallThumbnail" };
        for (String k : keys) {
            String u = imageLinks.optString(k, null);
            u = normalizeGoogleImageUrl(u);
            if (u != null) return u;
        }
        return null;
    }

    private static String normalizeGoogleImageUrl(String u) {
        if (u == null || u.isEmpty()) return null;
        if (u.startsWith("//")) u = "https:" + u;
        if (u.startsWith("http://")) u = "https://" + u.substring(7);
        u = u.replace("&edge=curl", "");
        u = u.replace("zoom=1", "zoom=2");
        return u;
    }
}
