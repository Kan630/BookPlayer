package com.driot.bookplayer.helpers;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.adapter.VoiceSpinnerAdapter;
import com.driot.bookplayer.objects.VoiceItem;
import com.driot.bookplayer.tts.TtsIds;
import com.driot.bookplayer.tts.AppTtsManager;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import java.io.File;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TtsHelper {
    private final Context context;
    private TextToSpeech tts;

    public static final int READY=0, SET_VOICE_FAILED=1, MISSING_DATA=2, SYNTH_FAIL=3, ERROR=4, TIMEOUT=5;

    // optional raw access
    public TextToSpeech raw() { return tts; }

    public int synthesizeToFile(CharSequence text, Bundle params, File file, String utteranceId) {
        return tts.synthesizeToFile(text, params, file, utteranceId);
    }

    public TtsHelper(@NonNull Context ctx, @NonNull TextToSpeech sharedTts) {
        this.context = ctx.getApplicationContext();
        this.tts = sharedTts;   // do NOT new TextToSpeech here
    }

    public boolean isReady() { return tts != null; }

    // ======== SPEAK API ========
    public void speakFromOffset(String text, int startOffset, float volume) {
        myLogD("speakFromOffset");
        if (tts == null || text == null || text.isEmpty()) {
            myLogD("speakFromOffset : empty");
            return;
        }
        if (!isReady()) {
            myLogD("speakFromOffset : not ready");
            return;
        }
        int maxLen = 1800;

        List<Chunk> chunks = buildChunks(text, maxLen);
        int safeOffset = Math.max(0, Math.min(startOffset, text.length()));

        // NEW: if we're at or beyond the end, do nothing (avoid empty utterances)
        if (safeOffset >= text.length()) {
            myLogD("speakFromOffset : at end, nothing to speak");
            return;
        }

        int idx = findChunkIndexForOffset(chunks, safeOffset);
        if (idx >= chunks.size()) return;

        Chunk base = chunks.get(idx);

        // If offset is past this chunk's end (can happen at exact boundary),
        // skip to the next chunk; if none, return.
        if (safeOffset >= base.end) {
            if (++idx >= chunks.size()) return;
            base = chunks.get(idx);
        }

        int headEnd = Math.min(base.end, safeOffset + maxLen);
        if (headEnd <= safeOffset) {
            // Nothing meaningful in head; fall through to queue remainder chunks
            myLogD("speakFromOffset : head slice empty, skip");
        }

        Bundle p = new Bundle();
        p.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, Math.max(0f, Math.min(1f, volume)));

        // Only queue the head if it has content
        if (headEnd > safeOffset) {
            String headId = com.driot.bookplayer.tts.TtsIds.utt(safeOffset, headEnd);
            tts.speak(text.substring(safeOffset, headEnd), TextToSpeech.QUEUE_FLUSH, p, headId);
        } else {
            // No head → still flush to clear any stale queue
            tts.stop(); // reliable flush
        }

        if (headEnd < base.end) {
            String tailId = com.driot.bookplayer.tts.TtsIds.utt(headEnd, base.end);
            tts.speak(text.substring(headEnd, base.end), TextToSpeech.QUEUE_ADD, p, tailId);
        }
        for (int i = idx + 1; i < chunks.size(); i++) {
            Chunk c = chunks.get(i);
            String id = com.driot.bookplayer.tts.TtsIds.utt(c.start, c.end);
            tts.speak(c.text, TextToSpeech.QUEUE_ADD, p, id);
        }
    }

    public void setSpeechRate(float rate) { if (tts != null) tts.setSpeechRate(rate); }
    public void stop()  { if (tts != null) tts.stop(); }
    public void pause() { if (tts != null) tts.playSilentUtterance(250, TextToSpeech.QUEUE_ADD, "pause"); }
    //public void shutdown() { if (tts != null) { tts.stop(); tts.shutdown(); tts = null; } }

    // ======== CHUNKING / UTILS ========

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
            @Nullable String savedCode,          // "system" or exact engine voice name
            @NonNull OnVoiceSelected callback
    ) {
        final Context ui  = ui_context;                       // themed
        final Context app = ui_context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());

        // 1) Temporary loading state
        final ArrayAdapter<String> loadingAdapter = new ArrayAdapter<>(
                ui, android.R.layout.simple_spinner_item,
                java.util.Collections.singletonList("Loading voices…"));
        loadingAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(loadingAdapter);
        spinner.setEnabled(false);

        final AppTtsManager mgr = AppTtsManager.get(app);

        // 2) Listener to (re)populate once TTS is ready
        final AppTtsManager.Listener mgrListener = new AppTtsManager.Listener() {
            @Override public void onTtsReady(TextToSpeech tts) {
                main.post(() -> {
                    myLog("onTtsReady => populating spinner");
                    // Build catalog
                    myLog("onTtsReady => listing voices");
                    final List<VoiceItem> voices = buildVoiceItems(app, tts); // your helper
                    if (voices == null || voices.isEmpty()) {
                        ArrayAdapter<String> empty = new ArrayAdapter<>(
                                ui, android.R.layout.simple_spinner_item,
                                java.util.Collections.singletonList("No voices"));
                        empty.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        spinner.setAdapter(empty);
                        spinner.setEnabled(false);
                        callback.onSelected(null);
                        return;
                    }

                    myLog("onTtsReady => system default");
                    // Prepend "system default" option (null voice)
                    final ArrayList<VoiceItem> all = new ArrayList<>();
                    VoiceItem system = VoiceItem.makeSystemDefault(tts);
                    if (system != null) all.add(system);
                    all.addAll(voices);

                    final VoiceSpinnerAdapter adapter = new VoiceSpinnerAdapter(ui, all);
                    spinner.setAdapter(adapter);
                    spinner.setEnabled(true);

                    // Preselect saved value
                    myLog("onTtsReady => Preselect saved value : " + savedCode);
                    int pre = 0; // default to "system"
                    if (savedCode != null && !"system".equalsIgnoreCase(savedCode)) {
                        for (int i = 1; i < all.size(); i++) {
                            if (savedCode.equals(all.get(i).name)) {
                                myLog("onTtsReady => Preselect saved value OK");
                                pre = i;
                                break;
                            }
                        }
                    }
                    spinner.setSelection(pre, false);

                    // Apply initial selection (skip setVoice for "system")
                    VoiceItem preSel = all.get(pre);
                    myLog("onTtsReady => callback.onSelected");
                    callback.onSelected(preSel);

                    // Wire selection changes
                    spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                            myLog("onItemSelected => callback.onSelected");
                            VoiceItem sel = all.get(pos);
                            callback.onSelected(sel);
                        }
                        @Override public void onNothingSelected(AdapterView<?> parent) { /* no-op */ }
                    });
                });
            }
        };

        // 3) Acquire a handle (ref-counted) and register listener
        final AutoCloseable acquireHandle = mgr.acquire(spinner /*owner*/, mgrListener);

        // 4) If already ready, populate immediately
        if (mgr.isReady() && mgr.raw() != null) {
            mgrListener.onTtsReady(mgr.raw());
        }

        // 5) Return a release handle (does NOT shutdown the engine)
        return () -> {
            main.post(() -> {
                try { spinner.setOnItemSelectedListener(null); } catch (Throwable ignored) {}
            });
            try { acquireHandle.close(); } catch (Exception ignored) {}
        };
    }


    // ---- INTERNALS ----

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

}
