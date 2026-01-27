// GutenbergBookshelfStore.java
package com.driot.bookplayer.ebooks.gutendex;

import android.content.Context;

import com.driot.bookplayer.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

/**
 * Loads static Gutenberg bookshelves from res/raw/gutenberg_bookshelves.json.
 *
 * JSON format:
 * [
 *   { "name": "Adventure", "count": 500 },
 *   { "name": "Science Fiction", "count": 300 }
 * ]
 */
public final class GutenbergBookshelfStore {

    private GutenbergBookshelfStore() {}

    private static List<GutenbergBookshelf> CACHED_BOOKSHELVES;

    public static synchronized List<GutenbergBookshelf> getBookshelves(Context ctx) {
        if (CACHED_BOOKSHELVES != null) return CACHED_BOOKSHELVES;

        List<GutenbergBookshelf> out = new ArrayList<>();

        try (InputStream is = ctx.getResources().openRawResource(R.raw.gutenberg_bookshelves);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);

            JSONArray arr = new JSONArray(sb.toString());

            myLog(arr.length() + " bookshelves loaded");

            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String name = obj.optString("name", "");
                int count = obj.optInt("count", 0);

                out.add(new GutenbergBookshelf(name, count > 0 ? count : null));
            }
        } catch (Exception e) {
            myLogEE(e, "GutenbergBookshelfStore failed");
        }

        CACHED_BOOKSHELVES = out;
        return out;
    }
}
