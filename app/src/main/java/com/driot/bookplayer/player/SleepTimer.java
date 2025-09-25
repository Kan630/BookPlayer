package com.driot.bookplayer.player;

import android.os.Handler;

import androidx.annotation.NonNull;

import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.utils.KanLogger;
import com.driot.bookplayer.utils.Tonio;

public final class SleepTimer {

    public interface Listener {
        void onTick(int elapsedSeconds);
        void onReachedMax();
    }

    private final Handler h;
    private final int tickMs;
    private final Listener l;

    private boolean running = false;
    private int elapsed = 0;  // en sec
    private int elapsed_count = 1;
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
            if (elapsed_count % 60 == 0) {
                FirebaseAnalyticsHelper.tellPlayFor1min();
            }
            if (elapsed >= maxMinutes * 60) {
                myLog("--------------   SLEEP PAUSE   ---------------------------------------------- " + Tonio.formatTime(elapsed*1000, true, true) + "s. since timer started.....      (AutoSleep set to " + maxMinutes + "min.)");
                running = false;
                l.onReachedMax();
            } else {
                myLogD("----------------------------------------------------------------------------- " + Tonio.formatTime(elapsed*1000, true, true) + "s. since timer started.....      (AutoSleep set to " + maxMinutes + "min.)");
                elapsed += tickMs / 1000;
                elapsed_count += 1;
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


    // ----------------------- LOG -----------------------
    private static final String TAG = "SleepTimer";
    private static void myLog(String str) { KanLogger.myLog(TAG, str); }
    private static void myLogD(String str) { KanLogger.myLogD(TAG, str); }
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
    private static void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, TAG, str); }
    private static void myToastEE(Throwable t, String str) { KanLogger.myToastEE(t, TAG, str); }

}
