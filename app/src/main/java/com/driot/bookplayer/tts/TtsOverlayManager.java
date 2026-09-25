package com.driot.bookplayer.tts;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.player.PlaybackCommands;
import com.driot.bookplayer.player.PlaybackUiState;

import java.lang.ref.WeakReference;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

/**
 * Manages the loading overlay for TTS.
 * Separated from TtsHighlighter so that overlay can functional even if
 * highlighting is disabled.
 */
public class TtsOverlayManager {

    /** Whatever shows the player's loading overlay (the player screen, e.g. PlayerFragment). */
    public interface Host {
        void showTtsLoading(boolean show, @Nullable String msg);
    }

    private final WeakReference<Host> hostRef;
    private final Context appContext;
    @Nullable
    private final AppTtsManager ttsManager;
    private final Handler uiH = new Handler(Looper.getMainLooper());
    private final Runnable loadingRunnable;
    /**
     * Safety timeout: force-hide the overlay after this many ms if TTS never
     * starts.
     */
    private final Runnable safetyTimeoutRunnable;

    private boolean ttsActuallyStarted;
    private boolean loadingProgressOverlayTimerStarted;
    private boolean overlayVisible;
    private int auto_hide_countdown_seconds = Option.DEFAULT_TTS_OVERLAY_TIMEOUT_SEC;
    private String currentPhase = "";

    public TtsOverlayManager(Host host, Context context, @Nullable AppTtsManager ttsManager) {
        this.hostRef = new WeakReference<>(host);
        this.appContext = context.getApplicationContext();
        this.ttsManager = ttsManager;
        this.safetyTimeoutRunnable = new Runnable() {
            @Override
            public void run() {
                Host host = hostRef.get();
                if (host == null)
                    return;

                if (auto_hide_countdown_seconds <= 0) {
                    // The engine may still be cold-binding (e.g. right after switching TTS
                    // engine in Settings - some engines take much longer than others to bind).
                    boolean stillWarmingUp = ttsManager != null && !ttsManager.isReady();
                    myLogW("TTS OVERLAY: safety timeout reached - force-hiding overlay & pausing playback"
                            + " (stillWarmingUp=" + stillWarmingUp + ")");
                    loadingProgressOverlayTimerStarted = false;
                    overlayVisible = false;
                    host.showTtsLoading(false, null);
                    PlaybackCommands.pause(appContext);
                    myToastEE(null, appContext.getString(stillWarmingUp
                            ? R.string.tts_engine_still_warming_up
                            : R.string.tts_error_timeout));
                } else {
                    overlayVisible = true;
                    host.showTtsLoading(true, currentPhase + " (" + auto_hide_countdown_seconds + "s)");
                    auto_hide_countdown_seconds--;
                    uiH.postDelayed(this, 1000);
                }
            }
        };
        this.loadingRunnable = () -> {
            if (!Option.getTtsShowLoadingOverlay()) {
                myLogD("TTS OVERLAY: disabled by user option");
                return;
            }
            if (hostRef.get() != null) {
                auto_hide_countdown_seconds = Option.getTtsOverlayTimeoutSec();
                uiH.removeCallbacks(safetyTimeoutRunnable);
                uiH.post(safetyTimeoutRunnable);
            }
        };
    }

    public void onPlaybackStateChanged(PlaybackUiState s) {
        if (s == null)
            return;

        // Harden: if we are not in TTS mode, kill everything immediately
        if (!Var.PLAY_MODE_TTS.equals(s.playMode)) {
            reset();
            myLog("playMode not TTS => killing");
            return;
        }

        // Overlay Logic
        this.currentPhase = s.loadPhase != null ? s.loadPhase : "";
        if (Intents.PHASE_SPEAKING.equals(s.loadPhase)) {
            if (!ttsActuallyStarted) {
                ttsActuallyStarted = true;
                stopLoadingProgressOverlayTimer();
                myLogI("TTS OVERLAY: Phase changed to SPEAKING");
            }
        } else if (!Intents.PHASE_OFF.equals(s.loadPhase)) {
            ttsActuallyStarted = false;
            startLoadingProgressOverlayTimer();
            // If already visible, update message immediately for responsiveness
            if (overlayVisible) {
                Host host = hostRef.get();
                if (host != null) {
                    host.showTtsLoading(true, currentPhase + " (" + auto_hide_countdown_seconds + "s)");
                }
            }
            myLogD("TTS OVERLAY: NOT SPEAKING, phase is : " + s.loadPhase);
        }
    }

    public void onHighlightReceived() {
        if (!ttsActuallyStarted && Intents.PHASE_SPEAKING.equals(currentPhase)) {
            ttsActuallyStarted = true;
            stopLoadingProgressOverlayTimer(); // <--- Hide overlay immediately
            myLogI("TTS OVERLAY: first highlight callback received (phase=" + currentPhase + ")");
        }
    }

    public void reset() {
        ttsActuallyStarted = false;
        stopLoadingProgressOverlayTimer();
        myLogE("reset");
    }

    private void startLoadingProgressOverlayTimer() {
        if (!loadingProgressOverlayTimerStarted) {
            loadingProgressOverlayTimerStarted = true;
            uiH.removeCallbacks(loadingRunnable);
            uiH.postDelayed(loadingRunnable, Var.PROGRESS_OVERLAY_START_DELAY);
            myLogI("startLoadingProgressOverlayTimer: scheduled in " + Var.PROGRESS_OVERLAY_START_DELAY + "ms");
        }
    }

    private void stopLoadingProgressOverlayTimer() {
        if (loadingProgressOverlayTimerStarted) {
            loadingProgressOverlayTimerStarted = false;
            overlayVisible = false;
            uiH.removeCallbacks(loadingRunnable);
            uiH.removeCallbacks(safetyTimeoutRunnable);
            myLogI("stopLoadingProgressOverlayTimer");
            Host host = hostRef.get();
            if (host != null) {
                host.showTtsLoading(false, null);
            }
        }
    }

    public void onDestroy() {
        uiH.removeCallbacksAndMessages(null);
    }
}
