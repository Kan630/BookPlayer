package com.driot.bookplayer.utils;

import org.json.JSONObject;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class MetaJson {
    private MetaJson() {}

    public static String toJson(Map<String, String> map) {
        try { return new JSONObject(map == null ? new HashMap<>() : map).toString(); }
        catch (Throwable ignore) { return "{}"; }
    }

    public static Map<String,String> fromJson(String json) {
        Map<String,String> out = new HashMap<>();
        try {
            JSONObject o = (json == null || json.isEmpty()) ? new JSONObject() : new JSONObject(json);
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                if (!o.isNull(k)) out.put(k, String.valueOf(o.get(k)));
            }
        } catch (Throwable ignore) {}
        return out;
    }
}
