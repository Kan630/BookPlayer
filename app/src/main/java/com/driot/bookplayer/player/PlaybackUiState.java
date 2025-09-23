package com.driot.bookplayer.player;

public class PlaybackUiState {
    public final boolean playing;
    public final int positionMs, durationMs;
    public final String title, subTitle;
    public PlaybackUiState(boolean playing, int pos, int dur, String t, String s) {
        this.playing = playing; this.positionMs = pos; this.durationMs = dur;
        this.title = t; this.subTitle = s;
    }
}
