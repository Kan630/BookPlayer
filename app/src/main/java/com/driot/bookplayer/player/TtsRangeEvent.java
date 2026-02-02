package com.driot.bookplayer.player;

import androidx.annotation.IntRange;

/**
 * Carries a TTS word range plus optional chunk bounds for progressive highlight delay.
 * When chunkStart/chunkEnd are valid, the highlighter can delay highlights for words
 * toward the end of a chunk to compensate for network TTS synthesis-vs-playback lag.
 */
public final class TtsRangeEvent {
    @IntRange(from = 0)
    public final int start;
    @IntRange(from = 0)
    public final int end;
    /** Chunk start in full text, or -1 if unknown. */
    public final int chunkStart;
    /** Chunk end in full text, or -1 if unknown. */
    public final int chunkEnd;

    public TtsRangeEvent(int start, int end, int chunkStart, int chunkEnd) {
        this.start = start;
        this.end = end;
        this.chunkStart = chunkStart;
        this.chunkEnd = chunkEnd;
    }

    public boolean hasChunkBounds() {
        return chunkStart >= 0 && chunkEnd > chunkStart;
    }
}
