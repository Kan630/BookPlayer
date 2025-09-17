package com.driot.bookplayer.helpers;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.adapter.VoiceSpinnerAdapter;
import com.driot.bookplayer.objects.VoiceItem;
import com.driot.bookplayer.utils.KanLogger;

import java.io.File;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class TtsHelper implements TextToSpeech.OnInitListener {
    private final Context ctx;
    private TextToSpeech tts;
    private boolean ready = false;

    public static final int READY=0, SET_VOICE_FAILED=1, MISSING_DATA=2, SYNTH_FAIL=3, ERROR=4, TIMEOUT=5;

    private volatile int lastStartOffset = 0;
    private volatile int lastEndOffset = 0;

    private final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());

    public interface Listener {
        void onStart(String uttId);
        void onDone(String uttId);
        void onError(String uttId, int errorCode);
        default void onTtsReady(TextToSpeech tts) {}
        default void onUtteranceRange(int start, int end) {}
        default void onWordRange(int start, int end) {}
    }

    // optional raw access
    public TextToSpeech raw() { return tts; }

    // NEW pass-throughs so AudioService can call them on your TtsHelper instance
    public void setOnUtteranceProgressListener(UtteranceProgressListener l) {
        tts.setOnUtteranceProgressListener(l);
    }

    public int isLanguageAvailable(Locale locale) {
        return tts.isLanguageAvailable(locale);
    }

    public int synthesizeToFile(CharSequence text, Bundle params, File file, String utteranceId) {
        return tts.synthesizeToFile(text, params, file, utteranceId);
    }

    private final Listener listener;

    public TtsHelper(Context ctx, Listener listener) {
        this.ctx = ctx.getApplicationContext();
        this.listener = listener;
        tts = new TextToSpeech(this.ctx, this);
    }

    @Override public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            try {
                // Engine + defaults
                myLogI("TTS engine: " + tts.getDefaultEngine());
                tts.setPitch(1.0f);
                tts.setSpeechRate(1.0f);
                tts.setAudioAttributes(new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build());

                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) {
                        int[] se = parseRange(id);
                        if (se != null) {
                            lastStartOffset = se[0];
                            lastEndOffset = se[1];
                            if (listener != null) listener.onUtteranceRange(se[0], se[1]);
                        }
                        if (listener != null) listener.onStart(id);
                    }
                    @Override public void onDone(String id) {
                        if (listener != null) listener.onDone(id);
                    }
                    @Override public void onError(String id) {
                        if (listener != null) listener.onError(id, 0);
                    }
                    @Override public void onError(String id, int code) {
                        if (listener != null) listener.onError(id, code);
                    }
                    @Override public void onRangeStart(String utteranceId, int start, int end, int frame) {
                        int[] se = parseRange(utteranceId);
                        if (se != null && listener != null) {
                            int absStart = se[0] + Math.max(0, start);
                            int absEnd   = se[0] + Math.max(0, end);
                            listener.onWordRange(absStart, absEnd);
                        }
                    }
                });

                boolean voiceSet = false;
                Voice best = pickBestVoice(Locale.getDefault(), null);
                if (best != null) {
                    int sr = tts.setVoice(best);
                    myLog("onInit: picked voice=" + VoiceItem.describeVoice(best) + " -> setVoice()=" + sr);
                    voiceSet = (sr == TextToSpeech.SUCCESS);
                } else {
                    myLogW("onInit: no suitable voice found for " + Locale.getDefault());
                }

                // Fallback to setLanguage for legacy / rare devices
                if (!voiceSet) {
                    int r = tts.setLanguage(Locale.getDefault());
                    myLogI("onInit: fallback setLanguage(" + Locale.getDefault() + ") -> " + r);
                    ready = (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED);
                } else {
                    ready = true;
                }

                if (ready) {
                    tryLogCurrentVoice();
                    myLog("----------");
                    //logAllVoices(); // dump the catalog once on init (handy during development)
                    if (listener != null) listener.onTtsReady(tts);
                }
            } catch (Throwable t) {
                myLogEE(t, "onInit failure");
            }
        } else {
            myLogE("onInit: status != SUCCESS (" + status + ")");
        }
    }

    public boolean isReady() { return ready; }

    // ======== SPEAK API (unchanged) ========
    public void speakFromOffset(String text, int startOffset) {
        if (!ready || text == null) return;
        int maxLen = 1800;
        List<Chunk> chunks = buildChunks(text, maxLen);

        int safeOffset = Math.max(0, Math.min(startOffset, text.length()));
        int idx = findChunkIndexForOffset(chunks, safeOffset);
        if (idx >= chunks.size()) return;

        Chunk base = chunks.get(idx);
        int headEnd = Math.min(base.end, safeOffset + maxLen);
        String headText = text.substring(safeOffset, headEnd);
        String headId   = "utt_" + safeOffset + "_" + headEnd;
        tts.speak(headText, TextToSpeech.QUEUE_FLUSH, null, headId);

        if (headEnd < base.end) {
            String tailText = text.substring(headEnd, base.end);
            String tailId   = "utt_" + headEnd + "_" + base.end;
            tts.speak(tailText, TextToSpeech.QUEUE_ADD, null, tailId);
        }

        for (int i = idx + 1; i < chunks.size(); i++) {
            Chunk c = chunks.get(i);
            String uttId = "utt_" + c.start + "_" + c.end;
            tts.speak(c.text, TextToSpeech.QUEUE_ADD, null, uttId);
        }
    }

    public void setSpeechRate(float rate) { if (tts != null) tts.setSpeechRate(rate); }
    public void stop()  { if (tts != null) tts.stop(); }
    public void pause() { if (tts != null) tts.playSilentUtterance(250, TextToSpeech.QUEUE_ADD, "pause"); }
    public void shutdown() { if (tts != null) { tts.stop(); tts.shutdown(); tts = null; } }

    // ======== VOICE HELPERS ========

    /** Dumps all voices with status/attributes to logcat. */
    public void logAllVoices() {
        if (tts == null) {
            myLogW("logAllVoices: TTS null");
            return;
        }
        try {
            myLogI("---- VOICES CATALOG (engine=" + tts.getDefaultEngine() + ") ----");
            for (Voice v : tts.getVoices()) {
                myLog(VoiceItem.describeVoice(v));
            }
            myLogI("---- END VOICES ----");
        } catch (Throwable t) {
            myLogEE(t, "logAllVoices failed");
        }
    }

    /** Logs the currently selected/default voice. */
    private void tryLogCurrentVoice() {
        try {
            Voice cur = tts.getVoice();
            Voice def = tts.getDefaultVoice();
            myLog("Current voice: " + VoiceItem.describeVoice(cur));
            myLog("Default voice: " + VoiceItem.describeVoice(def));
        } catch (Throwable t) {
            myLogEE(t, "tryLogCurrentVoice failed");
        }
    }

    /** Picks the best voice for a locale (optionally filter by name substring), ranking by quality desc, latency asc, embedded preferred. */
    public @Nullable Voice pickBestVoice(Locale locale, @Nullable String preferredNamePart) {
        if (tts == null) return null;
        try {
            Set<Voice> voices = tts.getVoices();
            if (voices == null || voices.isEmpty()) return null;
            List<Voice> candidates = new ArrayList<>();
            String lang = locale.getLanguage();
            for (Voice v : voices) {
                if (v.getLocale() == null) continue;
                if (!lang.equals(v.getLocale().getLanguage())) continue; // allow any region in same language
                if (preferredNamePart != null &&
                        !v.getName().toLowerCase(Locale.US).contains(preferredNamePart.toLowerCase(Locale.US))) {
                    // keep it in the pool; we will still consider in fallback
                }
                candidates.add(v);
            }
            if (candidates.isEmpty()) return null;

            // Sort: preferredNamePart match first, then embedded first, then higher quality, then lower latency
            Collections.sort(candidates, new Comparator<Voice>() {
                @Override public int compare(Voice a, Voice b) {
                    int scoreA = scoreVoice(a, preferredNamePart);
                    int scoreB = scoreVoice(b, preferredNamePart);
                    return Integer.compare(scoreB, scoreA); // desc
                }
            });
            return candidates.get(0);
        } catch (Throwable t) {
            myLogEE(t, "pickBestVoice failed");
            return null;
        }
    }

    private static int scoreVoice(Voice v, @Nullable String preferredNamePart) {
        int score = 0;
        Set<String> feat = v.getFeatures();
        boolean embedded = feat != null && feat.contains("embeddedTts");
        boolean network  = (feat != null && feat.contains("networkTts")) || v.isNetworkConnectionRequired();

        if (preferredNamePart != null &&
                v.getName().toLowerCase(Locale.US).contains(preferredNamePart.toLowerCase(Locale.US))) {
            score += 1000;
        }
        if (embedded) score += 200;
        if (!network) score += 50;
        // Quality ranges (higher is better), latency (lower is better)
        score += (10 * v.getQuality());
        score += (10 * (5 - Math.min(5, v.getLatency()))); // reverse latency
        // Prefer region match slightly
        score += 5;
        return score;
    }


    // ======== LANGUAGE (fallback / back-compat) ========

    /** Prefer voices. This remains for legacy devices or explicit fallbacks. */
    public int setLanguage(@androidx.annotation.NonNull Locale locale) {
        if (tts == null) return TextToSpeech.LANG_NOT_SUPPORTED;
        try {
            int r = tts.setLanguage(locale);
            myLogI("setLanguage(" + locale + ") -> " + r);
            return r;
        } catch (Throwable t) {
            myLogEE(t, "setLanguage failed");
            return TextToSpeech.LANG_NOT_SUPPORTED;
        }
    }

    /** Return all available voices from the current engine (may be empty). */
    public @NonNull Set<Voice> getVoices() {
        if (tts == null) return java.util.Collections.emptySet();
        try {
            return tts.getVoices();
        } catch (Throwable t) {
            myLogEE(t, "getVoices failed");
            return java.util.Collections.emptySet();
        }
    }

    /**
     * Attempt to set a specific voice object on the TTS engine.
     * Returns TextToSpeech.SUCCESS or TextToSpeech.ERROR.
     */
    public int setVoice(@NonNull Voice voice) {
        if (tts == null) return TextToSpeech.ERROR;
        try {
            int r = tts.setVoice(voice);
            myLog("setVoice(" + voice.getName() + ") -> " + r + " | " + VoiceItem.describeVoice(voice));
            return r;
        } catch (Throwable t) {
            myLogEE(t, "setVoice failed");
            return TextToSpeech.ERROR;
        }
    }







    // ======== CHUNKING / UTILS (unchanged) ========

    private static final class Chunk {
        final int start, end; final String text;
        Chunk(int s, int e, String t) { start = s; end = e; text = t; }
    }

    private static List<Chunk> buildChunks(String text, int maxLen) {
        List<Chunk> out = new ArrayList<>();
        BreakIterator it = BreakIterator.getSentenceInstance();
        it.setText(text);
        int sentStart = it.first();
        int sentEnd;

        StringBuilder buf = new StringBuilder();
        int chunkStart = sentStart;

        while ((sentEnd = it.next()) != BreakIterator.DONE) {
            String s = text.substring(sentStart, sentEnd);
            if (buf.length() == 0 && s.length() >= maxLen) {
                out.add(new Chunk(sentStart, sentEnd, s));
                chunkStart = sentEnd;
            } else if (buf.length() + s.length() > maxLen) {
                out.add(new Chunk(chunkStart, chunkStart + buf.length(), buf.toString()));
                buf.setLength(0);
                chunkStart = sentStart;
                buf.append(s);
            } else {
                buf.append(s);
            }
            sentStart = sentEnd;
        }
        if (buf.length() > 0) {
            out.add(new Chunk(chunkStart, chunkStart + buf.length(), buf.toString()));
        }
        if (out.isEmpty() && text.length() > 0) {
            for (int i = 0; i < text.length(); i += maxLen) {
                int end = Math.min(text.length(), i + maxLen);
                out.add(new Chunk(i, end, text.substring(i, end)));
            }
        }
        return out;
    }

    private static int findChunkIndexForOffset(List<Chunk> chunks, int offset) {
        int lo = 0, hi = chunks.size() - 1, ans = 0;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            Chunk c = chunks.get(mid);
            if (offset < c.start) hi = mid - 1;
            else { ans = mid; lo = mid + 1; }
        }
        while (ans < chunks.size() - 1 && chunks.get(ans).end <= offset) ans++;
        return ans;
    }

    private static int[] parseRange(String id) {
        try {
            if (id == null || !id.startsWith("utt_")) return null;
            int sep = id.lastIndexOf('_');
            int start = Integer.parseInt(id.substring(4, sep));
            int end   = Integer.parseInt(id.substring(sep + 1));
            return new int[]{start, end};
        } catch (Exception ignored) { return null; }
    }
    /** Returns [wordStart, wordEnd] for a given offset. */
    public static int[] findWordBounds(CharSequence text, int off) {
        int n = text.length();
        if (n == 0) return new int[]{0,0};
        // if we're on whitespace/punct, shift right to next letter/digit
        int i = off;
        while (i < n && !Character.isLetterOrDigit(text.charAt(i))) i++;
        if (i >= n) i = Math.max(0, off - 1);
        // go left to start
        int s = i;
        while (s > 0 && Character.isLetterOrDigit(text.charAt(s - 1))) s--;
        // go right to end
        int e = i;
        while (e < n && Character.isLetterOrDigit(text.charAt(e))) e++;
        if (s < 0) s = 0;
        if (e < s) e = s;
        return new int[]{s, e};
    }

    /** Return a list of all available voices (may be empty if TTS not initialized). */
    public List<Voice> getAllVoices() {
        if (tts == null) return new ArrayList<>();
        try {
            Set<Voice> voices = tts.getVoices();
            if (voices == null) return new ArrayList<>();
            return new ArrayList<>(voices);
        } catch (Exception e) {
            // some devices may throw; return empty list
            return new ArrayList<>();
        }
    }



    public interface OnVoiceSelected {
        void onSelected(@Nullable VoiceItem voice);
    }

    /**
     * Wires the spinner, builds voice list, preselects from savedCode ("system" or engine voice name),
     * applies the TTS voice internally, and invokes the callback. Returns a handle you should close() in onDestroy.
     */
    public static @NonNull AutoCloseable setupTtsVoiceSpinner(
            @NonNull Context ui_context,
            @NonNull Spinner spinner,
            @Nullable String savedCode,
            @NonNull OnVoiceSelected callback
    ) {
        final Context ui  = ui_context;                 // Activity/Fragment context (themed!)
        final Context app = ui_context.getApplicationContext(); // for TTS only
        final Handler main = new Handler(Looper.getMainLooper());

        // Disable spinner and show a tiny "loading" option until TTS is ready.
        final ArrayAdapter<String> loadingAdapter = new ArrayAdapter<>(
                app, android.R.layout.simple_spinner_item, Collections.singletonList("Loading voices…"));
        loadingAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(loadingAdapter);
        spinner.setEnabled(false);

        final TextToSpeech[] ttsHolder = new TextToSpeech[1];

        ttsHolder[0] = new TextToSpeech(app, status -> {
            if (status != TextToSpeech.SUCCESS) {
                main.post(() -> {
                    ArrayAdapter<String> err = new ArrayAdapter<>(
                            app, android.R.layout.simple_spinner_item,
                            Collections.singletonList("TTS init failed"));
                    err.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinner.setAdapter(err);
                    spinner.setEnabled(false);
                    callback.onSelected(null);
                });
                return;
            }

            final List<VoiceItem> all = buildVoiceItems(app, ttsHolder[0]);

            main.post(() -> {
                if (all.isEmpty()) {
                    ArrayAdapter<String> empty = new ArrayAdapter<>(
                            app, android.R.layout.simple_spinner_item, Collections.singletonList("No voices"));
                    empty.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinner.setAdapter(empty);
                    spinner.setEnabled(false);
                    callback.onSelected(null);
                    return;
                }

                // Adapter with flags + pretty labels
                VoiceSpinnerAdapter adapter = new VoiceSpinnerAdapter(ui, all);
                spinner.setAdapter(adapter);
                spinner.setEnabled(true);

                // Preselect (savedCode can be "system" or a voice name)
                int pre = 0;
                if (savedCode != null && !"system".equalsIgnoreCase(savedCode)) {
                    for (int i = 0; i < all.size(); i++) {
                        if (savedCode.equals(all.get(i).name)) { pre = i; break; }
                    }
                }
                spinner.setSelection(pre, false);

                // Immediately apply the preselected voice (unless "system")
                VoiceItem preSel = all.get(pre);
                if (savedCode == null || !"system".equalsIgnoreCase(savedCode)) {
                    applyVoice(ttsHolder[0], preSel);
                    callback.onSelected(preSel);
                } else {
                    callback.onSelected(null); // system/default
                }

                // Listen for changes
                spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        VoiceItem sel = all.get(position);
                        applyVoice(ttsHolder[0], sel);
                        callback.onSelected(sel);
                    }
                    @Override public void onNothingSelected(AdapterView<?> parent) {}
                });
            });
        });

        // Return a handle that shuts down TTS cleanly.
        return (AutoCloseable) ttsHolder[0]::shutdown;
    }

    // ---- INTERNALS ----

    private static void applyVoice(TextToSpeech tts, @Nullable VoiceItem item) {
        if (tts == null || item == null || item.voice == null) return;
        try { tts.setVoice(item.voice); } catch (Throwable ignored) {}
    }

    /** Build & sort list of VoiceItem, with flags and nice labels. */
    private static List<VoiceItem> buildVoiceItems(Context ctx, TextToSpeech tts) {
        List<VoiceItem> out = new ArrayList<>();
        try {
            for (Voice v : tts.getVoices()) {
                out.add(new VoiceItem(v));
            }
        } catch (Throwable ignored) {}

        // Sort: language → embedded first → quality desc → latency asc → name
        out.sort(Comparator
                .comparing((VoiceItem i) -> i.twoLetterCodeLanguage, String::compareToIgnoreCase)
                .thenComparing((VoiceItem i) -> !i.embedded)
                .thenComparing((VoiceItem i) -> -i.quality)
                .thenComparingInt(i -> i.latency)
                .thenComparing(i -> i.name, String.CASE_INSENSITIVE_ORDER));

        return out;
    }


    public static int countNewlines(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '\n') n++;
        return n;
    }

    // Simple, safe paragraphizer for totally-flat text.
// Inserts blank line after sentence-ending punctuation when next token looks like sentence start.
    public static String smartParagraphize(String s) {
        if (s == null) return "";
        String t = s.replace('\u00A0', ' ')
                .replace("\r", "")
                .replaceAll("[ \\t]{2,}", " ")
                .trim();

        // Scene breaks like "***"
        t = t.replaceAll("[ ]*\\*\\*\\*[ ]*", "\n\n***\n\n");

        // Insert \n\n after sentence end, before likely sentence start
        t = t.replaceAll("(?<=[.!?…])[ ]+(?=[\"“‘'(\\[]?[A-ZÀ-ÖØ-Þ0-9])", "\n\n");

        return t;
    }


    // ======== LOGGING ========
    private static final String TAG = "TtsHelper";
    private static void myLog(String str) { KanLogger.myLog(TAG, str); }
    private static void myLogD(String str) { KanLogger.myLogD(TAG, str); }
    private static void myLogI(String str) { KanLogger.myLogI(TAG, str); }
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
    private static void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, TAG, str); }
    private static void myToastEE(Throwable t, String str) { KanLogger.myToastEE(t, TAG, str); }
}
