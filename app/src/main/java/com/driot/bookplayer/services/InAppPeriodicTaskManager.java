package com.driot.bookplayer.services;

import static com.driot.bookplayer.global.Var.PODCAST_INDEX_ORG_SINCE;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.myLogD;

import android.content.Context;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.podcasts.PodcastHelper;
import com.driot.bookplayer.utils.log.LoggerHelper;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class InAppPeriodicTaskManager extends LoggerHelper {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> scheduledFuture;
    private final Context context;
    private final long periodMinutes;

    public InAppPeriodicTaskManager(Context context, long periodMinutes) {
        super(InAppPeriodicTaskManager.class);
        this.context = context.getApplicationContext();
        this.periodMinutes = periodMinutes;
    }

    public void start() {

        AppDatabase.databaseReadExecutor.execute(() -> {

            if (scheduledFuture == null || scheduledFuture.isCancelled()) {
                scheduledFuture = scheduler.scheduleWithFixedDelay(() -> {
                    myLogD("InAppPeriodicTask (images caching, optional podcasts auto-download and auto-delete) - every " + periodMinutes + " min.");
/// Podcast auto-download + auto-delete
                    PodcastHelper.doAutoDownloadAndDelete(context);
/// Images
                    ImageHelper.processPendingImages(context, System.currentTimeMillis());

                }, Var.PERIODIC_TASK_MANAGER_INITIAL_DELAY_IN_SECONDS, periodMinutes, TimeUnit.MINUTES);
            }
        });
    }

    public void stop() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
        }
    }

}
