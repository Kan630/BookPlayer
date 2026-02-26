package com.driot.bookplayer.services;

import static com.driot.bookplayer.global.Var.PODCAST_INDEX_ORG_SINCE;

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
            final int nbPodcastAutoDownload = AppDatabase.getDatabase(context).podcastDao().getNbAutoDownload();

            if (scheduledFuture == null || scheduledFuture.isCancelled()) {
                scheduledFuture = scheduler.scheduleWithFixedDelay(() -> {
                    myLogD("InAppPeriodicTask (images caching, optional podcasts auto-download and auto-delete) - every " + periodMinutes + " min.");
///  Podcasts AutoDownload
                    if (nbPodcastAutoDownload > 0 && (Pref.doCheckForPodcastAutoDownload() || Var.FORCE_AUTO_DOWNLOAD_NO_DELAY)) {
                        if (NetworkHelper.hasInternet(context)) {
                            PodcastHelper.checkForNewEpisodesToAutoDownload(context, PODCAST_INDEX_ORG_SINCE);
                        } else {
                            myLogD("no internet => bypassing podcast auto-download");
                        }
                    }
///  Podcasts AutoDelete
                    PodcastHelper.checkForEpisodesToAutoDelete(context);
/// Images
                    ImageHelper.processPendingImages(context);

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
