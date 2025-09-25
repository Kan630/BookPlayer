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

    private final BroadcastReceiver uiReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent i) {
            if (!AudioService.ACTION_UI_STATE.equals(i.getAction())) return;
            playing  = i.getBooleanExtra(AudioService.EXTRA_UI_PLAYING, false);
            posMs    = (long) i.getIntExtra(AudioService.EXTRA_UI_POS, 0);
            durMs    = (long) i.getIntExtra(AudioService.EXTRA_UI_DUR, 0);
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
            @Override public void onPlay()              { sendCmd("CMD_PLAY"); }
            @Override public void onPause()             { sendCmd("CMD_PAUSE"); }
            @Override public void onSkipToNext()        { sendCmd("CMD_NEXT"); }
            @Override public void onSkipToPrevious()    { sendCmd("CMD_PREV"); }
            @Override public void onSeekTo(long posMs)  {
                Intent i = new Intent(CarMediaService.this, AudioService.class).setAction("CMD_SEEK");
                i.putExtra("posMs", (int) posMs);
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
                List<Folder> folders = AppDatabase.getDatabase(getApplicationContext())
                        .FolderDao().getAll();
                if (folders == null || folders.isEmpty()) {
                    out.add(browsable("hint", "No items. Open BookPlayer on your phone and import a book."));
                    result.sendResult(out);
                    return;
                }
                for (Folder f : folders) {
                    MediaDescriptionCompat.Builder b = new MediaDescriptionCompat.Builder()
                            .setMediaId(PREFIX_FOLDER + f.getId())
                            .setTitle(f.getName());

                    // Load small icon as Bitmap (not URI)
                    android.graphics.Bitmap icon = null;
                    if (f.image != null) {
                        icon = iconCache.get(f.image);
                        if (icon == null) {
                            icon = decodeBitmapFromStringUri(f.image, ICON_MAX_PX);
                            if (icon != null) iconCache.put(f.image, icon);
                        }
                    }
                    if (icon != null) b.setIconBitmap(icon);

                    out.add(new MediaBrowserCompat.MediaItem(
                            b.build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
                }
                result.sendResult(out);
                return;
            }

            if (parentId.startsWith(PREFIX_FOLDER)) {
                int folderId = safeParseInt(parentId.substring(PREFIX_FOLDER.length()), -1);
                if (folderId > 0) {
                    List<ZikFile> tracks = AppDatabase.getDatabase(getApplicationContext())
                            .ZikFileDao().getZikFiles(folderId);
                    if (tracks == null || tracks.isEmpty()) {
                        out.add(browsable("hint", "Empty book"));
                    } else {
                        // Fetch folder (for its image)
                        Folder f = AppDatabase.getDatabase(getApplicationContext())
                                .FolderDao().getById(folderId); // add DAO method if missing
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

        String curTitle  = (title == null || title.isEmpty()) && z != null ? z.getDisplayName() : title;
        String curArtist = (subtitle == null || subtitle.isEmpty()) && z != null ? z.getFolderName()  : subtitle;
        long   curDurMs  = (durMs > 0) ? durMs : (z != null ? (long) z.getDuration() : 0);

        String coverUriStr = null;
        if (z != null) {
            try {
                Folder f = AppDatabase.getDatabase(getApplicationContext())
                        .FolderDao().getById(z.getIdFolder());
                if (f != null) coverUriStr = f.image;
            } catch (Throwable ignored) {}
        }

        MediaMetadataCompat.Builder mb = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE,  curTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, curArtist)
                .putLong  (MediaMetadataCompat.METADATA_KEY_DURATION, curDurMs);

        android.graphics.Bitmap art = null;
        if (coverUriStr != null) {
            art = artCache.get(coverUriStr);
            if (art == null) {
                // decode synchronously once; if worried about jank, put this in imgExec
                art = decodeBitmapFromStringUri(coverUriStr, ART_MAX_PX);
                if (art != null) artCache.put(coverUriStr, art);
            }
        }
        if (art != null) {
            mb.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART,    art);
            mb.putBitmap(MediaMetadataCompat.METADATA_KEY_ART,          art);
            mb.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, art);
        }

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
    private void sendCmd(String action) {
        // Ensure AudioService is running
        startService(new Intent(this, AudioService.class));
        // Send the specific command
        startService(new Intent(this, AudioService.class).setAction(action));
    }

    private static int safeParseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    @Nullable
    private android.graphics.Bitmap decodeBitmapFromStringUri(String uriString, int maxSidePx) {
        if (uriString == null) return null;
        try {
            android.net.Uri uri = android.net.Uri.parse(uriString);

            // File path support (if your DB sometimes stores plain paths)
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

}
