package com.driot.bookplayer.activities;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.MotionEvent;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.player.PlaybackViewModel;
import com.driot.bookplayer.tts.TtsHelper;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class TtsReaderActivity extends BaseBottomNavActivity {

    @Override protected int getNavId() { return R.id.nav_library; } // or whatever your "play" tab id is
    @Override protected int getLayoutResId() { return R.layout.activity_tts_reader; }
    @Override protected boolean enableOngoingTaskOverlay() { return true; }
    @Override protected boolean displayBottomNavBar() { return false; }

    private PlaybackViewModel vm;
    private TextView tvTtsFull;
    private Spannable spannableText;

    private final BackgroundColorSpan ttsBgSpan = new BackgroundColorSpan(0x55FFFF00);
    private final ForegroundColorSpan ttsFgSpan = new ForegroundColorSpan(Color.BLACK);

    private boolean suppressAutoScroll = false;
    private float downY;
    private int touchSlop;
    private int pendingStart = -1, pendingEnd = -1;
    private boolean highlightScheduled = false;
    private final android.os.Handler uiH = new android.os.Handler(android.os.Looper.getMainLooper());

    private int lastTtsTrackId = -1;
    @Nullable private String lastPlayMode = null;
    @Nullable private String lastPhase = null;
    private boolean lastPlaying = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        vm = new ViewModelProvider(this).get(PlaybackViewModel.class);

        tvTtsFull = findViewById(R.id.tvTtsFullText);
        tvTtsFull.setMovementMethod(ScrollingMovementMethod.getInstance());
        tvTtsFull.setVerticalScrollBarEnabled(true);

        touchSlop = android.view.ViewConfiguration.get(this).getScaledTouchSlop();

        // Text content
        vm.getTtsText().observe(this, txt -> {
            if (txt == null) txt = "";
            SpannableStringBuilder sb = new SpannableStringBuilder(txt);
            tvTtsFull.setText(sb, TextView.BufferType.SPANNABLE);
            spannableText = (Spannable) tvTtsFull.getText();
        });

        // Highlight range
        vm.getTtsRange().observe(this, p -> {
            if (p != null) scheduleTtsHighlight(p.first, p.second);
        });

        vm.getState().observe(this, s -> {
            if (s == null) return;

            boolean isTts = Var.PLAY_MODE_TTS.equals(s.playMode);
            int trackId   = s.trackId;
            String phase  = s.loadPhase;   // field of PlaybackUiState

            boolean trackChanged = isTts && (trackId != lastTtsTrackId);
            boolean becameReady  = isTts
                    && !Intents.PHASE_READY.equals(lastPhase)
                    && Intents.PHASE_READY.equals(phase);

            // 🔹 Detect play/pause toggle (used instead of PlayActivity's click listener)
            boolean playPauseToggled = isTts && (s.playing != lastPlaying);

            // 1) When user presses play/pause in the fragment (state toggles) → restore auto-follow
            if (playPauseToggled) {
                suppressAutoScroll = false;
            }

            // 2) When chapter changes OR TTS becomes READY for a new chapter → refresh text + auto-follow
            if (isTts && (trackChanged || becameReady)) {
                suppressAutoScroll = false;
                vm.requestTtsTextOnce();
            }

            lastTtsTrackId = trackId;
            lastPlayMode   = s.playMode;
            lastPhase      = phase;
            lastPlaying    = s.playing;
        });



        // Tap-to-seek
        final android.view.GestureDetector tapDetector =
                new android.view.GestureDetector(tvTtsFull.getContext(),
                        new android.view.GestureDetector.SimpleOnGestureListener() {
                            @Override public boolean onDown(@NonNull MotionEvent e) {
                                return true;
                            }
                            @Override public boolean onSingleTapUp(@NonNull MotionEvent e) {
                                Layout layout = tvTtsFull.getLayout();
                                if (layout == null || spannableText == null) return false;

                                int x = (int)e.getX() - tvTtsFull.getTotalPaddingLeft() + tvTtsFull.getScrollX();
                                int y = (int)e.getY() - tvTtsFull.getTotalPaddingTop() + tvTtsFull.getScrollY();
                                int line = layout.getLineForVertical(y);
                                int off  = layout.getOffsetForHorizontal(line, x);
                                off = Math.max(0, Math.min(off, tvTtsFull.getText().length()));

                                int[] word = TtsHelper.findWordBounds(spannableText, off);
                                try {
                                    spannableText.removeSpan(ttsBgSpan);
                                    spannableText.removeSpan(ttsFgSpan);
                                    spannableText.setSpan(ttsBgSpan, word[0], word[1], Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                                    spannableText.setSpan(ttsFgSpan, word[0], word[1], Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                                } catch (Throwable ignored) {}

                                vm.setTtsStartOffsetChars(word[0]);
                                return true;
                            }
                        });

        tvTtsFull.setOnTouchListener((v, ev) -> {
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
                        v.performClick();
                    }
                    return tapped;
                }
                case MotionEvent.ACTION_CANCEL:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    return false;
                default:
                    return false;
            }
        });

        // If service hasn't sent text yet, ask once
        vm.requestTtsTextOnce();
    }

    private void scheduleTtsHighlight(int s, int e) {
        pendingStart = s; pendingEnd = e;
        if (highlightScheduled) return;
        highlightScheduled = true;
        uiH.postDelayed(this::applyTtsHighlight, Option.getTtsHighlightDelayMs());
    }

    private void applyTtsHighlight() {
        highlightScheduled = false;
        if (spannableText == null || pendingStart < 0) return;
        int len = spannableText.length();
        int s = Math.max(0, Math.min(pendingStart, len));
        int e = Math.max(s + 1, Math.min(pendingEnd, len));
        try {
            spannableText.removeSpan(ttsBgSpan);
            spannableText.removeSpan(ttsFgSpan);
            spannableText.setSpan(ttsBgSpan, s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannableText.setSpan(ttsFgSpan, s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } catch (Throwable ignored) {}

        if (suppressAutoScroll) return;
        tvTtsFull.post(() -> {
            try {
                Layout layout = tvTtsFull.getLayout();
                if (layout != null) {
                    int line = layout.getLineForOffset(s);
                    int y = layout.getLineTop(line);
                    int targetY = Math.max(0, y - tvTtsFull.getHeight() / 3);
                    tvTtsFull.scrollTo(0, targetY);
                }
            } catch (Throwable ignored) {}
        });
    }

    // Convenience launcher
    public static void start(android.content.Context ctx) {
        ctx.startActivity(new android.content.Intent(ctx, TtsReaderActivity.class));
    }
}
