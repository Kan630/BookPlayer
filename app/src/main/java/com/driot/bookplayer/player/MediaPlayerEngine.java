package com.driot.bookplayer.player;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.driot.bookplayer.objects.KanMediaPlayer;

import java.io.IOException;

/**
 * Thin wrapper around KanMediaPlayer implementing PlayerEngine.
 * Thread-safe against stale callbacks using a generation token.
 */
public final class MediaPlayerEngine implements PlayerEngine {

    private final EngineListener listener;
    private final long gen;

    private final KanMediaPlayer mp = new KanMediaPlayer();
    private volatile boolean prepared = false;

    public MediaPlayerEngine(@NonNull EngineListener listener, long generationToken) {
        this.listener = listener;
        this.gen = generationToken;

        mp.setListener(new KanMediaPlayer.Listener() {
            @Override public void onPrepared() {
                prepared = true;
                listener.onPrepared(gen);
            }

            @Override public void onCompletion() {
                listener.onCompletion(gen);
            }

            @Override public void onError(String msg, int what, int extra) {
                prepared = false;
                listener.onError(gen, msg != null ? msg : "Media error", what, extra);
            }

            @Override public void onFatalError(String msg, int what, int extra) {
                prepared = false;
                listener.onFatal(gen, msg != null ? msg : "Media fatal", what, extra);
            }
        });
    }

    @Override
    public void setDataSource(@NonNull Context ctx, @NonNull Uri uri, @NonNull String displayName) throws IOException {
        prepared = false;
        mp.reset();
        mp.setDataSource(ctx, uri);
    }

    @Override public void prepareAsync() { mp.prepareAsync(); }
    @Override public void start()        { mp.start(); }
    @Override public void pause()        { mp.pause(); }
    @Override public void stop()         { mp.stop(); }
    @Override public void reset()        { mp.reset(); prepared = false; }
    @Override public boolean isPlaying() { return mp.isPlaying(); }
    @Override public boolean isReady()   { return prepared && !mp.isPreparing(); }
    @Override public int getCurrentPosition() { return mp.getCurrentPosition(); }
    @Override public int getDuration()        { return mp.getDuration(); }
    @Override public int getAudioSessionId()  { return mp.getAudioSessionId(); }
    @Override public void seekTo(int ms)      { mp.seekTo(ms); }

    @Override
    public void setSpeed(float speed) {
        try {
            mp.setPlaybackParams(mp.getPlaybackParams().setSpeed(speed));
        } catch (Throwable ignored) { /* keep calm on old devices */ }
    }
}
