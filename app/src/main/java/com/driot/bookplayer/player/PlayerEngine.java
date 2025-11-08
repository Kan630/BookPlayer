package com.driot.bookplayer.player;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

public interface PlayerEngine {
    void setDataSource(@NonNull Context ctx, @NonNull Uri uri, @NonNull String displayName) throws Exception;

    void prepareAsync();

    void start();

    void pause();

    void stop();

    void reset();

    boolean isPlaying();

    boolean isReady();

    long getCurrentPosition();

    long getDuration();

    int getAudioSessionId();

    void seekTo(long positionMs);

    void setSpeed(float speed);

    void setVolume(float volume);  // 0f..1f
    float getVolume();             // current effective volume (0f..1f)
}