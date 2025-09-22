package com.driot.bookplayer.player;

import android.media.AudioManager;
import android.content.Context;

public final class AudioFocusHelper implements AudioManager.OnAudioFocusChangeListener {
    public interface Listener {
        void onFocusGain();
        void onFocusLost(); // use for LOSS or LOSS_TRANSIENT (not DUCK)
    }

    private final AudioManager am;
    private final Listener cb;
    private long lastRequestUptime = 0L;

    public AudioFocusHelper(Context ctx, Listener cb) {
        this.am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        this.cb = cb;
    }

    public void request() {
        lastRequestUptime = android.os.SystemClock.uptimeMillis();
        am.requestAudioFocus(this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);
    }

    public void abandon() {
        am.abandonAudioFocus(this);
    }

    @Override public void onAudioFocusChange(int change) {
        switch (change) {
            case AudioManager.AUDIOFOCUS_GAIN:
                cb.onFocusGain();
                break;

            case AudioManager.AUDIOFOCUS_LOSS:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT: {
                // Debounce: ignore very-early transients during track switch
                long sinceReq = android.os.SystemClock.uptimeMillis() - lastRequestUptime;
                if (sinceReq < 500) return;
                cb.onFocusLost();
                break;
            }

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // DO NOT pause on duck. If you want, lower volume instead.
                // (no-op for now)
                break;

            default:
                // ignore
        }
    }
}
