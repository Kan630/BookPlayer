package com.driot.bookplayer.tts;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.player.PlaybackUiBus;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.inject.Inject;
import javax.inject.Singleton;
import dagger.hilt.android.qualifiers.ApplicationContext;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

@Singleton
public final class AppTtsManager implements TextToSpeech.OnInitListener {

    public interface Listener {
        default void onTtsReady(TextToSpeech tts) {
        }

        default void onStart(String uttId) {
        }

        default void onDone(String uttId) {
        }

        default void onError(String uttId, int code) {
        }

        default void onWordRange(int absStart, int absEnd) {
        }

        default void onUtteranceRange(int absStart, int absEnd) {
        }
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean ready = false;
    private TextToSpeech tts;
    private int consecutiveErrorCount = 0;
    private static final int MAX_CONSECUTIVE_ERRORS = 5;
    private volatile String currentUtteranceId = null; // tracks the utterance whose audio is currently playing

    private final List<WeakReference<Listener>> listeners = new CopyOnWriteArrayList<>();

    private final MutableLiveData<List<VoiceItem>> voicesLiveData = new MutableLiveData<>();

    public LiveData<List<VoiceItem>> getVoicesLiveData() {
        return voicesLiveData;
    }

    @Nullable
    private volatile String preferredVoiceName = null;

    @Inject
    public AppTtsManager(@ApplicationContext Context app) {
        myLogD("AppTtsManager: constructor - new TextToSpeech");
        String engine = Option.getTtsEngine();
        main.post(() -> {
            if (engine == null || engine.isEmpty()) {
                tts = new TextToSpeech(app, this);
            } else {
                tts = new TextToSpeech(app, this, engine);
            }
            describeTts(tts);
        }); // main thread
    }

    /** Set before init or anytime prior to onInit completing. */
    public void setPreferredVoiceName(@Nullable String name) {
        preferredVoiceName = (name == null || name.isEmpty() || Option.DEFAULT_VOICE.equalsIgnoreCase(name))
                ? null
                : name;
    }

    // --- Listener registration ---

    public void addListener(@Nullable Listener l) {
        if (l == null)
            return;
        listeners.add(new WeakReference<>(l));
        // if already ready, notify immediately
        if (ready && tts != null) {
            try {
                l.onTtsReady(tts);
            } catch (Throwable ignored) {
            }
        }
    }

    public void removeListener(@Nullable Listener l) {
        if (l == null)
            return;
        for (WeakReference<Listener> ref : listeners) {
            Listener cur = (ref != null) ? ref.get() : null;
            if (cur == null || cur == l) {
                listeners.remove(ref);
            }
        }
    }

