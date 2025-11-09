// PlaybackUiBus.java
package com.driot.bookplayer.player;

import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

public final class PlaybackUiBus {
    private static final PlaybackUiBus INSTANCE = new PlaybackUiBus();
    public static PlaybackUiBus get() { return INSTANCE; }

    private final MutableLiveData<PlaybackUiState> _state = new MutableLiveData<>();
    public LiveData<PlaybackUiState> state() { return _state; }

    public void emit(PlaybackUiState next) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) _state.setValue(next);
        else _state.postValue(next);
    }

    private PlaybackUiBus() {
        // Optional: set an initial state
        _state.setValue(new PlaybackUiState(
                /*loadPhase*/ "OFF",
                /*playing*/ false,
                /*ready*/ false,
                /*playMode*/ null,
                /*pos*/ 0L, /*dur*/ 0L,
                /*title*/ null, /*subTitle*/ null,/*cover*/ null,
                /*trackId*/ 0, /*folderId*/ 0, /*podcastId*/ 0L,
                /*calledFrom*/ "PlaybackUiBus constructor"
        ));
    }

    // --- Convenience updaters (rebuild immutable state) ----------------------

    public void setLoadPhase(String newPhase) {
        PlaybackUiState cur = _state.getValue();
        if (cur == null) return;
        emit(new PlaybackUiState(
                newPhase,
                cur.playing, cur.ready, cur.playMode,
                cur.positionMs, cur.durationMs,
                cur.title, cur.subTitle, cur.cover,
                cur.trackId, cur.folderId, cur.podcastFeedId,
                cur.calledFrom
        ));
    }

    public void setPlaying(boolean playing) {
        PlaybackUiState cur = _state.getValue();
        if (cur == null) return;
        emit(new PlaybackUiState(
                cur.loadPhase,
                playing, cur.ready, cur.playMode,
                cur.positionMs, cur.durationMs,
                cur.title, cur.subTitle, cur.cover,
                cur.trackId, cur.folderId, cur.podcastFeedId,
                cur.calledFrom
        ));
    }

    // Add other small setters as needed (position, title, cover, etc.)

}
