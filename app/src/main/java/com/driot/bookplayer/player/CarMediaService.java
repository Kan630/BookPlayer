package com.driot.bookplayer.player;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.utils.log.LoggingMediaBrowserServiceCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CarMediaService extends LoggingMediaBrowserServiceCompat {

    private final android.os.Handler carH = new android.os.Handler(android.os.Looper.getMainLooper());
    private final java.util.Set<Integer> pendingFolderRefresh = new java.util.HashSet<>();

    // Small caches to avoid re-decoding
    private final android.util.LruCache<String, android.graphics.Bitmap> artCache  = new android.util.LruCache<>(8);
    private final android.util.LruCache<String, android.graphics.Bitmap> iconCache = new android.util.LruCache<>(24);
    private static final int ART_MAX_PX  = 512;  // big artwork
    private static final int ICON_MAX_PX = 128;  // list thumbnails

    private static final java.util.concurrent.Executor imgExec =
            java.util.concurrent.Executors.newFixedThreadPool(1);

    public static final String ROOT_ID = "root";
    private static final String PREFIX_FOLDER = "folder:";
    private static final String PREFIX_TRACK  = "track:";

    private MediaSessionCompat mediaSession;

    // --- cache UI côté voiture (alimenté par AudioService via broadcast)
    private boolean playing = false;
    private long posMs = 0;
    private long durMs = 0;
    private String title = "";
    private String subtitle = "";
    private String cover = "";
    private int curTrackId = 0;
    private int curFolderId = 0;

    // For spam prevention in AA updates
    private String lastTitle = null, lastSubtitle = null, lastCover = null;
    private long lastDur = -1;
    private int lastTrackId = 0, lastFolderId = 0;


    @Override
    public String toString() {
        return "CarMediaService{" +
                "artCache=" + artCache +
                ", iconCache=" + iconCache +
                ", mediaSession=" + mediaSession +
                ", playing=" + playing +
                ", posMs=" + posMs +
                ", durMs=" + durMs +
                ", title='" + title + '\'' +
                ", subtitle='" + subtitle + '\'' +
                ", cover='" + cover + '\'' +
                ", uiReceiver=" + uiReceiver +
                '}';
    }

    private final BroadcastReceiver uiReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent i) {
            if (!Intents.ACTION_UI_STATE.equals(i.getAction())) return;

            int prevTrackId  = curTrackId;
            int prevFolderId = curFolderId;

            playing  = i.getBooleanExtra(Intents.EXTRA_UI_PLAYING, false);
            posMs    = i.getLongExtra(Intents.EXTRA_UI_POS, 0);
            durMs    = i.getLongExtra(Intents.EXTRA_UI_DUR, 0);
            title    = i.getStringExtra(Intents.EXTRA_UI_TITLE);
            subtitle = i.getStringExtra(Intents.EXTRA_UI_SUBTITLE);
            cover    = i.getStringExtra(Intents.EXTRA_UI_COVER);
            curTrackId  = i.getIntExtra(Intents.EXTRA_UI_TRACK_ID, 0);
            curFolderId = i.getIntExtra(Intents.EXTRA_UI_FOLDER_ID, 0);

            // push metadata + playbackstate vers Android Auto
            pushMetadataFromCurrent();
            pushPlaybackState(playing ? PlaybackStateCompat.STATE_PLAYING
                    : PlaybackStateCompat.STATE_PAUSED, posMs);

            if (curFolderId > 0 && (curFolderId != prevFolderId || curTrackId != prevTrackId)) {
                requestFolderRefresh(curFolderId);
                // If your ROOT shows “Continue …”, refresh it too:
                //requestRootRefresh(); // optional
            }
        }
    };

    @Override
    public void onCreate() {
        CarSignals.markCarConnected();
        super.onCreate();

        // 1) MediaSession locale pour Android Auto
        mediaSession = new MediaSessionCompat(this, "BookPlayerCarSession");
        setSessionToken(mediaSession.getSessionToken());

        mediaSession.setCallback(new MediaSessionCompat.Callback() {

            @Override public void onPlay() {
                myLog("onPlay()");

                PlayList pl = PlayList.getInstance();
                if (pl==null) {
                    myLogI("Car onPlay but Playlist is null");
                    if (Option.getAutomotiveLetCarAutoplay()) {
                        myLogD("Car AutoPlay option enabled");
                        FirebaseAnalyticsHelper.tellCarAutoPlay();
                        AppDatabase.databaseReadExecutor.execute(() -> {
                            ZikFile zikFile = AppDatabase.getDatabase(getApplicationContext())
                                    .zikFileDao().getLastListenedZikFile();
                            if (zikFile==null) {
                                myLogW("no last played zikfile !, must be pretty new");
                            } else {
                                myLog("go for last played zikfile : [" + zikFile.getDisplayName() + "], starting FOREGROUND");
                                ContextCompat.startForegroundService(
                                        CarMediaService.this,
                                        new Intent(CarMediaService.this, AudioService.class)
                                                .setAction(Intents.ACTION_PLAY_FROM_TRACK)
                                                .putExtra(Intents.EXTRA_TRACK_ID, zikFile.getId())
                                                .putExtra(Intents.EXTRA_CALLER, this.getClass().getSimpleName() + ".onPlay()")
                                                .putExtra(Intents.EXTRA_FOREGROUND, true)
                                );
                                // Optional: show buffering right away in AA
                                pushPlaybackState(PlaybackStateCompat.STATE_BUFFERING, 0);
                            }
                        });
                    } else {
                        myLogW("Android Auto not authorized to start audio (from Bookplayer settings)");
                    }
                } else {
                    ZikFile zikFile = pl.getZikFile();
                    if (zikFile==null) {
                        myLogEE(null,"Car onPlay but ZikFile is null");
                    } else {
                        if (Option.getAutomotiveAutoResumeOnCarConnect()) {
                            myLog("resuming play : [" + zikFile.getDisplayName() + "]");
                            sendCmd("CMD_PLAY");
                        } else {
                            myLogW("Android Auto not authorized to resume playback (from Bookplayer settings)");
                        }
                    }
                }
            }

            @Override public void onPause()             { sendCmd("CMD_PAUSE"); }
            @Override public void onSkipToNext()        { sendCmd("CMD_NEXT"); }
            @Override public void onSkipToPrevious()    { sendCmd("CMD_PREV"); }
            @Override public void onSeekTo(long posMs)  {
                FirebaseAnalyticsHelper.tellCarSendCmd("CMD_SEEK");
                Intent i = new Intent(CarMediaService.this, AudioService.class).setAction("CMD_SEEK");
                i.putExtra("posMs", (int) posMs);
                i.putExtra(Intents.EXTRA_CALLER, this.getClass().getSimpleName() + " (CarMediaService)");
                startService(i);
            }
            @Override
            public void onPlayFromMediaId(String mediaId, Bundle extras) {
                myLogI("---- AUTOMOTIVE user click Play -----");
                FirebaseAnalyticsHelper.tellCarOnPlayFromMediaId();
                if (mediaId == null) return;

                if (mediaId.startsWith(PREFIX_TRACK)) {
                    int trackId = safeParseInt(mediaId.substring(PREFIX_TRACK.length()), -1);
                    if (trackId > 0) {
                        ContextCompat.startForegroundService(
                                CarMediaService.this,
                                new Intent(CarMediaService.this, AudioService.class)
                                        .setAction(Intents.ACTION_PLAY_FROM_TRACK)
                                        .putExtra(Intents.EXTRA_TRACK_ID, trackId)
                                        .putExtra(Intents.EXTRA_CALLER, this.getClass().getSimpleName() + ".onPlayFromMediaId()")
                                        .putExtra(Intents.EXTRA_FOREGROUND, true)
                        );
                        // Optional: show buffering right away in AA
                        pushPlaybackState(PlaybackStateCompat.STATE_BUFFERING, 0);
                    }
                    return;
                }

                if (mediaId.startsWith(PREFIX_FOLDER)) {
                    int folderId = safeParseInt(mediaId.substring(PREFIX_FOLDER.length()), -1);
                    if (folderId > 0) {
                        // If you want to play index 0 immediately (single-track or your UX choice):
                        ContextCompat.startForegroundService(
                                CarMediaService.this,
                                new Intent(CarMediaService.this, AudioService.class)
                                        .setAction(Intents.ACTION_PLAY_FROM_FOLDER)
                                        .putExtra(Intents.EXTRA_FOLDER_ID, folderId)
                                        .putExtra(Intents.EXTRA_INDEX, 0)
                                        .putExtra(Intents.EXTRA_CALLER, this.getClass().getSimpleName())
                                        .putExtra(Intents.EXTRA_FOREGROUND, true)
                        );
                        pushPlaybackState(PlaybackStateCompat.STATE_BUFFERING, 0);
                    }
                }
            }
            @Override
            public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
                myLog("onMediaButtonEvent : " + mediaButtonIntent.getAction() + " - " + mediaButtonIntent.toString());
                if (mediaButtonIntent == null) return super.onMediaButtonEvent(null);
                // Forward the event to the session (translates KeyEvent → callbacks)
                androidx.media.session.MediaButtonReceiver.handleIntent(mediaSession, mediaButtonIntent);
                return true; // we handled it
            }
        });
        mediaSession.setActive(true);

        // 2) s’abonner aux mises à jour UI de l’AudioService
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(uiReceiver, new IntentFilter(Intents.ACTION_UI_STATE));
        LocalBroadcastManager.getInstance(this)
                .sendBroadcast(new Intent(Intents.ACTION_PING_UI));
        myLog("PING sent");

        // état initial neutre
        pushPlaybackState(PlaybackStateCompat.STATE_NONE, 0);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(uiReceiver);
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
    }

    // --------- Browse tree minimal ----------
    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, Bundle rootHints) {
        myLogI("------------ onGetRoot ------------");
        // Filtre au cas ou je ne set pas la permission dans le manifest pour le service : android:permission="android.permission.BIND_MEDIA_BROWSER_SERVICE"
/*
        if ("com.google.android.projection.gearhead".equals(clientPackageName)
         || "com.google.android.apps.automotive.inputmethod".equals(clientPackageName)
                || "com.google.android.googlequicksearchbox".equals(clientPackageName)) {
            return new BrowserRoot(ROOT_ID, null);
        }
        // Refuser les autres
        return null; // ou renvoyer un BrowserRoot restreint

 */
        CarSignals.markCarConnected();
        FirebaseAnalyticsHelper.tellCarOnRoot();
        return new BrowserRoot(ROOT_ID, null);
    }

