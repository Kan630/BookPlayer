package com.driot.bookplayer.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.driot.bookplayer.objects.LoadBookTaskState;
import com.driot.bookplayer.utils.KanLogger;
import com.driot.bookplayer.global.Pref;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class DownloadRetryWorker extends Worker {

    public static final String ACTION_DOWNLOAD_COMPLETE = "com.driot.bookplayer.DOWNLOAD_COMPLETE";
    public static final String ACTION_DOWNLOAD_CANCELLED = "com.driot.bookplayer.DOWNLOAD_CANCELLED";

    private static final int MAX_ATTEMPTS = 3;
    private static final int LATCH_WAIT_TIME_IN_MIN = 20;

    private final CountDownLatch latch = new CountDownLatch(1);

    private boolean downloadWasCancelled = false;



    public DownloadRetryWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();

        int attempts = getRunAttemptCount();  // starts from 0
        myLog("Run attempt: " + attempts + 1 );

        if (attempts > MAX_ATTEMPTS) {
            myLogE("Download failed too many times (" + attempts + ")");
            return Result.failure(); // Ends the chain
        }

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                String action = intent.getAction();
                if (ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                    myLogD("Broadcast received: download complete");
                    downloadWasCancelled = false;
                    latch.countDown();
                } else if (ACTION_DOWNLOAD_CANCELLED.equals(action)) {
                    myLogD("Broadcast received: download cancelled");
                    downloadWasCancelled = true;
                    latch.countDown();
                }
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_DOWNLOAD_COMPLETE);
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);


        try {
            myLogD("Starting download service and waiting...");
            LoadBookTaskState state = Pref.getLoadBookTaskState();
            if (state == null || !state.onGoingLoading || state.downloadFileUrl == null) {
                myLogE("Retry aborted: No valid task state found");
                return Result.failure();
            }
            state.downloadRetryCount += 1;
            Pref.setLoadBookTaskState(state);
            myLogW("Retrying download for: " + state.title + " (attempt " + state.downloadRetryCount + ")");
            Intent startDownloadForegroundServiceIntent = new Intent(context, DownloadForegroundService.class);
            ContextCompat.startForegroundService(context, startDownloadForegroundServiceIntent);


            // Wait up to ** minutes (optional timeout) and then retry
            boolean finished = latch.await(LATCH_WAIT_TIME_IN_MIN, TimeUnit.MINUTES);
            context.unregisterReceiver(receiver);

            if (finished) {
                if (downloadWasCancelled) {
                    myLogW("Download was cancelled by user or system");
                    return Result.failure(); // stop the chain
                } else {
                    myLogI("Download completed, launching next steps...");
                    BookLoadingWorkLauncher.launchAfterDownload(context);
                    return Result.success();
                }
            } else {
                myLogW("Download timed out");
                return Result.retry();
            }
        } catch (Exception e) {
            myLogEE(e, "Exception during download wait");
            return Result.retry();
        }


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
