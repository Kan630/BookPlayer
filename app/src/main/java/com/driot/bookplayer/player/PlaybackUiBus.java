// PlaybackUiBus.java
package com.driot.bookplayer.player;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.concurrent.atomic.AtomicBoolean;

import com.driot.bookplayer.global.Intents;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

public final class PlaybackUiBus {
    private static final PlaybackUiBus INSTANCE = new PlaybackUiBus();

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
        if (prev != null && next != null && (prev.trackId != next.trackId || prev.folderId != next.folderId)) {
            // Track changed -> reset TTS text request state
            ttsTextRequested.set(false);
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
                _ttsText.setValue("");
            else
                _ttsText.postValue("");
        }

        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
            _state.setValue(next);
        else
            _state.postValue(next);
    }

    public void clear() {
        myLog("PlaybackUiBus: clear() → PHASE_OFF");
        emit(new PlaybackUiState(
                Intents.PHASE_OFF, null, false, false, null,
                0, 0, 0, "", "", "",
                0, 0, 0, null,
                false,
                "PlaybackUiBus.clear()", 0, null));
    }

    // --- Convenience updaters (rebuild immutable state) ----------------------

    public void setLoadPhase(String newPhase, @Nullable String message) {
        PlaybackUiState cur = _state.getValue();
        if (cur == null)
            return;
        myLog("PlaybackUiBus: setLoadPhase [" + (cur.loadPhase) + "] → [" + newPhase + "] (" + message + ")");
        // Reset ttsAudioStarted when entering a preparation or idle phase.
        // SPEAKING is set by MediaService only when onTtsRange fires (audio truly
        // started),
        // so we clear the flag here to keep state consistent.
        boolean resetAudio = Intents.PHASE_WARMING_UP.equals(newPhase)
                || Intents.PHASE_STARTING.equals(newPhase)
                || Intents.PHASE_LOADING_TEXT.equals(newPhase)
                || Intents.PHASE_OFF.equals(newPhase);
        emit(new PlaybackUiState(
                newPhase,
                message != null ? message : cur.loadMessage,
                cur.playing, cur.ready, cur.playMode,
                cur.positionMs, cur.durationMs, cur.sleepLeftMS,
                cur.title, cur.subTitle, cur.cover,
                cur.trackId, cur.folderId, cur.podcastFeedId, cur.radioStationUuid,
                resetAudio ? false : cur.ttsAudioStarted,
                cur.calledFrom, cur.callCounter + 1, cur.extras));
    }

    public void setPlaying(boolean playing) {
        PlaybackUiState cur = _state.getValue();
        if (cur == null)
            return;
        emit(new PlaybackUiState(
                cur.loadPhase,
                cur.loadMessage,
                playing, cur.ready, cur.playMode,
                cur.positionMs, cur.durationMs, cur.sleepLeftMS,
                cur.title, cur.subTitle, cur.cover,
                cur.trackId, cur.folderId, cur.podcastFeedId, cur.radioStationUuid,
                cur.ttsAudioStarted,
                cur.calledFrom, cur.callCounter + 1, cur.extras));
    }

    public void setTtsAudioStarted(boolean started) {
        PlaybackUiState cur = _state.getValue();
        if (cur == null)
            return;
        // During preparation phases, ignore stale "started=true" callbacks from
        // a previous utterance. MediaService now emits PHASE_SPEAKING itself when
        // onTtsRange first fires, so setTtsAudioStarted(true) from an old utterance
        // during WARMING_UP or STARTING would be a stale duplicate.
        if (started) {
            boolean preparationPhase = Intents.PHASE_WARMING_UP.equals(cur.loadPhase)
                    || Intents.PHASE_STARTING.equals(cur.loadPhase)
                    || Intents.PHASE_LOADING_TEXT.equals(cur.loadPhase);
            if (preparationPhase) {
                myLogD("PlaybackUiBus: setTtsAudioStarted(true) ignored — phase=[" + cur.loadPhase
                        + "] (stale old-utterance callback)");
                return;
            }
        }
        myLogD("PlaybackUiBus: setTtsAudioStarted(" + started + ") phase=[" + cur.loadPhase + "]");
        emit(new PlaybackUiState(
                cur.loadPhase,
                cur.loadMessage,
                cur.playing, cur.ready, cur.playMode,
                cur.positionMs, cur.durationMs, cur.sleepLeftMS,
                cur.title, cur.subTitle, cur.cover,
                cur.trackId, cur.folderId, cur.podcastFeedId, cur.radioStationUuid,
                started,
                cur.calledFrom, cur.callCounter + 1, cur.extras));
    }

    // Add other small setters as needed (position, title, cover, etc.)

}
