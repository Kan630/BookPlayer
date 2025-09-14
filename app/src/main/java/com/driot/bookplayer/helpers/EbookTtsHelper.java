package com.driot.bookplayer.helpers;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EbookTtsHelper implements TextToSpeech.OnInitListener {
    private final Context ctx;
    private TextToSpeech tts;
    private boolean ready = false;

    // Track last-started chunk range (absolute offsets in the full text)
    private volatile int lastStartOffset = 0;
    private volatile int lastEndOffset = 0;

    private volatile int chunkMaxLen = 360;             // smaller chunks for better resume when no word ranges


    public interface Listener {
        void onStart(String uttId);
        void onDone(String uttId);
        void onError(String uttId, int errorCode);
        default void onTtsReady(TextToSpeech tts) {}
        /** Absolute range [start,end) of the chunk that just started */
        default void onUtteranceRange(int start, int end) {}
        /** Absolute range [start,end) of the current WORD (API 26+, else not called) */
        default void onWordRange(int start, int end) {}
    }

    private final Listener listener;

    public EbookTtsHelper(Context ctx, Listener listener) {
        this.ctx = ctx.getApplicationContext();
        this.listener = listener;
        tts = new TextToSpeech(this.ctx, this);
    }

    @Override public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int r = tts.setLanguage(Locale.getDefault());
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
                // Word-level progress
                @Override public void onRangeStart(String utteranceId, int start, int end, int frame) {
                    int[] se = parseRange(utteranceId);
                    if (se != null && listener != null) {
                        int absStart = se[0] + Math.max(0, start);
                        int absEnd   = se[0] + Math.max(0, end);
                        listener.onWordRange(absStart, absEnd);
                    }
                }

            });
            ready = (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED);
            if (ready && listener != null) listener.onTtsReady(tts);
        }
    }

    public boolean isReady() { return ready; }
    public TextToSpeech getTts() { return tts; }
    public int getLastStartOffset() { return lastStartOffset; }
    public int getLastEndOffset() { return lastEndOffset; }

    /** Start from the beginning */
    public void speak(String text) { speakFromOffset(text, 0); }

    /** Resume from a character offset (absolute in the full text) */
    public void speakFromOffset(String text, int startOffset) {
        if (!ready || text == null) return;

        int maxLen = 1800;
        List<Chunk> chunks = buildChunks(text, maxLen);

        int safeOffset = Math.max(0, Math.min(startOffset, text.length()));
        int idx = findChunkIndexForOffset(chunks, safeOffset);
        if (idx >= chunks.size()) return;

        // 1) HEAD: speak from EXACT offset (trim prefix of the base chunk)
        Chunk base = chunks.get(idx);
        int headEnd = Math.min(base.end, safeOffset + maxLen);
        String headText = text.substring(safeOffset, headEnd);
        String headId   = "utt_" + safeOffset + "_" + headEnd;
        tts.speak(headText, TextToSpeech.QUEUE_FLUSH, null, headId);

        // 2) Remaining of the base chunk (if any)
        if (headEnd < base.end) {
            String tailText = text.substring(headEnd, base.end);
            String tailId   = "utt_" + headEnd + "_" + base.end;
            tts.speak(tailText, TextToSpeech.QUEUE_ADD, null, tailId);
        }

        // 3) Subsequent chunks untouched
        for (int i = idx + 1; i < chunks.size(); i++) {
            Chunk c = chunks.get(i);
            String uttId = "utt_" + c.start + "_" + c.end;
            tts.speak(c.text, TextToSpeech.QUEUE_ADD, null, uttId);
        }
    }


    private static int findChunkIndexForOffset(List<Chunk> chunks, int offset) {
        // pick the chunk that CONTAINS offset, or the last one that starts before it
        int lo = 0, hi = chunks.size() - 1, ans = 0;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            Chunk c = chunks.get(mid);
            if (offset < c.start) hi = mid - 1;
            else { ans = mid; lo = mid + 1; }
        }
        // ensure we don’t start BEFORE offset if there’s a later chunk whose end > offset
        while (ans < chunks.size() - 1 && chunks.get(ans).end <= offset) ans++;
        return ans;
    }


    public void setSpeechRate(float rate) { if (tts != null) tts.setSpeechRate(rate); }
    public void stop()  { if (tts != null) tts.stop(); }
    public void pause() { if (tts != null) tts.playSilentUtterance(250, TextToSpeech.QUEUE_ADD, "pause"); }
    public void shutdown() { if (tts != null) { tts.stop(); tts.shutdown(); tts = null; } }

    // ---------- internals ----------
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
                // If one sentence is already long, emit it alone
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
        // Fallback: split raw if no sentence breaks
        if (out.isEmpty() && text.length() > 0) {
            for (int i = 0; i < text.length(); i += maxLen) {
                int end = Math.min(text.length(), i + maxLen);
                out.add(new Chunk(i, end, text.substring(i, end)));
            }
        }
        return out;
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

}
