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
import com.driot.bookplayer.tts.TtsReaderController;

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

        androidx.recyclerview.widget.RecyclerView rv = findViewById(R.id.rvTtsText);
        TtsReaderController controller = new TtsReaderController(this, rv);
        controller.bind(this, vm);

        findViewById(R.id.miniNowPlaying).setVisibility(Option.getTtsFullscreenControls() ? View.VISIBLE : View.GONE);
    }

    // Convenience launcher
    public static void start(android.content.Context ctx) {
        ctx.startActivity(new android.content.Intent(ctx, TtsReaderActivity.class));
    }
}
