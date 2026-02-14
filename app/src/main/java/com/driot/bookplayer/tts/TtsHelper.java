package com.driot.bookplayer.tts;

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
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.helpers.CallerHelper;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TtsHelper {
    private TextToSpeech tts;
    private float currentSpeechRate = 1.0f;

    public static final int READY = 0, SET_VOICE_FAILED = 1, MISSING_DATA = 2, SYNTH_FAIL = 3, ERROR = 4, TIMEOUT = 5;

    private static final int MIN_FIRST_UTT_CHARS = 25;

    // optional raw access
    public TextToSpeech raw() {
        return tts;
    }

    public TtsHelper(@NonNull Context ctx, @NonNull TextToSpeech sharedTts) {
        this.tts = sharedTts; // do NOT new TextToSpeech here
    }

    public boolean isReady() {
        return tts != null;
    }

    // ======== SPEAK API ========
    public void speakFromOffset(String text, int startOffset, float volume) {
        if (tts == null || text == null || text.isEmpty()) {
            myLogD("speakFromOffset : empty");
            return;
        }
        if (!isReady()) {
            myLogD("speakFromOffset : not ready");
            return;
        }
        
        // Safety check: ensure text is not just whitespace
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            myLogW("speakFromOffset : text is only whitespace, skipping");
            return;
        }

        final int maxLen = Option.getTtsChunkSize();
        int txtHash = text.hashCode();
        myLog("speakFromOffset : speed=" + currentSpeechRate + " text len=" + text.length() + " hash="
                + Integer.toHexString(txtHash) +
                " start=" + startOffset + " chunkBuf=" + maxLen);

        // Clamp and short-circuit if at end
        final int N = text.length();
        final int safeOffset = Math.max(0, Math.min(startOffset, N));
        if (safeOffset >= N) {
            myLogD("speakFromOffset : at end, nothing to speak");
            return;
        }

        // Build sentence-based chunks (<= maxLen each)
        final List<Chunk> chunks = buildChunks(text, maxLen);
        if (chunks.isEmpty()) {
            myLogD("speakFromOffset : no chunks built");
            return;
        }
        myLogD("speakFromOffset : built " + chunks.size() + " chunks");

        // Find first chunk to use (never returns a microscopic tail)
        int idx = findChunkIndexForOffset(chunks, safeOffset);
        if (idx >= chunks.size()) {
            myLogD("speakFromOffset : offset beyond last chunk");
            return;
        }

        // Params
        final Bundle p = new Bundle();
        p.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, Math.max(0f, Math.min(1f, volume)));

        // Hard flush once to clear any stale queue
        try {
            tts.stop();
        } catch (Throwable ignore) {
        }

        // 1) First utterance: find a safe sentence start if possible
        final Chunk first = chunks.get(idx);
        int clampedStart = Math.max(safeOffset, first.start);

        // Snap to preceding sentence start to avoid partial words/sentences
        int snapStart = clampedStart;
        if (clampedStart > first.start) {
            // Find start of sentence containing clampedStart within the chunk text
            int relStart = clampedStart - first.start;
            // Use simple heuristic: back up to punctuation or start
            BreakIterator bi = BreakIterator.getSentenceInstance();
            bi.setText(first.text);
            if (bi.isBoundary(relStart)) {
                // already on boundary
            } else {
                int preceding = bi.preceding(relStart);
                if (preceding != BreakIterator.DONE) {
                    snapStart = first.start + preceding;
                    myLog("speakFromOffset : snapped start from " + clampedStart + " to " + snapStart + " (rel "
                            + preceding + ")");
                }
            }
        }

        int firstStart = Math.max(snapStart, first.start);

        if (first.end - firstStart < MIN_FIRST_UTT_CHARS && idx + 1 < chunks.size()) {
            // Too small → skip to next full chunk instead
            idx++;
        } else {
            // Speak [firstStart, first.end)
            String id = com.driot.bookplayer.tts.TtsIds.utt(firstStart, first.end);
            int r = tts.speak(first.text.substring(firstStart - first.start, first.end - first.start),
                    TextToSpeech.QUEUE_ADD, p, id);
            TtsErrorUtils.logOperationResult("TTS", "speak(first)", r);
            idx++; // next chunks follow
        }

        // 2) Remaining chunks: enqueue as-is
        for (int i = idx; i < chunks.size(); i++) {
            Chunk c = chunks.get(i);
            String id = com.driot.bookplayer.tts.TtsIds.utt(c.start, c.end);
            int r = tts.speak(c.text, TextToSpeech.QUEUE_ADD, p, id);
            TtsErrorUtils.logOperationResult("TTS", "speak(chunk)", r);
        }
    }

    public void setSpeechRate(float rate) {
        this.currentSpeechRate = rate;
        if (tts != null)
            tts.setSpeechRate(rate);
    }

    public void stop() {
        if (tts != null)
            tts.stop();
    }

    public void pause() {
        if (tts != null)
            tts.playSilentUtterance(250, TextToSpeech.QUEUE_ADD, "pause");
    }
    // public void shutdown() { if (tts != null) { tts.stop(); tts.shutdown(); tts =
    // null; } }

    // ======== CHUNKING / UTILS ========

    private static final class Chunk {
        final int start, end;
        final String text;

        Chunk(int s, int e, String t) {
            start = s;
            end = e;
            text = t;
        }
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
        if (out.isEmpty() && !text.isEmpty()) {
            for (int i = 0; i < text.length(); i += maxLen) {
                int end = Math.min(text.length(), i + maxLen);
                out.add(new Chunk(i, end, text.substring(i, end)));
            }
        }
        return out;
    }

    private static int findChunkIndexForOffset(List<Chunk> chunks, int offset) {
        if (chunks.isEmpty())
            return 0;
        // Binary search: smallest i with offset < chunks[i].end
        int lo = 0, hi = chunks.size() - 1, ans = chunks.size(); // default = after last
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            Chunk c = chunks.get(mid);
            if (offset < c.end) {
                ans = mid;
                hi = mid - 1;
            } else {
                lo = mid + 1;
            }
        }
        if (ans >= chunks.size())
            return chunks.size(); // signals "past end"
        // If we're *extremely* close to boundary (e.g., < 25 chars left), prefer the
        // next chunk
        final int MIN_FIRST_UTT_CHARS = 25;
        Chunk c = chunks.get(ans);
        if (offset >= c.end - MIN_FIRST_UTT_CHARS && ans + 1 < chunks.size())
            return ans + 1;
        return ans;
    }

    /**
     * Very small, allocation-free-ish word-bound finder used to snap the highlight
     * immediately.
     */
    /** Returns [wordStart, wordEnd] for a given offset. */
    public static int[] findWordBounds(CharSequence text, int off) {
        int n = text.length();
        if (n == 0)
            return new int[] { 0, 0 };
        // if we're on whitespace/punct, shift right to next letter/digit
        int i = off;
        while (i < n && !Character.isLetterOrDigit(text.charAt(i)))
            i++;
        if (i >= n)
            i = Math.max(0, off - 1);
        // go left to start
        int s = i;
        while (s > 0 && Character.isLetterOrDigit(text.charAt(s - 1)))
            s--;
        // go right to end
        int e = i;
        while (e < n && Character.isLetterOrDigit(text.charAt(e)))
            e++;
        if (s < 0)
            s = 0;
        if (e < s)
            e = s;
        return new int[] { s, e };
    }

    public static int[] findWordBounds(@NonNull String s, int off) {
        return findWordBounds((CharSequence) s, off);
    }

    public interface OnVoiceSelected {
        void onSelected(@Nullable VoiceItem voice);
    }

    public static void setupTtsVoiceSpinnerForSettings(
            @NonNull Context ui_context,
            @NonNull Spinner spinner,
            @Nullable String savedCode, // "system" or exact engine voice name
            @NonNull OnVoiceSelected callback) {
        myLog("setupTtsVoiceSpinnerForSettings - called from " + CallerHelper.getCaller() + " - savedCode=[" + savedCode
                + "]");
        final Context app = ui_context.getApplicationContext();

        final AppTtsManager mgr = AppTtsManager.get(app);
        TextToSpeech tts = mgr.raw();
        if (tts == null) {
            myLogE("setupTtsVoiceSpinnerForSettings: TTS not ready (raw() == null)");
            ArrayAdapter<String> empty = new ArrayAdapter<>(
                    ui_context, android.R.layout.simple_spinner_item,
                    java.util.Collections.singletonList("No voices"));
            empty.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(empty);
            spinner.setEnabled(false);
            callback.onSelected(null);
            return;
        }

        final List<VoiceItem> voices = buildVoiceItems(tts);
        if (voices == null || voices.isEmpty()) {
            myLogE("setupTtsVoiceSpinnerForSettings - no voices");
            ArrayAdapter<String> empty = new ArrayAdapter<>(
                    ui_context, android.R.layout.simple_spinner_item,
                    java.util.Collections.singletonList("No voices"));
            empty.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(empty);
            spinner.setEnabled(false);
            callback.onSelected(null);
            return;
        }
        myLog(voices.size() + " voices");

        // Prepend "system default" option (null voice)
        final ArrayList<VoiceItem> all = new ArrayList<>();
        VoiceItem system = VoiceItem.makeSystemDefault(tts);
        if (system != null) {
            // myLog("setupTtsVoiceSpinnerForSettings => system default = " + system);
            all.add(system);
        } else {
            myLogE("setupTtsVoiceSpinnerForSettings => no system default");
        }
        all.addAll(voices);

        int currentSelected = -1;
        int i = 0;
        for (VoiceItem voiceItem : all) {
            if (voiceItem.name.equals(savedCode)) {
                currentSelected = i;
                break;
            }
            i = i + 1;
        }

        final VoiceSpinnerAdapter adapter = new VoiceSpinnerAdapter(ui_context, all);
        spinner.setAdapter(adapter);
        if (currentSelected >= 0) {
            spinner.setSelection(currentSelected);
            adapter.setSelectedPosition(currentSelected);
        }
        spinner.setEnabled(true);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                myLog("spinner : on item selected " + position + " - " + id);
                adapter.setSelectedPosition(position);
                VoiceItem selected = (VoiceItem) parent.getItemAtPosition(position);
                myLog("callback : " + selected.name);
                callback.onSelected(selected);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                myLog("spinner : on nothing selected");
            }
        });

    }

    /**
     * Wires the spinner, builds voice list, preselects from savedCode ("system" or
     * engine voice name),
     * applies the TTS voice internally, and invokes the callback. Returns a handle
     * you should close() in onDestroy.
     */
    public static @NonNull AutoCloseable setupTtsVoiceSpinner(
            @NonNull Context ui_context,
            @NonNull Spinner spinner,
            @Nullable String savedCode, // "system" or exact engine voice name
            @NonNull OnVoiceSelected callback) {
        myLog("setupTtsVoiceSpinner - called from " + CallerHelper.getCaller() + " - savedCode=[" + savedCode + "]");
        final Context ui = ui_context; // themed
        final Context app = ui_context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());

        myLogD("setting up a spinner temp load state with singleton -loading voices...-");
        // 1) Temporary loading state
        final ArrayAdapter<String> loadingAdapter = new ArrayAdapter<>(
                ui, android.R.layout.simple_spinner_item,
                java.util.Collections.singletonList("Loading voices…"));
        loadingAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(loadingAdapter);
        spinner.setEnabled(false);

        myLogD("setting a prefered voice name in AppTtsManager");
        final AppTtsManager mgr = AppTtsManager.get(app);
        mgr.setPreferredVoiceName(savedCode);

        final java.util.concurrent.atomic.AtomicBoolean populatedOnce = new java.util.concurrent.atomic.AtomicBoolean(
                false);
        final boolean[] suppressSelection = new boolean[] { true }; // suppress spurious onItemSelected
                                                                    // during/just-after init

        myLogD("recreating a final AppTts manager ????");
        // 2) Listener to (re)populate once TTS is ready
        final AppTtsManager.Listener mgrListener = new AppTtsManager.Listener() {
            @Override
            public void onTtsReady(TextToSpeech tts) {
                // avoid double-populating if listener is invoked twice
                if (!populatedOnce.compareAndSet(false, true)) {
                    myLogW("setupTtsVoiceSpinner.onTtsReady => ignored (already populated)");
                    return;
                }
                main.post(() -> {
                    myLog("setupTtsVoiceSpinner.onTtsReady => populating spinner");
                    // Build catalog
                    final List<VoiceItem> voices = buildVoiceItems(tts); // your helper
                    if (voices == null || voices.isEmpty()) {
                        myLog("setupTtsVoiceSpinner.onTtsReady => no voices");
                        ArrayAdapter<String> empty = new ArrayAdapter<>(
                                ui, android.R.layout.simple_spinner_item,
                                java.util.Collections.singletonList("No voices"));
                        empty.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        spinner.setAdapter(empty);
                        spinner.setEnabled(false);
                        callback.onSelected(null);
                        return;
                    }
                    myLog("setupTtsVoiceSpinner.onTtsReady => " + voices.size() + " voices");

                    // Prepend "system default" option (null voice)
                    final ArrayList<VoiceItem> all = new ArrayList<>();
                    VoiceItem system = VoiceItem.makeSystemDefault(tts);
                    if (system != null) {
                        myLog("setupTtsVoiceSpinner.onTtsReady => system default = " + system);
                        all.add(system);
                    } else {
                        myLog("setupTtsVoiceSpinner.onTtsReady => no system default");
                    }
                    all.addAll(voices);

                    final VoiceSpinnerAdapter adapter = new VoiceSpinnerAdapter(ui, all);
                    spinner.setAdapter(adapter);
                    spinner.setEnabled(true);

                    // Preselect saved value
                    myLog("setupTtsVoiceSpinner.onTtsReady => Preselect saved value : " + savedCode);
                    int pre = 0; // default to "system"
                    if (savedCode != null && !Option.DEFAULT_VOICE.equalsIgnoreCase(savedCode)) {
                        for (int i = 1; i < all.size(); i++) {
                            if (savedCode.equals(all.get(i).name)) {
                                myLog("setupTtsVoiceSpinner.onTtsReady => Preselect saved value OK");
                                pre = i;
                                break;
                            }
                        }
                    }

                    // Wire selection changes
                    // Wire listener but keep it suppressed initially
                    spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                            if (suppressSelection[0]) {
                                myLog("setupTtsVoiceSpinner.onTtsReady.onItemSelected suppressed during init (pos="
                                        + pos + ")");
                                // Still update adapter position even during init
                                adapter.setSelectedPosition(pos);
                                return;
                            }
                            myLogI("----  User picked a VOICE ---- onItemSelected => callback.onSelected ");
                            adapter.setSelectedPosition(pos);
                            callback.onSelected(all.get(pos));
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {
                            /* no-op */ }
                    });

                    spinner.setSelection(pre, false);
                    adapter.setSelectedPosition(pre);

                    myLog("setupTtsVoiceSpinner.onTtsReady => callback.onSelected");
                    callback.onSelected(all.get(pre));

                    spinner.post(() -> suppressSelection[0] = false);
                });
            }
        };

        // register
        mgr.addListener(mgrListener);

        // 4) If already ready, populate immediately
        if (mgr.isReady() && mgr.raw() != null) {
            mgrListener.onTtsReady(mgr.raw());
        }

        // 5) Return a release handle (does NOT shutdown the engine)
        return () -> {
            main.post(() -> {
                try {
                    spinner.setOnItemSelectedListener(null);
                } catch (Throwable ignored) {
                }
            });
            mgr.removeListener(mgrListener);
        };
    }

    // ---- INTERNALS ----

    /** Build & sort list of VoiceItem, with flags and nice labels. */
    private static List<VoiceItem> buildVoiceItems(TextToSpeech tts) {
        List<VoiceItem> out = new ArrayList<>();
        try {
            for (Voice v : tts.getVoices()) {
                out.add(new VoiceItem(v));
            }
        } catch (Throwable ignored) {
        }

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
        for (int i = 0; i < s.length(); i++)
            if (s.charAt(i) == '\n')
                n++;
        return n;
    }

    // Simple, safe paragraphizer for totally-flat text.
    // Inserts blank line after sentence-ending punctuation when next token looks
    // like sentence start.
    public static String smartParagraphize(String s) {
        if (s == null)
            return "";
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
