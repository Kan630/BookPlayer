package com.driot.bookplayer.player;

import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.utils.log.BaseActivity;
import com.driot.bookplayer.views.FrequencyVisualizerView;

import static com.driot.bookplayer.utils.PermissionRequest.isRecordAudioPermissionGranted;

public class ScreensaverActivity extends BaseActivity {

    private FrequencyVisualizerView visualizer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Full screen, keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_screensaver);

        visualizer = findViewById(R.id.screensaverVisualizer);

        // Get audio session ID from intent
        int sessionId = getIntent().getIntExtra(Intents.EXTRA_AUDIO_SESSION_ID, -1);

        if (sessionId > 0 && Option.getVisualizerOn()
                && isRecordAudioPermissionGranted(this)) {
            try {
                visualizer.setMode(Option.getVisualizerType());
                visualizer.link_toto(sessionId);
                visualizer.setVisibility(View.VISIBLE);
            } catch (Throwable t) {
                myLogEE(t, "Failed to link visualizer");
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            myLogI("Screensaver dismissed by touch");
            finish();
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}
