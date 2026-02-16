package com.driot.bookplayer.radio;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.*;

/** DEPRECATED -- only there to ensure migration from Prefs => Room */
public class RadioFavoritesStore {

    private static final String PREF = "radio_favorites_store";
    private static final String KEY_MAP = "favorites_map"; // Map<String, RadioFavoriteItem>
    private static final String KEY_ORDER = "favorites_order"; // List<String> (most recent first)

    private static final Gson GSON = new Gson();
    private static final Type TYPE_MAP = new TypeToken<Map<String, RadioFavoriteItem>>() {
    }.getType();
    private static final Type TYPE_ORDER = new TypeToken<List<String>>() {
    }.getType();

    private final SharedPreferences sp;

    public RadioFavoritesStore(@NonNull Context ctx) {
        sp = com.driot.bookplayer.global.Pref.getRadioFavoritesPrefs();
    }

    public synchronized boolean isFavorite(@NonNull String uuid) {
        Map<String, RadioFavoriteItem> map = loadMap();
        return map.containsKey(uuid);
    }

    public synchronized void add(@NonNull RadioFavoriteItem item) {
        Map<String, RadioFavoriteItem> map = loadMap();
        List<String> order = loadOrder();

        map.put(item.stationuuid, item);
        // move to front
        order.remove(item.stationuuid);
        order.add(0, item.stationuuid);

        save(map, order);
    }

    public synchronized void remove(@NonNull String uuid) {
        Map<String, RadioFavoriteItem> map = loadMap();
        List<String> order = loadOrder();

        map.remove(uuid);
        order.remove(uuid);

        save(map, order);
    }

    @NonNull
    public synchronized List<RadioFavoriteItem> getAll() {
        Map<String, RadioFavoriteItem> map = loadMap();
        List<String> order = loadOrder();

        List<RadioFavoriteItem> out = new ArrayList<>(order.size());
        for (String u : order) {
            RadioFavoriteItem it = map.get(u);
            if (it != null)
                out.add(it);
        }
        // If any un-ordered entries exist (shouldn’t), append them:
        for (Map.Entry<String, RadioFavoriteItem> e : map.entrySet()) {
            if (!order.contains(e.getKey()))
                out.add(e.getValue());
        }
        return out;
    }

    private Map<String, RadioFavoriteItem> loadMap() {
        String json = sp.getString(KEY_MAP, null);
        if (json == null || json.isEmpty())
            return new HashMap<>();
        Map<String, RadioFavoriteItem> map = GSON.fromJson(json, TYPE_MAP);
        return map != null ? map : new HashMap<>();
    }

    private List<String> loadOrder() {
        String json = sp.getString(KEY_ORDER, null);
        if (json == null || json.isEmpty())
            return new ArrayList<>();
        List<String> list = GSON.fromJson(json, TYPE_ORDER);
        return list != null ? list : new ArrayList<>();
    }

    private void save(Map<String, RadioFavoriteItem> map, List<String> order) {
        sp.edit()
                .putString(KEY_MAP, GSON.toJson(map, TYPE_MAP))
                .putString(KEY_ORDER, GSON.toJson(order, TYPE_ORDER))
                .apply();
    }

    // Update an existing favorite item while keeping its position in the order
    // list.
    // If the uuid does not exist, nothing happens.
    public synchronized void update(@NonNull RadioFavoriteItem updated) {
        Map<String, RadioFavoriteItem> map = loadMap();
        List<String> order = loadOrder();
        if (!map.containsKey(updated.stationuuid)) {
            return; // nothing to update
        }
        map.put(updated.stationuuid, updated);
        save(map, order);
    }
}
