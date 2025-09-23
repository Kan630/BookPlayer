package com.driot.bookplayer.player;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.objects.PlayList;
import com.driot.bookplayer.services.AudioService;

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
            // Seed with current snapshot if service is already running
            pushSnapshot();
        }
        @Override public void onServiceDisconnected(ComponentName name) { bound = false; service = null; }
    };

    public PlaybackViewModel(@NonNull Application app) {
        super(app);

        // ⚠️ Do NOT auto-create the service on app open.
        // Bind only if service is actually running.
        if (AudioService.isRunning) {
            app.bindService(new Intent(app, AudioService.class), conn, 0 /* no BIND_AUTO_CREATE */);
        }

        // Observe unified UI state + keep timer ticks to refresh position
        LocalBroadcastManager lb = LocalBroadcastManager.getInstance(app);
        IntentFilter f = new IntentFilter();
        f.addAction(AudioService.ACTION_UI_STATE);                 // NEW unified state
        f.addAction(AudioService.NOTIFICATION_PLAYBACK_TIMER_VALUE); // still useful for live progress
        f.addAction(AudioService.NOTIFICATION_NEWTRACK);
        f.addAction(AudioService.READY_TO_PLAY);
        f.addAction(AudioService.NOTIFICATION_TRACKFINISHED);
        lb.registerReceiver(receiver, f);

        // Seed initial UI:
        // Only trust lastUiState if service is truly running; else start empty so mini stays hidden.
        if (AudioService.isRunning && AudioService.lastUiState != null) {
            state.setValue(AudioService.lastUiState);
            miniSuppressed.setValue(AudioService.lastUiState.playing ? false : miniSuppressed.getValue());
        } else {
            state.setValue(new PlaybackUiState(false, 0, 0, "", ""));
            miniSuppressed.setValue(false);
        }
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (AudioService.ACTION_UI_STATE.equals(i.getAction())) {
                miniSuppressed.postValue(i.getBooleanExtra(AudioService.EXTRA_UI_SUPPRESS_MINI, false));
                state.postValue(new PlaybackUiState(
                        i.getBooleanExtra(AudioService.EXTRA_UI_PLAYING, false),
                        i.getIntExtra(AudioService.EXTRA_UI_POS, 0),
                        i.getIntExtra(AudioService.EXTRA_UI_DUR, 0),
                        i.getStringExtra(AudioService.EXTRA_UI_TITLE),
                        i.getStringExtra(AudioService.EXTRA_UI_SUBTITLE)
                ));
            } else if (bound && service != null) {
                // e.g., timer ticks; only safe if we have a live service handle
                pushSnapshot();
            } else {
                // ignore — don’t clobber state with empties
            }
        }
    };

    private void pushSnapshot() {
        if (service == null) {
            return;
        }
        PlayList pl = PlayList.getInstance();
        ZikFile z = (pl!=null) ? pl.getZikFile() : null;
        boolean playing = (service != null) && service.isPlaying();
        int pos = (service != null) ? service.getPosition() : 0;
        int dur = (z != null) ? (int) z.getDuration() : 0;
        String title = (z != null) ? z.getFolderName()  : "";
        String sub   = (z != null) ? z.getDisplayName() : "";

        state.postValue(new PlaybackUiState(playing, pos, dur, title, sub));
        miniSuppressed.postValue(service != null && service.isMiniSuppressed());
    }


    private PlaybackUiState fromIntent(Intent i) {
        return new PlaybackUiState(
                i.getBooleanExtra(AudioService.EXTRA_UI_PLAYING, false),
                i.getIntExtra(AudioService.EXTRA_UI_POS, 0),
                i.getIntExtra(AudioService.EXTRA_UI_DUR, 0),
                i.getStringExtra(AudioService.EXTRA_UI_TITLE),
                i.getStringExtra(AudioService.EXTRA_UI_SUBTITLE)
        );
    }

    // Control methods stay the same
    public void playPause() { if (service==null) return; if (service.isPlaying()) service.pauseAudio(); else service.playAudio(); }
    public void next() { if (service!=null) service.forwardAudio(); }
    public void prev() { if (service!=null) service.backwardAudio(); }
    public void seekTo(int ms) { if (service!=null) service.setPosition(ms); }

    @Override protected void onCleared() {
        if (bound) getApplication().unbindService(conn);
        LocalBroadcastManager.getInstance(getApplication()).unregisterReceiver(receiver);
    }

    public void dismissMini() {
        // Optimistic local UX: hide immediately
        miniSuppressed.setValue(true);

        // Always instruct the service to pause + suppress, even if we're not bound
        Context app = getApplication();
        Intent cmd = new Intent(app, AudioService.class)
                .setAction(AudioService.ACTION_CMD)
                .putExtra(AudioService.EXTRA_CMD, AudioService.CMD_PAUSE_AND_SUPPRESS);
        try {
            app.startService(cmd); // ok from foreground UI; service will pause, suppress, broadcast, and stopSelf()
        } catch (Exception ignored) {}

        // Do NOT overwrite state with an empty snapshot here.
        // We'll rely on ACTION_UI_STATE (broadcastUiCleared) from the service to update the UI.
    }

}
