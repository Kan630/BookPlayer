package com.driot.bookplayer.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.session.MediaButtonReceiver;

import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.objects.PlayList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CarMediaService extends MediaBrowserServiceCompat {

    public static final String ROOT_ID = "root";
    private static final String NODE_ALL = "all";
    private static final String PREFIX_FOLDER = "folder:";
    private static final String PREFIX_TRACK  = "track:";

    private MediaSessionCompat mediaSession;

    // --- cache UI côté voiture (alimenté par AudioService via broadcast)
    private boolean playing = false;
    private long posMs = 0;
    private long durMs = 0;
    private String title = "";
    private String subtitle = "";

    private final BroadcastReceiver uiReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent i) {
            if (!AudioService.ACTION_UI_STATE.equals(i.getAction())) return;
            playing  = i.getBooleanExtra(AudioService.EXTRA_UI_PLAYING, false);
            posMs    = i.getLongExtra(AudioService.EXTRA_UI_POS, 0);
            durMs    = i.getLongExtra(AudioService.EXTRA_UI_DUR, 0);
            title    = i.getStringExtra(AudioService.EXTRA_UI_TITLE);
            subtitle = i.getStringExtra(AudioService.EXTRA_UI_SUBTITLE);

            // push metadata + playbackstate vers Android Auto
            pushMetadataFromCurrent();
            pushPlaybackState(playing ? PlaybackStateCompat.STATE_PLAYING
                    : PlaybackStateCompat.STATE_PAUSED, posMs);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        // 1) MediaSession locale pour Android Auto
        mediaSession = new MediaSessionCompat(this, "BookPlayerCarSession");
        setSessionToken(mediaSession.getSessionToken());

        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override public void onPlay() {
                sendCmdToAudioService(PlaybackStateCompat.ACTION_PLAY);
            }
            @Override public void onPause() {
                sendCmdToAudioService(PlaybackStateCompat.ACTION_PAUSE);
            }
            @Override public void onSkipToNext() {
                // ton AudioService fait forward/next via ACTION_SKIP_TO_NEXT
                sendCmdToAudioService(PlaybackStateCompat.ACTION_SKIP_TO_NEXT);
            }
            @Override public void onSkipToPrevious() {
                sendCmdToAudioService(PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS);
            }
            @Override public void onSeekTo(long pos) {
                Intent i = new Intent(CarMediaService.this, AudioService.class);
                i.setAction("CMD_SEEK");
                i.putExtra("posMs", pos);
                startService(i);
            }
            @Override
            public void onPlayFromMediaId(String mediaId, Bundle extras) {
                if (mediaId == null) return;

                // Case 1: user clicked a track item → "track:<zikId>"
                if (mediaId.startsWith(PREFIX_TRACK)) {
                    final int trackId = safeParseInt(mediaId.substring(PREFIX_TRACK.length()), -1);
                    if (trackId <= 0) return;

                    AppDatabase.databaseReadExecutor.execute(() -> {
                        // 1) Resolve the clicked track
                        ZikFile clicked = AppDatabase.getDatabase(getApplicationContext())
                                .ZikFileDao().getById(trackId); // <-- if your DAO name is getByIdNow(), use that exact name
                        if (clicked == null) return;

                        final int folderId = clicked.getIdFolder();

                        // 2) Load the whole book (folder) track list
                        List<ZikFile> list = AppDatabase.getDatabase(getApplicationContext())
                                .ZikFileDao().getZikFiles(folderId);

                        if (list == null || list.isEmpty()) return;

                        // 3) Find clicked track index
                        int index = 0;
                        for (int i = 0; i < list.size(); i++) {
                            ZikFile z = list.get(i);
                            // IMPORTANT: use the correct getter for your ZikFile id
                            // If your entity uses getIdZikFile(), replace getId() with getIdZikFile()
                            if (z.getId() == trackId) { index = i; break; }
                        }

                        // 4) Build PlayList and start playback
                        PlayList.create(getApplicationContext(), list, index);

                        // Ensure AudioService is running, then explicit PLAY
                        startService(new Intent(CarMediaService.this, AudioService.class));
                        startService(new Intent(CarMediaService.this, AudioService.class).setAction("CMD_PLAY"));
                    });
                    return;
                }

                // Case 2 (optional): user clicked a folder item → "folder:<id>"
                // We don’t auto-play; Android Auto will call onLoadChildren() to show the tracks.
            }
            @Override
            public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
                if (mediaButtonIntent == null) return super.onMediaButtonEvent(null);
                // Forward the event to the session (translates KeyEvent → callbacks)
                androidx.media.session.MediaButtonReceiver.handleIntent(mediaSession, mediaButtonIntent);
                return true; // we handled it
            }
        });
        mediaSession.setActive(true);

        // 2) s’abonner aux mises à jour UI de l’AudioService
        LocalBroadcastManager.getInstance(this).registerReceiver(
                uiReceiver, new IntentFilter(AudioService.ACTION_UI_STATE));

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
        // Filtre au cas ou je ne set pas la permission dans le manifest pour le service : android:permission="android.permission.BIND_MEDIA_BROWSER_SERVICE"
