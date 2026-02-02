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

        // Force orientation before setContentView to avoid config change recreating the activity
        // (manifest has configChanges=orientation|screenSize so we won't recreate anyway).
        if (Option.getScreensaverForceOrientation()) {
            String mode = Option.getScreensaverOrientationMode();
            if ("PORTRAIT".equals(mode)) {
                setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            } else {
                setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            }
        }

        // True full screen: hide status bar AND navigation bar
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Hide navigation bar (immersive sticky mode)
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        setContentView(R.layout.activity_screensaver);

        visualizer = findViewById(R.id.screensaverVisualizer);

        // Get audio session ID from intent
        int sessionId = getIntent().getIntExtra(Intents.EXTRA_AUDIO_SESSION_ID, -1);

        // Always show visualizer container
        visualizer.setVisibility(View.VISIBLE);

        if (sessionId > 0 && Option.getVisualizerOn()
                && isRecordAudioPermissionGranted(this)) {
            try {
                visualizer.setMode(Option.getScreensaverVisualizerType());
                visualizer.link_toto(sessionId);
                myLogI("Screensaver visualizer linked to session " + sessionId);
            } catch (Throwable t) {
                myLogEE(t, "Failed to link visualizer");
            }
        } else {
            myLogW("Screensaver visualizer not linked: sessionId=" + sessionId
                    + ", visualizerOn=" + Option.getVisualizerOn()
                    + ", hasPermission=" + isRecordAudioPermissionGranted(this));
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

    @Override
    public void finish() {
        int prevOrientation = getIntent().getIntExtra("previous_orientation", -1);
        if (prevOrientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else if (prevOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        PlaybackCommands.resetLastUserAction(this);
        super.finish();
    }

    @Override
    protected void onPause() {
        if (visualizer != null) {
            visualizer.release();
        }
        super.onPause();
    }
}
