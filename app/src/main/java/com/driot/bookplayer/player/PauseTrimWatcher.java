package com.driot.bookplayer.player;

import android.os.Handler;

import androidx.annotation.NonNull;

import com.driot.bookplayer.utils.Tonio;

public final class PauseTrimWatcher {

    public interface Killer {
        void kill();            // called when threshold exceeded
        void onLog(String msg); // optional logging
    }

    private final Handler h;
    private final int periodMs;
    private final Killer killer;
    private final Supplier nowMs;
    private final Supplier pauseSinceMs;
    private final long trimAfterPauseMs;

    public interface Supplier { long get(); }

    public PauseTrimWatcher(@NonNull Handler handler,
                            int periodMs,
                            @NonNull Killer killer,
                            @NonNull Supplier nowMs,
                            @NonNull Supplier pauseSinceMs,
                            long trimAfterPauseMs) {
        this.h = handler; this.periodMs = periodMs; this.killer = killer;
        this.nowMs = nowMs; this.pauseSinceMs = pauseSinceMs; this.trimAfterPauseMs = trimAfterPauseMs;
    }

    private final Runnable r = new Runnable() {
        @Override public void run() {
            long p = pauseSinceMs.get();
            if (p != 0) {
                long delta = nowMs.get() - p;
                killer.onLog("Paused since " + Tonio.formatTime(delta) + " (max " + Tonio.formatTime(trimAfterPauseMs) + ")");
                if (delta > trimAfterPauseMs) killer.kill();
            }
            h.postDelayed(this, periodMs);
        }
    };

    public void start() { h.postDelayed(r, periodMs); }
    public void stop()  { h.removeCallbacks(r); }
}
