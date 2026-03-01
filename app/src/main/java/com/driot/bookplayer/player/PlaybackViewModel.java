package com.driot.bookplayer.player;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.media.session.MediaControllerCompat;
import android.util.Pair;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.tts.AppTtsManager;
import com.driot.bookplayer.tts.TtsHelper;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingAndroidViewModel;

import javax.inject.Inject;
import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * Mini player's single source of truth:
 * - ACTION_UI_STATE drives visibility/content.
 * - Snapshots are used ONLY when bound, for progress smoothing.
 * - We never overwrite with an "empty" state just because we're unbound.
 */
@HiltViewModel
public class PlaybackViewModel extends LoggingAndroidViewModel {

    private final AppTtsManager ttsManager;

    public interface WarmupUiCallback {
        void onResult(boolean ready, int reason);
    }

    private final MutableLiveData<Long> _seekPreviewMs = new MutableLiveData<>();
    private volatile boolean _stateSourcesAdded = false;

    private final MediatorLiveData<PlaybackUiState> _state = new MediatorLiveData<>();

    public LiveData<PlaybackUiState> getState() {
        if (!_stateSourcesAdded) {
            _stateSourcesAdded = true;
            _state.addSource(PlaybackUiBus.get().state(), s -> emitStateWithSeekPreview());
            _state.addSource(_seekPreviewMs, v -> emitStateWithSeekPreview());
        }
        return _state;
    }

    private void emitStateWithSeekPreview() {
        PlaybackUiState s = PlaybackUiBus.get().state().getValue();
        if (s == null)
            return;
        Long preview = _seekPreviewMs.getValue();
        if (preview != null) {
            // Clear preview when bus has caught up (after seek), so slider stays at new
            // position
            if (s.durationMs > 0 && Math.abs(s.positionMs - preview) < 2000) {
                _seekPreviewMs.setValue(null);
            } else {
                s = new PlaybackUiState(
                        s.loadPhase, s.playing, s.ready, s.playMode,
                        preview, s.durationMs, s.sleepLeftMS,
                        s.title, s.subTitle, s.cover,
                        s.trackId, s.folderId, s.podcastFeedId, s.radioStationUuid,
                        s.calledFrom, s.callCounter, s.extras);
            }
        }
        _state.setValue(s);

        // Auto-fetch TTS text if missing
        if (Var.PLAY_MODE_TTS.equals(s.playMode) && s.trackId > 0) {
            String txt = PlaybackUiBus.get().ttsText().getValue();
            if (txt == null || txt.isEmpty()) {
                requestTtsTextOnce();
            }
        }
    }

    /**
     * While user drags the seek bar, pass preview position so list triangle moves
     * in real time. Call with null on release.
     */
    public void setSeekPreview(@Nullable Long positionMs) {
        _seekPreviewMs.setValue(positionMs);
    }

    private final MutableLiveData<Pair<Integer, Integer>> ttsRange = new MutableLiveData<>();

    public LiveData<Pair<Integer, Integer>> getTtsRange() {
        return ttsRange;
    }

    public LiveData<String> getTtsText() {
        return PlaybackUiBus.get().ttsText();
    }

    private int sleepCustomMinutes = -1;

    public int getSleepCustomMinutes(String playMode) {
        int baseSleep = (Var.PLAY_MODE_RADIO.equals(playMode) ? Option.getTimeBeforeSleepRadio()
                : Option.getTimeBeforeSleep());
        return (sleepCustomMinutes > 0 ? sleepCustomMinutes : baseSleep);
    }

    public void requestTtsTextOnce() {
        // Only one request per VM/session by default
        if (!PlaybackUiBus.get().ttsTextRequested().compareAndSet(false, true)) {
            myLog("requestTtsTextOnce: already requested, ignoring");
            return;
        }

        android.os.ResultReceiver rr = new android.os.ResultReceiver(
                new android.os.Handler(android.os.Looper.getMainLooper())) {
            @Override
            protected void onReceiveResult(int resultCode, android.os.Bundle resultData) {
                String txt = resultData != null ? resultData.getString(Intents.EXTRA_TTS_TEXT, "") : "";
                PlaybackUiBus.get().setTtsText(txt);
            }
        };
        PlaybackCommands.requestTtsText(getApplication(), rr);
    }

    /**
     * Reset the TTS text request flag to allow requesting text again (e.g., when
     * track changes).
     */
    public void resetTtsTextRequestFlag() {
        PlaybackUiBus.get().ttsTextRequested().set(false);
    }

    @Inject
    public PlaybackViewModel(@NonNull Application app, AppTtsManager ttsManager) {
        super(app);
        this.ttsManager = ttsManager;
        LocalBroadcastManager.getInstance(app).registerReceiver(ttsRangeRx,
                new IntentFilter(Intents.NOTIFICATION_TTS_RANGE));
    }

