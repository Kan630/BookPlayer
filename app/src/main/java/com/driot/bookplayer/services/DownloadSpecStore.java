package com.driot.bookplayer.services;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

//very small SharedPreferences store
public final class DownloadSpecStore {
    private static final String PREF = "download_specs";

    public static void save(Context ctx, DownloadSpec s) {
        try {
            JSONObject o = new JSONObject();
            o.put("workId", s.workId);
            o.put("url", s.url);
            o.put("destFolder", s.destFolder);
            o.put("title", s.title);
            o.put("isManual", s.isManual);
            SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            sp.edit().putString(s.workId, o.toString()).apply();
            // also index by uniqueName so we can relaunch even if workId changes
            sp.edit().putString(s.uniqueName(), o.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static DownloadSpec getByWorkId(Context ctx, String workId) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String s = sp.getString(workId, null);
        return parse(s);
    }

    public static DownloadSpec getByUniqueName(Context ctx, String uniqueName) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String s = sp.getString(uniqueName, null);
        return parse(s);
    }

    public static void remove(Context ctx, String workId, String uniqueName) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        sp.edit().remove(workId).remove(uniqueName).apply();
    }

    private static DownloadSpec parse(String s) {
        if (s == null) return null;
        try {
            JSONObject o = new JSONObject(s);
            return new DownloadSpec(
                    o.getString("workId"),
                    o.getString("url"),
                    o.getString("destFolder"),
                    o.optString("title", ""),
                    o.optBoolean("isManual", false)
            );
        } catch (Exception e) { return null; }
    }
}
