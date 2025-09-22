package com.driot.bookplayer.player;

import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.content.Context;

import androidx.annotation.NonNull;

public final class AudioFocusHelper {

    public interface Listener {
        void onFocusLost();
        void onFocusGain();
    }

    private final AudioManager am;
    private final Listener listener;
    private AudioFocusRequest afr;

    public AudioFocusHelper(@NonNull Context ctx, @NonNull Listener l) {
        this.am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        this.listener = l;
    }

    public void request() {
        if (am == null) return;
        AudioAttributes aa = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        afr = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(aa)
                .setOnAudioFocusChangeListener(fc -> {
                    if (fc <= 0) listener.onFocusLost(); else listener.onFocusGain();
                })
                .build();
        am.requestAudioFocus(afr);
    }

    public void abandon() {
        if (am == null) return;
        if (afr != null) {
            am.abandonAudioFocusRequest(afr);
        } else {
            am.abandonAudioFocus(null);
        }
    }
}
