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
import android.os.Handler;
import android.os.Looper;
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
import com.driot.bookplayer.tts.TtsUiHelper;
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
    private final MutableLiveData<Boolean> _loading = new MutableLiveData<>(false);
    private final Handler loadingH = new Handler(Looper.getMainLooper());
    // Timer used only for initial load (LOADING_TEXT/STARTING phases) to absorb
    // fast loads without a flash. Voice-warmup shows immediately (no timer).
    private boolean _timerPending = false;
    private final Runnable loadingRunnable = () -> {
        _timerPending = false;
        myLogD("Loading Overlay: timer fired → showing overlay");
        _loading.setValue(true);
    };
    private static final long LOADING_DELAY_MS = 300;

    // Callback waiting for MediaService to confirm PHASE_READY after a voice change
    @Nullable
    private WarmupUiCallback pendingWarmupCallback = null;

    private volatile boolean _stateSourcesAdded = false;

    private final MediatorLiveData<PlaybackUiState> _state = new MediatorLiveData<>();

    public LiveData<PlaybackUiState> getState() {
        if (!_stateSourcesAdded) {
            _stateSourcesAdded = true;
            _state.addSource(PlaybackUiBus.get().state(), s -> {
                updateLoadingState(s);
                emitStateWithSeekPreview();
            });
            _state.addSource(_seekPreviewMs, v -> emitStateWithSeekPreview());
        }
        return _state;
    }

    public LiveData<Boolean> getLoading() {
        return _loading;
    }

    private void updateLoadingState(@Nullable PlaybackUiState s) {
        if (s == null) {
            showOverlay(false);
            return;
        }

        String phase = s.loadPhase;

        // --- Voice warmup path ---
        // warmUpTtsVoice() already set _loading=true immediately.
        // Keep overlay up until READY confirms the new voice is active.
        if (pendingWarmupCallback != null) {
            if (Intents.PHASE_READY.equals(phase)) {
                myLogD("Loading Overlay: voice warmup → READY received, hiding overlay and firing callback");
                WarmupUiCallback cb = pendingWarmupCallback;
                pendingWarmupCallback = null;
                showOverlay(false);
                cb.onResult(true, TtsHelper.READY);
            } else {
                myLogD("Loading Overlay: voice warmup in progress (phase=" + phase + "), overlay stays up");
            }
            return; // don't touch timer-based logic while warmup is active
        }

        // --- Normal load path ---
        // STARTING = engine.start() called, synthesis underway → show overlay with
        // a short delay to avoid a flash on fast loads.
        // LOADING_TEXT = text pipeline busy → same short delay.
        // SPEAKING = first word actually spoken → hide overlay.
        // Anything else (READY, OFF, ERROR, …) → hide overlay.
        if (Intents.PHASE_STARTING.equals(phase) || Intents.PHASE_LOADING_TEXT.equals(phase)) {
            myLogD("Loading Overlay: phase=[" + phase + "] → startLoadingTimer");
            startLoadingTimer();
        } else {
            myLogD("Loading Overlay: phase=[" + phase + "] → stopLoadingTimer");
            stopLoadingTimer();
        }
    }

    private void startLoadingTimer() {
        if (Boolean.TRUE.equals(_loading.getValue())) {
            myLogD("Loading Overlay: startLoadingTimer — already showing, skip");
            return;
        }
        if (_timerPending) {
            myLogD("Loading Overlay: startLoadingTimer — timer already pending, skip");
            return;
        }
        myLogD("Loading Overlay: startLoadingTimer — scheduling in " + LOADING_DELAY_MS + "ms");
        _timerPending = true;
        loadingH.postDelayed(loadingRunnable, LOADING_DELAY_MS);
    }

    private void stopLoadingTimer() {
        boolean wasPending = _timerPending;
        loadingH.removeCallbacks(loadingRunnable);
        _timerPending = false;
        if (Boolean.TRUE.equals(_loading.getValue())) {
            myLogD("Loading Overlay: stopLoadingTimer — hiding overlay");
            _loading.setValue(false);
        } else if (wasPending) {
            myLogD("Loading Overlay: stopLoadingTimer — cancelled pending timer");
        }
    }

    /** Directly show or hide the overlay (bypasses the timer). */
    private void showOverlay(boolean show) {
        loadingH.removeCallbacks(loadingRunnable);
        _timerPending = false;
        myLogD("Loading Overlay: showOverlay(" + show + ")");
        _loading.setValue(show);
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
                        s.loadPhase, s.loadMessage, s.playing, s.ready, s.playMode,
                        preview, s.durationMs, s.sleepLeftMS,
                        s.title, s.subTitle, s.cover,
                        s.trackId, s.folderId, s.podcastFeedId, s.radioStationUuid,
                        s.ttsAudioStarted,
                        s.calledFrom, s.callCounter, s.extras);
            }
        }
        _state.setValue(s);
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
            myLogD("requestTtsTextOnce: already requested (shared), ignoring");
            return;
        }

        android.os.ResultReceiver rr = new android.os.ResultReceiver(
                new android.os.Handler(android.os.Looper.getMainLooper())) {
            @Override
            protected void onReceiveResult(int resultCode, android.os.Bundle resultData) {
                String txt = resultData != null ? resultData.getString(Intents.EXTRA_TTS_TEXT, "") : "";
                // Update the shared bus text
                ((MutableLiveData<String>) PlaybackUiBus.get().ttsText()).setValue(txt);
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

    // Speed / Sleep timer via custom actions (or fallback intents)
    @Nullable
    public Double getSpeedOrNull() {
        // Prefer surfacing speed in PlaybackUiState or via MediaSession extras;
        // otherwise return null and let UI render “—”.
        return null;
    }

    public void setSpeed(double s) {
        PlaybackCommands.setSpeed(getApplication(), s);
    }

    public void updateSleepTimer(int minutes) {
        PlaybackCommands.updateSleepTimer(getApplication(), minutes);
        sleepCustomMinutes = minutes; // for activity display (listening without actions since...)
    }

    public void resetSleepTimer() {
        PlaybackCommands.resetLastUserAction(getApplication());
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
        LocalBroadcastManager.getInstance(getApplication()).unregisterReceiver(ttsRangeRx);
    }

    public void setTtsStartOffsetChars(int start) {
        PlaybackCommands.setTtsStartOffset(getApplication(), start);
    }

    public void setupTtsVoiceSpinner(
            Context ctx,
            Spinner spinner,
            String initial,
            TtsUiHelper.OnVoiceSelected onSelected) {
        myLog("setupTtsVoiceSpinner - initial = " + initial);
        // Delegate entirely to the Activity's onSelected callback (which calls
        // warmUpTtsVoice).
        // Do NOT call warmUpTtsVoice here — it would send a second CMD_TTS_SET_VOICE
        // and
        // overwrite pendingWarmupCallback, breaking the READY handshake.
        TtsUiHelper.setupTtsVoiceSpinner(ctx, spinner, ttsManager, initial, voiceItem -> {
            if (onSelected != null)
                onSelected.onSelected(voiceItem);
        });
    }

    private volatile boolean inError = false;

    private void setLoadPhase(@NonNull String phaseId, @Nullable String message) {
        myLog("TTS Phase change: " + phaseId + " (msg: " + message + ")");
        // If you want to ignore warmup/starting while in error, keep this guard:
        if (inError && (Intents.PHASE_WARMING_UP.equals(phaseId) || Intents.PHASE_STARTING.equals(phaseId))) {
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

        String finalMessage = message;
        if (finalMessage == null || finalMessage.isEmpty()) {
            finalMessage = PlaybackPhaseMapper.getPhaseMessage(getApplication(), phaseId);
        }

        // When entering a preparation phase, reset ttsAudioStarted so the loading
        // overlay condition (busyPhase && !ttsAudioStarted) can trigger correctly.
        boolean resetAudioStarted = Intents.PHASE_WARMING_UP.equals(phaseId)
                || Intents.PHASE_STARTING.equals(phaseId)
                || Intents.PHASE_LOADING_TEXT.equals(phaseId);

        PlaybackUiState next = new PlaybackUiState(
                phaseId, finalMessage, cur.playing, cur.ready, cur.playMode,
                cur.positionMs, cur.durationMs, cur.sleepLeftMS,
                cur.title, cur.subTitle, cur.cover,
                cur.trackId, cur.folderId, cur.podcastFeedId, cur.radioStationUuid,
                resetAudioStarted ? false : cur.ttsAudioStarted,
                "PlayBackViewModel.setPhase", cur.callCounter + 1, cur.extras);
        PlaybackUiBus.get().emit(next);
    }

    public void warmUpTtsVoice(String voiceName, @Nullable WarmupUiCallback cb) {
        // Store callback FIRST, then show overlay immediately (no timer — avoids
        // the race where READY arrives before the 300ms Handler fires).
        pendingWarmupCallback = cb;
        showOverlay(true);
        setLoadPhase(Intents.PHASE_WARMING_UP, getApplication().getString(R.string.tts_phase_warming_up));

        try {
            MediaControllerCompat mc = PlaybackCommands.mcOrNull(getApplication());
            if (mc == null) {
                throw new IllegalStateException("MediaController not available");
            }
            Bundle b = new Bundle();
            b.putString(Intents.EXTRA_TTS_VOICE_NAME, voiceName);
            mc.getTransportControls().sendCustomAction(Intents.CMD_TTS_SET_VOICE, b);
            // Overlay stays up; updateLoadingState() will call showOverlay(false)
            // when MediaService broadcasts PHASE_READY.

            // Safety timeout: hide overlay and fire callback if READY never arrives.
            loadingH.postDelayed(() -> {
                WarmupUiCallback pending = pendingWarmupCallback;
                if (pending != null) {
                    myLogW("warmUpTtsVoice: timeout - MediaService never confirmed READY, forcing cb");
                    pendingWarmupCallback = null;
                    showOverlay(false);
                    pending.onResult(true, TtsHelper.READY);
                }
            }, 3000);
        } catch (Throwable t) {
            pendingWarmupCallback = null;
            showOverlay(false);
            setLoadPhase(Intents.PHASE_ERROR, getApplication().getString(R.string.tts_phase_error));
            if (cb != null)
                cb.onResult(false, TtsHelper.ERROR);
        }
    }

}
