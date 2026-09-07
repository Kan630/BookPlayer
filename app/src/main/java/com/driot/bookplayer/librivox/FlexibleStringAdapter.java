package com.driot.bookplayer.librivox;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * archive.org's advancedsearch.php returns some fields (e.g. "creator") as a plain string
 * when there's a single value, but as a JSON array of strings when there are several (e.g.
 * items with more than one credited creator/narrator). Gson's default String deserializer
 * throws on the array case, aborting the whole response. This adapter accepts either shape
 * and joins array values into one comma-separated string.
 */
public class FlexibleStringAdapter implements JsonDeserializer<String> {
    @Override
    public String deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        if (json == null || json.isJsonNull()) {
            return null;
        }
        if (json.isJsonArray()) {
            JsonArray arr = json.getAsJsonArray();
            List<String> parts = new ArrayList<>();
            for (JsonElement el : arr) {
                if (el != null && !el.isJsonNull()) {
                    parts.add(el.getAsString());
                }
            }
            return String.join(", ", parts);
        }
        return json.getAsString();
    }
}
