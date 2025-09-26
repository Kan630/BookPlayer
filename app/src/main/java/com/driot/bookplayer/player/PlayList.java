package com.driot.bookplayer.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.utils.KanLogger;

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

    /** Immutable meta state exposed to UI */
    public static final class MetaState {
        public final boolean loaded;
        public final @Nullable Folder folder;
        public final @Nullable Podcast podcast;
        public final boolean isPodcast;

        public MetaState(boolean loaded, @Nullable Folder folder,
                         @Nullable Podcast podcast, boolean isPodcast) {
            this.loaded = loaded;
            this.folder = folder;
            this.podcast = podcast;
            this.isPodcast = isPodcast;
        }
    }
    // LiveData channel (process-local)
    private static final MutableLiveData<MetaState> metaLive = new MutableLiveData<>(new MetaState(false, null, null, false));
    public static LiveData<MetaState> getMetaLive() { return metaLive; }

    // (optional) keep last posted to avoid noisy duplicates
    private static @Nullable MetaState lastMetaPosted = null;


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

    // ==== Instance ====
    private final Context app;
    private final Object lock = new Object();
    private final Handler main = new Handler(Looper.getMainLooper());

    private List<ZikFile> zikFilesList = Collections.emptyList();
    private int index = -1;

    private Folder folder;
    private Podcast podcast;
    private boolean isPodcast;
    private boolean metaLoaded = false;
/*
    public Folder getFolder() {
        return folder;
    }

 */

    // Invalidate racing async loads
    private long version = 0L;

    private PlayList(Context app) { this.app = app; }

    // ==== Public API (kept compatible) ====
/*
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

 */

    public void clear() {
        synchronized (lock) {
            zikFilesList = Collections.emptyList();
            index = -1;
            folder = null;
            podcast = null;
            isPodcast = false;
            metaLoaded = false;
            version++;
        }
        clearStorage();
        instance = null;

        // tell observers meta is gone
        postMetaDistinct(new MetaState(false, null, null, false));
    }

    public void nextTrack() {
        synchronized (lock) {
            if (index >= 0 && index + 1 < zikFilesList.size()) {
                index++;
                saveToStorage();
            }
        }
        myLogD("nextTrack() index=" + getNumZikFile());
    }

    public boolean isLastTrack() {
        synchronized (lock) {
            return !zikFilesList.isEmpty() && index == zikFilesList.size() - 1;
        }
    }

    public int getSize() {
        synchronized (lock) { return zikFilesList.size(); }
    }

    public String getNumSlashTotal() {
        synchronized (lock) {
            int cur = (index >= 0) ? (index + 1) : 0;
            return cur + "/" + zikFilesList.size();
        }
    }

    public static boolean isAvailable() { return instance != null; }

    public int getNumZikFile() {
        synchronized (lock) { return index; }
    }

    public void setNumZikFile(int newIndex) {
        synchronized (lock) {
            if (zikFilesList.isEmpty()) {
                myLogW("setNumZikFile(): items empty");
                index = -1;
            } else {
                index = clamp(newIndex, 0, zikFilesList.size() - 1);
            }
            saveToStorage();
        }
        myLog("setNumZikFile(" + newIndex + ") -> " + getNumSlashTotal());
    }

    public @Nullable ZikFile getZikFile() {
        synchronized (lock) {
            if (zikFilesList.isEmpty() || index < 0 || index >= zikFilesList.size()) {
                myLogEE(null, "getZikFile(): out of bounds index=" + index + " size=" + zikFilesList.size());
                return null;
            }
            return zikFilesList.get(index);
        }
    }
/*
    public @Nullable Folder getFolder() { synchronized (lock) { return folder; } }
    public @Nullable Podcast getPodcast() { synchronized (lock) { return podcast; } }
    public boolean isPodcast() { synchronized (lock) { return isPodcast; } }

 */

    // ==== Internal helpers ====

    private void postMetaDistinct(MetaState s) {
        // very light distinct; compare folder id & isPodcast & loaded
        boolean same =
                lastMetaPosted != null &&
                        lastMetaPosted.loaded == s.loaded &&
                        lastMetaPosted.isPodcast == s.isPodcast &&
                        ((lastMetaPosted.folder == null && s.folder == null) ||
                                (lastMetaPosted.folder != null && s.folder != null && lastMetaPosted.folder.getId() == s.folder.getId()));
        if (!same) {
            lastMetaPosted = s;
            metaLive.postValue(s); // background-safe
            myLogD("metaLive posted: loaded=" + s.loaded + " folderId=" + (s.folder == null ? -1 : s.folder.getId()) + " isPodcast=" + s.isPodcast);
        }
    }

    private void replaceItems(@NonNull List<ZikFile> list, int startIndex) {
        synchronized (lock) {
            this.zikFilesList = Collections.unmodifiableList(list);
            this.index = clamp(startIndex, 0, list.size() - 1);
            this.folder = null;
            this.podcast = null;
            this.isPodcast = false;
            this.metaLoaded = false;
            this.version++; // invalidate prior asyncs
        }
        postMetaDistinct(new MetaState(false, null, null, false));
    }

    private void loadMetaAsync() {
        final long v;
        final int folderId;
        synchronized (lock) {
            v = version;
            if (zikFilesList.isEmpty()) return;
            folderId = zikFilesList.get(0).getIdFolder();
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

            synchronized (lock) {
                if (v != version) {
                    myLogD("loadMetaAsync(): stale result ignored");
                    return;
                }
                folder = f;
                podcast = p;
                isPodcast = isPod;
                metaLoaded = (folder != null);
            }
            // LiveData: post state (loaded==true only if folder!=null)
            postMetaDistinct(new MetaState(metaLoaded, folder, podcast, isPodcast));

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
            if (!zikFilesList.isEmpty()) folderId = zikFilesList.get(0).getIdFolder();
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
