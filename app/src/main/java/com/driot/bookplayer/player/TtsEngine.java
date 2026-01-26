package com.driot.bookplayer.player;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.helpers.TextExtractor;
import com.driot.bookplayer.tts.TtsErrorUtils;
import com.driot.bookplayer.tts.TtsHelper;
import com.driot.bookplayer.objects.VoiceItem;
import com.driot.bookplayer.tts.AppTtsManager;
import com.driot.bookplayer.utils.log.LoggerHelper;

import java.util.Locale;
import java.util.Set;

/**
 * TextToSpeech-backed PlayerEngine.
 * - Loads plain text via TextExtractor
 * - Warm-ups the selected voice (optional) before reporting prepared
 * - Emits onTtsRange progress callbacks
 * - Race-safe via (gen, disposed, preparing/prepared) guards
 */
public final class TtsEngine extends LoggerHelper implements PlayerEngine, AppTtsManager.Listener {

    private final Context app;
    private final EngineListener listener;
    private final long gen;

    // TTS infra (shared engine via AppTtsManager + per-engine helper)
    private final AppTtsManager mgr;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Nullable private TtsHelper tts;

    // State
    private volatile boolean disposed = false;
    private volatile boolean preparing = false;
    private volatile boolean prepared  = false;
    private volatile boolean playing   = false;
    private volatile boolean completionTriggered = false; // Prevent double-triggering of completion

    private String text = "";
    private int lastCharSpoken = 0;
    private int resumeOffsetChars = 0;
    private long estDurationMs = 0;
    private long estPositionMs = 0;
    private float speechRate = 1.0f;

    private float volume = 1f;

    private boolean registeredWithMgr = false;

    public TtsEngine(@NonNull Context appContext,
                     @NonNull AppTtsManager appTtsManager,
                     @NonNull EngineListener listener,
                     long generationToken) {
        super(TtsEngine.class);
        this.app = appContext.getApplicationContext();
        this.mgr = appTtsManager;
        this.listener = listener;
        this.gen = generationToken;

        // Register as listener to shared TTS manager
        mgr.addListener(this);
        registeredWithMgr = true;

        // If engine is already ready, we can create our helper now
        if (mgr.isReady() && mgr.raw() != null) {
            this.tts = new TtsHelper(app, mgr.raw());
        }
    }

    // --------------------- PlayerEngine ---------------------

    @Override
    public void setDataSource(@NonNull Context ctx, @NonNull Uri uri, @NonNull String displayName) {
        prepared = false; preparing = false; playing = false;
        lastCharSpoken = 0; resumeOffsetChars = 0; estPositionMs = 0;
        completionTriggered = false; // Reset completion flag for new track

        String raw = TextExtractor.getPlainText(ctx, uri, displayName);
        // Normalize newlines
        raw = raw.replace("\r\n", "\n").replace('\r', '\n');
        // Heuristic paragraphize if almost no newlines
        if (TtsHelper.countNewlines(raw) < 2) raw = TtsHelper.smartParagraphize(raw);

        this.text = raw;
        this.estDurationMs = estimateDurationMs(text, speechRate);
    }

    @Override
    public void prepareAsync() {
        if (disposed || preparing || prepared) return;
        preparing = true;

        if (tts == null && mgr.isReady() && mgr.raw() != null) {
            tts = new TtsHelper(app, mgr.raw());
        }

        if (tts == null || !mgr.isReady() || mgr.raw() == null) {
            // wait for onTtsReady -> prepareAsync() again
            preparing = false; // avoid a stuck "preparing" flag
            return;
        }
        prepared = true;
        preparing = false;
        logCurrentVoice();
        listener.onPrepared(gen);
    }

    @Override
    public void start() {
        if (disposed || !prepared) return;
        if (playing) return;

        if (tts == null) {
            if (mgr.isReady() && mgr.raw() != null) {
                tts = new TtsHelper(app, mgr.raw());
            } else {
                return; // wait for onTtsReady → prepareAsync → start again
            }
        }
        playing = true;
        tts.setSpeechRate(speechRate);
        speakFromOffset(resumeOffsetChars);
    }

    @Override
    public void pause() {
        resumeOffsetChars = lastCharSpoken;
        if (tts != null) tts.stop();
        playing = false;
    }

    @Override
    public void stop() {
        if (tts != null) tts.stop();
        playing = false;
    }

    @Override
    public void reset() {
        stop();
        prepared = false; preparing = false;
        resumeOffsetChars = 0; estPositionMs = 0;
        completionTriggered = false;
        text = "";
    }

    @Override public boolean isPlaying() { return playing; }
    @Override public boolean isReady()   { return !disposed && prepared && tts != null; }
    @Override public long getCurrentPosition() { return estPositionMs; }
    @Override public long getDuration()        { return estDurationMs; }
    @Override public int getAudioSessionId()  { return 0; } // no visualizer for TTS

