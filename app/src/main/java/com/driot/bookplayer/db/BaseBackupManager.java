package com.driot.bookplayer.db;

import android.content.Context;
import android.content.SharedPreferences;

import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Pref;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class BaseBackupManager {

    protected final Context context;
    protected final Gson gson;

    public BaseBackupManager(Context context) {
        this.context = context.getApplicationContext();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public static class BaseBackupData {
        public long timestamp;
        public Map<String, Map<String, ?>> preferences = new HashMap<>();
        public List<BookSource> bookSources = new ArrayList<>();
        // "Book progress" category - local audiobook playback position and per-book stats.
        // Applies to both flavors (unlike radios/podcasts/librivox, which are full-only).
        public List<ZikFile> zikFiles = new ArrayList<>();
        public List<Folder> folders = new ArrayList<>();
    }

    public abstract String exportToJson(boolean includePreferences, boolean includeRadios, boolean includePodcasts,
            boolean includeLibrivox, boolean includeBookProgress, boolean includePodcastHistory);

    public abstract void importFromJson(String json, boolean includePreferences, boolean includeRadios,
            boolean includePodcasts,
            boolean includeLibrivox, boolean includeBookProgress, boolean includePodcastHistory);

    public abstract BaseBackupData inspectJson(String json);

    protected void exportBaseData(BaseBackupData data, boolean includePreferences, boolean includeLibrivox,
            boolean includeBookProgress) {
        data.timestamp = System.currentTimeMillis();

        if (includePreferences) {
            data.preferences.put("SHARED_PREFERENCES_OPTIONS", Option.getSharedPrefs(context).getAll());
            data.preferences.put("SHARED_PREFERENCES_DIVERSE",
                    context.getSharedPreferences("SHARED_PREFERENCES_DIVERSE", Context.MODE_PRIVATE).getAll());
            data.preferences.put("SHARED_PREFERENCES_STATS",
                    context.getSharedPreferences("SHARED_PREFERENCES_STATS", Context.MODE_PRIVATE).getAll());
            data.preferences.put("SHARED_PREFERENCE_ADMIN",
                    context.getSharedPreferences("SHARED_PREFERENCES_ADMIN", Context.MODE_PRIVATE).getAll());
            data.preferences.put("SHARED_PREFERENCE_SEARCH_HISTORY",
                    context.getSharedPreferences("search_history_store", Context.MODE_PRIVATE).getAll());

            //Just for Admin visual check
            //data.preferences.put("SHARED_PREFERENCE_CENSORSHIP",
            //        context.getSharedPreferences("SHARED_PREFERENCE_CENSORSHIP", Context.MODE_PRIVATE).getAll());
        }

        if (includeLibrivox) {
            data.bookSources = AppDatabase.getDatabase(context).bookSourceDao().getAll();
        }

        if (includeBookProgress) {
            data.folders = AppDatabase.getDatabase(context).folderDao().getAll();
            data.zikFiles = AppDatabase.getDatabase(context).zikFileDao().getAll();
        }
    }

    protected void importBaseData(BaseBackupData data, boolean includePreferences, boolean includeLibrivox,
            boolean includeBookProgress) {
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
            Pref.init(context);
            Option.init(context);
        }

        if (includeLibrivox && data.bookSources != null) {
            AppDatabase.databaseWriteExecutor.execute(() -> {
                AppDatabase db = AppDatabase.getDatabase(context);
                db.runInTransaction(() -> {
                    for (BookSource bs : data.bookSources) {
                        bs.idFolder = null;
                    }
                    db.bookSourceDao().deleteAll();
                    db.bookSourceDao().insertAll(data.bookSources);
                });
            });
        }

        if (includeBookProgress && (data.folders != null || data.zikFiles != null)) {
            AppDatabase.databaseWriteExecutor.execute(() -> {
                AppDatabase db = AppDatabase.getDatabase(context);
                db.runInTransaction(() -> {
                    // Folder first - ZikFile.idFolder references it, and rows are reinserted
                    // with their original ids intact so the relationship survives. Note: the
                    // stored path/uri is tied to the old device/install and its SAF permission
                    // grant does not transfer - the entry reappears with its saved progress,
                    // but needs the file re-added before it can actually play again.
                    db.zikFileDao().deleteAll();
                    db.folderDao().deleteAll();
                    if (data.folders != null) {
                        db.folderDao().insertAll(data.folders);
                    }
                    if (data.zikFiles != null) {
                        db.zikFileDao().insertAll(data.zikFiles);
                    }
                });
            });
        }
    }
}
