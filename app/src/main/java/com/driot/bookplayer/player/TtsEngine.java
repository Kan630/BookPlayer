package com.driot.bookplayer.player;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.helpers.TextExtractor;
import com.driot.bookplayer.helpers.TtsHelper;
import com.driot.bookplayer.objects.VoiceItem;
import com.driot.bookplayer.utils.AppTtsManager;

import java.io.File;
import java.util.Set;

/**
 * TextToSpeech-backed PlayerEngine.
 * - Loads plain text via TextExtractor
 * - Warm-ups the selected voice (optional) before reporting prepared
 * - Emits onTtsRange progress callbacks
 * - Race-safe via (gen, disposed, preparing/prepared) guards
 */
public final class TtsEngine implements PlayerEngine, AppTtsManager.Listener {

    private final Context app;
    private final EngineListener listener;
    private final long gen;

    // TTS infra (shared engine via AppTtsManager + per-engine helper)
    private final AppTtsManager mgr;
    private final Handler main = new Handler(Looper.getMainLooper());

    private @Nullable AutoCloseable ttsHandle; // to release AppTtsManager listener
    private @Nullable TtsHelper tts;

    // State
    private volatile boolean disposed = false;
    private volatile boolean preparing = false;
    private volatile boolean prepared  = false;
    private volatile boolean playing   = false;

    private String text = "";
    private int lastCharSpoken = 0;
    private int resumeOffsetChars = 0;
    private int estDurationMs = 0;
    private int estPositionMs = 0;
    private float speechRate = 1.0f;

    public TtsEngine(@NonNull Context appContext,
                     @NonNull AppTtsManager appTtsManager,
                     @NonNull EngineListener listener,
                     long generationToken) {
        this.app = appContext.getApplicationContext();
        this.mgr = appTtsManager;
        this.listener = listener;
        this.gen = generationToken;

        // Acquire shared TTS and receive lifecycle callbacks
        this.ttsHandle = appTtsManager.acquire(this /*owner*/, this /*listener*/);
        this.tts = new TtsHelper(app, appTtsManager.raw(), /*listener*/ null);
    }

    // --------------------- PlayerEngine ---------------------

    @Override
    public void setDataSource(@NonNull Context ctx, @NonNull Uri uri, @NonNull String displayName) {
        prepared = false; preparing = false; playing = false;
        lastCharSpoken = 0; resumeOffsetChars = 0; estPositionMs = 0;

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
        if (tts == null || !mgr.isReady() || mgr.raw() == null) {
            // Defer until onTtsReady
            return;
        }
        preparing = true;

        // Optional: pick a saved/app-wide voice
        String voiceName = desiredVoiceName();
        if (voiceName == null) {
            prepared = true; preparing = false;
            listener.onPrepared(gen);
            return;
        }

        // Warm-up selected voice so first speak() is instant
        setTtsVoiceByNameAsync(voiceName, /*timeoutMs*/ 5000L, (ok, reason) -> {
            if (disposed) return;
            preparing = false;
            if (ok) {
                prepared = true;
                listener.onPrepared(gen);
            } else {
                listener.onError(gen, "TTS warm-up failed (" + reason + ")", reason, 0);
            }
        });
    }

    @Override
    public void start() {
        if (disposed || !prepared) return;
        if (tts == null) return;

        playing = true;
        lastCharSpoken = Math.max(0, Math.min(resumeOffsetChars, text.length()));
        tts.setSpeechRate(speechRate);
        speakFromOffset(resumeOffsetChars);
    }

    @Override
    public void pause() {
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
        text = "";
    }

    @Override public boolean isPlaying() { return playing; }
    @Override public boolean isReady()   { return !disposed && prepared && tts != null && mgr.isReady(); }
    @Override public int getCurrentPosition() { return estPositionMs; }
    @Override public int getDuration()        { return estDurationMs; }
    @Override public int getAudioSessionId()  { return 0; } // no visualizer for TTS

    @Override
    public void seekTo(int positionMs) {
        if (estDurationMs <= 0 || text.isEmpty()) return;
        int clamped = Math.max(0, Math.min(positionMs, estDurationMs));
        int charPos = (int) ((clamped / (double) estDurationMs) * Math.max(1, text.length()));
        resumeOffsetChars = charPos;
        estPositionMs = clamped;
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
        int old = estDurationMs;
        estDurationMs = estimateDurationMs(text, speechRate);
        if (old > 0) estPositionMs = (int) (estPositionMs * (estDurationMs / (double) old));
        if (playing && tts != null) tts.setSpeechRate(speechRate);
    }

    // --------------------- AppTtsManager.Listener ---------------------

    @Override
    public void onTtsReady(TextToSpeech engine) {
        if (disposed || prepared || preparing) return;
        prepareAsync();
    }

    @Override
    public void onStart(String utteranceId) {
        if (disposed) return;
        // ignore warm-up ids; nothing else to do
    }

