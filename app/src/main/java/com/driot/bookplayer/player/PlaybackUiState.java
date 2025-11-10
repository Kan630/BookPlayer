package com.driot.bookplayer.player;

import android.os.Bundle;

import androidx.annotation.Nullable;

import com.driot.bookplayer.utils.log.LoggerHelper;

public class PlaybackUiState extends LoggerHelper {

    public final String loadPhase;
    public final boolean playing;
    public final boolean ready;  // engine.isReady()
    public final String playMode;// = "book", "tts", "radio", "podcast", "book"

    public final long positionMs;
    public final long durationMs;
    public final String title;
    public final String subTitle;
    public final String cover;

    public final int trackId;    // current ZikFile id (or 0 if unknown)
    public final int folderId;   // current Folder id (or 0 if unknown)
    public final long podcastFeedId;

    public final String calledFrom;
    public final long callCounter;

    @Nullable
    public final Bundle extras;

    public PlaybackUiState(String loadPhase, boolean playing, boolean ready, String playMode,
                           long pos, long dur,
                           String title, String subTitle, String cover,
                           int trackId, int folderId, long podcastId,
                           String calledFrom, long callCounter,
                           @Nullable Bundle extras) {

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
        this.callCounter = callCounter;

        this.extras = extras;

        myLog(toString());
    }

    @Override public String toString() {
        return "PlaybackUiState{" +
                "  loadPhase=" + loadPhase +
                ", playMode=" + playMode +
                ", calledFrom='" + calledFrom + '\'' +
                ", callCounter=" + callCounter +
                ", playing=" + playing +
                ", ready=" + ready +
                ", positionMs=" + positionMs +
                ", durationMs=" + durationMs +
                ", title='" + title + '\'' +
                ", subTitle='" + subTitle + '\'' +
                ", cover='" + cover + '\'' +
                ", trackId=" + trackId +
                ", folderId=" + folderId +
                ", podcastId=" + podcastFeedId +
                ", extras=" + (extras == null ? "null" : extras.keySet()) +
                '}';
    }

}
