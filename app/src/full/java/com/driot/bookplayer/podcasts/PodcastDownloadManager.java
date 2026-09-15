package com.driot.bookplayer.podcasts;

import android.content.Context;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkManager;

import com.driot.bookplayer.R;
import com.driot.bookplayer.helpers.StorageHelper;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import java.io.File;
import java.util.List;

public class PodcastDownloadManager {

    /** Covers all three entry points (auto-download, manual single-episode, batch "download
     *  last N") with the same user-configurable thresholds used for book/ebook downloads
     *  (Settings > Download - separate for internal vs SD card) - PodcastDownloadEpisodeWorker
     *  itself has no free-space awareness, so without this a low-storage device would just retry
     *  a doomed download forever. */
    public static void enqueueDownloads(Context context, long podcastFeedId, List<PodcastEpisode> episodes,
            File targetFolder, Runnable onComplete) {
        long freeBytes = StorageHelper.getUsableSpaceForPath(targetFolder.getPath());
        int minFreeMb = StorageHelper.getMinFreeStorageMbForPath(context, targetFolder.getPath());
        long minFreeBytes = minFreeMb * 1024L * 1024L;
        if (freeBytes > 0 && freeBytes < minFreeBytes) {
            long freeMB = freeBytes / (1024 * 1024);
            myLogW("enqueueDownloads: skipped, low storage (" + freeMB + "MB free, need " + minFreeMb + "MB)");
            myToastE(context.getString(R.string.podcast_download_skipped_low_storage, freeMB, minFreeMb));
            return;
        }

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
                    .addTag("DOWNLOAD_EPISODE_" + episode.id)
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
