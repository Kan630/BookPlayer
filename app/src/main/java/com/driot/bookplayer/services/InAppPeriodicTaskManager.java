package com.driot.bookplayer.services;

import static com.driot.bookplayer.global.Var.PODCAST_INDEX_ORG_SINCE;

import android.content.Context;

import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.helpers.PodcastHelper;
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
        if (scheduledFuture == null || scheduledFuture.isCancelled()) {
            scheduledFuture = scheduler.scheduleWithFixedDelay(() -> {
                //myLogD("InAppPeriodicTask - Running task at " + new Date());

///  Pocasts
                if (Pref.doCheckForPodcastAutoDownload() || Var.FORCE_AUTO_DOWNLOAD_NO_DELAY) {
                    if (NetworkHelper.isNetworkAvailable(context)) {
                        PodcastHelper.checkForNewEpisodesToAutoDownload(context, PODCAST_INDEX_ORG_SINCE);
                    } else {
                        myLogD("no internet => bypassing podcast auto-download");
                    }
                    PodcastHelper.checkForEpisodesToAutoDelete(context);
                }
/// Images
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

}
