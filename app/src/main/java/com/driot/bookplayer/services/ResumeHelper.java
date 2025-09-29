package com.driot.bookplayer.services;

import android.content.Context;
import androidx.work.*;

import com.driot.bookplayer.global.Pref;

import java.util.List;
import java.util.concurrent.TimeUnit;

public final class ResumeHelper {
    private ResumeHelper() {}

    public static void resumeWholePipeline(Context ctx) {
        LoadBookTaskState bs = Pref.getLoadBookTaskState();
        if (bs == null || bs.downloadFileUrl == null || bs.downloadDestinationFolder == null) return;

        // Make sure we’ll download again (will resume via HTTP Range)
        bs.doDownload = true;
        bs.onGoingLoading = true;
        Pref.setLoadBookTaskState(bs);

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresStorageNotLow(true)
                .build();

        Data input = new Data.Builder()
                .putString(DownloadWorker.KEY_URL, bs.downloadFileUrl)
                .putString(DownloadWorker.KEY_DEST_FOLDER, bs.downloadDestinationFolder)
                .putString(DownloadWorker.KEY_TITLE, bs.title)
                .putBoolean(DownloadWorker.KEY_IS_MANUAL, true)
                .build();

        OneTimeWorkRequest downloadWork = new OneTimeWorkRequest.Builder(DownloadWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(input)
                .addTag(BookLoadingWorkLauncher.BOOK_LOADING_WORKERS)
                .build();

        List<OneTimeWorkRequest> post = BookLoadingWorkLauncher.buildPostDownloadChain(bs);

        String unique = (bs.uniqueChainName != null)
                ? bs.uniqueChainName
                : "bookload:" + (bs.futureFolderName != null ? bs.futureFolderName : downloadWork.getId().toString());

        WorkContinuation cont = WorkManager.getInstance(ctx)
                .beginUniqueWork(unique, ExistingWorkPolicy.REPLACE, downloadWork);
        for (OneTimeWorkRequest step : post) cont = cont.then(step);
        cont.enqueue();
    }
}