    // Transport
    public void playPause() {
        myLog("playpause");
        PlaybackCommands.playPause(getApplication());
    }

    public void next() {
        myLog("next");
        PlaybackCommands.next(getApplication());
    }

    public void prev() {
        myLog("prev");
        PlaybackCommands.prev(getApplication());
    }

    public void seekTo(long ms) {
        myLog("seekTo " + Tonio.formatHhMmSs(ms));
        PlaybackCommands.seekTo(getApplication(), ms);
    }

    /** Close/hide mini and pause audio even if we're not bound. */
    public void stop() {
        myLog("stop");
        PlaybackCommands.stop(getApplication());
    }

    public void setSpeed(double s) {
        PlaybackCommands.setSpeed(getApplication(), s);
    }

    public void updateSleepTimer(int minutes) {
        PlaybackCommands.updateSleepTimer(getApplication(), minutes);
        sleepCustomMinutes = minutes; // for activity display (listening without actions since...)
    }

    // --------------------------------------------------------------------
    // -- TTS
    // --------------------------------------------------------------------

    private final BroadcastReceiver ttsRangeRx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (Intents.NOTIFICATION_TTS_RANGE.equals(i.getAction())) {
                int s = i.getIntExtra(Intents.EXTRA_TTS_START, -1);
                int e = i.getIntExtra(Intents.EXTRA_TTS_END, -1);
                if (s >= 0)
                    ttsRange.postValue(new Pair<>(s, e));
            }
        }
    };

    @Override
    protected void onCleared() {
        if (voiceSpinnerHandle != null) {
            try {
                voiceSpinnerHandle.close();
            } catch (Exception ignored) {
            }
            voiceSpinnerHandle = null;
        }
        LocalBroadcastManager.getInstance(getApplication()).unregisterReceiver(ttsRangeRx);
    }

    public void setTtsStartOffsetChars(int start) {
        PlaybackCommands.setTtsStartOffset(getApplication(), start);
    }

    public void setupTtsVoiceSpinner(
            Context ctx,
            Spinner spinner,
            String initial,
            TtsHelper.OnVoiceSelected onSelected) {
        myLog("setupTtsVoiceSpinner - initial = " + initial);
        // Close previous handle if any (e.g. spinner recreated)
        if (voiceSpinnerHandle != null) {
            try {
                voiceSpinnerHandle.close();
            } catch (Exception ignored) {
            }
            voiceSpinnerHandle = null;
        }
        voiceSpinnerHandle = TtsHelper.setupTtsVoiceSpinner(ctx, spinner, ttsManager, initial, onSelected);
    }

    private volatile boolean inError = false;
    private AutoCloseable voiceSpinnerHandle;

    private void setLoadPhase(@NonNull String phaseId, @Nullable String message) {
        myLog("setLoadPhase " + phaseId + " - " + message);
        // If you want to ignore warmup/starting while in error, keep this guard:
        if (inError && (Intents.PHASE_WARMING_UP.equals(phaseId) || Intents.PHASE_ENGINE_STARTING.equals(phaseId))) {
            myLogE("setLoadPhase - inError");
            return;
        }
        if (Intents.PHASE_ERROR.equals(phaseId))
            inError = true;
        if (Intents.PHASE_WARMING_UP.equals(phaseId) || Intents.PHASE_LOADING_TEXT.equals(phaseId))
            inError = false;

        PlaybackUiState cur = PlaybackUiBus.get().state().getValue();
        if (cur == null) {
            myLogEE(null, "setLoadPhase - no current");
            return;
        }

        PlaybackUiState next = new PlaybackUiState(
                phaseId, cur.playing, cur.ready, cur.playMode,
                cur.positionMs, cur.durationMs, cur.sleepLeftMS,
                cur.title, cur.subTitle, cur.cover,
                cur.trackId, cur.folderId, cur.podcastFeedId, cur.radioStationUuid,
                "PlayBackViewModel.setPhase", cur.callCounter + 1, cur.extras);
        PlaybackUiBus.get().emit(next);
    }

    public void warmUpTtsVoice(String voiceName, @Nullable WarmupUiCallback cb) {
        try {
            MediaControllerCompat mc = PlaybackCommands.mcOrNull(getApplication());
            Bundle b = new Bundle();
            b.putString(Intents.EXTRA_TTS_VOICE_NAME, voiceName);
            mc.getTransportControls().sendCustomAction(Intents.CMD_TTS_SET_VOICE, b);

            if (cb != null)
                cb.onResult(true, TtsHelper.READY);
        } catch (Throwable t) {
            setLoadPhase(Intents.PHASE_ERROR, getApplication().getString(R.string.tts_phase_error));
            if (cb != null)
                cb.onResult(false, TtsHelper.ERROR);
        }
    }

}
