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
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.utils.log.LoggingMediaBrowserServiceCompat;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

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

        mediaSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        );

        /*
        // Optional but nice: lets AA open your app when user taps the header
        PendingIntent pi = PendingIntent.getActivity(
                this, 0,
                new Intent(this, com.driot.bookplayer.activities.MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        mediaSession.setSessionActivity(pi);

         */


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
                                        new Intent(CarMediaService.this, MediaService.class)
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
                Intent i = new Intent(CarMediaService.this, MediaService.class).setAction("CMD_SEEK");
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
                                new Intent(CarMediaService.this, MediaService.class)
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
                                new Intent(CarMediaService.this, MediaService.class)
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

            @Override
            public void onCustomAction(@NonNull String action, Bundle extras) {
                switch (action) {
                    case Intents.CMD_TTS_SET_VOICE: {
                        String voice = extras != null ? extras.getString(Intents.EXTRA_TTS_VOICE_NAME) : null;
                        ContextCompat.startForegroundService(
                                CarMediaService.this,
                                new Intent(CarMediaService.this, MediaService.class)
                                        .setAction(Intents.CMD_TTS_SET_VOICE)
                                        .putExtra(Intents.EXTRA_TTS_VOICE_NAME, voice)
                                        .putExtra(Intents.EXTRA_FOREGROUND, true)
                                        .putExtra(Intents.EXTRA_CALLER, "CarMediaService.onCustomAction")
                        );
                        break;
                    }
                    case Intents.CMD_TTS_SET_START: {
                        int start = extras != null ? extras.getInt(Intents.EXTRA_TTS_START_OFFSET, 0) : 0;
                        ContextCompat.startForegroundService(
                                CarMediaService.this,
                                new Intent(CarMediaService.this, MediaService.class)
                                        .setAction(Intents.CMD_TTS_SET_START)
                                        .putExtra(Intents.EXTRA_TTS_START_OFFSET, start)
                                        .putExtra(Intents.EXTRA_FOREGROUND, true)
                                        .putExtra(Intents.EXTRA_CALLER, "CarMediaService.onCustomAction")
                        );
                        break;
                    }
                    case Intents.CMD_SET_SPEED: {
                        double sp = extras != null ? extras.getDouble(Intents.EXTRA_SPEED, 1.0) : 1.0;
                        ContextCompat.startForegroundService(
                                CarMediaService.this,
                                new Intent(CarMediaService.this, MediaService.class)
                                        .setAction(Intents.CMD_SET_SPEED)
                                        .putExtra(Intents.EXTRA_SPEED, sp)
                                        .putExtra(Intents.EXTRA_FOREGROUND, true)
                                        .putExtra(Intents.EXTRA_CALLER, "CarMediaService.onCustomAction")
                        );
                        break;
                    }
                    case Intents.CMD_UPDATE_SLEEP: {
                        int minutes = extras != null ? extras.getInt(Intents.EXTRA_CUSTOM_SLEEP_MINUTES, 0) : 0;
                        ContextCompat.startForegroundService(
                                CarMediaService.this,
                                new Intent(CarMediaService.this, MediaService.class)
                                        .setAction(Intents.CMD_UPDATE_SLEEP)
                                        .putExtra(Intents.EXTRA_CUSTOM_SLEEP_MINUTES, minutes)
                                        .putExtra(Intents.EXTRA_FOREGROUND, true)
                                        .putExtra(Intents.EXTRA_CALLER, "CarMediaService.onCustomAction")
                        );
                        break;
                    }
                    case Intents.CMD_TTS_GET_TEXT: {
                        // Optional: support queries with a ResultReceiver
                        android.os.ResultReceiver rr = extras != null
                                ? extras.getParcelable(Intents.EXTRA_RESULT_RECEIVER) : null;

                        // You can ask AudioService to produce the value and reply into rr
                        ContextCompat.startForegroundService(
                                CarMediaService.this,
                                new Intent(CarMediaService.this, MediaService.class)
                                        .setAction(Intents.CMD_TTS_GET_TEXT)
                                        .putExtra(Intents.EXTRA_RESULT_RECEIVER, rr)
                                        .putExtra(Intents.EXTRA_FOREGROUND, true)
                                        .putExtra(Intents.EXTRA_CALLER, "CarMediaService.onCustomAction")
                        );
                        break;
                    }

                    default:
                        super.onCustomAction(action, extras);
                }
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
        myLogI("------------ onGetRoot ------------  from pkg=" + clientPackageName + " uid=" + clientUid + " hints=" + rootHints);
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


        Bundle extras = new Bundle();
        // Tell host we support styled lists (same keys AA passed you)
        extras.putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true);

        // 1 = list, 2 = grid
        extras.putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 1); //Folders
        extras.putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 1);  //ZikFiles

        // These androidx flags matter for some AA versions:
        extras.putInt("androidx.media.MediaBrowserCompat.Extras.KEY_ROOT_CHILDREN_SUPPORTED_FLAGS", 1);
        extras.putInt("androidx.media.MediaBrowserCompat.Extras.KEY_ROOT_CHILDREN_LIMIT", 1000);

        // If you’re okay to be searchable:
        extras.putBoolean("android.media.browse.SEARCH_SUPPORTED", true);

        return new BrowserRoot(ROOT_ID, extras);
    }

    @Override
    public void onLoadChildren(@NonNull String parentId,
                               @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        myLogD("onLoadChildren parentId=" + parentId + " (no options)  --->  doing nothing");
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
                art = ImageHelper.decodeBitmapFromStringUri(this.getApplicationContext(), coverUri, ART_MAX_PX);
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
                this, new Intent(this, MediaService.class).setAction(action)
                        .putExtra(Intents.EXTRA_CALLER, this.getClass().getSimpleName() + ".sendCmd " + action)
                        .putExtra(Intents.EXTRA_FOREGROUND, true)
        );
    }


    private static int safeParseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }




    private void requestFolderRefresh(int folderId) {
        myLog("requestFolderRefresh - folderId = " + folderId);
        if (folderId <= 0) return;
        // coalesce multiple requests for the same folder
        if (!pendingFolderRefresh.add(folderId)) return;
        carH.postDelayed(() -> {
            pendingFolderRefresh.remove(folderId);
            notifyChildrenChanged(PREFIX_FOLDER + folderId);
            notifyChildrenChanged(PREFIX_FOLDER + folderId, Bundle.EMPTY);
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
