package com.driot.bookplayer.utils;

import static com.driot.bookplayer.global.Pref.clearLoadBookTaskState;
import static com.driot.bookplayer.global.Pref.getLoadBookTaskState;
import static com.driot.bookplayer.global.Pref.setLoadBookTaskState;
import static com.driot.bookplayer.global.Var.FOLDER_DOWNLOAD;
import static com.driot.bookplayer.utils.KanFiles.deleteFolderRecursive;

import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;

import com.driot.bookplayer.activities.AddResourceActivity;
import com.driot.bookplayer.objects.LoadBookTaskState;
import com.driot.bookplayer.services.AddResourceService;
import com.driot.bookplayer.services.CopyFileService;
import com.driot.bookplayer.services.DownloadJobService;
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
        if (DownloadJobService.isJobRunning) {
            myLog("yes : DownloadJobService...");
            return true;
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

    public static void maybeResumeWorkFlow(Context c) {
        myLogD("maybeResumeDownloadFlow() - called from " + c.getClass().getSimpleName());
        LoadBookTaskState state = getLoadBookTaskState(c, true);

        if (state != null && state.downloadedFileReady && state.downloadedFilePath != null && !state.onGoing) {
            myLog("onGoing operation for: " + state.title);
            state.onGoing = true;
            setLoadBookTaskState(c, state);

            if (AddResourceService.isBusy) {
                myLogD("AddResourceService is Busy");
            } else {
                myLogD("AddResourceService not Busy");
            }

            myLogI("Restarting AddResourceActivity...");
            // Restart the AddResourceActivity
            Intent intentActivity = new Intent(c, AddResourceActivity.class);
            intentActivity.putExtra("LoadBookTaskState", state);
            c.startActivity(intentActivity);

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
        DownloadJobService.isJobRunning = false;
        DownloadService.isBusy = false;
    }

    public static void setWorkFlowFinished(Context context) {
        myLogD("...clear ALL - called from " + context.getClass().getSimpleName());
        clearLoadBookTaskState(context);
    }





    public static void cancelAllOngoingTasks(Context context) {
        myLog("...cancelAllOngoingTasks() - called from " + context.getClass().getSimpleName());

        // Cancel JobService (e.g., download)
        DownloadJobService.isJobRunning = false;

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
}
