package com.driot.bookplayer.player;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.view.KeyEvent;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.tts.TtsHelper;
import com.driot.bookplayer.utils.log.LoggingAndroidViewModel;

/**
 * Mini player's single source of truth:
 *   - ACTION_UI_STATE drives visibility/content.
 *   - Snapshots are used ONLY when bound, for progress smoothing.
 *   - We never overwrite with an "empty" state just because we're unbound.
 */
public class PlaybackViewModel extends LoggingAndroidViewModel {

    public interface WarmupUiCallback { void onResult(boolean ready, int reason); }

    private final MutableLiveData<PlaybackUiState> state = new MutableLiveData<>();
    public LiveData<PlaybackUiState> getState() { return state; }

    private final MutableLiveData<Boolean> miniSuppressed = new MutableLiveData<>(false);
    public LiveData<Boolean> getMiniSuppressed() { return miniSuppressed; }

    private final MutableLiveData<Boolean> isRadio = new MutableLiveData<>(false);
    public LiveData<Boolean> getIsRadio() { return isRadio; }

    private AudioService service;
    private boolean bound;

    // NEW: small holder for phase+message
    public static final class PhaseUi {
        public final @NonNull String phase;
        public final @Nullable String message;
        public PhaseUi(@NonNull String phase, @Nullable String message) {
            this.phase = phase; this.message = message;
        }
        public boolean isBusyPhase() {
            // Phases where we want a loading spinner
            return Intents.PHASE_LOADING_TEXT.equals(phase)
                    || Intents.PHASE_WARMING_UP.equals(phase)
                    || Intents.PHASE_STARTING.equals(phase);
        }
        @NonNull
        public String toString() { return phase + " - message = [" + message + "] - busy = [" + isBusyPhase() + "]"; }
    }

    private final MutableLiveData<PhaseUi> phase = new MutableLiveData<>(new PhaseUi(Intents.PHASE_LOADING_TEXT, null));
    public LiveData<PhaseUi> getPhase() { return phase; }


