package com.driot.bookplayer.activities;

import android.annotation.SuppressLint;
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
import com.driot.bookplayer.player.PlaybackUiState;
import com.driot.bookplayer.player.PlaybackViewModel;
import com.driot.bookplayer.tts.TtsHelper;
import com.driot.bookplayer.tts.TtsHighlighter;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class TtsReaderActivity extends BaseBottomNavActivity implements TtsHighlighter.HighlightListener {

    @Override
    protected int getNavId() {
        return R.id.nav_library;
    } // or whatever your "play" tab id is

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_tts_reader;
    }

    @Override
    protected boolean enableOngoingTaskOverlay() {
        return true;
    }

    @Override
    protected boolean displayBottomNavBar() {
        return false;
    }

    private PlaybackViewModel vm;
    private TextView tvTtsFull;
    private TtsHighlighter highlighter;

    private boolean suppressAutoScroll = false;
    private float downY;
    private int touchSlop;

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        vm = new ViewModelProvider(this).get(PlaybackViewModel.class);

        tvTtsFull = findViewById(R.id.tvTtsFullText);
        highlighter = new TtsHighlighter(tvTtsFull, this);

        touchSlop = android.view.ViewConfiguration.get(this).getScaledTouchSlop();

        // Text content
        vm.getTtsText().observe(this, txt -> {
            highlighter.onTextReady(txt);
        });

        // Highlight range
        vm.getTtsRange().observe(this, p -> {
            if (p != null)
                highlighter.scheduleHighlight(p.first, p.second);
        });

        vm.getState().observe(this, s -> {
            if (s == null)
                return;

            boolean isTts = Var.PLAY_MODE_TTS.equals(s.playMode);
            boolean trackChanged = isTts && (s.trackId != highlighter.getLastTtsTrackId());
            boolean becameReady = isTts
                    && !Intents.PHASE_READY.equals(highlighter.getLastTtsPhase())
                    && Intents.PHASE_READY.equals(s.loadPhase);

            // 1) When user presses play/pause in the fragment (state toggles) → restore
            // auto-follow
            // This logic is now handled within TtsHighlighter.onPlaybackStateChanged

            // 2) When chapter changes OR TTS becomes READY for a new chapter → refresh text
            // + auto-follow
            if (isTts && (trackChanged || becameReady)) {
                suppressAutoScroll = false;
                if (trackChanged) {
                    vm.resetTtsTextRequestFlag();
                }
                vm.requestTtsTextOnce();
            }

            highlighter.onPlaybackStateChanged(s);
        });

        // Tap-to-seek
        final android.view.GestureDetector tapDetector = new android.view.GestureDetector(tvTtsFull.getContext(),
                new android.view.GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(@NonNull MotionEvent e) {
                        return true;
                    }

                    @Override
                    public boolean onSingleTapUp(@NonNull MotionEvent e) {
                        Layout layout = tvTtsFull.getLayout();
                        Spannable sp = highlighter.getSpannableText();
                        if (layout == null || sp == null)
                            return false;

                        int x = (int) e.getX() - tvTtsFull.getTotalPaddingLeft() + tvTtsFull.getScrollX();
                        int y = (int) e.getY() - tvTtsFull.getTotalPaddingTop() + tvTtsFull.getScrollY();
                        int line = layout.getLineForVertical(y);
                        int off = layout.getOffsetForHorizontal(line, x);
                        off = Math.max(0, Math.min(off, tvTtsFull.getText().length()));

                        int[] word = TtsHelper.findWordBounds(sp, off);
                        highlighter.updateHighlightForManualSeek(word[0], word[1]);

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

    @Override
    public void onLoadingStatusChanged(boolean loading) {
        // ReaderActivity doesn't have the progress overlay currently,
        // but it could show/hide something if needed.
    }

    @Override
    public void onScrollToPosition(TextView tv, int charOffset) {
        if (suppressAutoScroll)
            return;
        tv.post(() -> {
            try {
                Layout layout = tv.getLayout();
                if (layout != null) {
                    int line = layout.getLineForOffset(charOffset);
                    int y = layout.getLineTop(line);
                    int targetY = Math.max(0, y - tv.getHeight() / 3);
                    tv.scrollTo(0, targetY);
                }
            } catch (Throwable ignored) {
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (highlighter != null)
            highlighter.onDestroy();
        super.onDestroy();
    }

    // Convenience launcher
    public static void start(android.content.Context ctx) {
        ctx.startActivity(new android.content.Intent(ctx, TtsReaderActivity.class));
    }
}
