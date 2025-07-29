package com.driot.bookplayer.services;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.driot.bookplayer.utils.KanLogger;

public class DownloadRetryWorker extends Worker {

    public DownloadRetryWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String fileUrl = getInputData().getString(DownloadForegroundService.EXTRA_URL);
        String destinationFolder = getInputData().getString(DownloadForegroundService.EXTRA_DEST);
        String title = getInputData().getString(DownloadForegroundService.EXTRA_TITLE);
        int retryCount = getInputData().getInt(DownloadForegroundService.EXTRA_RETRY_COUNT, 0);
        long downloadStartTime = getInputData().getLong(DownloadForegroundService.EXTRA_START_TIME, System.currentTimeMillis());

        myLogW("Retrying download for: " + title + " (attempt " + (retryCount + 1) + ")");

        Intent serviceIntent = new Intent(getApplicationContext(), DownloadForegroundService.class);
        serviceIntent.putExtra(DownloadForegroundService.EXTRA_URL, fileUrl);
        serviceIntent.putExtra(DownloadForegroundService.EXTRA_DEST, destinationFolder);
        serviceIntent.putExtra(DownloadForegroundService.EXTRA_TITLE, title);
        serviceIntent.putExtra(DownloadForegroundService.EXTRA_RETRY_COUNT, retryCount);
        serviceIntent.putExtra(DownloadForegroundService.EXTRA_START_TIME, downloadStartTime);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getApplicationContext().startForegroundService(serviceIntent);
        } else {
            myLogEE(null, "startForegroundService needs api > 26");
        }

        return Result.success();
    }


    //--- LOG --------------------------
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogInFile(String str) { KanLogger.myLogInFile(this.getClass().getName(), str); }
    private void myLogD(String str) { KanLogger.myLogD(this.getClass().getName(), str); }
    private void myLogI(String str) { KanLogger.myLogI(this.getClass().getName(), str); }
    private void myLogW(String str) { KanLogger.myLogW(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
    private void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, this.getClass().getName(), str); }
    private void myToast(String str) { KanLogger.myToast(this.getClass().getName(), str); }
    private void myToastE(String str) { KanLogger.myToastE(this.getClass().getName(), str); }
    private void myKeyFirebase(String strKey, String strValue) {KanLogger.myKeyFirebase(strKey, strValue);}
    private void myLogFirebase(String strLog) {KanLogger.myLogFirebase(strLog);}

}
