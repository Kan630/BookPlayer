// PlaybackUiBus.java
package com.driot.bookplayer.player;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.concurrent.atomic.AtomicBoolean;

import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.utils.log.LoggerHelper;

public final class PlaybackUiBus extends LoggerHelper {
    private static final PlaybackUiBus INSTANCE = new PlaybackUiBus(PlaybackUiBus.class);

    public PlaybackUiBus(Class<?> clazz) {
        super(clazz);
    }

    public static PlaybackUiBus get() {
        return INSTANCE;
    }

    private final MutableLiveData<PlaybackUiState> _state = new MutableLiveData<>();

    public LiveData<PlaybackUiState> state() {
        return _state;
    }

    private final MutableLiveData<String> _ttsText = new MutableLiveData<>("");

    public LiveData<String> ttsText() {
        return _ttsText;
    }

    private final AtomicBoolean ttsTextRequested = new AtomicBoolean(false);

    public AtomicBoolean ttsTextRequested() {
        return ttsTextRequested;
    }

    @Nullable
    public PlaybackUiState snapshot() {
        return _state.getValue();
    }

    public void emit(PlaybackUiState next) {
        PlaybackUiState prev = _state.getValue();
        if (prev != null && next != null && prev.trackId != next.trackId) {
            // Track changed -> reset TTS text request state
            ttsTextRequested.set(false);
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
                _ttsText.setValue("");
            else
                _ttsText.postValue("");
        }

        // Harden: if we just left TTS mode, ensure phase is OFF (unless next
        // specifically sets it)
        if (prev != null && next != null
                && Var.PLAY_MODE_TTS.equals(prev.playMode)
                && !Var.PLAY_MODE_TTS.equals(next.playMode)) {
            if (Intents.PHASE_OFF.equals(next.loadPhase) || next.loadPhase == null || next.loadPhase.isEmpty()) {
                // already off or empty, fine
            } else {
                // Force OFF to prevent residual "Loading" text in non-TTS modes
                next = new PlaybackUiState(
                        Intents.PHASE_OFF,
                        next.playing, next.ready, next.playMode,
                        next.positionMs, next.durationMs, next.sleepLeftMS,
                        next.title, next.subTitle, next.cover,
                        next.trackId, next.folderId, next.podcastFeedId, next.radioStationUuid,
                        next.calledFrom + " (clean-up)", next.callCounter + 1, next.extras);
                myLog("playMode not TTS => forcing phase OFF");
            }
        }

        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
            _state.setValue(next);
        else
            _state.postValue(next);
    }

    public void setTtsText(@NonNull String text) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
            _ttsText.setValue(text);
        else
            _ttsText.postValue(text);
    }

    public void clear() {
        myLog("clear PlaybackUiState");
        emit(new PlaybackUiState(
                Intents.PHASE_OFF, false, false, null,
                0, 0, 0, "", "", "",
                0, 0, 0, null,
                "PlaybackUiBus.clear()", 0, null));
    }

    // --- Convenience updaters (rebuild immutable state) ----------------------

    public void setLoadPhase(String newPhase) {
        myLogD("setUiPhase : " + newPhase);
        PlaybackUiState cur = _state.getValue();
        if (cur == null)
            return;
        emit(new PlaybackUiState(
                newPhase,
                cur.playing, cur.ready, cur.playMode,
                cur.positionMs, cur.durationMs, cur.sleepLeftMS,
                cur.title, cur.subTitle, cur.cover,
                cur.trackId, cur.folderId, cur.podcastFeedId, cur.radioStationUuid,
                cur.calledFrom, cur.callCounter + 1, cur.extras));
    }

    public void setPlaying(boolean playing) {
        PlaybackUiState cur = _state.getValue();
        if (cur == null)
            return;
        emit(new PlaybackUiState(
                cur.loadPhase,
                playing, cur.ready, cur.playMode,
                cur.positionMs, cur.durationMs, cur.sleepLeftMS,
                cur.title, cur.subTitle, cur.cover,
                cur.trackId, cur.folderId, cur.podcastFeedId, cur.radioStationUuid,
                cur.calledFrom, cur.callCounter + 1, cur.extras));
    }

    // Add other small setters as needed (position, title, cover, etc.)

}
