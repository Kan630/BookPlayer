package com.driot.bookplayer.objects;

import static com.driot.bookplayer.services.BookLoadingWorkLauncher.BOOK_LOADING_WORKERS;

import android.content.Context;

import androidx.work.WorkManager;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.FileHelper;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.utils.KanLogger;

import java.io.File;

public class WorkFlow {

    public static boolean isSomeWorkFlowRunning(Context c) {
        myLogD("isSomeWorkFlowRunning() - called from " + c.getClass().getSimpleName());
        LoadBookTaskState state = Pref.getLoadBookTaskState();
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
        LoadBookTaskState state = Pref.getLoadBookTaskState();

        if (state == null) {
            myLogD("no WorkFlow");
        } else {
            myLogI("WorkFlow " + state.currentOperation + " - " + state);
        }
    }

    public static void cancelAllOngoingTasks(Context context) {
        myLog("...cancelAllOngoingTasks() - called from " + context.getClass().getSimpleName());

        LoadBookTaskState state = Pref.getLoadBookTaskState();

        try {
            Pref.clearLoadBookTaskState(context);
        } catch (Exception e) {
            myLogEE(e, "cancelAllOngoingTasks - clearLoadBookTaskState");
        }

        WorkManager.getInstance(context).cancelAllWorkByTag(BOOK_LOADING_WORKERS);


        // 5) Delete ONLY the expected partial file for this workflow (and its .lock), not the whole folder
        try {
            if (state != null && state.downloadFileUrl != null && state.downloadDestinationFolder != null) {
                String fileName = com.driot.bookplayer.utils.Tonio.getFileNameFromUrl(state.downloadFileUrl);
                String downloadDirPath = state.downloadDestinationFolder;
                java.io.File partial = new java.io.File(downloadDirPath, fileName);
                java.io.File lock    = new java.io.File(partial.getAbsolutePath() + ".lock");

                // It’s okay if these fail because the worker may still be releasing handles in onStopped/finally
                if (lock.exists()) { try { lock.delete(); } catch (Throwable ignored) {} }
                if (partial.exists()) { try { partial.delete(); } catch (Throwable ignored) {} }
            } else {
                myLogD("Skip partial delete: state missing url/destination");
            }
        } catch (Exception e) {
            myLogEE(e, "cancelAllOngoingTasks - delete partial/lock");
        }

        // MY STUFF

        //Ensure nothing left in Download Folder
        try {
            String downloadDirPath = StorageHelper.getDownloadFolderPath(context);
            FileHelper.deleteFolderRecursive(downloadDirPath);
            File outputDir = new File(downloadDirPath);
            if (!outputDir.exists()) {
                if (!outputDir.mkdirs()) myLogE("error mkdir");
            }
        } catch (Exception e) {
            myLogEE(e, "cancelAllOngoingTasks - delete Download Folder");
        }

        //and Unzip Folder
        try {
            if (state != null) {
                String folderToDeletePath = state.futureFolderPath;
                if (folderToDeletePath.length()>5) {
                    if (StorageHelper.isInInternalMemory(folderToDeletePath)) { //only internal files
                        if (FileHelper.exists(folderToDeletePath)) {
                            AppDatabase.databaseReadExecutor.execute(() -> { //make sure not in DB
                                if (AppDatabase.getDatabase(context).FolderDao().folderAlreadyExist_checkFolderPath(folderToDeletePath) == 0) {
                                    myLogI("deleting internal audio folder [" + folderToDeletePath + "]");
                                    FileHelper.deleteFolderRecursive(folderToDeletePath);
                                } else {
                                    myLogW("tried to delete a folder still in DB : [" + folderToDeletePath + "]");
                                }
                            });
                        } else {
                            myLogD("folderToDeletePath does not exist : [" + folderToDeletePath + "]");
                        }
                    } else {
                        myLogD("no delete for non internal folder : [" + folderToDeletePath + "]");
                    }
                } else {
                    myLogW("tried to delete a folder with bad length : [" + folderToDeletePath + "]");
                }
            } else {
                myLogD("cleaning folder - state=null");
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
            Pref.clearLoadBookTaskState(context);
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
