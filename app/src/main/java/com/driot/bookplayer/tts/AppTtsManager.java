package com.driot.bookplayer.tts;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import androidx.annotation.Nullable;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.global.Option;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

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

    public static void init(Context context) {
        AppDatabase.databaseReadExecutor.execute(() -> {
            if (AppDatabase.getDatabase(context).folderDao().hasSomeTtsBook()) {
                get(context);
            }
        });
    }

    public static AppTtsManager get(Context ctx) {
        if (sInstance == null) {
            synchronized (AppTtsManager.class) {
                if (sInstance == null) {
                    sInstance = new AppTtsManager(ctx.getApplicationContext());
                }
            }
        }
        return sInstance;
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean ready = false;
    private TextToSpeech tts;

    // Weak listener list (no owner concept anymore)
    private final List<WeakReference<Listener>> listeners = new CopyOnWriteArrayList<>();

    @Nullable
    private volatile String preferredVoiceName = null;

    private AppTtsManager(Context app) {
        myLogD("AppTtsManager: constructor - new TextToSpeech");
        main.post(() -> {
            tts = new TextToSpeech(app, this);
            describeTts(tts);
        }); // main thread
    }

    /** Set before init or anytime prior to onInit completing. */
    public void setPreferredVoiceName(@Nullable String name) {
        preferredVoiceName = (name == null || name.isEmpty() || Option.DEFAULT_VOICE.equalsIgnoreCase(name))
                ? null : name;
    }

    // --- Listener registration ---

    public void addListener(@Nullable Listener l) {
        if (l == null) return;
        listeners.add(new WeakReference<>(l));
        // if already ready, notify immediately
        if (ready && tts != null) {
            try { l.onTtsReady(tts); } catch (Throwable ignored) {}
        }
    }

    public void removeListener(@Nullable Listener l) {
        if (l == null) return;
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
                } catch (Throwable ignored) {}
            }
        }
    }

    // --- TextToSpeech.OnInitListener ---

    @Override
    public void onInit(int status) {
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
                    int[] se = TtsIds.parseUtt(utteranceId);
                    if (se != null) {
                        final int s = se[0];
                        // notify a zero-length range at start (start,start)
                        forEachListener(l -> l.onUtteranceRange(s, s));
                    }
                    forEachListener(l -> l.onStart(utteranceId));
                }

                @Override public void onDone(String utteranceId) {
                    myLogD("setOnUtteranceProgressListener.onDone - utteranceId=" + utteranceId);
                    forEachListener(l -> l.onDone(utteranceId));
                }

                @Override public void onError(String utteranceId) {
                    myLogE("onError (legacy) for " + utteranceId);
                    forEachListener(l -> l.onError(utteranceId, 0));
                }

                @Override public void onError(String utteranceId, int errorCode) {
                    myLogE("onError " + utteranceId + " -> " + TtsErrorUtils.describeOnErrorCode(errorCode));
                    forEachListener(l -> l.onError(utteranceId, errorCode));
                }

                @Override public void onRangeStart(String uttId, int start, int end, int frame) {
                    int[] se = TtsIds.parseUtt(uttId);
                    if (se != null) {
                        int absStart = se[0] + Math.max(0, start);
                        int absEnd   = se[0] + Math.max(0, end);
                        forEachListener(l -> l.onWordRange(absStart, absEnd));
                    } else {
                        // Fallback: pass relative range
                        forEachListener(l -> l.onWordRange(start, end));
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
        }
    }

    // --- optional best-voice helper ---

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
                if (!lang.equals(vl.getLanguage())) continue;
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

    // --- public API ---

    public boolean isReady() { return ready && tts != null; }

    public void stop() {
        TextToSpeech t = tts;
        if (t != null) t.stop();
    }

    public int setVoice(Voice v) {
        TextToSpeech t = tts;
        return (t == null) ? TextToSpeech.ERROR : t.setVoice(v);
    }

    public Set<Voice> getVoices() {
        TextToSpeech t = tts;
        return (t == null) ? Collections.emptySet() : t.getVoices();
    }

    public TextToSpeech raw() { return tts; }

    private static void describeTts(TextToSpeech tts) {
        List<TextToSpeech.EngineInfo> listTtsEngine = tts.getEngines();
        if (listTtsEngine==null || listTtsEngine.isEmpty()) {
            myLogE("no tts engine");
        } else {
            int nbEngine = listTtsEngine.size();
            int i = 0;
            for (TextToSpeech.EngineInfo ei : listTtsEngine) {
                i = i + 1;
                myLog("TTS engine n°" + i + "/" + nbEngine + " : "+ ei.label + " ["  + ei.name + "]");
            }
        }
        String defaultEngineStr = tts.getDefaultEngine();
        if (defaultEngineStr==null) {
            myLogE("no default engine");
            return;
        }
        myLog("default engine = [" + defaultEngineStr + "]");
        myLog("max input (limited by device, not specific tts engine) = [" + TextToSpeech.getMaxSpeechInputLength() + "]");


    }

}
