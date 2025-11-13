package com.driot.bookplayer.views;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** MRU list per key, stored in SharedPreferences as a pipe-joined string. */
class SearchHistoryStore {

    private static final String PREFS = "search_history_store";

    static List<String> get(Context c, String key) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = sp.getString(key, "");
        List<String> out = new ArrayList<>();
        if (raw.isEmpty()) return out;
        String[] parts = raw.split("\\|\\|", -1);
        for (String p : parts) {
            if (!p.isEmpty()) out.add(p);
        }
        return out;
    }

    static void add(Context c, String key, String value, int max) {
        value = value.trim();
        if (value.isEmpty()) return;

        List<String> current = get(c, key);

        // MRU: remove if exists, then add to front
        current.remove(value);
        current.add(0, value);

        // trim to max
        while (current.size() > max) current.remove(current.size() - 1);

        // dedupe while preserving order (LinkedHashSet trick)
        LinkedHashSet<String> set = new LinkedHashSet<>(current);
        current.clear();
        current.addAll(set);

        save(c, key, current);
    }

    static void clear(Context c, String key) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().remove(key).apply();
    }

    private static void save(Context c, String key, List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (String s : items) {
            if (sb.length() != 0) sb.append("||");
            // very simple escaping: replace our delimiter if present
            sb.append(s.replace("||", "¦¦"));
        }
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().putString(key, sb.toString()).apply();
    }
}
