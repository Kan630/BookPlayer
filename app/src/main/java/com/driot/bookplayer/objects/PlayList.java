package com.driot.bookplayer.objects;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.utils.KanLogger;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 05/09/21
 * refactored -- on 2025-09-23
 *
 * Thread-safe singleton playlist that:
 *  - stores only folderId + current index to SharedPreferences
 *  - restores items from DB on process death
 *  - calls OnMetaLoadedListener on the main thread
 *  - guards all state with a lock + a "version" to ignore stale async results
 */
public final class PlayList {

    // ==== Singleton ====
    private static volatile PlayList instance;

    public static @Nullable PlayList getInstance() { return instance; }

    /** Create/replace the singleton with a new list. startIndex is clamped to [0, size). */
    public static void create(@NonNull Context ctx, @NonNull List<ZikFile> items) {
        create(ctx, items, 0);
    }

    public static void create(@NonNull Context ctx, @NonNull List<ZikFile> items, int startIndex) {
        if (items.isEmpty()) throw new IllegalStateException("PlayList.create(): empty list");
        Context app = ctx.getApplicationContext();
        PlayList pl = new PlayList(app);
        pl.replaceItems(items, startIndex);
        instance = pl;
        pl.saveToStorage();     // persist folderId + index
        pl.loadMetaAsync();     // async folder/podcast fetch
        myLogD("Playlist created with " + items.size() + " items, index=" + pl.index);
    }

    /** Try to restore from storage (folderId + index); returns true if restored. */
    public static boolean restoreIfExists(@NonNull Context ctx) {
        Context app = ctx.getApplicationContext();
        FirebaseAnalyticsHelper.tellAnalyticsPlaylistLoadFromStorage(app);
        SharedPreferences p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        // New keys first
        int folderId = p.getInt(KEY_FOLDER_ID, -1);
        int index = p.getInt(KEY_INDEX, -1);

        // Back-compat: if no folderId, try old JSON blob
        if (folderId < 0) {
            String json = p.getString(KEY_ZIK_FILES_LIST_LEGACY, null);
            if (json == null) {
                myLogD("restoreIfExists(): nothing to restore");
                return false;
            }
            try {
                Type type = new TypeToken<List<ZikFile>>() {}.getType();
                List<ZikFile> list = new Gson().fromJson(json, type);
                if (list == null || list.isEmpty()) return false;
                index = Math.max(0, Math.min(p.getInt(KEY_ZIK_FILE_INDEX_LEGACY, 0), list.size() - 1));
                PlayList pl = new PlayList(app);
                pl.replaceItems(list, index);
                pl.saveToStorage(); // migrate to new keys
                instance = pl;
                pl.loadMetaAsync();
                myLogW("restoreIfExists(): migrated from legacy JSON");
                return true;
            } catch (Throwable t) {
                myLogEE(t, "restoreIfExists(): legacy JSON failed");
                return false;
            }
        }

        // Normal path: re-query DB by folderId
        try {
            List<ZikFile> list = AppDatabase.getDatabase(app).ZikFileDao().getZikFiles(folderId);
            if (list == null || list.isEmpty()) {
                myLogW("restoreIfExists(): DB returned empty for folderId=" + folderId);
                return false;
            }
            index = Math.max(0, Math.min(index, list.size() - 1));
            PlayList pl = new PlayList(app);
            pl.replaceItems(list, index);
            instance = pl;
            pl.loadMetaAsync();
            myLogD("restoreIfExists(): restored folderId=" + folderId + " size=" + list.size() + " index=" + index);
            return true;
        } catch (Throwable t) {
            myLogEE(t, "restoreIfExists(): DB error");
            return false;
        }
    }

    // ==== Instance ====
    private final Context app;
    private final Object lock = new Object();
    private final Handler main = new Handler(Looper.getMainLooper());

    private List<ZikFile> items = Collections.emptyList();
    private int index = -1;

    private Folder folder;
    private Podcast podcast;
    private boolean isPodcast;
    private boolean metaLoaded = false;
    private OnMetaLoadedListener metaListener;

    // Invalidate racing async loads
    private long version = 0L;

    private PlayList(Context app) { this.app = app; }

    // ==== Public API (kept compatible) ====

    public interface OnMetaLoadedListener {
        void onMetaLoaded(Folder folder, @Nullable Podcast podcast, boolean isPodcast);
    }

    public void setOnMetaLoadedListener(@Nullable OnMetaLoadedListener l) {
        synchronized (lock) {
            this.metaListener = l;
            if (metaLoaded && folder != null && l != null) {
                // deliver on main thread
                main.post(() -> l.onMetaLoaded(folder, podcast, isPodcast));
            }
        }
    }

    public void clear() {
        synchronized (lock) {
            items = Collections.emptyList();
            index = -1;
            folder = null;
            podcast = null;
            isPodcast = false;
            metaLoaded = false;
            version++;
        }
        clearStorage();
        instance = null;
    }

