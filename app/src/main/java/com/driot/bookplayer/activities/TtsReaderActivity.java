package com.driot.bookplayer.activities;

import android.os.Bundle;
import android.util.TypedValue;
import android.text.Layout;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.nav.BaseBottomNavActivity;
import com.driot.bookplayer.player.PlaybackViewModel;
import com.driot.bookplayer.tts.TtsHighlighter;
import com.driot.bookplayer.views.TtsTextView;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class TtsReaderActivity extends BaseBottomNavActivity {

    @Override
    protected int getNavId() {
        return R.id.nav_library;
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_tts_reader;
    }

    @Override
    protected boolean enableOngoingTaskOverlay() {
        return false;
    }

    @Override
    protected boolean displayBottomNavBar() {
        return false;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        PlaybackViewModel vm = new ViewModelProvider(this).get(PlaybackViewModel.class);

        TtsTextView tvTtsFull = findViewById(R.id.tvTtsFullText);
        tvTtsFull.setTextSize(TypedValue.COMPLEX_UNIT_SP, (float) Option.getTtsFullscreenTextSize());

        TtsHighlighter ttsHighlighter = new TtsHighlighter(this, tvTtsFull);
        ttsHighlighter.setListener(this::applyAutoScroll);
        ttsHighlighter.attachTouchLogic(vm);
        ttsHighlighter.subscribe(this, vm);
        findViewById(R.id.miniNowPlaying).setVisibility(Option.getTtsFullscreenControls() ? View.VISIBLE : View.GONE);
    }

    private void applyAutoScroll(TtsTextView tv, int s) {
        tv.post(() -> {
            try {
                Layout layout = tv.getLayout();
                if (layout != null) {
                    int line = layout.getLineForOffset(s);
                    int y = layout.getLineTop(line);
                    int targetY = Math.max(0, y - tv.getHeight() / 3);
                    tv.scrollTo(0, targetY);
                }
            } catch (Throwable ignored) {
            }
        });
    }

    // Convenience launcher
    public static void start(android.content.Context ctx) {
        ctx.startActivity(new android.content.Intent(ctx, TtsReaderActivity.class));
    }
}
