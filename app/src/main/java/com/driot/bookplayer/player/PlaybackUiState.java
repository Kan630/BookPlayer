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
    public final long podcastFeedId;

    public final boolean ready;  // engine.isReady()
    public final String loadPhase;
    public final String playMode;// = "book", "tts", "radio", "podcast", "book"
    public final String calledFrom;

    public PlaybackUiState(String loadPhase, boolean playing, boolean ready, String playMode,
                           long pos, long dur,
                           String title, String subTitle, String cover,
                           int trackId, int folderId, long podcastId,
                           String calledFrom) {

        super(PlaybackUiState.class);

        this.loadPhase = loadPhase;
        this.playing = playing;
        this.ready = ready;
        this.playMode = playMode;

        this.positionMs = pos;
        this.durationMs = dur;
        this.title = title;
        this.subTitle = subTitle;
        this.cover = cover;

        this.trackId = trackId;
        this.folderId = folderId;
        this.podcastFeedId = podcastId;

        this.calledFrom = calledFrom;

        myLog(toString());
    }

    @Override public String toString() {
        return "PlaybackUiState{" +
                "  loadPhase=" + loadPhase +
                ", playing=" + playing +
                ", ready=" + ready +
                ", playMode=" + playMode +
                ", positionMs=" + positionMs +
                ", durationMs=" + durationMs +
                ", title='" + title + '\'' +
                ", subTitle='" + subTitle + '\'' +
                ", cover='" + cover + '\'' +
                ", trackId=" + trackId +
                ", folderId=" + folderId +
                ", podcastId=" + podcastFeedId +
                ", calledFrom='" + calledFrom + '\'' +
                '}';
    }

}
