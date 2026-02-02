package com.driot.bookplayer.player;

import android.os.Handler;

import androidx.annotation.NonNull;

import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggerHelper;

public final class PlayTimer extends LoggerHelper {

    public interface Listener {
        void onTick(int elapsedSeconds);

        void onReachedMax();

        /** Called each full minute with a label like "05:00", "12:00" or "01:15:00". */
        void onEveryMinute(@NonNull String elapsedCategory);
    }

    private final Handler h;
    private final int tickMs;
    private final Listener l;

    private boolean running = false;
    private long elapsedMs = 0L;
    private long msSinceLastUserAction = 0L;
    private String elapsed_category = "00:00";
    private int maxMinutes = 0;

    public PlayTimer(@NonNull Handler handler, int tickMs, @NonNull Listener listener) {
        super(PlayTimer.class);
        this.h = handler;
        this.tickMs = tickMs;
        this.l = listener;
    }

    // monotonic timing helpers
    private long playedSinceLastMinuteMs = 0L;
    private long lastPostRealtime = 0L;

    private final Runnable r = new Runnable() {
        @Override
        public void run() {
            if (!running)
                return;

            long now = android.os.SystemClock.elapsedRealtime();
            if (lastPostRealtime == 0L)
                lastPostRealtime = now;
            long delta = now - lastPostRealtime;
            lastPostRealtime = now;

            elapsedMs += delta;
            msSinceLastUserAction += delta;

            int elapsedSec = (int) (elapsedMs / 1000L);
            int inactivitySec = (int) (msSinceLastUserAction / 1000L);

            // 5-minute bin label
            int binSec = (elapsedSec / 300) * 300;
            int hours = binSec / 3600;
            int minutes = (binSec % 3600) / 60;
            elapsed_category = (hours > 0)
                    ? String.format(java.util.Locale.US, "%02d:%02d:00", hours, minutes)
                    : String.format(java.util.Locale.US, "%02d:00", minutes);

            // notify every full minute
            playedSinceLastMinuteMs += delta;
            if (playedSinceLastMinuteMs >= 60_000L) {
                playedSinceLastMinuteMs -= 60_000L;
                myLog(Tonio.formatMmSs(elapsedMs) + "...     sleep in "
                        + Tonio.formatMmSs(((long) maxMinutes * 60 - inactivitySec) * 1000) + " -- from " + maxMinutes
                        + "min.");
                l.onEveryMinute(elapsed_category);
            }

            l.onTick(inactivitySec);

            if (inactivitySec >= maxMinutes * 60) {
                myLog("SLEEP PAUSE after "
                        + com.driot.bookplayer.utils.Tonio.formatTime(inactivitySec * 1000L, true, true));
                running = false;
                l.onReachedMax();
            } else {
                h.postDelayed(this, tickMs);
            }
        }
    };

    public void start(int customMinutes) {
        myLog("=> starting - (sleep in " + customMinutes + " min.)");
        this.maxMinutes = Math.max(0, customMinutes);
        this.elapsedMs = 0L;
        this.msSinceLastUserAction = 0L;
        this.playedSinceLastMinuteMs = 0L;
        this.lastPostRealtime = 0L;
        this.elapsed_category = "00:00";
        if (running)
            stop();
        running = true;
        h.postDelayed(r, tickMs);
    }

    public void stop() {
        if (!running)
            myLog("=> stopping after " + Tonio.formatMmSsMs(elapsedMs));
        running = false;
        h.removeCallbacks(r);
    }

    public void reload(int customMinutes) {
        stop();
        start(customMinutes);
    }

    public void resetLastUserAction() {
        if (running) {
            myLog("=> resetting LAST USER ACTION timer");
            this.msSinceLastUserAction = 0L;
            // NOTE: We do NOT reset elapsedMs or playedSinceLastMinuteMs
            // because we want stats to reflect the total continuous playback session.
        }
    }

    public boolean isRunning() {
        return running;
    }

    public long getSleepLeftMs() {
        return Math.max(0, (long) maxMinutes * 60 * 1000 - msSinceLastUserAction);
    }
}
