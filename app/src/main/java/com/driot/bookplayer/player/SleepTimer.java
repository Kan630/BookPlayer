package com.driot.bookplayer.player;

import android.os.Handler;

import androidx.annotation.NonNull;

public final class SleepTimer {

    public interface Listener {
        void onTick(int elapsedSeconds);
        void onReachedMax();
    }

    private final Handler h;
    private final int tickMs;
    private final Listener l;

    private boolean running = false;
    private int elapsed = 0;
    private int maxMinutes = 0;
    private boolean beepOnStop = false;

    public SleepTimer(@NonNull Handler handler, int tickMs, @NonNull Listener listener) {
        this.h = handler;
        this.tickMs = tickMs;
        this.l = listener;
    }

    private final Runnable r = new Runnable() {
        @Override public void run() {
            if (!running) return;
            l.onTick(elapsed);
            if (elapsed >= maxMinutes * 60) {
                running = false;
                l.onReachedMax();
            } else {
                elapsed += tickMs / 1000;
                h.postDelayed(this, tickMs);
            }
        }
    };

    public void start(int customMinutes) {
        this.maxMinutes = Math.max(0, customMinutes);
        this.elapsed = 0;
        if (running) stop();
        running = true;
        h.postDelayed(r, tickMs);
    }

    public void stop() {
        running = false;
        h.removeCallbacks(r);
    }

    public void reload(int customMinutes) {
        stop();
        start(customMinutes);
    }

    public boolean isRunning() { return running; }
    public int elapsedSeconds() { return elapsed; }
}
