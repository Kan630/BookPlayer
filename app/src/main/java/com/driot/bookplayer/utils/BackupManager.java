package com.driot.bookplayer.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.BookSource;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Pref;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BackupManager {

    private final Context context;
    private final Gson gson;

    public BackupManager(Context context) {
        this.context = context.getApplicationContext();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public static class BackupData {
        public long timestamp;
        public Map<String, Map<String, ?>> preferences = new HashMap<>();
        /* TODO
        public List<RadioStation> radioStations = new ArrayList<>();
        public List<Podcast> podcasts = new ArrayList<>();
        public List<Episode> episodes = new ArrayList<>();

         */
        public List<BookSource> bookSources = new ArrayList<>();
    }

    public String exportToJson(boolean includePreferences, boolean includeRadios, boolean includePodcasts,
            boolean includeLibrivox) {
        BackupData data = new BackupData();
        data.timestamp = System.currentTimeMillis();

        // Preferences
        if (includePreferences) {
            data.preferences.put("SHARED_PREFERENCES_OPTIONS", Option.getSharedPrefs(context).getAll());
            data.preferences.put("SHARED_PREFERENCES_DIVERSE",
                    context.getSharedPreferences("SHARED_PREFERENCES_DIVERSE", Context.MODE_PRIVATE).getAll());
            data.preferences.put("SHARED_PREFERENCES_STATS",
                    context.getSharedPreferences("SHARED_PREFERENCES_STATS", Context.MODE_PRIVATE).getAll());
            data.preferences.put("SHARED_PREFERENCE_ADMIN",
                    context.getSharedPreferences("SHARED_PREFERENCES_ADMIN", Context.MODE_PRIVATE).getAll());
            data.preferences.put("SHARED_PREFERENCE_RADIO_FAVORITES",
                    context.getSharedPreferences("radio_favorites_store", Context.MODE_PRIVATE).getAll());
        }

        // Database
        AppDatabase db = AppDatabase.getDatabase(context);
        /* TODO
        if (includeRadios) {
            data.radioStations = db.radioStationDao().getAll();
        }
        if (includePodcasts) {
            data.podcasts = db.podcastDao().getAll();
            data.episodes = db.episodeDao().getAll();
        }

         */
        if (includeLibrivox) {
            data.bookSources = db.bookSourceDao().getAll();
        }

        return gson.toJson(data);
    }

    public BackupData inspectJson(String json) {
        return gson.fromJson(json, BackupData.class);
    }

    public void importFromJson(String json, boolean includePreferences, boolean includeRadios, boolean includePodcasts,
            boolean includeLibrivox) {
        BackupData data = gson.fromJson(json, BackupData.class);
        if (data == null)
            return;

        // Restore Preferences
        if (includePreferences && data.preferences != null) {
            for (Map.Entry<String, Map<String, ?>> entry : data.preferences.entrySet()) {
                String prefName = entry.getKey();
                SharedPreferences sp = context.getSharedPreferences(prefName, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sp.edit();
                editor.clear();
                for (Map.Entry<String, ?> prefEntry : entry.getValue().entrySet()) {
                    Object value = prefEntry.getValue();
                    if (value instanceof Boolean)
                        editor.putBoolean(prefEntry.getKey(), (Boolean) value);
                    else if (value instanceof Float)
                        editor.putFloat(prefEntry.getKey(), (Float) value);
                    else if (value instanceof Integer)
                        editor.putInt(prefEntry.getKey(), (Integer) value);
                    else if (value instanceof Long)
                        editor.putLong(prefEntry.getKey(), (Long) value);
                    else if (value instanceof String)
                        editor.putString(prefEntry.getKey(), (String) value);
                }
                editor.apply();
            }
        }

        // Restore Database
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(context);
            db.runInTransaction(() -> {
                /* TODO
                if (includeRadios && data.radioStations != null) {
                    db.radioStationDao().deleteAll();
                    db.radioStationDao().insertAll(data.radioStations);
                }
                if (includePodcasts) {
                    if (data.podcasts != null) {
                        db.podcastDao().insertAll(data.podcasts);
                    }
                    if (data.episodes != null) {
                        db.episodeDao().insertAll(data.episodes);
                    }
                }

                 */
                if (includeLibrivox && data.bookSources != null) {
                    // Important: clear local folder references if folders are not backed up
                    for (BookSource bs : data.bookSources) {
                        bs.idFolder = null;
                    }
                    db.bookSourceDao().deleteAll();
                    db.bookSourceDao().insertAll(data.bookSources);
                }
            });
        });

        // Re-initialize Prefs and Options caches if anything was changed
        if (includePreferences) {
            Pref.init(context);
            Option.init(context);
        }
    }
}
