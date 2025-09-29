package com.driot.bookplayer.services;

import android.content.Context;
import android.content.Intent;

import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.driot.bookplayer.utils.KanLogger;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DownloadControl {

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();

    private DownloadControl() {}

    // --- Public, id-less API for UI ---

    public static void sendPause(Context ctx) {
        forEachRunningDownload(ctx, id -> sendPause(ctx, id));
    }

    public static void sendResume(Context ctx) {
        forEachRunningDownload(ctx, id -> sendResume(ctx, id));

        // Fallback: if nothing is running, force the pipeline to (re)start
        EXEC.execute(() -> {
            try {
                List<WorkInfo> infos = WorkManager.getInstance(ctx)
                        .getWorkInfosByTag(DownloadWorker.TAG_DOWNLOAD).get();

                boolean anyRunning = false;
                if (infos != null) {
                    for (WorkInfo wi : infos) {
                        if (wi.getState() == WorkInfo.State.RUNNING) {
                            anyRunning = true; break;
                        }
                    }
                }
                if (!anyRunning) {
                    myLogD("No running DownloadWorker → force resume pipeline");
                    // Rebuild based on current Pref/LoadBookTaskState
                    BookLoadingWorkLauncher.launch(ctx);
                }
            } catch (Exception ignored) {}
        });
    }

    public static void sendCancel(Context ctx) {
        // Courtesy broadcast to alive workers (they'll clean up partials)
        forEachRunningDownload(ctx, id -> sendCancel(ctx, id));
        // Your app logic already cancels the pipeline by tag or unique name elsewhere
    }

    // --- Legacy ID-based variants (optional to keep) ---

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
        Intent i = new Intent(DownloadWorker.ACTION_RESUME);
        i.setPackage(ctx.getPackageName());
        i.putExtra(DownloadWorker.EXTRA_WORK_ID, workId.toString());
        ctx.sendBroadcast(i);
    }

    // --- Internal: enumerate running DownloadWorkers by tag ---

    private interface IdConsumer { void accept(UUID id); }

    private static void forEachRunningDownload(Context ctx, IdConsumer consumer) {
        EXEC.execute(() -> {
            try {
                List<WorkInfo> infos = WorkManager.getInstance(ctx)
                        .getWorkInfosByTag(DownloadWorker.TAG_DOWNLOAD).get();
                if (infos == null) return;
                for (WorkInfo wi : infos) {
                    if (wi.getState() == WorkInfo.State.RUNNING) {
                        consumer.accept(wi.getId());
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    ////////////////////////////////////////////////////////
    private static final String TAG = "DownloadControl";
    private static void myLog(String str) { KanLogger.myLog(TAG, str); }
    private static void myLogD(String str) { KanLogger.myLogD(TAG, str); }
    private static void myLogI(String str) { KanLogger.myLogI(TAG, str); }
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
    private static void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, TAG, str); }
}
