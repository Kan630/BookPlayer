// PlaybackUiBus.java
package com.driot.bookplayer.player;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.utils.log.LoggerHelper;

public final class PlaybackUiBus extends LoggerHelper {
    private static final PlaybackUiBus INSTANCE = new PlaybackUiBus(PlaybackUiBus.class);

    public PlaybackUiBus(Class<?> clazz) {
        super(clazz);
    }

    public static PlaybackUiBus get() { return INSTANCE; }

    private final MutableLiveData<PlaybackUiState> _state = new MutableLiveData<>();
    public LiveData<PlaybackUiState> state() { return _state; }

    @Nullable
    public PlaybackUiState snapshot() { return _state.getValue(); }

    public void emit(PlaybackUiState next) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) _state.setValue(next);
        else _state.postValue(next);
    }

    public void clear() {
        myLog("clear PlaybackUiState");
        emit(new PlaybackUiState(
                Intents.PHASE_OFF, false, false, null,
                0, 0, 0, "", "", "",
                0, 0, 0, null,
                "PlaybackUiBus.clear()", 0, null
        ));
    }

    // --- Convenience updaters (rebuild immutable state) ----------------------

    public void setLoadPhase(String newPhase) {
        myLogD("setUiPhase : " + newPhase);
        PlaybackUiState cur = _state.getValue();
        if (cur == null) return;
        emit(new PlaybackUiState(
                newPhase,
                cur.playing, cur.ready, cur.playMode,
                cur.positionMs, cur.durationMs, cur.sleepLeftMS,
                cur.title, cur.subTitle, cur.cover,
                cur.trackId, cur.folderId, cur.podcastFeedId, cur.radioStationUuid,
                cur.calledFrom, cur.callCounter + 1, cur.extras
        ));
    }

    public void setPlaying(boolean playing) {
        PlaybackUiState cur = _state.getValue();
        if (cur == null) return;
        emit(new PlaybackUiState(
                cur.loadPhase,
                playing, cur.ready, cur.playMode,
                cur.positionMs, cur.durationMs, cur.sleepLeftMS,
                cur.title, cur.subTitle, cur.cover,
                cur.trackId, cur.folderId, cur.podcastFeedId, cur.radioStationUuid,
                cur.calledFrom, cur.callCounter + 1, cur.extras
        ));
    }

    // Add other small setters as needed (position, title, cover, etc.)

}
