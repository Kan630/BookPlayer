package com.driot.bookplayer.utils;

import static com.driot.bookplayer.global.Pref.clearLoadBookTaskState;
import static com.driot.bookplayer.global.Pref.getLoadBookTaskState;
import static com.driot.bookplayer.global.Pref.setLoadBookTaskState;
import static com.driot.bookplayer.global.Var.FOREGROUND_DOWNLOAD_SERVICE_TAG;
import static com.driot.bookplayer.services.BookLoadingWorkLauncher.BOOK_LOADING_WORKERS;
import static com.driot.bookplayer.helpers.FileHelper.deleteFolderRecursive;

import android.content.Context;
import android.content.Intent;

import androidx.work.WorkManager;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.objects.AppViewModelStoreOwner;
import com.driot.bookplayer.objects.LoadBookTaskState;
import com.driot.bookplayer.services.DownloadForegroundService;
import com.driot.bookplayer.helpers.StorageHelper;

import java.io.File;

public class WorkFlow {

    public static boolean isSomeWorkFlowRunning(Context c) {
        myLogD("isSomeWorkFlowRunning() - called from " + c.getClass().getSimpleName());
        LoadBookTaskState state = getLoadBookTaskState();
        if (state==null) {
            myLogD("LoadBookTaskState : null instance...");
        } else {
            if (state.downloadedFileReady || state.downloadedFilePath != null) {
                myLog("LoadBookTaskState : Downloading...");
                return true;
            }
        }
        return false;
    }

    public static void maybeResumeWorkFlow(Context context) {
        String callerClass = context.getClass().getSimpleName();
        myLogD("maybe Resume WorkFlow...    called from " + callerClass);
        LoadBookTaskState state = getLoadBookTaskState();

        if (state == null) {
            myLogD("no WorkFlow");
        } else {
            myLogI("WorkFlow " + state.currentOperation + " - " + state);
        }
    }


    public static void setDownloadFinished(Context context, String filePath) {
        myLog("...setDownloadFinished() - called from " + context.getClass().getSimpleName());
        LoadBookTaskState state = getLoadBookTaskState();
        if (state != null) {
            state.downloadedFileReady = true;
            state.downloadedFilePath = filePath;
            state.isLoadingPaused = false;
            state.progressText = "download finished";
            setLoadBookTaskState(state);
            myLog("downloadedFilePath set to : " + filePath);
        }
    }

    public static void setWorkFlowFinished(Context context) {
        myLogD("...clear ALL - called from " + context.getClass().getSimpleName());
        clearLoadBookTaskState(context);
        AppViewModelStoreOwner.clear();
    }

    public static void cancelAllOngoingTasks(Context context) {
        myLog("...cancelAllOngoingTasks() - called from " + context.getClass().getSimpleName());

        Intent intent = new Intent(context, DownloadForegroundService.class);
        context.stopService(intent);

        WorkManager.getInstance(context).cancelAllWorkByTag(FOREGROUND_DOWNLOAD_SERVICE_TAG);
        WorkManager.getInstance(context).cancelAllWorkByTag(BOOK_LOADING_WORKERS);

        //Ensure nothing left in Download Folder
        String downloadDirPath = StorageHelper.getDownloadFolderPath(context);
        deleteFolderRecursive(downloadDirPath);
        File outputDir = new File(downloadDirPath);
        if (!outputDir.exists()) {
            if (!outputDir.mkdirs()) myLogE("error mkdir");
        }

        //and Unzip Folder
        LoadBookTaskState state = getLoadBookTaskState();
        if (state != null) {
            String folderToDeletePath = state.futureFolderPath;
            if (folderToDeletePath.length()>5) {
                if (!folderToDeletePath.contains(Var.PATH_CHECK_AUDIO_FILE_INTERNAL)) {
                    if (state.onGoingLoading) { //means stuck in error
                        AppDatabase.databaseReadExecutor.execute(() -> { //make sure not in DB
                            if (AppDatabase.getDatabase(context).FolderDao().folderAlreadyExist_checkFolderPath(folderToDeletePath) == 0) {
                                myLogI("deleting internal audio folder [" + folderToDeletePath + "]");
                                deleteFolderRecursive(folderToDeletePath);
                            }
                        });
                    }
                }
            }
        }

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