    private final ServiceConnection conn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            AudioService.BackgroundBinder b = (AudioService.BackgroundBinder) binder;
            service = b.getService();
            bound = true;
            // Optional: seed a first progress snapshot (won't affect visibility logic)
            pushSnapshot();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            bound = false;
            service = null;
            // IMPORTANT: do NOT post an empty state here.
            // We keep last known ACTION_UI_STATE so mini doesn't flicker/hide.
        }
    };

    public PlaybackViewModel(@NonNull Application app) {
        super(app);

        // Bind only if service is already running. Never auto-create.
        if (AudioService.isRunning) {
            app.bindService(new Intent(app, AudioService.class), conn, 0 /* no BIND_AUTO_CREATE */);
        }

        // Listen to unified UI state and (optionally) timer ticks for progress.
        LocalBroadcastManager lb = LocalBroadcastManager.getInstance(app);
        IntentFilter f = new IntentFilter();
        f.addAction(Intents.ACTION_UI_STATE);
        f.addAction(AudioService.NOTIFICATION_PLAYBACK_TIMER_VALUE); // progress only
        lb.registerReceiver(receiver, f);

        // Initial seed: if running and we have a last snapshot, use it; otherwise leave null.
        // (Leaving null keeps the mini hidden via fragment's initial GONE + null guard.)
        if (AudioService.isRunning && AudioService.lastUiState != null) {
            state.setValue(AudioService.lastUiState);
            // Use the suppression coming from service if you cache it there;
            // otherwise start "not suppressed" and wait for first ACTION_UI_STATE.
            miniSuppressed.setValue(false);
        }
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            final String action = i.getAction();
            if (Intents.ACTION_UI_STATE.equals(action)) {
                // we now know the service is running; bind if not already
                maybeBindOnFirstUiState();

                final boolean playing = i.getBooleanExtra(Intents.EXTRA_UI_PLAYING, false);
                final long pos        = i.getLongExtra(Intents.EXTRA_UI_POS, 0);
                final long dur        = i.getLongExtra(Intents.EXTRA_UI_DUR, 0);
                final String title    = i.getStringExtra(Intents.EXTRA_UI_TITLE);
                final String sub      = i.getStringExtra(Intents.EXTRA_UI_SUBTITLE);
                final String cover    = i.getStringExtra(Intents.EXTRA_UI_COVER);

                final int trackId     = i.getIntExtra(Intents.EXTRA_UI_TRACK_ID, 0);
                final int folderId    = i.getIntExtra(Intents.EXTRA_UI_FOLDER_ID, 0);
                final boolean ready   = i.getBooleanExtra(Intents.EXTRA_UI_READY, false);
                final boolean ttsMode = i.getBooleanExtra(Intents.EXTRA_UI_TTS, false);

                final String uiPhase = i.getStringExtra(Intents.EXTRA_UI_PHASE);
                final String uiMsg   = i.getStringExtra(Intents.EXTRA_UI_PHASE_MSG);
                if (uiPhase != null) {
                    phase.postValue(new PhaseUi(uiPhase, uiMsg));
                }

                miniSuppressed.postValue(i.getBooleanExtra(Intents.EXTRA_UI_SUPPRESS_MINI, false));

                boolean isRadioNow = i.getBooleanExtra(Intents.EXTRA_UI_IS_RADIO, false);
                isRadio.postValue(isRadioNow);

                state.postValue(new PlaybackUiState(
                        playing, pos, dur, title, sub, cover,
                        trackId, folderId, ready, ttsMode
                ));
            } else if (AudioService.NOTIFICATION_PLAYBACK_TIMER_VALUE.equals(action)) {
                if (bound && service != null) pushSnapshot();
            }
        }
    };


    /** Progress-only refresh. Never called when unbound. */
    private void pushSnapshot() {
        if (!bound || service == null) return;

        PlaybackUiState prev = state.getValue();
        if (prev == null) return; // nothing to smooth yet

        boolean playing = service.isPlaying();
        int pos         = service.getPosition();
        // Prefer existing duration unless service can provide a non-zero duration now
        long dur        = (prev.durationMs > 0) ? prev.durationMs : service.getDuration();

        boolean ready   = service.isReadyToPlay();
        boolean ttsMode = service.isTtsMode();

        state.postValue(new PlaybackUiState(
                playing,
                pos,
                dur,
                prev.title,
                prev.subTitle,
                prev.cover,
                prev.trackId,
                prev.folderId,
                ready,
                ttsMode
        ));
        miniSuppressed.postValue(service.isMiniSuppressed());
    }


    // Transport
    public void playPause() {
        myLog("playpause");
        if (service != null) {
            if (service.isPlaying()) {
                service.pauseAudio();
                FirebaseAnalyticsHelper.tellAnalyticsPlayAction("pause", "");
            } else {
                service.playAudio();
                FirebaseAnalyticsHelper.tellAnalyticsPlayAction("play", "");
            }
        } else {
            sendMediaButton(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
            FirebaseAnalyticsHelper.tellAnalyticsPlayAction("keycode playpause", "");
        }
    }

    public void next() {
        myLog("next");
        if (service != null) {
            service.forwardAudio();
            FirebaseAnalyticsHelper.tellAnalyticsPlayAction("next", "");
        } else {
            sendMediaButton(KeyEvent.KEYCODE_MEDIA_NEXT);
            FirebaseAnalyticsHelper.tellAnalyticsPlayAction("keycode next", "");
        }
    }

    public void prev() {
        myLog("prev");
        if (service != null) {
            service.backwardAudio();
            FirebaseAnalyticsHelper.tellAnalyticsPlayAction("prev", "");
        } else {
            sendMediaButton(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
            FirebaseAnalyticsHelper.tellAnalyticsPlayAction("keycode prev", "");
        }
    }

    /** seek needs binder access; no safe media-button fallback. */
    public void seekTo(int ms) {
        myLog("seekTo");
        if (service != null) {
            service.setPosition(ms);
            FirebaseAnalyticsHelper.tellAnalyticsPlayAction("seekTo", "");
        }
    }

    /** Close/hide mini and pause audio even if we're not bound. */
    public void dismissMini() {
        // Optimistic local UX
        miniSuppressed.setValue(true);

        // Let the service do the real work regardless of binding.
        Context app = getApplication();
        try {
            app.startService(new Intent(app, AudioService.class)
                    .setAction("CMD_STOP")
                    .putExtra(Intents.EXTRA_CALLER, this.getClass().getSimpleName()));
        } catch (IllegalStateException e) {
            // If the app is truly backgrounded and startService() is disallowed,
            // just request a hard stop (no-ops if not running).
            app.stopService(new Intent(app, AudioService.class));
        }
    }

    @Override protected void onCleared() {
        if (bound) getApplication().unbindService(conn);
        LocalBroadcastManager.getInstance(getApplication()).unregisterReceiver(receiver);
    }


    private void maybeBindOnFirstUiState() {
        if (!bound) {
            try {
                getApplication().bindService(
                        new Intent(getApplication(), AudioService.class),
                        conn,
                        0 /* no auto-create; service is already running because it just broadcast */
                );
            } catch (Throwable ignored) {}
        }
    }

    /** Send a media button to the service so it routes via MediaSession callbacks. */
    private void sendMediaButton(int keyCode) {
        Context app = getApplication();
        // ACTION_DOWN
        Intent down = new Intent(app, AudioService.class)
                .setAction(Intent.ACTION_MEDIA_BUTTON)
                .putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                .putExtra(Intents.EXTRA_CALLER, this.getClass().getSimpleName())
                .putExtra(Intents.EXTRA_FOREGROUND, true)
                ;
        ContextCompat.startForegroundService(app, down);
        // ACTION_UP (some OEMs need both)
        Intent up = new Intent(app, AudioService.class)
                .setAction(Intent.ACTION_MEDIA_BUTTON)
                .putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_UP, keyCode))
                .putExtra(Intents.EXTRA_CALLER, this.getClass().getSimpleName())
                .putExtra(Intents.EXTRA_FOREGROUND, true)
                ;
        ContextCompat.startForegroundService(app, up);
    }
    // inside PlaybackViewModel
    @Nullable public Double getSpeedOrNull() {
        if (service != null) try { return service.getSpeed(); } catch (Throwable ignored) {}
        return null;
    }
    public void setSpeed(double s) {
        if (service != null) { try { service.setSpeed(s); return; } catch (Throwable ignored) {} }
        // optional: send intent command if you have one (e.g., EXTRA_CMD_SET_SPEED)
        // ContextCompat.startForegroundService(getApplication(),
        //     new Intent(getApplication(), AudioService.class)
        //         .setAction(AudioService.EXTRA_CMD_SET_SPEED)
        //         .putExtra(AudioService.EXTRA_SPEED, s)
        //         .putExtra(Var.EXTRA_CALLER, getClass().getSimpleName()));
    }

    public void updateSleepTimer(int minutes) {
        if (service != null) { try { service.updateSleepTimer(minutes); return; } catch (Throwable ignored) {} }
        // or send an intent to service if you support it
    }

    @Nullable public Integer getCustomSleepMinutesOrNull() {
        if (service != null) try { return service.getCustomSleepTime(); } catch (Throwable ignored) {}
        return null;
    }

    @Nullable
    public Integer getAudioSessionIdOrNull() {
        if (service != null) try { return service.getAudioSessionId(); } catch (Throwable ignored) {}
        return null;
    }

    public String getTtsTextOrEmpty() {
        if (service != null) try { String t = service.getTtsText(); return t == null ? "" : t; } catch (Throwable ignored) {}
        return "";
    }
    public void setTtsStartOffsetChars(int start) {
        if (service != null) try { service.setTtsStartOffsetChars(start); } catch (Throwable ignored) {}
    }

    public void setupTtsVoiceSpinner(
            Context ctx,
            Spinner spinner,
            String initial,
            TtsHelper.OnVoiceSelected onSelected
    ) {
        myLog("setupTtsVoiceSpinner - initial = " + initial);
        final java.util.concurrent.atomic.AtomicBoolean first = new java.util.concurrent.atomic.AtomicBoolean(true);

        TtsHelper.setupTtsVoiceSpinner(ctx, spinner, initial, voiceItem -> {
            // 1) Always forward to UI if needed
            if (onSelected != null) onSelected.onSelected(voiceItem);

            // 2) Skip the very first callback (it’s the programmatic preselect)
            if (first.getAndSet(false)) return;

            // 3) Only warm up if it’s actually a new voice vs current engine
            final String picked = (voiceItem == null || voiceItem.name == null || voiceItem.name.isEmpty())
                    ? "system" : voiceItem.name;

            String current = null;
            if (service != null) try { current = service.getCurrentTtsVoiceName(); } catch (Throwable ignored) {}
            if (current != null && current.equalsIgnoreCase(picked)) {
                myLog("setupTtsVoiceSpinner: same as current engine voice → no warmup");
                return;
            }

            warmUpTtsVoice(picked, /*cb*/ null);
        });
    }

    private volatile boolean inError = false;

    private void setPhase(@NonNull String phaseId, @Nullable String message) {
        // If you want to ignore warmup/starting while in error, keep this guard:
        if (inError && (Intents.PHASE_WARMING_UP.equals(phaseId) || Intents.PHASE_STARTING.equals(phaseId))) {
            return;
        }
        if (Intents.PHASE_ERROR.equals(phaseId)) inError = true;
        if (Intents.PHASE_WARMING_UP.equals(phaseId) || Intents.PHASE_LOADING_TEXT.equals(phaseId)) inError = false;

        PhaseUi p = new PhaseUi(phaseId, message);
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            phase.setValue(p);
        } else {
            phase.postValue(p);
        }
    }

        public void warmUpTtsVoice(String voiceName, @Nullable WarmupUiCallback cb) {
        // Show spinner in the Activity while we switch
        setPhase(Intents.PHASE_WARMING_UP, getApplication().getString(R.string.tts_phase_warming_up));

        try {
            Context app = getApplication();
            // Ask the service to apply the voice right away
            ContextCompat.startForegroundService(
                    app,
                    new Intent(app, AudioService.class)
                            .setAction(Intents.CMD_TTS_SET_VOICE)
                            .putExtra(Intents.EXTRA_TTS_VOICE_NAME, voiceName)
                            .putExtra(Intents.EXTRA_FOREGROUND, true)
                            .putExtra(Intents.EXTRA_CALLER, this.getClass().getSimpleName() + ".warmUpTtsVoice()")
            );

            // Consider it ready (we switched instantly). If you later add true warm-up,
            // you can move this to the success callback.
            setPhase(Intents.PHASE_READY, null);
            if (cb != null) cb.onResult(true, TtsHelper.READY);
        } catch (Throwable t) {
            setPhase(Intents.PHASE_ERROR, getApplication().getString(R.string.tts_phase_error));
            if (cb != null) cb.onResult(false, TtsHelper.ERROR);
        }
    }


}

