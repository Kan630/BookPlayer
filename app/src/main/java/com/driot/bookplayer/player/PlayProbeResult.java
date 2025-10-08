package com.driot.bookplayer.player;

public final class PlayProbeResult {
    public final boolean playable;
    public final long durationMs;   // 0 if unknown
    public final String engine;     // "mediaplayer", "exo", or ""
    public final String error;      // nullable message

    public PlayProbeResult(boolean playable, long durationMs, String engine, String error) {
        this.playable = playable;
        this.durationMs = durationMs;
        this.engine = engine;
        this.error = error;
    }

    public static PlayProbeResult ok(String engine, long durationMs) {
        return new PlayProbeResult(true, Math.max(0, durationMs), engine, null);
    }

    public static PlayProbeResult fail(String engine, String error) {
        return new PlayProbeResult(false, 0, engine, error);
    }
}
