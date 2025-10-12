package com.driot.bookplayer.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.driot.bookplayer.services.PodcastSyncWorker;
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

        WorkManager.getInstance(getApplicationContext()).enqueue(syncRequest);

        return Result.success();
    }

    //--- LOG --------------------------
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
}