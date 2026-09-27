package com.driot.bookplayer.podcasts;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.driot.bookplayer.utils.log.KanLogger;

public class FinalizeDownloadWorker extends Worker {

    public static final String KEY_FOLDER_PATH = "folder_path";
    public static final String KEY_FOLDER_NAME = "folder_name";
    public static final String KEY_FEED_ID = "feed_id";

    public FinalizeDownloadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String folderPath = getInputData().getString(KEY_FOLDER_PATH);
        String folderName = getInputData().getString(KEY_FOLDER_NAME);
        long feedId = getInputData().getLong(KEY_FEED_ID,0);

        if (folderPath == null || folderName == null) {
            myLogE("Missing folder path or name");
            return Result.failure();
        }

        // Chain the PodcastSyncWorker
        Data syncData = new Data.Builder()
                .putString(KEY_FOLDER_PATH, folderPath)
                .putString(KEY_FOLDER_NAME, folderName)
                .putLong(KEY_FEED_ID, feedId)
                .build();

        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(PodcastSyncWorker.class)
                .setInputData(syncData)
                .build();

        // One sync per folder at a time: each sync scans the whole folder and inserts what it
        // doesn't find in the DB, so two running together both inserted the same files (duplicate
        // ZikFile rows, seen on real devices). APPEND_OR_REPLACE queues this one after a running
        // sync of the same folder instead (and replaces a failed/cancelled chain).
        WorkManager.getInstance(getApplicationContext()).enqueueUniqueWork(
                "podcast_sync:" + folderPath, ExistingWorkPolicy.APPEND_OR_REPLACE, syncRequest);

        return Result.success();
    }

    //--- LOG --------------------------
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
}