    @Override
    public void onDone(String utteranceId) {
        if (disposed) return;
        // Warm-up completion?
        if (handleWarmupDone(utteranceId, true, 0)) return;

        // End of logical text?
        if (lastCharSpoken >= logicalTextEndIndex()) {
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
    public void onError(String utteranceId, int code) {
        if (disposed) return;
        if (handleWarmupDone(utteranceId, false, code)) return;
        playing = false;
        listener.onError(gen, "TTS error", code, 0);
    }

    @Override
    public void onUtteranceRange(int start, int end) {
        if (disposed) return;
        if (!text.isEmpty() && estDurationMs > 0) {
            estPositionMs = (int) ((start / (double) text.length()) * estDurationMs);
        }
        if (end > lastCharSpoken) lastCharSpoken = end;
        listener.onTtsRange(gen, start, Math.min(end, Math.max(0, text.length())));
    }

    @Override
    public void onWordRange(int s, int e) { onUtteranceRange(s, e); }

    // --------------------- Public helpers specific to TTS ---------------------

    /** Expose the loaded raw text for UI. */
    @Nullable
    public String getText() {
        return text;
    }

    /** Public wrapper to set voice and warm-up. */
    public void setVoiceByNameAndWarmUp(@NonNull String voiceName,
                                        long timeoutMs,
                                        @NonNull WarmupCallback cb) {
        setTtsVoiceByNameAsync(voiceName, timeoutMs, cb); // delegates to the internal method
    }

    /** Allow service/UI to jump by characters (e.g., sentence/paragraph). */
    public void setStartOffsetChars(@IntRange(from = 0) int charOffset) {
        if (text == null) return;
        int target = Math.max(0, Math.min(charOffset, text.length()));
        resumeOffsetChars = target;

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
        try { if (ttsHandle != null) ttsHandle.close(); } catch (Exception ignored) {}
        ttsHandle = null;
        if (tts != null) tts.stop();
        tts = null;
        lastCharSpoken = 0; resumeOffsetChars = 0; estPositionMs = 0;
    }

    // --------------------- Internals ---------------------

    private void speakFromOffset(int offsetChars) {
        if (tts == null) return;
        tts.speakFromOffset(text, Math.max(0, Math.min(offsetChars, text.length())));
        logCurrentVoice();
    }

    private int logicalTextEndIndex() {
        if (text == null) return 0;
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

    // ---- Voice warm-up ----

    public interface WarmupCallback { void onResult(boolean ready, int reason); }

    private boolean handleWarmupDone(@Nullable String id, boolean ok, int reason) {
        if (id == null || !id.startsWith("warmup-")) return false;
        // no persistence needed here; a single warm-up in flight
        // (kept simple; if you want multiple concurrent warmups, map id->cb)
        return true;
    }

    private void setTtsVoiceByNameAsync(@NonNull String voiceName, long timeoutMs, @NonNull WarmupCallback cb) {
        if (tts == null) { cb.onResult(false, TtsHelper.ERROR); return; }

        main.post(() -> {
            try {
                Set<Voice> voices = tts.getVoices();
                Voice target = null;
                for (Voice v : voices) {
                    if (voiceName.equals(v.getName())) { target = v; break; }
                }
                if (target == null) { cb.onResult(false, TtsHelper.SET_VOICE_FAILED); return; }

                int rSet = tts.setVoice(target);
                if (rSet != TextToSpeech.SUCCESS) { cb.onResult(false, TtsHelper.SET_VOICE_FAILED); return; }

                final String id = "warmup-" + android.os.SystemClock.uptimeMillis();
                File out = new File(app.getCacheDir(), id + ".wav");
                int rr = tts.synthesizeToFile("ok", new Bundle(), out, id);
                if (rr != TextToSpeech.SUCCESS) { cb.onResult(false, TtsHelper.SYNTH_FAIL); safeDelete(out); return; }

                // Minimal timeout guard
                main.postDelayed(() -> cb.onResult(true, TtsHelper.READY), Math.max(1500L, timeoutMs));
            } catch (Throwable t) {
                cb.onResult(false, TtsHelper.ERROR);
            }
        });
    }

    private void safeDelete(File f) { try { if (f != null) f.delete(); } catch (Throwable ignored) {} }

    @Nullable
    private String desiredVoiceName() {
        // Keep this generic—service can still override or persist per-folder in Pref/Option.
        try {
            // Prefer app-wide option; return null to use system default.
            String name = com.driot.bookplayer.global.Option.getTtsVoice();
            if (name == null || name.isEmpty() || "system".equalsIgnoreCase(name)) return null;
            return name;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void logCurrentVoice() {
        try {
            if (tts == null) return;
            TextToSpeech raw = mgr.raw();
            if (raw == null) return;
            Voice v = raw.getVoice();
            if (v != null) {
                // Replace with your logger if desired:
                // myLog("Current voice: " + v.getName() + " - " + VoiceItem.describeVoice(v));
                VoiceItem.describeVoice(v); // no-op; keeps parity with your utils
            }
        } catch (Throwable ignored) {}
    }

}
