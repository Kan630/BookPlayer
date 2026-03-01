package com.driot.bookplayer.tts;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.widget.TextView;
import com.driot.bookplayer.views.TtsTextView;
import androidx.lifecycle.LifecycleOwner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.text.Layout;

import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.player.PlaybackUiState;
import com.driot.bookplayer.player.PlaybackViewModel;

import java.lang.ref.WeakReference;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

/**
 * Helper class to manage TTS highlighting synchronization and seek detection.
 * Extracted from PlayActivity to clean up code.
 */
public class TtsHighlighter {

    public interface Listener {
        void onTtsHighlightApplied(TtsTextView tv, int startPos);
    }

    private final TtsTextView tvTtsText;
    private final Handler uiH = new Handler(Looper.getMainLooper());

    private Spannable spannableText;
    private final BackgroundColorSpan ttsBgSpan = new BackgroundColorSpan(0x55FFFF00);
    private final ForegroundColorSpan ttsFgSpan = new ForegroundColorSpan(Color.BLACK);

    // State tracking
    @Nullable
    private String lastTtsTextString = null;
    private int lastTtsTrackId = -1;
    private boolean lastTtsPlaying = false;
    private String lastTtsPhase = null;
    private long lastTtsPositionMs = -1;

    // Highlight scheduling
    private int pendingStart = -1;
    private int pendingEnd = -1;
    private boolean highlightScheduled = false;
    private Runnable pendingHighlightRunnable = null;
    private int lastAppliedHighlightEnd = -1;
    private long lastHighlightTime = 0;

    // Seek / Sync logic
    private long lastSeekTime = 0;
    private boolean suppressAutoScroll = false;
    private float downY;
    private final int touchSlop;
    private Listener listener;

    // Constants
    private static final long MIN_HIGHLIGHT_INTERVAL_MS = 50;
    private static final long SEEK_COOLDOWN_MS = 500;
    // Increased from 2000 to 5000 to avoid false positives at high playback speeds
    private static final long SEEK_DETECTION_THRESHOLD_MS = 5000;

