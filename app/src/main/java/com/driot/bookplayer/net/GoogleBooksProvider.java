// com.driot.bookplayer.net.GoogleBooksProvider.java
package com.driot.bookplayer.net;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import com.driot.bookplayer.objects.CoverResult;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

public class GoogleBooksProvider implements CoverSearchProvider {
    @Override public List<CoverResult> search(Context ctx, String query, int max) {
        ArrayList<CoverResult> out = new ArrayList<>();
        HttpURLConnection conn = null;
        try {
            String q = java.net.URLEncoder.encode("intitle:" + query, "UTF-8");
            URL url = new URL("https://www.googleapis.com/books/v1/volumes?q=" + q + "&maxResults=20");
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "BookPlayer/1.0 (+https://example.invalid)");
            try (InputStream in = conn.getInputStream()) {
                String json = NetUtils.readUtf8(in); // ← use helper
                JSONObject root = new JSONObject(json);
                JSONArray items = root.optJSONArray("items");
                if (items == null) return out;
                for (int i = 0; i < items.length() && out.size() < max; i++) {
                    JSONObject volume = items.getJSONObject(i).optJSONObject("volumeInfo");
                    if (volume == null) continue;
                    JSONObject imageLinks = volume.optJSONObject("imageLinks");
                    if (imageLinks == null) continue;
                    String urlBig = imageLinks.optString("extraLarge", null);
                    if (urlBig == null) urlBig = imageLinks.optString("large", null);
                    if (urlBig == null) urlBig = imageLinks.optString("medium", null);
                    if (urlBig == null) urlBig = imageLinks.optString("thumbnail", null);
                    if (urlBig == null) continue;
                    String title = volume.optString("title", query);
                    out.add(new CoverResult(title, urlBig, "GoogleBooks"));
                }
            }
        } catch (Exception e) {
            myLogEE(e, "GoogleBooks search failed");
        } finally {
            if (conn != null) conn.disconnect();
        }
        return out;
    }
}