    public void nextTrack() {
        synchronized (lock) {
            if (index >= 0 && index + 1 < items.size()) {
                index++;
                saveToStorage();
            }
        }
        myLogD("nextTrack() index=" + getNumZikFile());
    }

    public boolean isLastTrack() {
        synchronized (lock) {
            return !items.isEmpty() && index == items.size() - 1;
        }
    }

    public int getSize() {
        synchronized (lock) { return items.size(); }
    }

    public String getNumSlashTotal() {
        synchronized (lock) {
            int cur = (index >= 0) ? (index + 1) : 0;
            return cur + "/" + items.size();
        }
    }

    public static boolean isAvailable() { return instance != null; }

    public int getNumZikFile() {
        synchronized (lock) { return index; }
    }

    public void setNumZikFile(int newIndex) {
        synchronized (lock) {
            if (items.isEmpty()) {
                myLogW("setNumZikFile(): items empty");
                index = -1;
            } else {
                index = clamp(newIndex, 0, items.size() - 1);
            }
            saveToStorage();
        }
        myLog("setNumZikFile(" + newIndex + ") -> " + getNumSlashTotal());
    }

    public @Nullable ZikFile getZikFile() {
        synchronized (lock) {
            if (items.isEmpty() || index < 0 || index >= items.size()) {
                myLogEE(null, "getZikFile(): out of bounds index=" + index + " size=" + items.size());
                return null;
            }
            return items.get(index);
        }
    }

    public @Nullable Folder getFolder() { synchronized (lock) { return folder; } }
    public @Nullable Podcast getPodcast() { synchronized (lock) { return podcast; } }
    public boolean isPodcast() { synchronized (lock) { return isPodcast; } }

    // ==== Internal helpers ====

    private void replaceItems(@NonNull List<ZikFile> list, int startIndex) {
        synchronized (lock) {
            this.items = Collections.unmodifiableList(list);
            this.index = clamp(startIndex, 0, list.size() - 1);
            this.folder = null;
            this.podcast = null;
            this.isPodcast = false;
            this.metaLoaded = false;
            this.version++; // invalidate prior asyncs
        }
    }

    private void loadMetaAsync() {
        final long v;
        final int folderId;
        synchronized (lock) {
            v = version;
            if (items.isEmpty()) return;
            folderId = items.get(0).getIdFolder();
        }
        AppDatabase.databaseReadExecutor.execute(() -> {
            Folder f = null;
            Podcast p = null;
            boolean isPod = false;
            try {
                f = AppDatabase.getDatabase(app).FolderDao().getById(folderId);
                if (f != null) {
                    p = AppDatabase.getDatabase(app).PodcastDao().getPodcastByFolderId(f.getId());
                    isPod = (p != null);
                }
            } catch (Throwable t) {
                myLogEE(t, "loadMetaAsync()");
            }

            final Folder fFinal = f;
            final Podcast pFinal = p;
            final boolean isPodFinal = isPod;

            synchronized (lock) {
                if (v != version) {
                    myLogD("loadMetaAsync(): stale result ignored");
                    return;
                }
                folder = fFinal;
                podcast = pFinal;
                isPodcast = isPodFinal;
                metaLoaded = (folder != null);
            }

            OnMetaLoadedListener l;
            synchronized (lock) { l = metaListener; }
            if (metaLoaded && l != null && fFinal != null) {
                main.post(() -> l.onMetaLoaded(fFinal, pFinal, isPodFinal));
            }
        });
    }

    // ==== Persistence (SharedPreferences) ====
    private static final String PREFS = "SHARED_PREFERENCE_CURRENT_PLAYLIST";
    // New keys
    private static final String KEY_FOLDER_ID = "KEY_FOLDER_ID";
    private static final String KEY_INDEX = "KEY_INDEX";
    // Legacy keys
    private static final String KEY_ZIK_FILES_LIST_LEGACY = "KEY_ZIK_FILES_LIST";
    private static final String KEY_ZIK_FILE_INDEX_LEGACY = "KEY_ZIK_FILE";

    private void saveToStorage() {
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor e = prefs.edit();
        int folderId = -1;
        synchronized (lock) {
            if (!items.isEmpty()) folderId = items.get(0).getIdFolder();
            e.putInt(KEY_FOLDER_ID, folderId);
            e.putInt(KEY_INDEX, index);
        }
        e.apply();
        myLogD("saveToStorage(): folderId=" + folderId + " index=" + index);
    }

    private void clearStorage() {
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
        myLogW("clearStorage()");
    }

    // ==== Utils / logging ====
    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private static final String TAG = "PlayList";
    private static void myLog(String s)  { KanLogger.myLog(TAG, s); }
    private static void myLogD(String s) { KanLogger.myLogD(TAG, s); }
    private static void myLogW(String s) { KanLogger.myLogW(TAG, s); }
    private static void myLogE(String s) { KanLogger.myLogE(TAG, s); }
    private static void myLogEE(Throwable t, String s) { KanLogger.myLogEE(t, TAG, s); }
}
