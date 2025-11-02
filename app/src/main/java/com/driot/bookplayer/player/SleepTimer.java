package com.driot.bookplayer.player;

import android.os.Handler;

import androidx.annotation.NonNull;

import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggerHelper;

public final class SleepTimer extends LoggerHelper {

    public interface Listener {
        void onTick(int elapsedSeconds);
        void onReachedMax();
    }

    private final Handler h;
    private final int tickMs;
    private final Listener l;
    private final String typePlayed;

    private boolean running = false;
    private long elapsedMs = 0L;
    private String elapsed_category = "00:00";
    private int maxMinutes = 0;

    public SleepTimer(@NonNull Handler handler, int tickMs, String typePlayed, @NonNull Listener listener) {
        super(SleepTimer.class);
        this.h = handler;
        this.tickMs = tickMs;
        this.l = listener;
        this.typePlayed = typePlayed;
    }

    // monotonic timing helpers
    private long playedSinceLastMinuteMs = 0L;
    private long lastPostRealtime = 0L;

    private final Runnable r = new Runnable() {
        @Override public void run() {
            if (!running) return;

            long now = android.os.SystemClock.elapsedRealtime();
            if (lastPostRealtime == 0L) lastPostRealtime = now;
            long delta = now - lastPostRealtime;           // real elapsed since last tick
            lastPostRealtime = now;

            elapsedMs += delta;
            int elapsedSec = (int) (elapsedMs / 1000L);

            // ---- 5-minute bin label ----
            // Floor to nearest lower 300-second boundary, then format as XX:00
            int binSec = (elapsedSec / 300) * 300;    // 300 = 5 minutes
            int hours = binSec / 3600;
            int minutes = (binSec % 3600) / 60;

            if (hours > 0) {
                elapsed_category = String.format(java.util.Locale.US, "%02d:%02d:00", hours, minutes); // Over an hour -> "HH:MM:00"
            } else {
                elapsed_category = String.format(java.util.Locale.US, "%02d:00", minutes); // Under an hour -> "MM:00"
            }

            // accumulate **real** played time for analytics
            playedSinceLastMinuteMs += delta;
            if (playedSinceLastMinuteMs >= 60_000L) {
                playedSinceLastMinuteMs -= 60_000L;
                if ("radio".equals(typePlayed)) {
                    FirebaseAnalyticsHelper.tellRadioFor1min(elapsed_category);
                } else {
                    FirebaseAnalyticsHelper.tellPlayFor1min(elapsed_category);
                }


            }

            l.onTick(elapsedSec);

            if (elapsedSec >= maxMinutes * 60) {
                myLog("SLEEP PAUSE after " + Tonio.formatTime(elapsedSec*1000, true, true));
                running = false;
                l.onReachedMax();
            } else {
                h.postDelayed(this, tickMs);
            }
        }
    };

    public void start(int customMinutes) {
        this.maxMinutes = Math.max(0, customMinutes);
        this.elapsedMs = 0L;
        this.playedSinceLastMinuteMs = 0L;
        this.lastPostRealtime = 0L;
        this.elapsed_category = "00:00";
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

}
