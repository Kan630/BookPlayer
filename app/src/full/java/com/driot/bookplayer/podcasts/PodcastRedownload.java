package com.driot.bookplayer.podcasts;

import android.content.Context;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkManager;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Episode;
import com.driot.bookplayer.redownload.RedownloadHelper;

import java.util.Collections;
import java.util.List;

/** Full-flavor half of RedownloadHelper: a podcast folder's episodes each remember their own
 *  download address, so each missing one is fetched straight to the path its track already has. */
public final class PodcastRedownload {

    private PodcastRedownload() {
    }

    public static List<Long> folderIds(Context context) {
        return AppDatabase.getDatabase(context).episodeDao().getFoldersWhollyRedownloadable();
    }

    public static boolean isRedownloadable(Context context, long folderId) {
        return folderIds(context).contains(folderId);
    }

    public static void enqueue(Context context, long folderId) {
        WorkManager wm = WorkManager.getInstance(context);
        WorkContinuation continuation = null;
        for (RedownloadHelper.MissingTrack t : RedownloadHelper.prepareMissingTracks(context, folderId)) {
            Episode ep = AppDatabase.getDatabase(context).episodeDao().getByZikFileId(t.zik.getId());
            if (ep == null || ep.enclosureUrl == null || ep.enclosureUrl.isEmpty()) {
                continue;
            }
            OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(PodcastDownloadEpisodeWorker.class)
                    .setInputData(new Data.Builder()
                            .putString(PodcastDownloadEpisodeWorker.KEY_URL, ep.enclosureUrl)
                            .putString(PodcastDownloadEpisodeWorker.KEY_DEST_PATH, t.dest.getAbsolutePath())
                            .build())
                    .build();
            continuation = continuation == null ? wm.beginWith(request) : continuation.then(request);
        }
        if (continuation != null) {
            continuation.enqueue();
        }
    }
}
