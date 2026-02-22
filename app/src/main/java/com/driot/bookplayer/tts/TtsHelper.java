package com.driot.bookplayer.tts;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.global.Option;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;

public class TtsHelper {
    private TextToSpeech tts;
    private float currentSpeechRate = 1.0f;

    public static final int READY = 0, SET_VOICE_FAILED = 1, MISSING_DATA = 2, SYNTH_FAIL = 3, ERROR = 4, TIMEOUT = 5;

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
    // Returns the actual char offset where speech starts (may differ from
    // startOffset
    // when snap-to-sentence is on). Returns -1 if nothing was queued.
    public int speakFromOffset(String text, int startOffset, float volume) {
        if (tts == null || text == null || text.trim().isEmpty()) {
            myLogD("speakFromOffset : empty or not ready");
            return -1;
        }

        final int maxLen = Option.getTtsChunkSize();
        myLog("speakFromOffset : speed=" + currentSpeechRate + " textLen=" + text.length()
                + " start=" + startOffset + " chunkBuf=" + maxLen);

        final int N = text.length();
        final int safeOffset = Math.max(0, Math.min(startOffset, N));
        if (safeOffset >= N) {
            myLogD("speakFromOffset : at end, nothing to speak");
            return -1;
        }

        final List<Chunk> chunks = buildChunks(text, maxLen);
        if (chunks.isEmpty())
            return -1;

        int idx = findChunkIndexForOffset(chunks, safeOffset);
        if (idx >= chunks.size())
            return -1;

        final Bundle p = new Bundle();
        p.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, Math.max(0f, Math.min(1f, volume)));

        try {
            tts.stop();
        } catch (Throwable ignore) {
        }

        // 1) First chunk — optionally snap back to the sentence boundary
        final Chunk first = chunks.get(idx);
        int firstStart = Math.max(safeOffset, first.start);

        if (Option.getTtsSnapToSentence() && firstStart > first.start) {
            BreakIterator bi = BreakIterator.getSentenceInstance();
            bi.setText(first.text);
            int rel = firstStart - first.start;
            if (!bi.isBoundary(rel)) {
                int preceding = bi.preceding(rel);
                if (preceding != BreakIterator.DONE) {
                    firstStart = first.start + preceding;
                    myLog("speakFromOffset : snapped " + safeOffset + " \u2192 " + firstStart);
                }
            }
        }

        TtsErrorUtils.logOperationResult("TTS", "speak(first)",
                tts.speak(first.text.substring(firstStart - first.start, first.end - first.start),
                        TextToSpeech.QUEUE_ADD, p, TtsIds.utt(firstStart, first.end)));

        // Only one chunk at a time: onDone progression queues the next one.
        // Pre-queuing all remaining chunks causes network TTS to synthesize them
        // ahead of audio playback, making onRangeStart callbacks fire at 2x speed.

        return firstStart;
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

    public static int countNewlines(String s) {
        int n = 0;
        if (s == null)
            return 0;
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
