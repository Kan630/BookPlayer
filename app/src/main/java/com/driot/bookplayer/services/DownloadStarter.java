package com.driot.bookplayer.services;

import android.content.Context;
import androidx.work.*;

public final class DownloadStarter {
    public static void startOrResume(Context ctx, DownloadSpec spec) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        Data input = new Data.Builder()
                .putString(DownloadWorker.KEY_URL, spec.url)
                .putString(DownloadWorker.KEY_DEST_FOLDER, spec.destFolder)
                .putString(DownloadWorker.KEY_TITLE, spec.title)
                .putBoolean(DownloadWorker.KEY_IS_MANUAL, spec.isManual)
                .build();

        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(DownloadWorker.class)
                .setConstraints(constraints)
                .setInputData(input)
                .addTag("download")
                .build();

        WorkManager.getInstance(ctx).enqueueUniqueWork(
                spec.uniqueName(),
                ExistingWorkPolicy.REPLACE,    // replace any queued retry so it runs now if possible
                req
        );
    }
}