    @Override
    public void seekTo(long positionMs) {
        if (estDurationMs <= 0 || text.isEmpty()) return;
        long clamped = Math.max(0, Math.min(positionMs, estDurationMs));
        
        // If seeking to or near the end (within 500ms threshold), trigger completion
        // This matches ExoPlayer behavior where seeking to end triggers next track
        final long END_THRESHOLD_MS = 500;
        if (clamped >= estDurationMs - END_THRESHOLD_MS || clamped >= estDurationMs) {
            if (completionTriggered) {
                myLogD("seekTo: completion already triggered, ignoring");
                return;
            }
            myLogD("seekTo: seeking to end, triggering completion");
            completionTriggered = true;
            playing = false;
            if (tts != null) {
                tts.stop();
            }
            // Set position to end
            resumeOffsetChars = text.length();
            lastCharSpoken = text.length();
            estPositionMs = estDurationMs;
            // Trigger completion to move to next track
            listener.onCompletion(gen);
            return;
        }
        
        int charPos = (int) ((clamped / (double) estDurationMs) * Math.max(1, text.length()));
        resumeOffsetChars = charPos;
        estPositionMs = clamped;
        lastCharSpoken = resumeOffsetChars;
        if (playing) {
            if (tts != null) {
                tts.stop();
                speakFromOffset(resumeOffsetChars);
            }
        }
    }

    @Override
    public void setSpeed(float speed) {
        this.speechRate = Math.max(0.1f, speed);
        long old = estDurationMs;
        estDurationMs = estimateDurationMs(text, speechRate);
        if (old > 0) estPositionMs = (int) (estPositionMs * (estDurationMs / (double) old));
        if (playing && tts != null) tts.setSpeechRate(speechRate);
    }

    // --------------------- AppTtsManager.Listener ---------------------

    @Override
    public void onTtsReady(TextToSpeech engine) {
        if (disposed || prepared || preparing) return;
        this.tts = new TtsHelper(app, engine);
        prepareAsync();
    }

    @Override
    public void onStart(String utteranceId) {
        myLogD("onStart " + utteranceId);
        if (disposed) return;
        // ignore warm-up ids; nothing else to do
    }

    @Override
    public void onDone(String utteranceId) {
        myLogD("onDone " + utteranceId);
        if (disposed) return;

        // End of logical text?
        // Use >= to ensure we catch cases where we're at or past the end
        int logicalEnd = logicalTextEndIndex();
        if (lastCharSpoken >= logicalEnd) {
            if (completionTriggered) {
                myLogD("onDone: completion already triggered, ignoring");
                return;
            }
            myLogD("onDone: reached end (lastCharSpoken=" + lastCharSpoken + ", logicalEnd=" + logicalEnd + "), triggering completion");
            completionTriggered = true;
            playing = false;
            listener.onCompletion(gen);
            return;
        }

        // Continue speaking from where we stopped
        main.post(() -> {
            if (disposed || !playing) return;
            resumeOffsetChars = lastCharSpoken;
            speakFromOffset(resumeOffsetChars);
        });
    }

    @Override
    public void onError(String utteranceId, int errorCode) {
        String desc = TtsErrorUtils.describeOnErrorCode(errorCode);
        myLogD("onError " + utteranceId + ", code = " + errorCode + " -> " + desc);
        if (disposed) return;
        playing = false;
        if (listener != null) {
            // msg starts with "TTS" so MediaService knows it's a TTS error
            listener.onError(gen,
                    "TTS " + desc,
                    errorCode,
                    0 /* extra, if you have one */);
        }
    }

    @Override
    public void onUtteranceRange(int start, int end) {
        if (disposed) return;
        lastCharSpoken = Math.min(Math.max(0, end), text.length());
        if (!text.isEmpty() && estDurationMs > 0) {
            estPositionMs = (int) ((lastCharSpoken / (double) text.length()) * estDurationMs);
        }
        listener.onTtsRange(gen, start, Math.min(end, Math.max(0, text.length())));
        
        // Check if we've reached the end of the text and trigger completion
        // This ensures completion is triggered even if onDone doesn't fire reliably
        // Only trigger if we're at or past the logical end and still playing
        int logicalEnd = logicalTextEndIndex();
        if (playing && lastCharSpoken >= logicalEnd && logicalEnd > 0 && !completionTriggered) {
            myLogD("onUtteranceRange: reached end of text (lastCharSpoken=" + lastCharSpoken + ", logicalEnd=" + logicalEnd + "), triggering completion");
            completionTriggered = true;
            playing = false;
            // Post to main thread to avoid calling listener callbacks from TTS callback thread
            main.post(() -> {
                if (!disposed && tts != null) {
                    tts.stop();
                }
                if (!disposed) {
                    listener.onCompletion(gen);
                }
            });
        }
    }

    @Override
    public void onWordRange(int s, int e) { onUtteranceRange(s, e); }

