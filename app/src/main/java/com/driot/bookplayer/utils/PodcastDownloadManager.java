package com.driot.bookplayer.utils;

import android.content.Context;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkManager;

import com.driot.bookplayer.podcasts.PodcastHelper;
import com.driot.bookplayer.podcasts.PodcastEpisode;
import com.driot.bookplayer.services.PodcastDownloadEpisodeWorker;

import java.io.File;
import java.util.List;

public class PodcastDownloadManager {

    public static void enqueueDownloads(Context context, long podcastFeedId, List<PodcastEpisode> episodes, File targetFolder, Runnable onComplete) {
        WorkManager wm = WorkManager.getInstance(context);
        WorkContinuation continuation = null;

        for (PodcastEpisode episode : episodes) {
            String destFileName = PodcastHelper.buildPodcastEpisodeFileName(episode);
            String destPath = new File(targetFolder, destFileName).getAbsolutePath();

            Data inputData = new Data.Builder()
                    .putString(PodcastDownloadEpisodeWorker.KEY_URL, episode.enclosureUrl)
                    .putString(PodcastDownloadEpisodeWorker.KEY_DEST_PATH, destPath)
                    .build();

            OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(PodcastDownloadEpisodeWorker.class)
                    .setInputData(inputData)
                    .build();

            if (continuation == null) {
                continuation = wm.beginWith(request);
            } else {
                continuation = continuation.then(request);
            }
        }

        Data finalizeData = new Data.Builder()
                .putString(FinalizeDownloadWorker.KEY_FOLDER_PATH, targetFolder.getAbsolutePath())
                .putString(FinalizeDownloadWorker.KEY_FOLDER_NAME, targetFolder.getName())
                .putLong(FinalizeDownloadWorker.KEY_FEED_ID, podcastFeedId)
                .build();

        OneTimeWorkRequest finalizeRequest = new OneTimeWorkRequest.Builder(FinalizeDownloadWorker.class)
                .setInputData(finalizeData)
                .build();

        continuation = continuation.then(finalizeRequest);
        continuation.enqueue();
    }

}

