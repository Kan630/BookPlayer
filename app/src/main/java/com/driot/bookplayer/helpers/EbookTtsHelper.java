package com.driot.bookplayer.helpers;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import com.driot.bookplayer.tts.SentenceChunker;

import java.util.Locale;

public class EbookTtsHelper implements TextToSpeech.OnInitListener {
    private final Context ctx;
    private TextToSpeech tts;
    private boolean ready = false;

    public interface Listener {
        void onStart(String uttId);
        void onDone(String uttId);
        void onError(String uttId, int errorCode);
        // NEW: called once the TTS engine is initialized and usable
        void onTtsReady(TextToSpeech tts);
    }

    private final Listener listener;

    public EbookTtsHelper(Context ctx, Listener listener) {
        this.ctx = ctx.getApplicationContext();
        this.listener = listener;
        tts = new TextToSpeech(this.ctx, this);
    }

    @Override public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int r = tts.setLanguage(Locale.getDefault());
            tts.setPitch(1.0f);
            tts.setSpeechRate(1.0f);

            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { if (listener != null) listener.onStart(id); }
                @Override public void onDone(String id)  { if (listener != null) listener.onDone(id); }
                @Override public void onError(String id) { if (listener != null) listener.onError(id, 0); }
                @Override public void onError(String id, int code) { if (listener != null) listener.onError(id, code); }
            });

            tts.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build());
            ready = (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED);

            // >>> Notify activity that the engine is ready
            if (ready && listener != null) {
                listener.onTtsReady(tts);
            }
        }
    }

    public boolean isReady() { return ready; }

    public void speak(String text) {
        if (!ready) return;
        for (String chunk : SentenceChunker.chunk(text, 1800)) {
            String uttId = "utt_" + System.nanoTime();
            tts.speak(chunk, TextToSpeech.QUEUE_ADD, null, uttId);
        }
    }

    public void setSpeechRate(float rate) { if (tts != null) tts.setSpeechRate(rate); }
    public TextToSpeech getTts() { return tts; }

    public void stop()  { if (tts != null) tts.stop(); }
    public void pause() { if (tts != null) tts.playSilentUtterance(250, TextToSpeech.QUEUE_ADD, "pause"); }
    public void shutdown() { if (tts != null) { tts.stop(); tts.shutdown(); tts = null; } }
}
