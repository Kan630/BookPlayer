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
import com.driot.bookplayer.utils.log.KanLogger;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

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
        public final @Nullable Podcast podcast;
        public final boolean isPodcast;

        public MetaState(boolean loaded,
                         @Nullable Podcast podcast, boolean isPodcast) {
            this.loaded = loaded;
            this.podcast = podcast;
            this.isPodcast = isPodcast;
        }
    }
    // LiveData channel (process-local)
    private static final MutableLiveData<MetaState> metaLive = new MutableLiveData<>(new MetaState(false, null, false));
    public static LiveData<MetaState> getMetaLive() { return metaLive; }

    // (optional) keep last posted to avoid noisy duplicates
    private static @Nullable MetaState lastMetaPosted = null;


    // ==== Singleton ====
    private static volatile PlayList instance;
    public static @Nullable PlayList getInstance() { return instance; }

    // ==== Instance ====
    private final Context app;
    private final Object lock = new Object();

    private List<ZikFile> zikFilesList = Collections.emptyList();
    private int index = -1;

    private Folder folder;
    private Podcast podcast;
    private String url;
    private String playMode;
    private boolean isPodcast;
    private boolean metaLoaded = false;


    // Invalidate racing async loads
    private long version = 0L;

    private PlayList(Context app) { this.app = app; }

    public static void create(@NonNull Context ctx, @NonNull String playMode, @NonNull Folder folder, @NonNull List<ZikFile> items, int startIndex) {
        if (ctx==null) throw new IllegalStateException("PlayList.createFromRadio(): no context");
        if (playMode==null || playMode.isEmpty()) throw new IllegalStateException("PlayList.createFromRadio(): no playMode");
        if (items.isEmpty()) throw new IllegalStateException("PlayList.create(): empty list");
        Context app = ctx.getApplicationContext();
        PlayList pl = new PlayList(app);

        pl.replaceItems(playMode, folder, items, startIndex, null);
        instance = pl;
        pl.saveToStorage();     // persist folderId + index -//TODO remove
        pl.loadMetaAsync();     // async podcast fetch
        myLogD("Playlist [" + playMode + "] created with " + items.size() + " items, index=" + pl.index);
    }

    public static void createFromStream(@NonNull Context ctx, @NonNull String playMode, @NonNull String url) {
        if (ctx==null) throw new IllegalStateException("PlayList.createFromRadio(): no context");
        if (playMode==null || playMode.isEmpty()) throw new IllegalStateException("PlayList.createFromRadio(): no playMode");
        if (url==null || url.isEmpty()) throw new IllegalStateException("PlayList.createFromRadio(): no url");
        Context app = ctx.getApplicationContext();
        PlayList pl = new PlayList(app);

        pl.replaceItems(playMode, null, null, -1, url);
        instance = pl;
        pl.saveToStorage();     // persist folderId + index -//TODO remove
        pl.loadMetaAsync();     // async podcast fetch
        myLogD("Playlist [" + playMode + "] created with url = [" + url + "]");
    }

    public static void createFromStorage(@NonNull Context ctx) {
        Context app = ctx.getApplicationContext();
        PlayList pl = new PlayList(app);
        pl.getFromStorage();
    }


    public void clear() {
        synchronized (lock) {
            zikFilesList = Collections.emptyList();
            index = -1;
            folder = null;
            podcast = null;
            url = null;
            isPodcast = false;
            metaLoaded = false;
            version++;
        }
        clearStorage();
        instance = null;
        // tell observers meta is gone
        postMetaDistinct(new MetaState(false, null, false));
    }

    public ZikFile nextTrack() {
        synchronized (lock) {
            if (index >= 0 && index + 1 < zikFilesList.size()) {
                index++;
                saveToStorage();
                myLogD("nextTrack() index=" + getNumZikFile());
                return zikFilesList.get(index);
            } else {
                myLogW("nextTrack() return null");
                return null;
            }
        }
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

    public @Nullable ZikFile getZikFile() {
        synchronized (lock) {
            if (zikFilesList.isEmpty() || index < 0 || index >= zikFilesList.size()) {
                myLogEE(null, "getZikFile(): out of bounds index=" + index + " size=" + zikFilesList.size());
                return null;
            }
            return zikFilesList.get(index);
        }
    }
    public @Nullable Folder getFolder() {
        synchronized (lock) {
            return folder;
        }
    }
    public @NonNull String getPlayMode() {
        synchronized (lock) {
            return playMode;
        }
    }
    public @NonNull String getUrl() {
        synchronized (lock) {
            return url;
        }
    }


    // ==== Internal helpers ====

    private void postMetaDistinct(MetaState s) {
        // very light distinct; compare isPodcast & loaded
        boolean same =
                lastMetaPosted != null &&
                        lastMetaPosted.loaded == s.loaded &&
                        lastMetaPosted.isPodcast == s.isPodcast &&
                        Objects.equals(lastMetaPosted.podcast, s.podcast)
                ;
        if (!same) {
            lastMetaPosted = s;
            metaLive.postValue(s); // background-safe
            myLogD("metaLive posted: loaded=" + s.loaded + " - isPodcast=" + s.isPodcast);
        }
    }

    private void replaceItems(String playMode, Folder folder, List<ZikFile> list, int startIndex, String url) {
        synchronized (lock) {
            //direct args
            this.playMode = playMode;
            this.folder = folder;
            if (list!=null) {
                this.zikFilesList = Collections.unmodifiableList(list);
                this.index = clamp(startIndex, 0, list.size() - 1);
            } else {
                this.zikFilesList = Collections.emptyList();
                this.index = -1;
            }
            this.url = url;
            // async data
            this.podcast = null;
            this.isPodcast = false;
            this.metaLoaded = false;
            this.version++; // invalidate prior asyncs
        }
        postMetaDistinct(new MetaState(false, null, false));
    }

    private void loadMetaAsync() {
        final long v;
        synchronized (lock) {
            v = version;
            if (zikFilesList.isEmpty()) return;
        }
        AppDatabase.databaseReadExecutor.execute(() -> {
            Podcast p = null;
            boolean isPod = false;
            try {
                if (folder != null) {
                    p = AppDatabase.getDatabase(app).podcastDao().getPodcastByFolderId(folder.getId());
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
                podcast = p;
                isPodcast = isPod;
                metaLoaded = (folder != null);
            }
            // LiveData: post state (loaded==true only if folder!=null)
            postMetaDistinct(new MetaState(metaLoaded, podcast, isPodcast));

        });
    }

    // ==== Persistence (SharedPreferences) ====
    private static final String PREFS = "SHARED_PREFERENCE_CURRENT_PLAYLIST";
    private static final String KEY_PLAY_MODE = "KEY_PLAY_MODE";
    private static final String KEY_FOLDER_ID = "KEY_FOLDER_ID";
    private static final String KEY_URL = "KEY_URL";
    private static final String KEY_INDEX = "KEY_INDEX";

    private void saveToStorage() {
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor e = prefs.edit();
        int folderId = -1;
        synchronized (lock) {
            if (!zikFilesList.isEmpty()) folderId = zikFilesList.get(0).getIdFolder();
            e.putInt(KEY_FOLDER_ID, folderId);
            e.putInt(KEY_INDEX, index);
            e.putString(KEY_PLAY_MODE, playMode);
            e.putString(KEY_URL, url);
        }
        e.apply();
        myLogD("saveToStorage(): folderId=" + folderId + " index=" + index);
    }

    private void getFromStorage() {
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        //TODO, not finished, load async fucking folder, or delete all this shit and play with "playFromTrackId"...  the cover and other shit got covered by UiState updates

        synchronized (lock) {
            this.playMode = prefs.getString(KEY_PLAY_MODE, null);
            this.url      = prefs.getString(KEY_URL, null);

            this.index    = prefs.getInt(KEY_INDEX, -1);
            int folderId  = prefs.getInt(KEY_FOLDER_ID, -1);

            myLogD("getFromStorage(): folderId=" + folderId + " index=" + index + " playMode=" + playMode + " url=" + url);
        }

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
