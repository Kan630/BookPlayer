package com.driot.bookplayer.utils;

import static com.driot.bookplayer.global.Pref.clearLoadBookTaskState;
import static com.driot.bookplayer.global.Pref.getLoadBookTaskState;
import static com.driot.bookplayer.global.Pref.setLoadBookTaskState;
import static com.driot.bookplayer.global.Var.FOLDER_DOWNLOAD;
import static com.driot.bookplayer.global.Var.FOREGROUND_DOWNLOAD_SERVICE_TAG;
import static com.driot.bookplayer.utils.KanFiles.deleteFolderRecursive;

import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;
import androidx.work.WorkManager;

import com.driot.bookplayer.activities.AddResourceActivity;
import com.driot.bookplayer.activities.GetResourceActivity;
import com.driot.bookplayer.objects.LoadBookTaskState;
import com.driot.bookplayer.services.AddResourceService;
import com.driot.bookplayer.services.CopyFileService;
import com.driot.bookplayer.services.DownloadForegroundService;
import com.driot.bookplayer.services.DownloadService;
import com.driot.bookplayer.services.SplitM4bService;
import com.driot.bookplayer.services.UnzipService;

import java.io.File;

public class WorkFlow {


    public static boolean isSomeWorkFlowRunning(Context c) {
        myLogD("isSomeWorkFlowRunning() - called from " + c.getClass().getSimpleName());
        LoadBookTaskState state = getLoadBookTaskState(c, false);
        if (state==null) {
            myLogD("LoadBookTaskState : null instance...");
        } else {
            if (state.downloadedFileReady || state.downloadedFilePath != null) {
                myLog("LoadBookTaskState : Downloading...");
                return true;
            }
        }
        if (SplitM4bService.isSplitRunning) {
            myLog("yes : SplitM4bService...");
            return true;
        }
        if (UnzipService.isUnzipRunning) {
            myLog("yes : UnzipService...");
            return true;
        }
        if (CopyFileService.isCopyRunning) {
            myLog("yes : CopyFileService...");
            return true;
        }
        if (DownloadService.isBusy) {
            myLog("yes : DownloadService...");
            return true;
        }
        if (AddResourceService.isBusy) {
            myLog("yes : AddResourceService...");
            return true;
        }
        return false;
    }

    public static void maybeResumeWorkFlow(Context context) {
        String callerClass = context.getClass().getSimpleName();
        myLogD("maybe Resume WorkFlow...    called from " + callerClass);
        LoadBookTaskState state = getLoadBookTaskState(context, true);

        if (state == null) {
            myLogD("no WorkFlow");
            return;
        }

        myLog("WorkFlow " + state.currentLoadingOperation);
        myLog(state.toString().replace(", ","\n"));

        if (state.onGoingLoading) {

            if (AddResourceService.isBusy) {
                myLogD("AddResourceService is Busy");
            } else {
                myLogD("AddResourceService not Busy");
            }

            /*
            // Restart the AddResourceActivity
            if (!callerClass.equals(GetResourceActivity.class.getSimpleName())) {
                myLogI("Restarting AddResourceActivity...");
                Intent intentActivity = new Intent(context, AddResourceActivity.class);
                intentActivity.putExtra("LoadBookTaskState", state);
                context.startActivity(intentActivity);
            }

             */
        }
    }


    public static void setDownloadFinished(Context context, String filePath) {
        myLog("...setDownloadFinished() - called from " + context.getClass().getSimpleName());
        LoadBookTaskState state = getLoadBookTaskState(context);
        if (state != null) {
            state.downloadedFileReady = true;
            state.downloadedFilePath = filePath;
            setLoadBookTaskState(context, state);
            myLog("downloadedFilePath set to : " + filePath);
        }
    }

    public static void clearDownloadFinished(Context context) {
        myLogD("...clearDownloadFinished() - called from " + context.getClass().getSimpleName());
        LoadBookTaskState state = getLoadBookTaskState(context);
        if (state != null) {
            state.downloadedFileReady = false;
            state.downloadedFilePath = null;
            setLoadBookTaskState(context, state);
            myLog("downloadedFilePath set to null");
        }
        DownloadService.isBusy = false;
    }

    public static void setWorkFlowFinished(Context context) {
        myLogD("...clear ALL - called from " + context.getClass().getSimpleName());
        clearLoadBookTaskState(context);
    }





    public static void cancelAllOngoingTasks(Context context) {
        myLog("...cancelAllOngoingTasks() - called from " + context.getClass().getSimpleName());


        // Cancel Download
        Intent cancelIntent = new Intent(context, DownloadForegroundService.class);
        cancelIntent.setAction(DownloadForegroundService.ACTION_CANCEL);
        ContextCompat.startForegroundService(context, cancelIntent);

        WorkManager.getInstance(context).cancelAllWorkByTag(FOREGROUND_DOWNLOAD_SERVICE_TAG);


        // Cancel heavy stuff
        UnzipService.isUnzipRunning = false;
        SplitM4bService.isSplitRunning = false;
        CopyFileService.isCopyRunning = false;
        AddResourceService.isBusy = false;
        DownloadService.isBusy = false;

        // Also stop foreground/background services
        context.stopService(new Intent(context, AddResourceService.class));
        context.stopService(new Intent(context, CopyFileService.class));
        context.stopService(new Intent(context, UnzipService.class));
        context.stopService(new Intent(context, SplitM4bService.class));

        // Cancel jobs if any are registered with JobScheduler
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler != null) {
            scheduler.cancelAll();  // OR scheduler.cancel(jobId);
        }

        //Ensure nothing left in Download Folder
        String downloadDirPath = context.getFilesDir().getAbsolutePath() + "/" + FOLDER_DOWNLOAD;
        deleteFolderRecursive(downloadDirPath);
        File outputDir = new File(downloadDirPath);
        if (!outputDir.exists()) outputDir.mkdirs();

        setWorkFlowFinished(context);


    }







        ////////////////////////////////////////////////////////
        ///////// Loggers
        ////////////////////////////////////////////////////////
        private static final String TAG = "WorkFlow";
    private static void myLog(String str) { KanLogger.myLog(TAG, str); }
    private static void myLogD(String str) { KanLogger.myLogD(TAG, str); }
    private static void myLogI(String str) { KanLogger.myLogI(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
    private void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, TAG, str); }
    private void myToastEE(Throwable t, String str) { KanLogger.myToastEE(t, TAG, str); }
}