    public TtsHighlighter(Context context, TtsTextView tvTtsText) {
        this.tvTtsText = tvTtsText;
        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void onTextReady(@Nullable String text) {
        if (text == null)
            text = "";
        if (!text.equals(lastTtsTextString)) {
            lastTtsTextString = text;
            SpannableStringBuilder sb = new SpannableStringBuilder(text);
            tvTtsText.setText(sb, TtsTextView.BufferType.SPANNABLE);
            spannableText = (Spannable) tvTtsText.getText();
            tvTtsText.setMovementMethod(ScrollingMovementMethod.getInstance());
            tvTtsText.setVerticalScrollBarEnabled(true);

            // Reset highlight tracking when text changes (new track)
            resetHighlightTracking();
        }
    }

    public void onPlaybackStateChanged(@Nullable PlaybackUiState s, PlaybackViewModel vm) {
        if (s == null)
            return;

        boolean isTts = "tts".equals(s.playMode);

        // Detect seeks
        if (isTts && lastTtsPositionMs >= 0 && s.positionMs > 0) {
            long positionDelta = Math.abs(s.positionMs - lastTtsPositionMs);
            if (positionDelta > SEEK_DETECTION_THRESHOLD_MS && s.playing) {
                myLog("TTS seek detected: position jumped from " + lastTtsPositionMs + " to " + s.positionMs);
                lastSeekTime = System.currentTimeMillis();
                // Reset tracking but keep ttsActuallyStarted=true since we're already playing
                resetHighlightTracking();
            }
        }
        if (isTts)
            lastTtsPositionMs = s.positionMs;

        // Detect track change
        if (s.trackId != lastTtsTrackId) {
            lastTtsTrackId = s.trackId;
            resetHighlightTracking();
        }

        // Detect Play/Pause state change to reset tracking if needed
        boolean isSpeak = Intents.PHASE_SPEAKING.equals(s.loadPhase);

        if (isSpeak != lastTtsPlaying) {
            lastTtsPlaying = isSpeak;
            if (!isSpeak) {
                resetHighlightTracking();
            }
            // Reset suppression when starting to speak or pausing
            suppressAutoScroll = false;
        }

        // Detect large jumps
        long pos = s.positionMs;
        if (lastTtsPositionMs >= 0) {
            long diff = Math.abs(pos - lastTtsPositionMs);
            if (diff > SEEK_DETECTION_THRESHOLD_MS) {
                myLogI("TTS seek detected: position jumped from " + lastTtsPositionMs + " to " + pos);
                resetHighlightTracking();
            }
        }
        lastTtsPositionMs = pos;

        lastTtsPhase = s.loadPhase;
    }

    public void scheduleHighlight(int s, int e) {
        long now = System.currentTimeMillis();

        // Ignore callbacks during seek cooldown period to prevent racing ahead
        if (lastSeekTime > 0 && (now - lastSeekTime) < SEEK_COOLDOWN_MS) {
            myLogD("TTS HIGHLIGHT: ignoring callback during seek cooldown [" + s + "-" + e + "]");
            return;
        }
        // Clear cooldown once it expires
        if (lastSeekTime > 0 && (now - lastSeekTime) >= SEEK_COOLDOWN_MS) {
            lastSeekTime = 0;
        }

        // Reset tracking when TTS actually starts to avoid stale highlights
        if (lastAppliedHighlightEnd < 0) {
            resetHighlightTracking();
        }

        // Large jump detection (backup for missing UI updates)
        if (lastAppliedHighlightEnd >= 0 && s > lastAppliedHighlightEnd + 1000) {
            myLogD("TTS HIGHLIGHT: large jump detected [" + s + "-" + e + "] (last=" + lastAppliedHighlightEnd
                    + "), resetting tracking");
            resetHighlightTracking();
            lastSeekTime = now;
        }

        if (highlightScheduled && (now - lastHighlightTime) < MIN_HIGHLIGHT_INTERVAL_MS) {
            pendingStart = s;
            pendingEnd = e;
            return;
        }

        pendingStart = s;
        pendingEnd = e;

        if (highlightScheduled && pendingHighlightRunnable != null) {
            uiH.removeCallbacks(pendingHighlightRunnable);
            highlightScheduled = false;
        }

        pendingHighlightRunnable = this::applyHighlight;
        highlightScheduled = true;
        uiH.postDelayed(pendingHighlightRunnable, Option.getTtsHighlightDelayMs());
    }

    private void applyHighlight() {
        highlightScheduled = false;
        pendingHighlightRunnable = null;
        if (spannableText == null || pendingStart < 0)
            return;
        int len = spannableText.length();
        int s = Math.max(0, Math.min(pendingStart, len));
        int e = Math.max(s + 1, Math.min(pendingEnd, len));

        try {
            // Debug logging for highlighted word
            if (spannableText != null && e <= len && s < e) {
                String highlightedWord = spannableText.subSequence(s, e).toString();
                String[] words = highlightedWord.trim().split("\\s+");
                if (words.length > 0)
                    highlightedWord = words[0];
                myLog("TTS HIGHLIGHT: pos=[" + s + "-" + e + "] word=[" + highlightedWord + "]");
            }

            if (spannableText != null) {
                spannableText.removeSpan(ttsBgSpan);
                spannableText.removeSpan(ttsFgSpan);
                spannableText.setSpan(ttsBgSpan, s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                spannableText.setSpan(ttsFgSpan, s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            lastAppliedHighlightEnd = e;
            lastHighlightTime = System.currentTimeMillis();

            // Trigger auto-scroll if needed
            if (!suppressAutoScroll) {
                triggerAutoScroll(s);
            }

        } catch (Throwable ignored) {
        }
    }

    private void triggerAutoScroll(int startPos) {
        if (listener != null) {
            listener.onTtsHighlightApplied(tvTtsText, startPos);
        }
    }

    public void attachTouchLogic(PlaybackViewModel vm) {
        final GestureDetector tapDetector = new GestureDetector(tvTtsText.getContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(@NonNull MotionEvent e) {
                        return true;
                    }

                    @Override
                    public boolean onSingleTapUp(@NonNull MotionEvent e) {
                        Layout layout = tvTtsText.getLayout();
                        if (layout == null || spannableText == null)
                            return false;

                        int x = (int) e.getX() - tvTtsText.getTotalPaddingLeft() + tvTtsText.getScrollX();
                        int y = (int) e.getY() - tvTtsText.getTotalPaddingTop() + tvTtsText.getScrollY();
                        int line = layout.getLineForVertical(y);
                        int off = layout.getOffsetForHorizontal(line, x);
                        off = Math.max(0, Math.min(off, tvTtsText.getText().length()));

                        int[] word = TtsHelper.findWordBounds(spannableText, off);
                        updateHighlightForManualSeek(word[0], word[1]);

                        vm.setTtsStartOffsetChars(word[0]);
                        return true;
                    }
                });

        tvTtsText.setOnTouchListener((v, ev) -> {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downY = ev.getY();
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    tapDetector.onTouchEvent(ev);
                    return false;
                case MotionEvent.ACTION_MOVE:
                    if (!suppressAutoScroll && Math.abs(ev.getY() - downY) > touchSlop) {
                        suppressAutoScroll = true;
                    }
                    tapDetector.onTouchEvent(ev);
                    return false;
                case MotionEvent.ACTION_UP: {
                    boolean tapped = tapDetector.onTouchEvent(ev);
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    if (tapped) {
                        suppressAutoScroll = false;
                    }
                    v.performClick();
                    return tapped;
                }
                case MotionEvent.ACTION_CANCEL:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    return false;
                default:
                    return false;
            }
        });
    }

    public void resetHighlightTracking() {
        myLog("resetHighlightTracking");
        lastAppliedHighlightEnd = -1;
        lastHighlightTime = 0;
        if (highlightScheduled && pendingHighlightRunnable != null) {
            uiH.removeCallbacks(pendingHighlightRunnable);
            highlightScheduled = false;
            pendingHighlightRunnable = null;
        }
        pendingStart = -1;
        pendingEnd = -1;
    }

    public void onDestroy() {
        uiH.removeCallbacksAndMessages(null);
    }

    public Spannable getSpannableText() {
        return spannableText;
    }

    // For manual tap-to-seek
    public void updateHighlightForManualSeek(int start, int end) {
        if (spannableText == null)
            return;
        try {
            spannableText.removeSpan(ttsBgSpan);
            spannableText.removeSpan(ttsFgSpan);
            spannableText.setSpan(ttsBgSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannableText.setSpan(ttsFgSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } catch (Throwable t) {
            myLogEE(t, "updateHighlightForManualSeek");
        }
    }

    public int getLastTtsTrackId() {
        return lastTtsTrackId;
    }

    public String getLastTtsPhase() {
        return lastTtsPhase;
    }

    @Nullable
    public String getLastTtsTextString() {
        return lastTtsTextString;
    }

    /** Centralized subscription to common TTS playback state */
    public void subscribe(LifecycleOwner owner, PlaybackViewModel vm) {
        // Text content
        vm.getTtsText().observe(owner, this::onTextReady);

        // Highlight range
        vm.getTtsRange().observe(owner, p -> {
            if (p != null)
                scheduleHighlight(p.first, p.second);
        });

        // Playback state (reset suppression on track change or play/pause)
        vm.getState().observe(owner, s -> {
            onPlaybackStateChanged(s, vm);
        });
    }
}
