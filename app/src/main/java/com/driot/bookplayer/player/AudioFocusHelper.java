package com.driot.bookplayer.player;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;

import com.driot.bookplayer.utils.log.LoggerHelper;

public final class AudioFocusHelper extends LoggerHelper implements AudioManager.OnAudioFocusChangeListener {

    public interface Listener {
        /** Called on AUDIOFOCUS_GAIN. Also implies duck=false. */
        void onFocusGain();

        /** Called on LOSS or LOSS_TRANSIENT (not DUCK). You get the raw change code. */
        void onFocusLost(int change);

        /** true when entering duck, false when leaving duck (e.g., on GAIN or before LOSS). */
        void onDuck(boolean ducking);
    }

    private final AudioManager am;
    private final Listener cb;

    // O+ request object so we can properly abandon
    private AudioFocusRequest focusReq;

    // For debouncing spurious early losses right after our own request
    private long lastRequestUptime = 0L;

    public AudioFocusHelper(Context ctx, Listener cb) {
        super(AudioFocusHelper.class);
        this.am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        this.cb = cb;
    }

    public void request() {
        lastRequestUptime = android.os.SystemClock.uptimeMillis();

        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH) // your content is mostly spoken word
                .build();

        focusReq = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(this)
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(false) // we prefer duck callbacks, not auto-pause
                .build();

        am.requestAudioFocus(focusReq);
    }

    public void abandon() {
        if (focusReq != null) {
            am.abandonAudioFocusRequest(focusReq);
            focusReq = null;
        }
    }

    @Override public void onAudioFocusChange(int change) {
        // Debounce: ignore very-early transients right after we asked for focus (during our own start)
        if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            long sinceReq = android.os.SystemClock.uptimeMillis() - lastRequestUptime;
            if (sinceReq < 500) {
                myLogW("Debounce: ignore very-early transients right after we asked for focus (during our own start)");
                return;
            }
        }

        switch (change) {
            case AudioManager.AUDIOFOCUS_GAIN:
                myLog("AUDIO_FOCUS_GAIN");
                // Leaving duck as well
                cb.onDuck(false);
                cb.onFocusGain();
                break;

            case AudioManager.AUDIOFOCUS_LOSS:
                myLog("AUDIO_FOCUS_LOSS");
                cb.onDuck(false);
                cb.onFocusLost(AudioManager.AUDIOFOCUS_LOSS);
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                myLog("AUDIO_FOCUS_LOSS_TRANSIENT");
                cb.onDuck(false);
                cb.onFocusLost(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                myLog("AUDIO_FOCUS_LOSS_TRANSIENT_CAN_DUCK");
                cb.onDuck(true);
                break;

            default:
                myLogW("Unknown AUDIO FOCUS change : " + change);
                // ignore
        }
    }
}