    private void forEachListener(java.util.function.Consumer<Listener> block) {
        for (WeakReference<Listener> ref : listeners) {
            Listener l = (ref != null) ? ref.get() : null;
            if (l == null) {
                listeners.remove(ref); // cleanup GC'ed listeners
            } else {
                try {
                    block.accept(l);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    @Override
    public void onInit(int status) {
        if (TextToSpeech.SUCCESS != status)
            myLogE("TTS init FAILED");
        ready = (status == TextToSpeech.SUCCESS) && (tts != null);
        if (!ready || tts == null)
            return;

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
                @Override
                public void onStart(String utteranceId) {
                    myLogD("----------------------------------------------------------------------------------------");
                    myLog("setOnUtteranceProgressListener.onStart - utteranceId=" + utteranceId);
                    myLogD("----------------------------------------------------------------------------------------");
                    PlaybackUiBus.get().setLoadPhase(Intents.PHASE_SPEAKING);
                    // Reset error count on successful start
                    consecutiveErrorCount = 0;
                    currentUtteranceId = utteranceId; // mark as actively playing
                    forEachListener(l -> l.onStart(utteranceId));
                }

                @Override
                public void onDone(String utteranceId) {
                    myLogD("----------------------------------------------------------------------------------------");
                    myLog("setOnUtteranceProgressListener.onDone - utteranceId=" + utteranceId);
                    myLogD("----------------------------------------------------------------------------------------");
                    forEachListener(l -> l.onDone(utteranceId));
                }

                @Override
                public void onError(String utteranceId) {
                    myLogE("onError (legacy) for " + utteranceId);
                    forEachListener(l -> l.onError(utteranceId, 0));
                }

                @Override
                public void onError(String utteranceId, int errorCode) {
                    myLogE("onError " + utteranceId + " -> " + TtsErrorUtils.describeOnErrorCode(errorCode));

                    consecutiveErrorCount++;
                    myLogE("TTS consecutive error count: " + consecutiveErrorCount);

                    // Emergency shutdown if we get repeated errors (likely infinite loop)
                    if (consecutiveErrorCount >= MAX_CONSECUTIVE_ERRORS) {
                        myLogE("TTS ERROR LOOP DETECTED - triggering emergency shutdown");
                        emergencyShutdown();
                        return; // Don't notify listeners, engine is dead
                    }

                    // Stop TTS immediately to prevent further errors
                    try {
                        if (tts != null) {
                            tts.stop();
                        }
                    } catch (Exception e) {
                        myLogEE(e, "Error stopping TTS in onError callback");
                    }

                    forEachListener(l -> l.onError(utteranceId, errorCode));
                }

                @Override
                public void onRangeStart(String uttId, int start, int end, int frame) {
                    // Only dispatch for the utterance whose audio is currently playing.
                    // Pre-queued chunks get synthesized ahead of playback; without this guard
                    // their onRangeStart callbacks fire while a prior chunk still plays,
                    // making the highlight race far ahead of the spoken word.
                    if (!uttId.equals(currentUtteranceId))
                        return;
                    int[] se = TtsIds.parseUtt(uttId);
                    if (se != null) {
                        int absStart = se[0] + Math.max(0, start);
                        int absEnd = se[0] + Math.max(0, end);
                        forEachListener(l -> l.onWordRange(absStart, absEnd));
                    } else {
                        // Fallback: pass relative range
                        forEachListener(l -> l.onWordRange(start, end));
                        myLogE("fallback");
                    }
                }
            });

            // --- Voice selection policy ---
            boolean voiceSet = false;
            try {
                Voice current = (tts != null) ? tts.getVoice() : null;

                // 1) Preferred voice name
                String prefName = preferredVoiceName;
                if (prefName != null && tts != null) {
                    Set<Voice> all = tts.getVoices();
                    if (all != null) {
                        for (Voice v : all) {
                            if (prefName.equalsIgnoreCase(v.getName())) {
                                int sr = tts.setVoice(v);
                                myLog("setting tts voice (preferred) : " + v.getName());
                                voiceSet = (sr == TextToSpeech.SUCCESS);
                                if (!voiceSet) {
                                    myLogE("could not set preferred voice : " + v.getName());
                                }
                                break;
                            }
                        }
                    }
                }

                // 2) Keep engine's current voice if present
                if (!voiceSet && current != null) {
                    myLog("keeping engine's current voice: " + current.getName());
                    voiceSet = true;
                }

                // 3) Fallback: set language to Locale.getDefault()
                if (!voiceSet) {
                    Locale locale = Locale.getDefault();
                    int langSetResult = tts.setLanguage(locale);
                    TtsErrorUtils.logSetLanguageResult("TTS", langSetResult, locale);
                    ready = (langSetResult != TextToSpeech.LANG_MISSING_DATA
                            && langSetResult != TextToSpeech.LANG_NOT_SUPPORTED);
                }
            } catch (Exception e) {
                myLogEE(e, "AppTtsManager.onInit - setting Voice");
                ready = false;
            }
        } catch (Exception e) {
            myLogEE(e, "AppTtsManager.onInit");
            ready = false;
        }

        if (ready) {
            forEachListener(l -> l.onTtsReady(tts));
            final TextToSpeech ttsSnap = tts;
            main.post(() -> {
                List<VoiceItem> list = new ArrayList<>();
                try {
                    for (Voice v : ttsSnap.getVoices()) {
                        list.add(new VoiceItem(v));
                    }
                    list.sort(Comparator
                            .comparing((VoiceItem i) -> i.twoLetterCodeLanguage, String::compareToIgnoreCase)
                            .thenComparing((VoiceItem i) -> !i.embedded)
                            .thenComparing((VoiceItem i) -> -i.quality)
                            .thenComparingInt(i -> i.latency)
                            .thenComparing(i -> i.name, String.CASE_INSENSITIVE_ORDER));
                } catch (Throwable t) {
                    myLogEE(t, "onInit - building VoiceItem list for LiveData");
                }
                voicesLiveData.setValue(list);
            });
        }
    }

    // --- public API ---

    public boolean isReady() {
        return ready && tts != null;
    }

    public void stop() {
        TextToSpeech t = tts;
        if (t != null) {
            try {
                t.stop();
            } catch (Exception e) {
                myLogEE(e, "Error stopping TTS");
            }
        }
    }

    /**
     * Forcefully shutdown TTS engine to prevent infinite loops.
     * Should be called when TTS is in a bad state.
     */
    public void emergencyShutdown() {
        myLogE("EMERGENCY TTS SHUTDOWN - stopping and resetting engine");
        TextToSpeech t = tts;
        if (t != null) {
            try {
                t.stop();
            } catch (Exception e) {
                myLogEE(e, "Error stopping TTS in emergency shutdown");
            }
            try {
                t.shutdown();
            } catch (Exception e) {
                myLogEE(e, "Error shutting down TTS in emergency shutdown");
            }
        }
        ready = false;
        tts = null;
        consecutiveErrorCount = 0;
    }

    public int setVoice(Voice v) {
        TextToSpeech t = tts;
        return (t == null) ? TextToSpeech.ERROR : t.setVoice(v);
    }

    public Set<Voice> getVoices() {
        TextToSpeech t = tts;
        return (t == null) ? Collections.emptySet() : t.getVoices();
    }

    public TextToSpeech raw() {
        return tts;
    }

    private static void describeTts(TextToSpeech tts) {
        List<TextToSpeech.EngineInfo> listTtsEngine = tts.getEngines();
        if (listTtsEngine == null || listTtsEngine.isEmpty()) {
            myLogE("no tts engine");
        } else {
            int nbEngine = listTtsEngine.size();
            int i = 0;
            for (TextToSpeech.EngineInfo ei : listTtsEngine) {
                i = i + 1;
                myLog("TTS engine n°" + i + "/" + nbEngine + " : " + ei.label + " [" + ei.name + "]");
            }
        }
        String defaultEngineStr = tts.getDefaultEngine();
        if (defaultEngineStr == null) {
            myLogE("no default engine");
            return;
        }
        myLog("default engine = [" + defaultEngineStr + "]");
        myLog("max input (limited by device, not specific tts engine) = [" + TextToSpeech.getMaxSpeechInputLength()
                + "]");

    }

    public void reinitialize(@ApplicationContext Context app, @Nullable String enginePackageName) {
        myLogD("AppTtsManager: reinitialize with engine: " + enginePackageName);
        ready = false;
        currentUtteranceId = null;
        voicesLiveData.postValue(null); // Clear voices so UI reloads
        TextToSpeech old = tts;
        if (old != null) {
            try {
                old.stop();
                old.shutdown();
            } catch (Exception e) {
                myLogEE(e, "Error shutting down old TTS during reinitialization");
            }
        }

        main.post(() -> {
            if (enginePackageName == null || enginePackageName.isEmpty()) {
                tts = new TextToSpeech(app, this);
            } else {
                tts = new TextToSpeech(app, this, enginePackageName);
            }
            describeTts(tts);
        });
    }

    public List<TextToSpeech.EngineInfo> getEngines() {
        if (tts == null)
            return Collections.emptyList();
        return tts.getEngines();
    }

}
