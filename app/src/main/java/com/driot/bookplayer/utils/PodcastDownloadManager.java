package com.driot.bookplayer.utils;

import static com.driot.bookplayer.utils.PodcastHelper.buildPodcastEpisodeName;

import android.content.Context;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkManager;

import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.objects.PodcastEpisode;

import java.io.File;
import java.util.List;

public class PodcastDownloadManager {

    public static void enqueueDownloads(Context context, Podcast podcast, List<PodcastEpisode> episodes, File targetFolder, Runnable onComplete) {
        WorkManager wm = WorkManager.getInstance(context);
        WorkContinuation continuation = null;

        for (PodcastEpisode episode : episodes) {
            String destFileName = buildPodcastEpisodeName(episode);
            String destPath = new File(targetFolder, destFileName).getAbsolutePath();

            Data inputData = new Data.Builder()
                    .putString(DownloadEpisodeWorker.KEY_URL, episode.enclosureUrl)
                    .putString(DownloadEpisodeWorker.KEY_DEST_PATH, destPath)
                    .build();

            OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(DownloadEpisodeWorker.class)
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
                .putLong(FinalizeDownloadWorker.KEY_FEED_ID, podcast.feedId)
                .build();

        OneTimeWorkRequest finalizeRequest = new OneTimeWorkRequest.Builder(FinalizeDownloadWorker.class)
                .setInputData(finalizeData)
                .build();

        continuation = continuation.then(finalizeRequest);
        continuation.enqueue();
    }

}

