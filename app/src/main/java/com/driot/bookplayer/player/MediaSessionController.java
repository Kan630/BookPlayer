package com.driot.bookplayer.player;

import android.app.PendingIntent;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

public final class MediaSessionController {

    private final MediaSessionCompat session;

    public MediaSessionController(@NonNull Context ctx,
                                  @NonNull MediaSessionCompat.Callback cb) {
        session = new MediaSessionCompat(ctx, "BookplayerMediaSession");
        session.setCallback(cb);
        session.setActive(true);
    }

    public MediaSessionCompat session() { return session; }

    public void setActive(boolean active) { session.setActive(active); }

    public void setDuration(long durMs) { //TODO call should be replaced by below setMetaData
        MediaMetadataCompat md = new MediaMetadataCompat.Builder()
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durMs)
                .build();
        session.setMetadata(md);
    }

    public void updateState(int state, long position, float speed, long actions) {
        PlaybackStateCompat ps = new PlaybackStateCompat.Builder()
                .setState(state, position, speed)
                .setActions(actions)
                .build();
        session.setPlaybackState(ps);
    }

    public void release() { session.release(); }

    // NEW: allow the service to tell Android which activity to open
    public void setSessionActivity(@NonNull PendingIntent pi) {
        session.setSessionActivity(pi);
    }

    // NEW: richer metadata for cars / watches / lockscreen
    public void setMetadata(@NonNull String title,
                            @NonNull String artist,
                            @NonNull String album,
                            long durationMs,
                            @Nullable android.graphics.Bitmap art /* pass null if you don't have it */) {
        MediaMetadataCompat.Builder b = new MediaMetadataCompat.Builder()
                .putText(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putText(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putText(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs);
        if (art != null) {
            b.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art);
            b.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, art);
        }
        session.setMetadata(b.build());
    }
    public void setMetadataRadio(@NonNull String title,
                            @NonNull String artist,
                            @NonNull String album,
                            @Nullable android.graphics.Bitmap art /* pass null if you don't have it */) {
        MediaMetadataCompat.Builder b = new MediaMetadataCompat.Builder()
                .putText(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putText(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putText(MediaMetadataCompat.METADATA_KEY_ALBUM, album);
        if (art != null) {
            b.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art);
            b.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, art);
        }
        session.setMetadata(b.build());
    }

}