    // --------------------- Public helpers specific to TTS ---------------------

    /** Expose the loaded raw text for UI. */
    @Nullable
    public String getText() {
        return text;
    }

    /** Allow service/UI to jump by characters (e.g., sentence/paragraph). */
    public void setStartOffsetChars(@IntRange(from = 0) int charOffset) {
        int target = Math.max(0, Math.min(charOffset, text.length()));
        resumeOffsetChars = target;
        lastCharSpoken = target;

        if (estDurationMs > 0 && !text.isEmpty()) {
            estPositionMs = (int) ((resumeOffsetChars / (double) text.length()) * estDurationMs);
        } else {
            estPositionMs = 0;
        }

        if (playing && prepared && tts != null) {
            tts.stop();
            speakFromOffset(resumeOffsetChars);
        }
    }

    /** Call when replacing engine to stop callbacks. */
    public void release() {
        disposed = true;
        if (registeredWithMgr) {
            mgr.removeListener(this);
            registeredWithMgr = false;
        }
        if (tts != null) tts.stop();
        tts = null;
        lastCharSpoken = 0;
        resumeOffsetChars = 0;
        estPositionMs = 0;
    }


    // --------------------- Internals ---------------------

    private void speakFromOffset(int offsetChars) {
        if (disposed || tts == null) return;
        tts.setSpeechRate(speechRate);
        final int off = Math.max(0, Math.min(offsetChars, text.length()));
        LocalBroadcastManager.getInstance(app).sendBroadcast(
                new Intent(Intents.NOTIFICATION_TTS_RANGE)
                        .putExtra(Intents.EXTRA_TTS_START, off)
                        .putExtra(Intents.EXTRA_TTS_END, off)
        );
        tts.speakFromOffset(text, off, volume);  // all chunking lives in TtsHelper
    }

    private int logicalTextEndIndex() {
        int i = text.length();
        while (i > 0) {
            char ch = text.charAt(i - 1);
            if (Character.isWhitespace(ch) || ch == '\u200B' || ch == '\uFEFF') i--;
            else break;
        }
        return i;
    }

    private static int estimateDurationMs(@Nullable String text, float rate) {
        if (text == null) return 0;
        int words = Math.max(1, text.trim().split("\\s+").length);
        double wpm = 180.0 * Math.max(0.1, rate);
        return (int) Math.round((words / wpm) * 60_000.0);
    }

    @Override public void setVolume(float v) {
        volume = Math.max(0f, Math.min(1f, v));
    }

    @Override public float getVolume() {
        return volume;
    }

    private void logCurrentVoice() {
        try {
            if (tts == null) return;
            TextToSpeech raw = mgr.raw();
            if (raw == null) return;
            Voice v = raw.getVoice();
            myLog("Current voice: " + VoiceItem.describeVoice(v).replace(", ","\n"));
        } catch (Throwable ignored) {}
    }

    public String getVoiceName() {
        TextToSpeech raw = mgr.raw();
        if (raw == null) return null;
        Voice v = raw.getVoice();
        return (v != null) ? v.getName() : null;
    }

    public boolean setVoiceByName(@Nullable String voiceName) {
        if (disposed) return false;

        // "system" or empty -> revert to engine default (language-based)
        if (voiceName == null || voiceName.isEmpty() || Option.DEFAULT_VOICE.equalsIgnoreCase(voiceName)) {
            try {
                TextToSpeech raw = mgr.raw();
                if (raw == null) return false;
                // Reset to device default locale
                Locale locale = Locale.getDefault();
                int langSetResult = raw.setLanguage(locale);
                TtsErrorUtils.logSetLanguageResult("TTS", langSetResult, locale);
                boolean ok = (langSetResult != TextToSpeech.LANG_MISSING_DATA && langSetResult != TextToSpeech.LANG_NOT_SUPPORTED);
                if (ok) restartIfPlaying();
                return ok;
            } catch (Throwable ignored) {
                return false;
            }
        }

        try {
            Set<Voice> voices = mgr.getVoices();
            if (voices == null || voices.isEmpty()) return false;

            Voice target = null;
            for (Voice v : voices) {
                if (voiceName.equals(v.getName())) { target = v; break; }
            }
            if (target == null) return false;

            int r = mgr.setVoice(target);
            if (r != TextToSpeech.SUCCESS) {
                myLogE("error setting TTS engine Voice");
                return false;
            } else {
                myLog("TTS engine Voice set : " + target.getName());
            }

            restartIfPlaying();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void restartIfPlaying() {
        if (tts == null) return;
        // keep current rate
        tts.setSpeechRate(speechRate);

        if (playing) {
            // resume from last audible boundary we tracked
            int start = Math.max(0, Math.min(lastCharSpoken, text != null ? text.length() : 0));
            // ensure internal offset reflects where we’re resuming
            resumeOffsetChars = start;
            tts.stop();
            speakFromOffset(start);
        }
    }

}
