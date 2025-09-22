package com.driot.bookplayer.player;

import android.content.Context;

import androidx.annotation.NonNull;
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

    public void setDuration(long durMs) {
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
}
