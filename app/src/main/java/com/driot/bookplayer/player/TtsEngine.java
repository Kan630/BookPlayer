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

    @Nullable
    private TtsHelper tts;

    // State
    private volatile boolean disposed = false;
    private volatile boolean preparing = false;
    private volatile boolean prepared = false;
    private volatile boolean playing = false;
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
        prepared = false;
        preparing = false;
        playing = false;
        lastCharSpoken = 0;
        resumeOffsetChars = 0;
        estPositionMs = 0;
        completionTriggered = false; // Reset completion flag for new track

        String raw = TextExtractor.getPlainText(ctx, uri, displayName);
        // Normalize newlines
        raw = raw.replace("\r\n", "\n").replace('\r', '\n');
        // Heuristic paragraphize if almost no newlines
        if (TtsHelper.countNewlines(raw) < 2)
            raw = TtsHelper.smartParagraphize(raw);

        this.text = raw;
        this.estDurationMs = estimateDurationMs(text, speechRate);
    }

    @Override
    public void prepareAsync() {
        if (disposed || preparing || prepared)
            return;
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
        if (disposed || !prepared)
            return;
        if (playing)
            return;

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
        // "Time-Based Latency Correction"
        // Since TTS callbacks (onRangeStart) fire when text is *buffered* (not spoken),
        // `lastCharSpoken` is always ahead of what the user actually heard (latency).
        // To prevent skipping text on resume, we calculate the estimated position based
        // on
        // elapsed time and reading speed.
        if (currentUtteranceStartTime > 0 && currentUtteranceStartOffset >= 0) {
            long elapsed = System.currentTimeMillis() - currentUtteranceStartTime;

            // Estimate chars spoken based on time.
            // Baseline: ~15-16 chars/sec for average speech.
            // 0.015 chars/ms seems a safe conservative estimate.
            double estimatedChars = elapsed * 0.015 * speechRate;

            int calculatedPos = currentUtteranceStartOffset + (int) estimatedChars;

            // Safety:
            // 1. Clamp to lastCharSpoken (which is "ahead"). We never want to go BEYOND it.
            int target = Math.min(calculatedPos, lastCharSpoken);

            // 2. Ensure we don't rewind before the start of this chunk.
            target = Math.max(target, currentUtteranceStartOffset);

            myLogD("pause SMART: elapsed=" + elapsed + "ms est=" + (int) estimatedChars
                    + " (off=" + currentUtteranceStartOffset + ") -> calc=" + calculatedPos
                    + " vs lastChar=" + lastCharSpoken + " => target=" + target);

            resumeOffsetChars = target;
        } else {
            // Fallback if we haven't started an utterance tracking yet
            resumeOffsetChars = lastCharSpoken;
        }

        if (tts != null)
            tts.stop();
        playing = false;
    }

    @Override
    public void stop() {
        if (tts != null)
            tts.stop();
        playing = false;
    }

    @Override
    public void reset() {
        stop();
        prepared = false;
        preparing = false;
        resumeOffsetChars = 0;
        estPositionMs = 0;
        completionTriggered = false;
        text = "";
    }

    @Override
    public boolean isPlaying() {
        return playing;
    }

    @Override
    public boolean isReady() {
        return !disposed && prepared && tts != null;
    }

    @Override
    public long getCurrentPosition() {
        return estPositionMs;
    }

    @Override
    public long getDuration() {
        return estDurationMs;
    }

    @Override
    public int getAudioSessionId() {
        return 0;
    } // no visualizer for TTS

    @Override
    public void seekTo(long positionMs) {
        if (estDurationMs <= 0 || text.isEmpty())
            return;
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

        // TEMP LOG: Log seekbar position and corresponding word
        String wordAtPos = "";
        if (!text.isEmpty() && charPos < text.length()) {
            int wordStart = charPos;
            int wordEnd = charPos;
            // Find word start (go backwards to find start of word)
            while (wordStart > 0 && !Character.isWhitespace(text.charAt(wordStart - 1)) &&
                    Character.isLetterOrDigit(text.charAt(wordStart - 1))) {
                wordStart--;
            }
            // Find word end (go forwards to find end of word)
            while (wordEnd < text.length() && !Character.isWhitespace(text.charAt(wordEnd)) &&
                    Character.isLetterOrDigit(text.charAt(wordEnd))) {
                wordEnd++;
            }
            if (wordStart < wordEnd && wordEnd <= text.length()) {
                wordAtPos = text.substring(wordStart, wordEnd);
            }
        }
        myLog("TTS SEEK: posMs=" + clamped + " charPos=" + charPos + " word=[" + wordAtPos + "] playing=" + playing);

        if (playing) {
            if (tts != null) {
                tts.stop();
                speakFromOffset(resumeOffsetChars);
            }
        }
    }

    @Override
    public void setSpeed(float speed) {
        float newRate = Math.max(0.1f, speed);
        if (Math.abs(newRate - speechRate) < 0.01f) {
            // Speed hasn't meaningfully changed, skip update
            return;
        }

        this.speechRate = newRate;
        long old = estDurationMs;
        estDurationMs = estimateDurationMs(text, speechRate);
        if (old > 0)
            estPositionMs = (int) (estPositionMs * (estDurationMs / (double) old));

        // If playing, restart from current position with new speed for real-time effect
        if (playing && tts != null && !text.isEmpty()) {
            // Resume from last audible boundary we tracked
            int start = Math.max(0, Math.min(lastCharSpoken, text.length()));
            resumeOffsetChars = start;
            tts.stop();
            // Restart without immediate broadcast - let TTS callbacks drive highlighting
            // naturally
            // This prevents highlighting from jumping incorrectly
            tts.setSpeechRate(speechRate);
            tts.speakFromOffset(text, start, volume);
            // Don't broadcast range here - let onUtteranceRange callbacks handle it
        } else if (tts != null) {
            // Not playing, just update the rate for next time
            tts.setSpeechRate(speechRate);
        }
    }

    // --------------------- AppTtsManager.Listener ---------------------

    @Override
    public void onTtsReady(TextToSpeech engine) {
        if (disposed || prepared || preparing)
            return;
        this.tts = new TtsHelper(app, engine);
        prepareAsync();
    }

    private long currentUtteranceStartTime = 0;
    private int currentUtteranceStartOffset = 0;

    @Override
    public void onStart(String utteranceId) {
        myLogD("onStart " + utteranceId);
        if (disposed)
            return;

        currentUtteranceStartTime = System.currentTimeMillis();
        int[] range = com.driot.bookplayer.tts.TtsIds.parseUtt(utteranceId);
        if (range != null) {
            currentUtteranceStartOffset = range[0];
        } else {
            // Reset if unknown ID format to prevent bad math
            currentUtteranceStartOffset = -1;
        }
    }

    @Override
    public void onDone(String utteranceId) {
        myLogD("onDone " + utteranceId);
        if (disposed)
            return;

        // Check if this utterance reached the end by examining both lastCharSpoken and
        // utterance ID
        int logicalEnd = logicalTextEndIndex();
        boolean reachedEnd = false;

        // Check 1: lastCharSpoken (updated by onUtteranceRange callbacks)
        if (lastCharSpoken >= logicalEnd && logicalEnd > 0) {
            reachedEnd = true;
        }

        // Check 2: utterance ID end position (more reliable - checks actual utterance
        // boundaries)
        int[] uttBounds = com.driot.bookplayer.tts.TtsIds.parseUtt(utteranceId);
        if (uttBounds != null && uttBounds[1] >= logicalEnd && logicalEnd > 0) {
            reachedEnd = true;
            // Update lastCharSpoken to match utterance end for consistency
            lastCharSpoken = Math.max(lastCharSpoken, Math.min(uttBounds[1], text.length()));
        }

        if (reachedEnd) {
            if (completionTriggered) {
                myLogD("onDone: completion already triggered, ignoring");
                return;
            }
            myLogD("onDone: reached end (lastCharSpoken=" + lastCharSpoken + ", logicalEnd=" + logicalEnd + ", uttEnd="
                    + (uttBounds != null ? uttBounds[1] : "N/A") + "), triggering completion");
            completionTriggered = true;
            playing = false;
            listener.onCompletion(gen);
            return;
        }

        // Continue speaking from where we stopped
        main.post(() -> {
            if (disposed || !playing)
                return;
            
            // Safety check: ensure we haven't reached the end
            if (text == null || text.isEmpty() || lastCharSpoken >= text.length()) {
                myLogD("onDone: reached end of text, not continuing");
                return;
            }
            
            resumeOffsetChars = lastCharSpoken;
            speakFromOffset(resumeOffsetChars);
        });
    }

    @Override
    public void onError(String utteranceId, int errorCode) {
        String desc = TtsErrorUtils.describeOnErrorCode(errorCode);
        myLogE("onError " + utteranceId + ", code = " + errorCode + " -> " + desc);
        if (disposed)
            return;
        
        // Stop TTS immediately to prevent infinite error loops
        playing = false;
        if (tts != null) {
            try {
                tts.stop();
            } catch (Exception e) {
                myLogEE(e, "Error stopping TTS in onError");
            }
        }
        
        // Also stop at the manager level to ensure complete stop
        try {
            if (mgr != null) {
                mgr.stop();
            }
        } catch (Exception e) {
            myLogEE(e, "Error stopping TTS manager in onError");
        }
        
        // Prevent further synthesis attempts
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
        if (disposed)
            return;

        // Ignore callbacks when not playing - prevents highlighting from racing ahead
        // after pause/resume
        if (!playing) {
            myLogD("TTS RANGE....: pos= ignoring callback (not playing) [" + start + "-" + end + "]");
            return;
        }

        lastCharSpoken = Math.min(Math.max(0, end), text.length());
        if (!text.isEmpty() && estDurationMs > 0) {
            estPositionMs = (int) ((lastCharSpoken / (double) text.length()) * estDurationMs);
        }

        // TEMP LOG: Log the word being spoken from TTS callbacks
        String wordAtRange = "";
        if (!text.isEmpty() && start < text.length() && end <= text.length() && start < end) {
            wordAtRange = text.substring(start, Math.min(end, text.length()));
            // Extract just the word
            String[] words = wordAtRange.trim().split("\\s+");
            if (words.length > 0) {
                wordAtRange = words[0];
            }
        }
        //myLogD("TTS RANGE....: pos=[" + start + "-" + end + "] word=[" + wordAtRange + "] lastCharSpoken=" + lastCharSpoken);

        listener.onTtsRange(gen, start, Math.min(end, Math.max(0, text.length())));

        // Don't trigger completion here - onUtteranceRange fires DURING speaking, not
        // after
        // Completion should only be triggered from onDone when the utterance actually
        // finishes
        // This prevents premature track switching when the seekbar reaches the end but
        // text isn't fully spoken
    }

    @Override
    public void onWordRange(int s, int e) {
        onUtteranceRange(s, e);
    }

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
        if (tts != null)
            tts.stop();
        tts = null;
        lastCharSpoken = 0;
        resumeOffsetChars = 0;
        estPositionMs = 0;
    }

    // --------------------- Internals ---------------------

    private void speakFromOffset(int offsetChars) {
        if (disposed || tts == null)
            return;
        
        // Safety check: ensure text is not empty
        if (text == null || text.isEmpty() || text.trim().isEmpty()) {
            myLogW("speakFromOffset: text is empty, stopping");
            playing = false;
            return;
        }
        
        tts.setSpeechRate(speechRate);
        final int off = Math.max(0, Math.min(offsetChars, text.length()));
        LocalBroadcastManager.getInstance(app).sendBroadcast(
                new Intent(Intents.NOTIFICATION_TTS_RANGE)
                        .putExtra(Intents.EXTRA_TTS_START, off)
                        .putExtra(Intents.EXTRA_TTS_END, off));
        tts.speakFromOffset(text, off, volume); // all chunking lives in TtsHelper
    }

    private int logicalTextEndIndex() {
        int i = text.length();
        while (i > 0) {
            char ch = text.charAt(i - 1);
            if (Character.isWhitespace(ch) || ch == '\u200B' || ch == '\uFEFF')
                i--;
            else
                break;
        }
        return i;
    }

    private static int estimateDurationMs(@Nullable String text, float rate) {
        if (text == null)
            return 0;
        int words = Math.max(1, text.trim().split("\\s+").length);
        double wpm = 180.0 * Math.max(0.1, rate);
        return (int) Math.round((words / wpm) * 60_000.0);
    }

    @Override
    public void setVolume(float v) {
        volume = Math.max(0f, Math.min(1f, v));
    }

    @Override
    public float getVolume() {
        return volume;
    }

    private void logCurrentVoice() {
        try {
            if (tts == null)
                return;
            TextToSpeech raw = mgr.raw();
            if (raw == null)
                return;
            Voice v = raw.getVoice();
            myLog("Current voice: " + VoiceItem.describeVoice(v).replace(", ", "\n"));
        } catch (Throwable ignored) {
        }
    }

    public String getVoiceName() {
        TextToSpeech raw = mgr.raw();
        if (raw == null)
            return null;
        Voice v = raw.getVoice();
        return (v != null) ? v.getName() : null;
    }

    public boolean setVoiceByName(@Nullable String voiceName) {
        if (disposed)
            return false;

        // "system" or empty -> revert to engine default (language-based)
        if (voiceName == null || voiceName.isEmpty() || Option.DEFAULT_VOICE.equalsIgnoreCase(voiceName)) {
            try {
                TextToSpeech raw = mgr.raw();
                if (raw == null)
                    return false;
                // Reset to device default locale
                Locale locale = Locale.getDefault();
                int langSetResult = raw.setLanguage(locale);
                TtsErrorUtils.logSetLanguageResult("TTS", langSetResult, locale);
                boolean ok = (langSetResult != TextToSpeech.LANG_MISSING_DATA
                        && langSetResult != TextToSpeech.LANG_NOT_SUPPORTED);
                if (ok)
                    restartIfPlaying();
                return ok;
            } catch (Throwable ignored) {
                return false;
            }
        }

        try {
            Set<Voice> voices = mgr.getVoices();
            if (voices == null || voices.isEmpty())
                return false;

            Voice target = null;
            for (Voice v : voices) {
                if (voiceName.equals(v.getName())) {
                    target = v;
                    break;
                }
            }
            if (target == null)
                return false;

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
        if (tts == null)
            return;
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
