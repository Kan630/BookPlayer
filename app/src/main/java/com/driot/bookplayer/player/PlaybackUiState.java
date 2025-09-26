package com.driot.bookplayer.player;

import com.driot.bookplayer.utils.KanLogger;

public class PlaybackUiState {

    public final boolean playing;
    public final long positionMs;
    public final long durationMs;
    public final String title;
    public final String subTitle;
    public final String  cover;

    public PlaybackUiState(boolean playing, long pos, long dur, String t, String s, String cover) {
        this.playing = playing;
        this.positionMs = pos;
        this.durationMs = dur;
        this.title = t;
        this.subTitle = s;
        this.cover = cover;
        myLog(toString());
    }

    @Override
    public String toString() {
        return "PlaybackUiState{" +
                "playing=" + playing +
                ", positionMs=" + positionMs +
                ", durationMs=" + durationMs +
                ", title='" + title + '\'' +
                ", subTitle='" + subTitle + '\'' +
                ", cover='" + cover + '\'' +
                '}';
    }

    ////////////////////////////////////////////////////////
    private static final String TAG = "PlaybackUiState";
    private static void myLog(String str) { KanLogger.myLog(TAG, str); }
    private static void myLogD(String str) { KanLogger.myLogD(TAG, str); }
    private static void myLogI(String str) { KanLogger.myLogI(TAG, str); }
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
    private static void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, TAG, str); }
    private static void myToastEE(Throwable t, String str) { KanLogger.myToastEE(t, TAG, str); }
}
