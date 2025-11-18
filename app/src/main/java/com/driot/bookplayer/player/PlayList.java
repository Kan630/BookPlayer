package com.driot.bookplayer.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.utils.log.KanLogger;

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

    // Invalidate racing async loads
    private long version = 0L;

    private PlayList(Context app) { this.app = app; }

    public interface OnRestoredListener { void onRestored(@Nullable PlayList pl); }

    public static void createFromZikFile(@NonNull Context ctx, @NonNull String playMode, Folder folder, ZikFile zikFile, List<ZikFile> list, int index) {
        if (ctx==null) throw new IllegalStateException("PlayList.createFromTrackId(): no context");
        if (playMode==null || playMode.isEmpty()) throw new IllegalStateException("PlayList.createFromTrackId(): no playMode");
        if (zikFile==null) throw new IllegalStateException("PlayList.createFromTrackId(): zikfile = null");
        if (folder==null) throw new IllegalStateException("PlayList.createFromTrackId(): folder = null");
        if (list==null || list.isEmpty()) throw new IllegalStateException("PlayList.createFromTrackId(): List<ZikFile> = null or empty");
        Context app = ctx.getApplicationContext();
        PlayList pl = new PlayList(app);

        pl.replaceItemsForBook(playMode, folder, zikFile, list, index);
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

        pl.replaceItemsForStream(playMode, url);
        instance = pl;
        pl.saveToStorage();
        myLogD("Playlist [" + playMode + "] created for url = [" + url + "]" + " - toString: " + pl);
    }

    public static void createFromStorage(@NonNull Context ctx, boolean createFromLastListenedIfNothingToRestore, @NonNull OnRestoredListener listener) {
        Context app = ctx.getApplicationContext();
        PlayList pl = new PlayList(app);
        pl.getFromStorage();

        final boolean nothingToRestore;
        synchronized (pl.lock) {
            nothingToRestore =
                    (pl.playMode == null)
                            && (pl.trackId == -1)
                            && (pl.url == null || pl.url.isEmpty());
        }

        if (nothingToRestore) {
            myLogW("createFromStorage(): nothing to restore from SharedPreferences");

            if (!createFromLastListenedIfNothingToRestore) {
                // No fallback requested -> just notify caller with null
                new Handler(Looper.getMainLooper()).post(() -> listener.onRestored(null));
                return;
            }

            // === FALLBACK: create playlist from last listened ZikFile ===
            AppDatabase.databaseReadExecutor.execute(() -> {

                try {
                    AppDatabase db = AppDatabase.getInstance(app);
                    ZikFile last = db.zikFileDao().getLastListenedZikFile();

                    if (last == null) {
                        myLogW("createFromStorage(): fallback failed, no last listened ZikFile");
                        new Handler(Looper.getMainLooper()).post(() -> listener.onRestored(null));
                        return;
                    }

                    Folder folder = db.folderDao().getById(last.getIdFolder());
                    if (folder == null) {
                        myLogW("createFromStorage(): fallback failed, folder not found for ZikFile id=" + last.getId());
                        new Handler(Looper.getMainLooper()).post(() -> listener.onRestored(null));
                        return;
                    }

                    List<ZikFile> list = db.zikFileDao().getZikFiles(folder.getId());
                    if (list == null || list.isEmpty()) {
                        myLogW("createFromStorage(): fallback failed, no ZikFiles for folder id=" + folder.getId());
                        new Handler(Looper.getMainLooper()).post(() -> listener.onRestored(null));
                        return;
                    }

                    int idx = 0;
                    for (int i = 0; i < list.size(); i++) {
                        if (list.get(i).getId() == last.getId()) {
                            idx = i;
                            break;
                        }
                    }

                    PlayList fallbackPl = new PlayList(app);
                    String playMode = (Var.PLAY_TYPE_TEXT.equals(folder.playType) ? Var.PLAY_MODE_TTS : Var.PLAY_MODE_BOOK);
                    fallbackPl.replaceItemsForBook(playMode, folder, last, list, idx);
                    instance = fallbackPl;
                    fallbackPl.saveToStorage(); // so next time, normal restore works

                    FirebaseAnalyticsHelper.tellAnalyticsPlaylistLoadFromStorage(ctx, "room", fallbackPl.toString());

                    new Handler(Looper.getMainLooper()).post(() -> listener.onRestored(fallbackPl));

                    myLogD("createFromStorage(): fallback playlist created from last listened " +
                            "folder=" + folder.getName() +
                            " index=" + idx +
                            " size=" + list.size());

                } catch (Exception e) {
                    myLogEE(e, "createFromStorage(): error in fallback (last listened)");
                    new Handler(Looper.getMainLooper()).post(() -> listener.onRestored(null));
                }
            });

            return;
        }

        // === Normal path: we have something in prefs ===
        instance = pl;
        myLogEE(null, "Playlist created from storage - toString: " + pl);
        FirebaseAnalyticsHelper.tellAnalyticsPlaylistLoadFromStorage(ctx, "pref", pl.toString());

        // decide if we need DB
        String mode;
        String urlSnapshot;
        synchronized (pl.lock) {
            mode = pl.playMode;
            urlSnapshot = pl.url;
        }

        if (Var.PLAY_MODE_BOOK.equals(mode) || Var.PLAY_MODE_TTS.equals(mode)) {
            pl.restoreFromDbAsync(listener);
        } else {
            // radio / podcast stream: URL is enough, no DB work
            new Handler(Looper.getMainLooper()).post(() -> listener.onRestored(pl));
        }
    }


    public void clear() {
        synchronized (lock) {
            zikFilesList = Collections.emptyList();
            index = -1;
            folder = null;
            podcast = null;
            url = null;
            isPodcast = false;
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

    private void replaceItemsForBook(String playMode, Folder folder, ZikFile zikFile, List<ZikFile> list, int startIndex) {
        synchronized (lock) {
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
            this.trackId = zikFile.getId();
            this.url = null;
            this.version++; // invalidate prior asyncs
        }
    }

    private void replaceItemsForStream(String playMode, String url) {
        synchronized (lock) {
            this.playMode = playMode;
            this.trackId = -1;
            this.zikFilesList = Collections.emptyList();
            this.index = -1;
            this.url = url;
            this.podcast = null;
            this.isPodcast = false;
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

    private void restoreFromDbAsync(@NonNull OnRestoredListener listener) {
        final long startVersion;
        final int savedTrackId;

        synchronized (lock) {
            startVersion = version;
            savedTrackId = trackId;
        }

        if (savedTrackId <= 0) {
            myLogW("restoreFromDbAsync(): invalid trackId=" + savedTrackId);
            new Handler(Looper.getMainLooper()).post(() -> listener.onRestored(null));
            return;
        }

        AppDatabase db = AppDatabase.getInstance(app);

        new Thread(() -> {
            try {
                ZikFile track = db.zikFileDao().getById(savedTrackId);
                if (track == null) {
                    myLogW("restoreFromDbAsync(): track not found for id=" + savedTrackId);
                    new Handler(Looper.getMainLooper()).post(() -> listener.onRestored(null));
                    return;
                }

                Folder folder = db.folderDao().getById(track.getIdFolder());
                List<ZikFile> list = db.zikFileDao().getZikFiles(folder.getId());

                int idx = -1;
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i).getId() == savedTrackId) {
                        idx = i;
                        break;
                    }
                }
                if (idx == -1 && !list.isEmpty()) idx = 0;

                synchronized (lock) {
                    if (version != startVersion) {
                        myLogW("restoreFromDbAsync(): stale result, ignoring");
                        return;
                    }
                    this.folder = folder;
                    this.zikFilesList = Collections.unmodifiableList(list);
                    this.index = idx;
                    this.zikFile = (idx >= 0 && idx < list.size()) ? list.get(idx) : null;
                    version++; // consume this async result
                }

                new Handler(Looper.getMainLooper()).post(() -> listener.onRestored(this));

                myLogD("restoreFromDbAsync(): restored folder=" +
                        (folder != null ? folder.getName() : "null") +
                        " index=" + idx + " size=" + list.size());

            } catch (Exception e) {
                myLogEE(e, "restoreFromDbAsync(): error restoring from DB");
                new Handler(Looper.getMainLooper()).post(() -> listener.onRestored(null));
            }
        }).start();
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
                ", zikFilesList.count=" + zikFilesList.size() +
                ", index=" + index +
                ", podcast=" + podcast +
                ", folder=" + folder +
                ", isPodcast=" + isPodcast +
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
