package com.driot.bookplayer.services;

import static com.driot.bookplayer.global.Var.PODCASTINDEXORG_SINCE;

import android.content.Context;

import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.helpers.PodcastHelper;
import com.driot.bookplayer.utils.KanLogger;

import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class InAppPeriodicTaskManager {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> scheduledFuture;
    private final Context context;
    private final long periodMinutes;

    public InAppPeriodicTaskManager(Context context, long periodMinutes) {
        this.context = context.getApplicationContext();
        this.periodMinutes = periodMinutes;
    }

    public void start() {
        if (scheduledFuture == null || scheduledFuture.isCancelled()) {
            scheduledFuture = scheduler.scheduleWithFixedDelay(() -> {
                myLog("InAppPeriodicTask - Running task at " + new Date());

                if (Pref.shouldCheckApiForAutoDownload() || Var.FORCE_AUTO_DOWNLOAD_NO_DELAY) {
                    PodcastHelper.checkForNewEpisodesToAutoDownload(context, PODCASTINDEXORG_SINCE);
                    PodcastHelper.checkForEpisodesToAutoDelete(context);
                }
                ImageHelper.processPendingImages(context);

            }, 0, periodMinutes, TimeUnit.MINUTES);
        }
    }

    public void stop() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
        }
    }

    public void shutdown() {
        stop();
        scheduler.shutdownNow();
    }

    ////////////////////////////////////////////////////////
    private static final String TAG = "InAppPeriodicTaskManager";
    private static void myLog(String str) { KanLogger.myLog(TAG, str); }
    private static void myLogD(String str) { KanLogger.myLogD(TAG, str); }
    private static void myLogI(String str) { KanLogger.myLogI(TAG, str); }
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
    private static void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, TAG, str); }
    private static void myToastEE(Throwable t, String str) { KanLogger.myToastEE(t, TAG, str); }

}
