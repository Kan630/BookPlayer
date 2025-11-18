package com.driot.bookplayer.activities;

import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.views.FrequencyVisualizerView;

public class VisualizerScreensaverActivity extends AppCompatActivity {

    public static final String EXTRA_AUDIO_SESSION_ID = "EXTRA_AUDIO_SESSION_ID";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_visualizer_screensaver);

        // Optional: keep screen on while this activity is visible
        // (you might want a user option for this later)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Go immersive full screen
        hideSystemUi();

        FrequencyVisualizerView visualizerView = findViewById(R.id.fullscreenVisualizer);

        // Set mode from your saved option (legacy / wave / bars / radial)
        String mode = Option.getVisualizerType();
        visualizerView.setMode(mode);

        // Get audio session ID from intent
        int audioSessionId = getIntent().getIntExtra(EXTRA_AUDIO_SESSION_ID, 0);
        if (audioSessionId != 0) {
            visualizerView.link_toto(audioSessionId);
        }

        // Tap anywhere to exit
        View root = findViewById(R.id.rootScreensaver);
        root.setOnClickListener(v -> finish());
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUi();
        }
    }

    private void hideSystemUi() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
        );
    }
}
