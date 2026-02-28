package com.driot.bookplayer.helpers;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Helper to manage a loading message ticker with elapsed time.
 * Consolidates Handler/Runnable logic used across various search activities.
 */
public class LoadingProgressHelper {

    public interface MessageProvider {
        @NonNull
        String getInitialMessage();

        @NonNull
        String getTickMessage(long elapsedSec);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable runnable;
    private long startTime;
    private TextView textView;
    private MessageProvider provider;

    /**
     * Start the ticker. Shows the initial message immediately, then updates every
     * second.
     */
    public void start(@Nullable TextView tv, @NonNull MessageProvider provider) {
        stop(); // Ensure any previous ticker is stopped

        this.textView = tv;
        this.provider = provider;

        if (textView == null)
            return;

        textView.setText(provider.getInitialMessage());
        textView.setVisibility(View.VISIBLE);

        startTime = System.currentTimeMillis();

        runnable = new Runnable() {
            @Override
            public void run() {
                if (textView == null || textView.getVisibility() != View.VISIBLE) {
                    stop();
                    return;
                }

                long elapsedSec = (System.currentTimeMillis() - startTime) / 1000;
                textView.setText(provider.getTickMessage(elapsedSec));
                handler.postDelayed(this, 1000);
            }
        };

        handler.postDelayed(runnable, 1000);
    }

    /**
     * Stop the ticker and hide the TextView.
     */
    public void stop() {
        handler.removeCallbacks(runnable);
        runnable = null;
        if (textView != null) {
            textView.setVisibility(View.GONE);
            textView = null;
        }
        provider = null;
    }
}
