// com.driot.bookplayer.net.GoogleBooksProvider.java
package com.driot.bookplayer.net;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.objects.CoverResult;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

public class GoogleBooksProvider implements CoverSearchProvider {

    @Override
    public List<CoverResult> search(Context ctx, String query, int max) {
        ArrayList<CoverResult> out = new ArrayList<>();
        HttpURLConnection conn = null;
        try {
            String q = java.net.URLEncoder.encode("intitle:" + query, "UTF-8");
            URL url = new URL("https://www.googleapis.com/books/v1/volumes?q=" + q + "&maxResults=20");
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", Var.USER_AGENT_BOOKPLAYER);

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                myLogW("GoogleBooks search returned response code: " + responseCode);
                return out;
            }

            try (InputStream in = conn.getInputStream()) {
                String json = NetUtils.readUtf8(in);
                JSONObject root = new JSONObject(json);
                JSONArray items = root.optJSONArray("items");
                if (items == null)
                    return out;

                for (int i = 0; i < items.length() && out.size() < max; i++) {
                    JSONObject volume = items.getJSONObject(i).optJSONObject("volumeInfo");
                    if (volume == null)
                        continue;
                    JSONObject imageLinks = volume.optJSONObject("imageLinks");
                    if (imageLinks == null)
                        continue;

                    String img = pickBestImageUrl(imageLinks);
                    if (img == null)
                        continue;

                    String title = volume.optString("title", query);
                    out.add(new CoverResult(title, img, "GoogleBooks"));
                }
            }
        } catch (Exception e) {
            myLogEE(e, "GoogleBooks search failed");
        } finally {
            if (conn != null)
                conn.disconnect();
        }
        return out;
    }

    private static String pickBestImageUrl(JSONObject imageLinks) {
        // Try larger first; some fields may be absent
        String[] keys = { "extraLarge", "large", "medium", "small", "thumbnail", "smallThumbnail" };
        for (String k : keys) {
            String u = imageLinks.optString(k, null);
            u = normalizeGoogleImageUrl(u);
            if (u != null)
                return u;
        }
        return null;
    }

    private static String normalizeGoogleImageUrl(String u) {
        if (u == null || u.isEmpty())
            return null;

        // Handle protocol-relative URLs like //books.google.com/...
        if (u.startsWith("//"))
            u = "https:" + u;

        // Force HTTPS (Android blocks cleartext HTTP by default)
        if (u.startsWith("http://"))
            u = "https://" + u.substring(7);

        // Remove the curled-edge border parameter if present
        // (purely cosmetic; some thumbnails add a white border)
        u = u.replace("&edge=curl", "");

        // Ask for a bit larger thumbnail when possible
        // (zoom=1 is tiny; 2 is usually safe; 3 sometimes available)
        u = u.replace("zoom=1", "zoom=2");

        return u;
    }
}