/*
        if ("com.google.android.projection.gearhead".equals(clientPackageName)
                || "com.google.android.googlequicksearchbox".equals(clientPackageName)) {
            return new BrowserRoot(ROOT_ID, null);
        }
        // Refuser les autres
        return null; // ou renvoyer un BrowserRoot restreint

 */
        return new BrowserRoot(ROOT_ID, null);
    }


    @Override
    public void onLoadChildren(@NonNull String parentId,
                               @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        // Chargements DB → thread bg
        result.detach();
        AppDatabase.databaseReadExecutor.execute(() -> {
            List<MediaBrowserCompat.MediaItem> out = new ArrayList<>();

            if (ROOT_ID.equals(parentId)) {
                // 1) Lister les Folders
                List<Folder> folders = AppDatabase.getDatabase(getApplicationContext())
                        .FolderDao().getAll();
                if (folders == null || folders.isEmpty()) {
                    out.add(browsable("hint", "No items. Open BookPlayer on your phone and import a book."));
                } else {
                    for (Folder f : folders) {
                        MediaDescriptionCompat desc = new MediaDescriptionCompat.Builder()
                                .setMediaId(PREFIX_FOLDER + f.getId())
                                .setTitle(f.getName())
                                //.setSubtitle(formatFolderSubtitle(f))
                                // Option: setIconUri(Uri.parse(f.image)) si image stockée en URI accessible
                                .build();
                        out.add(new MediaBrowserCompat.MediaItem(desc,
                                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
                    }
                }
                result.sendResult(out);
                return;
            }

            // 2) Si parent = un folder → lister ses tracks
            if (parentId.startsWith(PREFIX_FOLDER)) {
                int folderId = safeParseInt(parentId.substring(PREFIX_FOLDER.length()), -1);
                if (folderId > 0) {
                    List<ZikFile> tracks = AppDatabase.getDatabase(getApplicationContext())
                            .ZikFileDao().getZikFiles(folderId);
                    if (tracks == null || tracks.isEmpty()) {
                        out.add(browsable("hint", "Empty book"));
                    } else {
                        for (ZikFile z : tracks) {
                            Bundle extras = new Bundle();
                            if (z.getDuration() > 0) {
                                extras.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, (long) z.getDuration());
                            }
                            MediaDescriptionCompat desc = new MediaDescriptionCompat.Builder()
                                    .setMediaId(PREFIX_TRACK + z.getId())
                                    .setTitle(z.getDisplayName())
                                    .setSubtitle(z.getFolderName())
                                    .setExtras(extras)
                                    .build();
                            out.add(new MediaBrowserCompat.MediaItem(desc,
                                    MediaBrowserCompat.MediaItem.FLAG_PLAYABLE));
                        }
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
        PlayList pl = PlayList.getInstance();
        ZikFile z = (pl != null) ? pl.getZikFile() : null;

        MediaMetadataCompat.Builder mb = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE,  (title == null || title.isEmpty()) && z != null ? z.getDisplayName() : title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, (subtitle == null || subtitle.isEmpty()) && z != null ? z.getFolderName() : subtitle)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, (durMs > 0) ? durMs : (long) (z != null ? z.getDuration() : 0));

        mediaSession.setMetadata(mb.build());
    }

    private void pushPlaybackState(int state, long positionMs) {
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
    private void sendCmdToAudioService(long action) {
        // Redirige les actions standard via MediaButtonReceiver (ton AudioService les gère déjà)
        Intent i = new Intent(this, AudioService.class);
        i.setAction(Intent.ACTION_MEDIA_BUTTON);
        i.putExtra(Intent.EXTRA_KEY_EVENT,
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, action));
        // Petit trick : on appelle directement MediaButtonReceiver côté service
        startService(new Intent(this, AudioService.class)); // s’assure qu’il tourne
        MediaButtonReceiver.handleIntent(
                // on n’a pas accès à la session d’AudioService, mais il a son propre MediaSessionController
                mediaSession, // suffisant pour relayer l’événement
                new Intent(Intent.ACTION_MEDIA_BUTTON));
        // Alternative simple : commandes directes déjà supportées par AudioService :
        if (action == PlaybackStateCompat.ACTION_PLAY) {
            startService(new Intent(this, AudioService.class)); // démarre
        } else if (action == PlaybackStateCompat.ACTION_PAUSE) {
            Intent pause = new Intent(this, AudioService.class);
            pause.setAction("CMD_PAUSE");
            startService(pause);
        } else if (action == PlaybackStateCompat.ACTION_SKIP_TO_NEXT) {
            Intent next = new Intent(this, AudioService.class);
            next.setAction("CMD_NEXT");
            startService(next);
        } else if (action == PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS) {
            Intent prev = new Intent(this, AudioService.class);
            prev.setAction("CMD_PREV");
            startService(prev);
        }
    }

    private static int safeParseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
}
