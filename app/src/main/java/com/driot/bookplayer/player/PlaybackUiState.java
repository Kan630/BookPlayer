package com.driot.bookplayer.player;

import com.driot.bookplayer.utils.log.LoggerHelper;

public class PlaybackUiState extends LoggerHelper {

    public final boolean playing;
    public final long positionMs;
    public final long durationMs;
    public final String title;
    public final String subTitle;
    public final String cover;

    // NEW — identity + engine info
    public final int trackId;    // current ZikFile id (or 0 if unknown)
    public final int folderId;   // current Folder id (or 0 if unknown)
    public final boolean ready;  // engine.isReady()
    public final String playMode;// = "book", "tts", "radio", "podcast", "book"
    public final String calledFrom;

    public PlaybackUiState(boolean playing, long pos, long dur,
                           String t, String s, String cover,
                           int trackId, int folderId, boolean ready,
                           String playMode, String calledFrom) {
        super(PlaybackUiState.class);
        this.playing = playing;
        this.positionMs = pos;
        this.durationMs = dur;
        this.title = t;
        this.subTitle = s;
        this.cover = cover;

        this.trackId = trackId;
        this.folderId = folderId;
        this.ready = ready;
        this.playMode = playMode;
        this.calledFrom = calledFrom;

        myLog(toString());
    }

    @Override public String toString() {
        return "PlaybackUiState{" +
                "playing=" + playing +
                ", positionMs=" + positionMs +
                ", durationMs=" + durationMs +
                ", title='" + title + '\'' +
                ", subTitle='" + subTitle + '\'' +
                ", cover='" + cover + '\'' +
                ", trackId=" + trackId +
                ", folderId=" + folderId +
                ", ready=" + ready +
                ", playMode=" + playMode +
                ", calledFrom='" + calledFrom + '\'' +
                '}';
    }

}
