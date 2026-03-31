// com.driot.bookplayer.net.OpenLibraryProvider.java
package com.driot.bookplayer.net;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.objects.CoverResult;

import java.util.ArrayList;
import java.util.List;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

public class OpenLibraryProvider implements CoverSearchProvider {

    @Override
    public List<CoverResult> search(Context ctx, String query, int max) {
        ArrayList<CoverResult> out = new ArrayList<>();
        try {
            String q = java.net.URLEncoder.encode(query, "UTF-8");
            String urlStr = "https://openlibrary.org/search.json?title=" + q + "&limit=" + Math.max(20, max);

            okhttp3.Request req = new okhttp3.Request.Builder()
                    .url(urlStr)
                    .header("User-Agent", Var.USER_AGENT_BOOKPLAYER)
                    .build();

            try (okhttp3.Response resp = NetUtils.sharedClient().newCall(req).execute()) {
                String json = resp.body().string();
                JSONObject root = new JSONObject(json);
                JSONArray docs = root.optJSONArray("docs");
                if (docs == null) return out;

                for (int i = 0; i < docs.length() && out.size() < max; i++) {
                    JSONObject d = docs.getJSONObject(i);
                    int coverId = d.optInt("cover_i", -1);
                    if (coverId <= 0) continue;
                    String title = d.optString("title", query);
                    String img = "https://covers.openlibrary.org/b/id/" + coverId + "-L.jpg";
                    out.add(new CoverResult(title, img, "OpenLibrary"));
                }
            }
        } catch (Exception e) {
            myLogEE(e, "OpenLibrary search failed");
        }
        return out;
    }
}
