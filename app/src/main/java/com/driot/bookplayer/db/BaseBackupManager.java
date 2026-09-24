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

    // SharedPreferences.getAll() returns a Map<String, ?> with real Boolean/Float/Integer/Long/
    // String objects, but Gson has no way to know which of those a bare JSON number was once it's
    // deserialized back into a Map<String, ?> field (it defaults every JSON number to Double,
    // regardless of whether the original value was an Integer, Long, or Float) - so every non-
    // Boolean, non-String preference would silently vanish on restore (falls through every
    // `instanceof` check below). Wrapping each value with an explicit type tag before it ever
    // reaches Gson sidesteps that entirely.
    public static class TypedPref {
        public String type; // "boolean" | "float" | "int" | "long" | "string"
        public String value;
    }

    public static class BaseBackupData {
        public long timestamp;
        public Map<String, Map<String, TypedPref>> preferences = new HashMap<>();
        public List<BookSource> bookSources = new ArrayList<>();
        // "Book progress" category - local audiobook playback position and per-book stats.
        // Applies to both flavors (unlike radios/podcasts/librivox, which are full-only).
        public List<ZikFile> zikFiles = new ArrayList<>();
        public List<Folder> folders = new ArrayList<>();
    }

    private static Map<String, TypedPref> toTypedPrefs(Map<String, ?> raw) {
        Map<String, TypedPref> typed = new HashMap<>();
        for (Map.Entry<String, ?> entry : raw.entrySet()) {
            Object value = entry.getValue();
            TypedPref tp = new TypedPref();
            if (value instanceof Boolean) {
                tp.type = "boolean";
            } else if (value instanceof Float) {
                tp.type = "float";
            } else if (value instanceof Integer) {
                tp.type = "int";
            } else if (value instanceof Long) {
                tp.type = "long";
            } else if (value instanceof String) {
                tp.type = "string";
            } else {
                continue; // StringSet or unknown type - not used anywhere in this app currently
            }
            tp.value = String.valueOf(value);
            typed.put(entry.getKey(), tp);
        }
        return typed;
    }

    /** Ids of folders made only of downloaded podcast episodes whose feed address is still known,
     *  so their audio can be fetched again. Podcasts only exist in the full flavor. */
    public abstract java.util.List<Long> getRedownloadablePodcastFolderIds();

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
            data.preferences.put("SHARED_PREFERENCES_OPTIONS", toTypedPrefs(Option.getSharedPrefs(context).getAll()));
            data.preferences.put("SHARED_PREFERENCES_DIVERSE", toTypedPrefs(
                    context.getSharedPreferences("SHARED_PREFERENCES_DIVERSE", Context.MODE_PRIVATE).getAll()));
            data.preferences.put("SHARED_PREFERENCES_STATS", toTypedPrefs(
                    context.getSharedPreferences("SHARED_PREFERENCES_STATS", Context.MODE_PRIVATE).getAll()));
            data.preferences.put("SHARED_PREFERENCE_ADMIN", toTypedPrefs(
                    context.getSharedPreferences("SHARED_PREFERENCES_ADMIN", Context.MODE_PRIVATE).getAll()));
            data.preferences.put("SHARED_PREFERENCE_SEARCH_HISTORY", toTypedPrefs(
                    context.getSharedPreferences("search_history_store", Context.MODE_PRIVATE).getAll()));

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
            for (Map.Entry<String, Map<String, TypedPref>> entry : data.preferences.entrySet()) {
                String prefName = entry.getKey();
                SharedPreferences sp = context.getSharedPreferences(prefName, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sp.edit();
                editor.clear();
                for (Map.Entry<String, TypedPref> prefEntry : entry.getValue().entrySet()) {
                    TypedPref tp = prefEntry.getValue();
                    if (tp == null || tp.type == null)
                        continue;
                    try {
                        switch (tp.type) {
                            case "boolean":
                                editor.putBoolean(prefEntry.getKey(), Boolean.parseBoolean(tp.value));
                                break;
                            case "float":
                                editor.putFloat(prefEntry.getKey(), Float.parseFloat(tp.value));
                                break;
                            case "int":
                                editor.putInt(prefEntry.getKey(), Integer.parseInt(tp.value));
                                break;
                            case "long":
                                editor.putLong(prefEntry.getKey(), Long.parseLong(tp.value));
                                break;
                            case "string":
                                editor.putString(prefEntry.getKey(), tp.value);
                                break;
                        }
                    } catch (NumberFormatException ignored) {
                        // corrupt/unexpected value for this key - skip it, don't fail the whole restore
                    }
                }
                editor.apply();
            }
            Pref.init(context);
            Option.init(context);
        }

        boolean restoreFolders = includeBookProgress && (data.folders != null || data.zikFiles != null);
        boolean restoreSources = includeLibrivox && data.bookSources != null;
        if (restoreFolders || restoreSources) {
            Runnable dbWork = () -> {
                AppDatabase db = AppDatabase.getDatabase(context);
                db.runInTransaction(() -> {
                    // Folder first - ZikFile.idFolder and BookSource.idFolder reference it, and
                    // rows are reinserted with their original ids intact so the relationships
                    // survive. Note: the stored path/uri is tied to the old device/install and
                    // its SAF permission grant does not transfer - the entry reappears with its
                    // saved progress, but needs the file re-added (or downloaded again, when it
                    // has a saved download address) before it can actually play again.
                    java.util.Set<Long> restoredFolderIds = new java.util.HashSet<>();
                    // Deleting the tracks cascades into PlaySession (listening history, stats): set
                    // it aside and put back what still belongs to a restored track.
                    java.util.List<com.driot.bookplayer.player.heatmaps.PlaySession> keptSessions =
                            restoreFolders ? db.playSessionDao().getAll() : new java.util.ArrayList<>();
                    if (restoreFolders) {
                        db.zikFileDao().deleteAll();
                        db.folderDao().deleteAll();
                        if (data.folders != null) {
                            db.folderDao().insertAll(data.folders);
                            for (com.driot.bookplayer.db.Folder f : data.folders) {
                                restoredFolderIds.add(f.getId());
                            }
                        }
                        if (data.zikFiles != null) {
                            db.zikFileDao().insertAll(data.zikFiles);
                            java.util.Set<Long> restoredZikIds = new java.util.HashSet<>();
                            for (ZikFile z : data.zikFiles) {
                                restoredZikIds.add(z.getId());
                            }
                            java.util.List<com.driot.bookplayer.player.heatmaps.PlaySession> back =
                                    new java.util.ArrayList<>();
                            for (com.driot.bookplayer.player.heatmaps.PlaySession ps : keptSessions) {
                                if (restoredZikIds.contains(ps.zikFileId)) {
                                    back.add(ps);
                                }
                            }
                            if (!back.isEmpty()) {
                                db.playSessionDao().insertAll(back);
                            }
                        }
                    }
                    if (restoreSources) {
                        for (BookSource bs : data.bookSources) {
                            // Keep the link to its book only when that book was restored too.
                            if (bs.idFolder != null && !restoredFolderIds.contains(bs.idFolder)) {
                                bs.idFolder = null;
                            }
                        }
                        db.bookSourceDao().deleteAll();
                        db.bookSourceDao().insertAll(data.bookSources);
                    }
                });
            };
            // Off the main thread run it right here, so a caller that continues afterwards (the
            // full restore looking for books to download again) sees the finished result.
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                AppDatabase.databaseWriteExecutor.execute(dbWork);
            } else {
                dbWork.run();
            }
        }
    }
}
