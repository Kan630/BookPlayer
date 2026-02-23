package com.driot.bookplayer.tts;

import android.os.Handler;
import android.os.Looper;

import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.player.PlayActivity;
import com.driot.bookplayer.player.PlaybackUiState;
import com.driot.bookplayer.utils.log.BaseActivity;

import java.lang.ref.WeakReference;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

/**
 * Manages the loading overlay for TTS.
 * Separated from TtsHighlighter so that overlay can functional even if
 * highlighting is disabled.
 */
public class TtsOverlayManager {

    private final WeakReference<BaseActivity> activityRef;
    private final Handler uiH = new Handler(Looper.getMainLooper());
    private final Runnable loadingRunnable;
    /**
     * Safety timeout: force-hide the overlay after this many ms if TTS never
     * starts.
     */
    private static final long OVERLAY_SAFETY_TIMEOUT_MS = 10_000;
    private final Runnable safetyTimeoutRunnable;

    private boolean ttsActuallyStarted;
    private boolean loadingProgressOverlayTimerStarted;

    public TtsOverlayManager(BaseActivity activity) {
        this.activityRef = new WeakReference<>(activity);
        // Initialize safetyTimeoutRunnable FIRST so loadingRunnable can reference it
        // safely
        this.safetyTimeoutRunnable = () -> {
            myLogW("TTS OVERLAY: safety timeout reached (" + OVERLAY_SAFETY_TIMEOUT_MS + "ms) – force-hiding overlay");
            loadingProgressOverlayTimerStarted = false;
            BaseActivity act = activityRef.get();
            if (act instanceof PlayActivity) {
                ((PlayActivity) act).showTtsLoading(false);
            }
        };
        this.loadingRunnable = () -> {
            BaseActivity act = activityRef.get();
            if (act instanceof PlayActivity) {
                ((PlayActivity) act).showTtsLoading(true);
            }
            // Arm the safety timeout the moment the overlay becomes visible
            uiH.removeCallbacks(safetyTimeoutRunnable);
            uiH.postDelayed(safetyTimeoutRunnable, OVERLAY_SAFETY_TIMEOUT_MS);
        };
    }

    public void onPlaybackStateChanged(PlaybackUiState s) {
        if (s == null)
            return;

        // Overlay Logic
        if (Intents.PHASE_SPEAKING.equals(s.loadPhase)) {
            if (!ttsActuallyStarted) {
                ttsActuallyStarted = true;
                stopLoadingProgressOverlayTimer();
                myLogI("TTS OVERLAY: Phase changed to SPEAKING");
            }
        } else {
            ttsActuallyStarted = false;
            startLoadingProgressOverlayTimer();
            myLogD("TTS OVERLAY: NOT SPEAKING, phase is : " + s.loadPhase);
        }
    }

    public void onHighlightReceived() {
        // Mark that TTS has actually started when we receive the first callback
        if (!ttsActuallyStarted) {
            ttsActuallyStarted = true;
            stopLoadingProgressOverlayTimer(); // <--- Hide overlay immediately
            myLogI("TTS OVERLAY: first highlight callback received");
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
            myLogE("startLoadingProgressOverlayTimer: scheduled in " + Var.PROGRESS_OVERLAY_START_DELAY + "ms");
        }
    }

    private void stopLoadingProgressOverlayTimer() {
        if (loadingProgressOverlayTimerStarted) {
            loadingProgressOverlayTimerStarted = false;
            uiH.removeCallbacks(loadingRunnable);
            uiH.removeCallbacks(safetyTimeoutRunnable);
            myLogE("stopLoadingProgressOverlayTimer");
            BaseActivity act = activityRef.get();
            if (act instanceof PlayActivity) {
                ((PlayActivity) act).showTtsLoading(false);
            }
        }
    }

    public void onDestroy() {
        uiH.removeCallbacksAndMessages(null);
    }
}
