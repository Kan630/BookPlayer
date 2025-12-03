package com.driot.bookplayer.librivox;

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
 * Loads static Librivox genres from res/raw/librivox_genres.json.
 *
 * JSON format:
 * [
 *   { "name": "Children's Fiction", "subgenre": "", "completed": 583, "in_progress": 17 },
 *   { "name": "Children's Fiction", "subgenre": "Action & Adventure", "completed": 512, "in_progress": 20 }
 * ]
 */
public final class LibrivoxGenreStore {

    private LibrivoxGenreStore() {}

    private static List<LibrivoxGenre> CACHED_STRUCTURED;

    public static synchronized List<LibrivoxGenre> getGenres(Context ctx) {
        if (CACHED_STRUCTURED != null) return CACHED_STRUCTURED;

        List<LibrivoxGenre> out = new ArrayList<>();

        try (InputStream is = ctx.getResources().openRawResource(R.raw.librivox_genres);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);



            JSONArray arr = new JSONArray(sb.toString());

            myLog(arr.length() + " genres loaded");

            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String genre = obj.optString("name", "");
                int completed = obj.optInt("completed", 0);


                out.add(new LibrivoxGenre(null, genre, completed));
            }
        } catch (Exception e) {
            myLogEE(e, "LibrivoxGenreStore failed");
        }

        CACHED_STRUCTURED = out;
        return out;
    }
}
