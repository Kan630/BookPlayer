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

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.driot.bookplayer.utils.KanLogger;

/**
 * Mini player's single source of truth:
 *   - ACTION_UI_STATE drives visibility/content.
 *   - Snapshots are used ONLY when bound, for progress smoothing.
 *   - We never overwrite with an "empty" state just because we're unbound.
 */
public class PlaybackViewModel extends AndroidViewModel {

    private final MutableLiveData<PlaybackUiState> state = new MutableLiveData<>();
    public LiveData<PlaybackUiState> getState() { return state; }

    private final MutableLiveData<Boolean> miniSuppressed = new MutableLiveData<>(false);
    public LiveData<Boolean> getMiniSuppressed() { return miniSuppressed; }

    private AudioService service;
    private boolean bound;

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
        f.addAction(AudioService.ACTION_UI_STATE);
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
            if (AudioService.ACTION_UI_STATE.equals(action)) {
                // we now know the service is running; bind if not already
                maybeBindOnFirstUiState();

                final boolean playing = i.getBooleanExtra(AudioService.EXTRA_UI_PLAYING, false);
                final long pos        = i.getLongExtra(AudioService.EXTRA_UI_POS, 0);
                final long dur        = i.getLongExtra(AudioService.EXTRA_UI_DUR, 0);
                final String title    = i.getStringExtra(AudioService.EXTRA_UI_TITLE);
                final String sub      = i.getStringExtra(AudioService.EXTRA_UI_SUBTITLE);
                final String cover    = i.getStringExtra(AudioService.EXTRA_UI_COVER);

                // NEW
                final int trackId     = i.getIntExtra(AudioService.EXTRA_UI_TRACK_ID, 0);
                final int folderId    = i.getIntExtra(AudioService.EXTRA_UI_FOLDER_ID, 0);
                final boolean ready   = i.getBooleanExtra(AudioService.EXTRA_UI_READY, false);
                final boolean ttsMode = i.getBooleanExtra(AudioService.EXTRA_UI_TTS, false);

                miniSuppressed.postValue(i.getBooleanExtra(AudioService.EXTRA_UI_SUPPRESS_MINI, false));
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
            if (service.isPlaying()) service.pauseAudio(); else service.playAudio();
        } else {
            // unbound path → toggle
            sendMediaButton(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        }
    }

    public void next() {
        myLog("next");
        if (service != null) service.forwardAudio();
        else sendMediaButton(KeyEvent.KEYCODE_MEDIA_NEXT);
    }

    public void prev() {
        myLog("prev");
        if (service != null) service.backwardAudio();
        else sendMediaButton(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
    }

    /** seek needs binder access; no safe media-button fallback. */
    public void seekTo(int ms) {
        myLog("seekTo");
        if (service != null) service.setPosition(ms);
    }

    /** Close/hide mini and pause audio even if we're not bound. */
    public void dismissMini() {
        // Optimistic local UX
        miniSuppressed.setValue(true);

        // Let the service do the real work regardless of binding.
        Context app = getApplication();
        Intent cmd = new Intent(app, AudioService.class)
                .setAction(AudioService.ACTION_CMD)
                .putExtra(AudioService.EXTRA_CMD, AudioService.CMD_STOP);
        try {
            androidx.core.content.ContextCompat.startForegroundService(app, cmd);
        } catch (Throwable ignored) {}
        // Do NOT post empty state; wait for ACTION_UI_STATE from service.
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
                .putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        ContextCompat.startForegroundService(app, down);
        // ACTION_UP (some OEMs need both)
        Intent up = new Intent(app, AudioService.class)
                .setAction(Intent.ACTION_MEDIA_BUTTON)
                .putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_UP, keyCode));
        ContextCompat.startForegroundService(app, up);
    }


    // ----------------------- LOG -----------------------
    private static final String TAG = "PlaybackViewModel";
    private static void myLog(String str) { KanLogger.myLog(TAG, str); }
    private static void myLogD(String str) { KanLogger.myLogD(TAG, str); }
    private static void myLogI(String str) { KanLogger.myLogI(TAG, str); }
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
    private static void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, TAG, str); }
    private static void myToastEE(Throwable t, String str) { KanLogger.myToastEE(t, TAG, str); }
}