// getString(R.string.automotive_no_item_in_bookplayer)
    @Override
    public void onLoadChildren(@NonNull String parentId,
                               @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        myLogI("------------ OnChildren ------------");
        FirebaseAnalyticsHelper.tellCarOnChildren();
        // Chargements DB → thread bg
        result.detach();
        AppDatabase.databaseReadExecutor.execute(() -> {
            List<MediaBrowserCompat.MediaItem> out = new ArrayList<>();

            if (ROOT_ID.equals(parentId)) {
                List<Folder> folders = AppDatabase.getDatabase(getApplicationContext())
                        .folderDao().getAll();

                if (folders == null || folders.isEmpty()) {
                    out.add(browsable("hint", getString(R.string.automotive_no_item_in_bookplayer)));
                    result.sendResult(out);
                    return;
                }

                for (Folder f : folders) {
                    MediaDescriptionCompat.Builder b = new MediaDescriptionCompat.Builder()
                            .setMediaId(PREFIX_FOLDER + f.getId())
                            .setTitle(f.getName());

                    // Small icon
                    Bitmap icon = null;
                    if (f.image != null) {
                        icon = iconCache.get(f.image);
                        if (icon == null) {
                            icon = decodeBitmapFromStringUri(f.image, ICON_MAX_PX);
                            if (icon != null) iconCache.put(f.image, icon);
                        }
                    }
                    if (icon != null) b.setIconBitmap(icon);

                    // If only 1 track => Make the "folder" tap play directly
                    int count = AppDatabase.getDatabase(getApplicationContext())
                            .zikFileDao().countTracks(f.getId());
                    if (count == 1) {
                        ZikFile only = AppDatabase.getDatabase(getApplicationContext()).zikFileDao().getFirstInFolder(f.getId());
                        if (only != null) {
                            b.setSubtitle(only.getDisplayName());      // track label
                            out.add(new MediaBrowserCompat.MediaItem(b.build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE));
                        }
                    } else {
                        out.add(new MediaBrowserCompat.MediaItem(b.build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
                    }
                }

                result.sendResult(out);
                return;
            }

            if (parentId.startsWith(PREFIX_FOLDER)) {
                int folderId = safeParseInt(parentId.substring(PREFIX_FOLDER.length()), -1);
                if (folderId > 0) {
                    List<ZikFile> tracks = AppDatabase.getDatabase(getApplicationContext())
                            .zikFileDao().getZikFiles(folderId);

                    if (tracks == null || tracks.isEmpty()) {
                        out.add(browsable("hint", getString(R.string.automotive_empty_book)));
                        result.sendResult(out);
                        return;
                    }

                    // Put a "Resume" item first
                    ZikFile resume = AppDatabase.getDatabase(this).zikFileDao().getLastListenedZikFileOfFolder(folderId);
                    if (resume != null) {
                        MediaDescriptionCompat.Builder rb = new MediaDescriptionCompat.Builder()
                                .setMediaId(PREFIX_TRACK + resume.getId())
                                .setTitle("▶ " + getString(R.string.automotive_resume_play) + " : \n" + resume.getDisplayName())
                                .setSubtitle(resume.getFolderName());
                        // optional icon from folder cover (reuse your icon code)
                        Folder f = AppDatabase.getDatabase(getApplicationContext()).folderDao().getById(folderId);
                        Bitmap icon = null;
                        if (f != null && f.image != null) {
                            icon = iconCache.get(f.image);
                            if (icon == null) {
                                icon = decodeBitmapFromStringUri(f.image, ICON_MAX_PX);
                                if (icon != null) iconCache.put(f.image, icon);
                            }
                        }
                        if (icon != null) rb.setIconBitmap(icon);

                        Bundle rExtras = new Bundle();
                        if (resume.getDuration() > 0) rExtras.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, (long) resume.getDuration());
                        rb.setExtras(rExtras);

                        out.add(new MediaBrowserCompat.MediaItem(rb.build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE));
                    }

                    // Fetch folder (for its image)
                    Folder f = AppDatabase.getDatabase(getApplicationContext())
                            .folderDao().getById(folderId); // add DAO method if missing
                    android.graphics.Bitmap icon = null;
                    if (f != null && f.image != null) {
                        icon = iconCache.get(f.image);
                        if (icon == null) {
                            icon = decodeBitmapFromStringUri(f.image, ICON_MAX_PX);
                            if (icon != null) iconCache.put(f.image, icon);
                        }
                    }
                    for (ZikFile z : tracks) {
                        MediaDescriptionCompat.Builder b = new MediaDescriptionCompat.Builder()
                                .setMediaId(PREFIX_TRACK + z.getId()) // or getIdZikFile()
                                .setTitle(z.getDisplayName())
                                .setSubtitle(z.getFolderName());
                        if (icon != null) b.setIconBitmap(icon);

                        Bundle extras = new Bundle();
                        if (z.getDuration() > 0) {
                            extras.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, (long) z.getDuration());
                        }
                        b.setExtras(extras);

                        out.add(new MediaBrowserCompat.MediaItem(
                                b.build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE));
                    }

                }
                result.sendResult(out);
                return;
            }


            result.sendResult(Collections.emptyList());
        });
    }

    // --------- Helpers browse ----------
    private MediaBrowserCompat.MediaItem browsable(String id, String title) {
        MediaDescriptionCompat desc = new MediaDescriptionCompat.Builder()
                .setMediaId(id)
                .setTitle(title)
                .build();
        return new MediaBrowserCompat.MediaItem(desc, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE);
    }

    private MediaBrowserCompat.MediaItem playable(String id, String title, String subtitle, @Nullable Uri mediaUri, long durationMs) {
        MediaDescriptionCompat.Builder b = new MediaDescriptionCompat.Builder()
                .setMediaId(id)
                .setTitle(title)
                .setSubtitle(subtitle);
        if (mediaUri != null) b.setMediaUri(mediaUri);

        Bundle extras = new Bundle();
        if (durationMs > 0) extras.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs);
        b.setExtras(extras);

        return new MediaBrowserCompat.MediaItem(b.build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE);
    }

    // --------- Push metadata/state to AA ----------
    private void pushMetadataFromCurrent() {
        myLog("pushMetadataFromCurrent");

        // Prefer the snapshot fields sent by AudioService
        final String curTitle   = (title != null)    ? title    : "";
        final String curArtist  = (subtitle != null) ? subtitle : "";
        final long   curDurMs   = Math.max(0L, durMs);
        final String coverUri   = (cover != null && !cover.isEmpty()) ? cover : null;

        // Optional: if you also cached trackId/folderId from the broadcast, use them here
        // to decide whether to skip identical updates. If you didn't add those, the string
        // fields + duration still work as a change key.

        boolean unchanged =
                safeEq(curTitle,  lastTitle) &&
                        safeEq(curArtist, lastSubtitle) &&
                        safeEq(coverUri,  lastCover) &&
                        curDurMs == lastDur &&
                        curTrackId == lastTrackId &&
                        curFolderId == lastFolderId;
        if (unchanged) return;

        MediaMetadataCompat.Builder mb = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE,   curTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST,  curArtist)
                .putLong  (MediaMetadataCompat.METADATA_KEY_DURATION, curDurMs)
        // helps some AA skins and resume flows
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, "track:" + curTrackId)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, curArtist) // optional alias
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, curTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, curArtist);

        // Artwork (use cache; decode off main thread if you see jank)
        Bitmap art = null;
        if (coverUri != null) {
            art = artCache.get(coverUri);
            if (art == null) {
                // If you notice stutter, move this to imgExec and set a small placeholder first
                art = decodeBitmapFromStringUri(coverUri, ART_MAX_PX);
                if (art != null) artCache.put(coverUri, art);
            }
        }
        if (art != null) {
            mb.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART,    art);
            mb.putBitmap(MediaMetadataCompat.METADATA_KEY_ART,          art);
            mb.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, art);
        }

        mediaSession.setMetadata(mb.build());

        // remember last
        lastTitle     = curTitle;
        lastSubtitle  = curArtist;
        lastCover     = coverUri;
        lastDur       = curDurMs;
        lastTrackId   = curTrackId;
        lastFolderId  = curFolderId;

        myLog(toString());
    }

    private static boolean safeEq(Object a, Object b) {
        return (a == b) || (a != null && a.equals(b));
    }


    private void pushPlaybackState(int state, long positionMs) {
        myLog("pushPlaybackState");
        long actions = PlaybackStateCompat.ACTION_PLAY
                | PlaybackStateCompat.ACTION_PAUSE
                | PlaybackStateCompat.ACTION_PLAY_PAUSE
                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                | PlaybackStateCompat.ACTION_SEEK_TO;

        PlaybackStateCompat st = new PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, positionMs, 1.0f, SystemClock.elapsedRealtime())
                .build();
        mediaSession.setPlaybackState(st);
    }

    // --------- Bridge vers AudioService ----------
    private void sendCmd(String action) {
        myLog("sendCmd : " + action);
        FirebaseAnalyticsHelper.tellCarSendCmd(action);
        ContextCompat.startForegroundService(
                this, new Intent(this, AudioService.class).setAction(action)
                        .putExtra(Intents.EXTRA_CALLER, this.getClass().getSimpleName() + ".sendCmd " + action)
                        .putExtra(Intents.EXTRA_FOREGROUND, true)
        );
    }


    private static int safeParseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    @Nullable
    private android.graphics.Bitmap decodeBitmapFromStringUri(String uriString, int maxSidePx) {
        //myLog("decodeBitmapFromStringUri : " + uriString + " - " + maxSidePx);
        if (uriString == null) return null;
        try {
            android.net.Uri uri = android.net.Uri.parse(uriString);

            // File path support (if your DB sometimes stores plain paths)
            //myLog("decodeBitmapFromStringUri by file");
            if ("file".equalsIgnoreCase(uri.getScheme()) || uriString.startsWith("/")) {
                String path = "file".equalsIgnoreCase(uri.getScheme()) ? uri.getPath() : uriString;
                if (path == null) return null;
                android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                android.graphics.BitmapFactory.decodeFile(path, o);
                int sample = 1;
                while (Math.max(o.outWidth / sample, o.outHeight / sample) > maxSidePx) sample *= 2;
                android.graphics.BitmapFactory.Options o2 = new android.graphics.BitmapFactory.Options();
                o2.inSampleSize = sample;
                o2.inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888;
                return android.graphics.BitmapFactory.decodeFile(path, o2);
            }

            // Content:// (SAF) — decode via stream (we are the same app → we can read it)
            try (java.io.InputStream is = getContentResolver().openInputStream(uri)) {
                myLogW("decodeBitmapFromStringUri by stream");
                if (is == null) return null;
                byte[] all = readAll(is);
                android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                android.graphics.BitmapFactory.decodeByteArray(all, 0, all.length, o);
                int sample = 1;
                while (Math.max(o.outWidth / sample, o.outHeight / sample) > maxSidePx) sample *= 2;
                android.graphics.BitmapFactory.Options o2 = new android.graphics.BitmapFactory.Options();
                o2.inSampleSize = sample;
                o2.inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888;
                return android.graphics.BitmapFactory.decodeByteArray(all, 0, all.length, o2);
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private static byte[] readAll(java.io.InputStream is) throws java.io.IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int r;
        while ((r = is.read(buf)) != -1) bos.write(buf, 0, r);
        return bos.toByteArray();
    }

    private void requestFolderRefresh(int folderId) {
        myLog("requestFolderRefresh - folderId = " + folderId);
        if (folderId <= 0) return;
        // coalesce multiple requests for the same folder
        if (!pendingFolderRefresh.add(folderId)) return;
        carH.postDelayed(() -> {
            pendingFolderRefresh.remove(folderId);
            notifyChildrenChanged(PREFIX_FOLDER + folderId);
        }, 350); // small debounce
    }
    // Optional: if your ROOT shows “Continue …” subtitles, refresh ROOT too
    private boolean rootRefreshPending = false;
    private void requestRootRefresh() {
        if (rootRefreshPending) return;
        rootRefreshPending = true;
        carH.postDelayed(() -> {
            rootRefreshPending = false;
            notifyChildrenChanged(ROOT_ID);
        }, 500);
    }

}
