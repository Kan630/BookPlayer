package com.driot.bookplayer.player;

import android.os.Bundle;

import androidx.annotation.Nullable;

import com.driot.bookplayer.utils.log.LoggerHelper;

public class PlaybackUiState extends LoggerHelper {

    public final String loadPhase;
    public final boolean playing;
    public final boolean ready; // engine.isReady()
    public final String playMode;// = "book", "tts", "radio", "podcast", "book"

    public final long positionMs;
    public final long durationMs;
    public final long sleepLeftMS;

    public final String title;
    public final String subTitle;
    public final String cover;

    public final long trackId; // current ZikFile id (or 0 if unknown)
    public final long folderId; // current Folder id (or 0 if unknown)

    public final String calledFrom;
    public final long callCounter;

    @Nullable
    public final Bundle extras;

    public PlaybackUiState(String loadPhase, boolean playing, boolean ready, String playMode,
            long pos, long dur, long sleepLeftMS,
            String title, String subTitle, String cover,
            long trackId, long folderId,
            String calledFrom, long callCounter,
            @Nullable Bundle extras) {

        super(PlaybackUiState.class);

        this.loadPhase = loadPhase;
        this.playing = playing;
        this.ready = ready;
        this.playMode = playMode;

        this.positionMs = pos;
        this.durationMs = dur;
        this.sleepLeftMS = sleepLeftMS;

        this.title = title;
        this.subTitle = subTitle;
        this.cover = cover;

        this.trackId = trackId;
        this.folderId = folderId;

        this.calledFrom = calledFrom;
        this.callCounter = callCounter;

        this.extras = extras;

        // myLog(toString());
    }

    @Override
    public String toString() {
        return "PlaybackUiState{" +
                "  loadPhase=" + loadPhase +
                ", playMode=" + playMode +
                ", calledFrom='" + calledFrom + '\'' +
                ", callCounter=" + callCounter +
                ", playing=" + playing +
                ", ready=" + ready +
                ", positionMs=" + positionMs +
                ", durationMs=" + durationMs +
                ", sleepLeftMS=" + sleepLeftMS +
                ", title='" + title + '\'' +
                ", subTitle='" + subTitle + '\'' +
                ", cover='" + cover + '\'' +
                ", trackId=" + trackId +
                ", folderId=" + folderId +
                ", extras=" + (extras == null ? "null" : extras.keySet()) +
                '}';
    }

}
