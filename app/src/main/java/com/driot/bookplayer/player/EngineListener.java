package com.driot.bookplayer.player;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

public interface EngineListener {
    void onPrepared(long gen);
    void onCompletion(long gen);
    void onError(long gen, @NonNull String msg, int what, int extra);
    void onFatal(long gen, @NonNull String msg, int what, int extra);

    /** TTS only (Media engine won’t call it). */
    default void onTtsRange(long gen, @IntRange(from = 0) int start, @IntRange(from = 0) int end) {}

    /** TTS only, with chunk bounds for progressive highlight delay. Default calls 3-arg version. */
    default void onTtsRange(long gen, @IntRange(from = 0) int start, @IntRange(from = 0) int end,
            int chunkStart, int chunkEnd) {
        onTtsRange(gen, start, end);
    }
}
