package com.driot.bookplayer.services;

import android.content.Context;
import android.content.Intent;

import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.driot.bookplayer.utils.KanLogger;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.UUID;

public final class DownloadControl {

    private DownloadControl() {}

    public static void sendPause(Context ctx, UUID workId) {
        myLog("sendPause - workId = [" + workId + "]");
        Intent i = new Intent(DownloadWorker.ACTION_PAUSE);
        i.setPackage(ctx.getPackageName());
        i.putExtra(DownloadWorker.EXTRA_WORK_ID, workId.toString());
        ctx.sendBroadcast(i);
    }

    public static void sendCancel(Context ctx, UUID workId) {
        myLog("sendCancel - workId = [" + workId + "]");
        Intent i = new Intent(DownloadWorker.ACTION_CANCEL);
        i.setPackage(ctx.getPackageName());
        i.putExtra(DownloadWorker.EXTRA_WORK_ID, workId.toString());
        ctx.sendBroadcast(i);
    }

    public static void sendResume(Context ctx, UUID workId) {
        myLog("sendResume - workId = [" + workId + "]");

        // 1) Try the live in-process receiver (when Worker is paused but alive)
        Intent i = new Intent(DownloadWorker.ACTION_RESUME);
        i.setPackage(ctx.getPackageName());
        i.putExtra(DownloadWorker.EXTRA_WORK_ID, workId.toString());
        ctx.sendBroadcast(i);

        // 2) Fallback: if no running worker, rebuild the WHOLE chain under the same unique name
        WorkManager.getInstance(ctx).getWorkInfoById(workId).addListener(() -> {
            try {
                WorkInfo info = WorkManager.getInstance(ctx).getWorkInfoById(workId).get();
                boolean running = (info != null && info.getState() == WorkInfo.State.RUNNING);
                if (!running) {
                    myLogD("not running, resuming pipeline");
                    ResumeHelper.resumeWholePipeline(ctx);   // <<<< use pipeline resume
                }
            } catch (Exception ignored) {}
        }, java.util.concurrent.Executors.newSingleThreadExecutor());
    }



    ////////////////////////////////////////////////////////
    private static final String TAG = "DownloadControl";
    private static void myLog(String str) { KanLogger.myLog(TAG, str); }
    private static void myLogD(String str) { KanLogger.myLogD(TAG, str); }
    private static void myLogI(String str) { KanLogger.myLogI(TAG, str); }
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
    private static void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, TAG, str); }
    private static void myToastEE(Throwable t, String str) { KanLogger.myToastEE(t, TAG, str); }
}
