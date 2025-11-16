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
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
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

    // ==== Singleton ====
    private static volatile PlayList instance;
    public static @Nullable PlayList getInstance() { return instance; }

    // ==== Instance ====
    private final Context app;
    private final Object lock = new Object();

    // primary data
    private String playMode;
    private String url;
    private int trackId;
    private Folder folder;
    private ZikFile zikFile;
    private List<ZikFile> zikFilesList = Collections.emptyList();
    private int index = -1;
    private Podcast podcast;
    private boolean isPodcast;

    // async meta data
    private boolean metaLoaded = false;

    // Invalidate racing async loads
    private long version = 0L;

    private PlayList(Context app) { this.app = app; }

    //public static void create(@NonNull Context ctx, @NonNull String playMode, @NonNull Folder folder, @NonNull List<ZikFile> items, int startIndex) {
    public static void createFromTrackId(@NonNull Context ctx, @NonNull String playMode, int trackId) {
        if (ctx==null) throw new IllegalStateException("PlayList.createFromTrackId(): no context");
        if (playMode==null || playMode.isEmpty()) throw new IllegalStateException("PlayList.createFromTrackId(): no playMode");
        if (trackId<0) throw new IllegalStateException("PlayList.createFromTrackId(): trackId=" + trackId);
        Context app = ctx.getApplicationContext();
        PlayList pl = new PlayList(app);

        pl.replaceItems(playMode, trackId, null);
        instance = pl;
        pl.saveToStorage();     // persist folderId + index -//TODO remove
        myLogD("Playlist [" + playMode + "] created for trackId = [" + trackId + "]  - toString: " + pl);
    }

    public static void createFromZikFile(@NonNull Context ctx, @NonNull String playMode, Folder folder, ZikFile zikFile, List<ZikFile> list, int index) {
        if (ctx==null) throw new IllegalStateException("PlayList.createFromTrackId(): no context");
        if (playMode==null || playMode.isEmpty()) throw new IllegalStateException("PlayList.createFromTrackId(): no playMode");
        if (zikFile==null) throw new IllegalStateException("PlayList.createFromTrackId(): zikfile = null");
        Context app = ctx.getApplicationContext();
        PlayList pl = new PlayList(app);

        pl.replaceItems(playMode, folder, zikFile, list, index, null);
        instance = pl;
        pl.saveToStorage();
        myLogD("Playlist [" + playMode + "] created for zikFile = [" + zikFile.getDisplayName() + "]  - toString: " + pl);
    }

    public static void createFromStream(@NonNull Context ctx, @NonNull String playMode, @NonNull String url) {
        if (ctx==null) throw new IllegalStateException("PlayList.createFromRadio(): no context");
        if (playMode==null || playMode.isEmpty()) throw new IllegalStateException("PlayList.createFromRadio(): no playMode");
        if (url==null || url.isEmpty()) throw new IllegalStateException("PlayList.createFromRadio(): no url");
        Context app = ctx.getApplicationContext();
        PlayList pl = new PlayList(app);

        pl.replaceItems(playMode, -1, url);
        instance = pl;
        pl.saveToStorage();
        myLogD("Playlist [" + playMode + "] created for url = [" + url + "]" + " - toString: " + pl);
    }

    public static void createFromStorage(@NonNull Context ctx) {
        Context app = ctx.getApplicationContext();
        PlayList pl = new PlayList(app);
        pl.getFromStorage();
        instance = pl;
        myLogEE(null,"Playlist created from storage " + " - toString: " + pl);
        FirebaseAnalyticsHelper.tellAnalyticsPlaylistLoadFromStorage(ctx, pl.toString());
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

    private void replaceItems(String playMode, Folder folder, ZikFile zikFile, List<ZikFile> list, int startIndex, String url) {
        synchronized (lock) {
            //direct args
            this.playMode = playMode;
            this.zikFile = zikFile;
            this.folder = folder;
            if (list!=null) {
                this.zikFilesList = Collections.unmodifiableList(list);
                this.index = clamp(startIndex, 0, list.size() - 1);
            } else {
                this.zikFilesList = Collections.emptyList();
                this.index = -1;
            }
            this.url = url;
            this.version++; // invalidate prior asyncs
        }
    }

    private void replaceItems(String playMode, ZikFile zikFile, String url) {
        synchronized (lock) {
            //direct args
            this.playMode = playMode;
            this.zikFile = zikFile;
            this.url = url;
            this.podcast = null;
            this.isPodcast = false;
            this.metaLoaded = false;
            this.version++; // invalidate prior asyncs
        }
    }

    private void replaceItems(String playMode, int trackId, String url) {
        synchronized (lock) {
            //direct args
            this.playMode = playMode;
            this.trackId = trackId;
            this.url = url;
            this.podcast = null;
            this.isPodcast = false;
            this.metaLoaded = false;
            this.version++; // invalidate prior asyncs
        }
    }

    // ==== Persistence (SharedPreferences) ====
    private static final String PREFS = "SHARED_PREFERENCE_CURRENT_PLAYLIST";
    private static final String KEY_PLAY_MODE = "KEY_PLAY_MODE";
    private static final String KEY_TRACK_ID = "KEY_TRACK_ID";
    private static final String KEY_URL = "KEY_URL";

    private void saveToStorage() {
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor e = prefs.edit();
        synchronized (lock) {
            e.putInt(KEY_TRACK_ID, trackId);
            e.putString(KEY_PLAY_MODE, playMode);
            e.putString(KEY_URL, url);
        }
        e.apply();
    }

    private void getFromStorage() {
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        synchronized (lock) {
            this.playMode = prefs.getString(KEY_PLAY_MODE, null);
            this.trackId    = prefs.getInt(KEY_TRACK_ID, -1);
            this.url      = prefs.getString(KEY_URL, null);
            //myLogD("getFromStorage(): folderId=" + folderId + " index=" + index + " playMode=" + playMode + " url=" + url);
        }
    }



    private void clearStorage() {
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
        myLogW("clearStorage()");
    }

    @Override
    public String toString() {
        return "PlayList{" +
                "playMode='" + playMode + '\'' +
                ", app=" + app +
                ", trackId=" + trackId +
                ", url='" + url + '\'' +
                ", lock=" + lock +
                ", version=" + version +
                //", zikFilesList=" + zikFilesList +
                ", index=" + index +
                ", podcast=" + podcast +
                ", folder=" + folder +
                ", isPodcast=" + isPodcast +
                ", metaLoaded=" + metaLoaded +
                '}';
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
