package com.driot.bookplayer.utils;

import static com.driot.bookplayer.utils.KanFiles.sanitizeFilename;

import android.content.Context;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkManager;

import com.driot.bookplayer.objects.PodcastEpisode;

import java.io.File;
import java.util.List;

public class PodcastDownloadManager {

    public static void enqueueDownloads(Context context, List<PodcastEpisode> episodes, File targetFolder, Runnable onComplete) {
        WorkManager wm = WorkManager.getInstance(context);
        WorkContinuation continuation = null;

        for (PodcastEpisode episode : episodes) {
            String safeTitle = sanitizeFilename(episode.title);
            String safeDate = sanitizeFilename(episode.datePublishedPretty);
            String destPath = new File(targetFolder, safeTitle + " - " + safeDate + ".mp3").getAbsolutePath();

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
                .build();

        OneTimeWorkRequest finalizeRequest = new OneTimeWorkRequest.Builder(FinalizeDownloadWorker.class)
                .setInputData(finalizeData)
                .build();

        continuation = continuation.then(finalizeRequest);
        continuation.enqueue();
    }

    public static void enqueuePodcastDownload(Context context, PodcastEpisode episode, String destPath) {
        Data inputData = new Data.Builder()
                .putString(DownloadEpisodeWorker.KEY_URL, episode.enclosureUrl)
                .putString(DownloadEpisodeWorker.KEY_DEST_PATH, destPath)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(DownloadEpisodeWorker.class)
                .setInputData(inputData)
                .build();

        WorkManager.getInstance(context).enqueue(request);
    }
}

