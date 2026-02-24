package com.driot.bookplayer.tts;

import android.os.Handler;
import android.os.Looper;

import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.player.PlayActivity;
import com.driot.bookplayer.player.PlaybackCommands;
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
    private int countdownSeconds = 10;
    private String currentPhase = "";

    public TtsOverlayManager(BaseActivity activity) {
        this.activityRef = new WeakReference<>(activity);
        this.safetyTimeoutRunnable = new Runnable() {
            @Override
            public void run() {
                BaseActivity act = activityRef.get();
                if (!(act instanceof PlayActivity))
                    return;
                PlayActivity playAct = (PlayActivity) act;

                if (countdownSeconds <= 0) {
                    myLogW("TTS OVERLAY: safety timeout reached - force-hiding overlay & killing engine");
                    loadingProgressOverlayTimerStarted = false;
                    playAct.showTtsLoading(false);
                    PlaybackCommands.stop(playAct.getApplicationContext());
                    myToastEE(null, "TTS error: timeout");
                } else {
                    playAct.showTtsLoading(true, currentPhase + " (" + countdownSeconds + "s)");
                    countdownSeconds--;
                    uiH.postDelayed(this, 1000);
                }
            }
        };
        this.loadingRunnable = () -> {
            BaseActivity act = activityRef.get();
            if (act instanceof PlayActivity) {
                countdownSeconds = 10;
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
            if (loadingProgressOverlayTimerStarted) {
                BaseActivity act = activityRef.get();
                if (act instanceof PlayActivity) {
                    ((PlayActivity) act).showTtsLoading(true, currentPhase + " (" + countdownSeconds + "s)");
                }
            }
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
            myLogI("startLoadingProgressOverlayTimer: scheduled in " + Var.PROGRESS_OVERLAY_START_DELAY + "ms");
        }
    }

    private void stopLoadingProgressOverlayTimer() {
        if (loadingProgressOverlayTimerStarted) {
            loadingProgressOverlayTimerStarted = false;
            uiH.removeCallbacks(loadingRunnable);
            uiH.removeCallbacks(safetyTimeoutRunnable);
            myLogI("stopLoadingProgressOverlayTimer");
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
