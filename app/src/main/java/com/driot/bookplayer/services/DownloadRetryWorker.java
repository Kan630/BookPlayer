package com.driot.bookplayer.services;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.driot.bookplayer.objects.LoadBookTaskState;
import com.driot.bookplayer.utils.KanLogger;
import com.driot.bookplayer.global.Pref;

public class DownloadRetryWorker extends Worker {

    public DownloadRetryWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        LoadBookTaskState state = Pref.getLoadBookTaskState(getApplicationContext());
        if (state == null || !state.onGoingLoading || state.downloadFileUrl == null) {
            myLogE("Retry aborted: No valid task state found");
            return Result.failure();
        }

        state.downloadRetryCount += 1;
        Pref.setLoadBookTaskState(getApplicationContext(), state);

        myLogW("Retrying download for: " + state.title + " (attempt " + state.downloadRetryCount + ")");

        Intent startDownloadForegroundServiceIntent = new Intent(getApplicationContext(), DownloadForegroundService.class);
        ContextCompat.startForegroundService(getApplicationContext(), startDownloadForegroundServiceIntent);

        return Result.success();
    }

    //--- LOG --------------------------
    private void myLogW(String str) { KanLogger.myLogW(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
}
