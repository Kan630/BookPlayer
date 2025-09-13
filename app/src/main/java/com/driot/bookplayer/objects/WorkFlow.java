package com.driot.bookplayer.objects;

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
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.services.DownloadForegroundService;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.utils.KanLogger;

import java.io.File;

public class WorkFlow {

    public static boolean isSomeWorkFlowRunning(Context c) {
        myLogD("isSomeWorkFlowRunning() - called from " + c.getClass().getSimpleName());
        LoadBookTaskState state = getLoadBookTaskState();
        if (state==null) {
            myLogD("LoadBookTaskState : null instance...");
        } else {
            //if (state.downloadedFileReady || state.downloadedFilePath != null) {
            if (state.onGoingLoading) {
                myLog("LoadBookTaskState : onGoingLoading...");
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

    public static void cancelAllOngoingTasks(Context context) {
        myLog("...cancelAllOngoingTasks() - called from " + context.getClass().getSimpleName());

        try {
            Intent intent = new Intent(context, DownloadForegroundService.class);
            context.stopService(intent);
        } catch (Exception e) {
            myLogEE(e, "cancelAllOngoingTasks - DownloadForegroundService");
        }

        try {
            WorkManager.getInstance(context).cancelAllWorkByTag(FOREGROUND_DOWNLOAD_SERVICE_TAG);
            WorkManager.getInstance(context).cancelAllWorkByTag(BOOK_LOADING_WORKERS);
        } catch (Exception e) {
            myLogEE(e, "cancelAllOngoingTasks - WorkManager");
        }

        //Ensure nothing left in Download Folder
        try {
            String downloadDirPath = StorageHelper.getDownloadFolderPath(context);
            deleteFolderRecursive(downloadDirPath);
            File outputDir = new File(downloadDirPath);
            if (!outputDir.exists()) {
                if (!outputDir.mkdirs()) myLogE("error mkdir");
            }
        } catch (Exception e) {
            myLogEE(e, "cancelAllOngoingTasks - delete Download Folder");
        }

        //and Unzip Folder
        try {
            LoadBookTaskState state = getLoadBookTaskState();
            if (state != null) {
                String folderToDeletePath = state.futureFolderPath;
                if (folderToDeletePath.length()>5) {
                    if (folderToDeletePath.contains(Var.PATH_CHECK_AUDIO_FILE_INTERNAL)) { //only internal files
                        AppDatabase.databaseReadExecutor.execute(() -> { //make sure not in DB
                            if (AppDatabase.getDatabase(context).FolderDao().folderAlreadyExist_checkFolderPath(folderToDeletePath) == 0) {
                                myLogI("deleting internal audio folder [" + folderToDeletePath + "]");
                                deleteFolderRecursive(folderToDeletePath);
                        } else {
                                myLogW("tried to delete a folder still in DB : [" + folderToDeletePath + "]");
                            }
                        });
                    } else {
                        myLogD("no delete for non internal folder : [" + folderToDeletePath + "]");
                    }
                } else {
                    myLogW("tried to delete a folder with bad length : [" + folderToDeletePath + "]");
                }
            } else {
                myLogW("tried to delete a folder with state=null");
            }
        } catch (Exception e) {
            myLogEE(e, "cancelAllOngoingTasks - delete Internal (unzip) Folder");
        }

        try {
            ImageHelper.deleteTempImportImage(context);
        } catch (Exception e) {
            myLogEE(e, "cancelAllOngoingTasks - delete Temp Import Image");
        }

        try {
            clearLoadBookTaskState(context);
        } catch (Exception e) {
            myLogEE(e, "cancelAllOngoingTasks - clearLoadBookTaskState");
        }

        try {
            AppViewModelStoreOwner.clear();
        } catch (Exception e) {
            myLogEE(e, "cancelAllOngoingTasks - clear AppViewModelStoreOwner");
        }



    }







        ////////////////////////////////////////////////////////
        ///////// Loggers
        ////////////////////////////////////////////////////////
        private static final String TAG = "WorkFlow";
    private static void myLog(String str) { KanLogger.myLog(TAG, str); }
    private static void myLogD(String str) { KanLogger.myLogD(TAG, str); }
    private static void myLogI(String str) { KanLogger.myLogI(TAG, str); }
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
    private static void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, TAG, str); }
    private static void myToastEE(Throwable t, String str) { KanLogger.myToastEE(t, TAG, str); }
}
