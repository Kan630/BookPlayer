package com.driot.bookplayer.tts;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import androidx.annotation.Nullable;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class AppTtsManager implements TextToSpeech.OnInitListener {
    public interface Listener {
        default void onTtsReady(TextToSpeech tts) {}
        default void onStart(String uttId) {}
        default void onDone(String uttId) {}
        default void onError(String uttId, int code) {}
        default void onWordRange(int absStart, int absEnd) {}
        default void onUtteranceRange(int absStart, int absEnd) {}
    }

    private static volatile AppTtsManager sInstance;
    public static AppTtsManager get(Context ctx) {
        if (sInstance == null) {
            synchronized (AppTtsManager.class) {
                if (sInstance == null) sInstance = new AppTtsManager(ctx.getApplicationContext());
            }
        }
        return sInstance;
    }

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();
    private final Map<Object, WeakReference<Listener>> listeners = new ConcurrentHashMap<>();

    private TextToSpeech tts;
    private boolean ready = false;
    private int refCount = 0;
    private Runnable delayedShutdown;

    private AppTtsManager(Context app) {
        this.app = app;
        myLogD("AppTtsManager: constructor - new TextToSpeech");
        main.post(() -> tts = new TextToSpeech(app, this)); // main thread
    }

    // --- lifecycle for consumers ---
    public AutoCloseable acquire(Object owner, Listener l) {
        synchronized (lock) {
            refCount++;
            if (l != null && owner != null) listeners.put(owner, new WeakReference<>(l));
            // Ensure engine exists even after a previous shutdown
            if (tts == null) {
                main.post(() -> {
                    if (tts == null) tts = new TextToSpeech(app, this);
                });
            }
        }
        if (ready && l != null) l.onTtsReady(tts);
        return () -> release(owner);
    }

    public void release(Object owner) {
        synchronized (lock) {
            if (owner != null) listeners.remove(owner);
            if (--refCount <= 0) scheduleShutdown();
        }
    }

    private void scheduleShutdown() {
        if (delayedShutdown != null) main.removeCallbacks(delayedShutdown);
        delayedShutdown = () -> {
            synchronized (lock) {
                if (refCount == 0 && tts != null) {
                    try { tts.stop(); tts.shutdown(); } catch (Throwable ignored) {}
                    tts = null; ready = false;
                    // Lazy re-create on next use
                    main.post(() -> tts = new TextToSpeech(app, this));
                }
            }
        };
        main.postDelayed(delayedShutdown, 20_000); // 20s idle grace
    }

    // --- TextToSpeech.OnInitListener ---
    @Override public void onInit(int status) {
        ready = (status == TextToSpeech.SUCCESS) && (tts != null);
        if (!ready) return;

        try {
            // Engine defaults
            tts.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            tts.setPitch(1.0f);
            tts.setSpeechRate(1.0f);

            // Progress listener (multiplexed to all registered listeners)
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) {
                    myLogD("setOnUtteranceProgressListener.onStart - utteranceId=" + utteranceId);
                    //listeners.values().forEach(w -> opt(w).onStart(utteranceId));
                    int[] se = TtsIds.parseUtt(utteranceId);
                    if (se != null) {
                        final int s = se[0];
                        // notify a zero-length range at start (start,start)
                        listeners.values().forEach(w -> opt(w).onUtteranceRange(s, s));
                    }
                    listeners.values().forEach(w -> opt(w).onStart(utteranceId));
                }
                @Override public void onDone(String utteranceId) {
                    myLogD("setOnUtteranceProgressListener.onDone - utteranceId=" + utteranceId);
                    listeners.values().forEach(w -> opt(w).onDone(utteranceId));
                }
                @Override public void onError(String utteranceId) {
                    myLogE("onError (legacy) for " + utteranceId);
                    listeners.values().forEach(w -> opt(w).onError(utteranceId, 0));
                }
                @Override public void onError(String utteranceId, int errorCode) {
                    myLogE("onError " + utteranceId + " -> " + TtsErrorUtils.describeOnErrorCode(errorCode));
                    listeners.values().forEach(w -> opt(w).onError(utteranceId, errorCode));
                }
                @Override public void onRangeStart(String uttId, int start, int end, int frame) {
                    // Convert to absolute using the uttId "utt_<absStart>_<absEnd>"
                    int[] se = TtsIds.parseUtt(uttId);
                    if (se != null) {
                        int absStart = se[0] + Math.max(0, start);
                        int absEnd   = se[0] + Math.max(0, end);
                        listeners.values().forEach(w -> opt(w).onWordRange(absStart, absEnd));
                    } else {
                        // Fallback: pass relative range
                        listeners.values().forEach(w -> opt(w).onWordRange(start, end));
                    }
                }
            });

            // Voice selection (try best voice for system locale; fallback to language)
            boolean voiceSet = false;
            try {
                Voice best = pickBestVoice(Locale.getDefault(), null);
                if (best != null) {
                    int sr = tts.setVoice(best);
                    voiceSet = (sr == TextToSpeech.SUCCESS);
                }
            } catch (Throwable ignored) {}

            if (!voiceSet) {
                Locale locale = Locale.getDefault();
                int langSetResult = tts.setLanguage(locale);
                TtsErrorUtils.logSetLanguageResult("TTS", langSetResult, locale);
                ready = (langSetResult != TextToSpeech.LANG_MISSING_DATA && langSetResult != TextToSpeech.LANG_NOT_SUPPORTED);
            } else {
                ready = true;
            }
        } catch (Throwable ignored) {
            ready = false;
        }

        // Notify everybody that TTS is ready (or not)
        if (ready) {
            for (WeakReference<Listener> w : listeners.values()) opt(w).onTtsReady(tts);
        }
    }


    private static Listener opt(WeakReference<Listener> w) {
        Listener l = (w == null) ? null : w.get();
        return (l == null) ? new Listener() {} : l;
    }

    @Nullable
    public Voice pickBestVoice(Locale locale, @Nullable String preferredNamePart) {
        try {
            Set<Voice> voices = (tts != null) ? tts.getVoices() : null;
            if (voices == null || voices.isEmpty()) return null;
            String lang = locale.getLanguage();
            List<Voice> candidates = new ArrayList<>();
            for (Voice v : voices) {
                Locale vl = v.getLocale();
                if (vl == null) continue;
                if (!lang.equals(vl.getLanguage())) continue; // accept any region in same language
                candidates.add(v);
            }
            if (candidates.isEmpty()) return null;

            candidates.sort((a, b) -> {
                int sa = score(a, preferredNamePart);
                int sb = score(b, preferredNamePart);
                return Integer.compare(sb, sa);
            });
            return candidates.get(0);
        } catch (Throwable ignored) { return null; }
    }
    private static int score(Voice v, @Nullable String pref) {
        int s = 0;
        Set<String> f = v.getFeatures();
        boolean embedded = f != null && f.contains("embeddedTts");
        boolean network  = (f != null && f.contains("networkTts")) || v.isNetworkConnectionRequired();
        if (pref != null && v.getName().toLowerCase(Locale.US).contains(pref.toLowerCase(Locale.US))) s += 1000;
        if (embedded) s += 200;
        if (!network) s += 50;
        s += 10 * v.getQuality();
        s += 10 * (5 - Math.min(5, v.getLatency()));
        return s;
    }

    // --- public API (thread-safe wrappers) ---
    public boolean isReady() { return ready && tts != null; }

    public void stop() { TextToSpeech t = tts; if (t != null) t.stop(); }

    public int setVoice(Voice v) { TextToSpeech t = tts; return (t == null) ? TextToSpeech.ERROR : t.setVoice(v); }
    public Set<Voice> getVoices() { TextToSpeech t = tts; return (t == null) ? Collections.emptySet() : t.getVoices(); }
    public TextToSpeech raw() { return tts; } // if you need low-level access



}
