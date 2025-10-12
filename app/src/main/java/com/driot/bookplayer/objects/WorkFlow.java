/*
package com.driot.bookplayer.objects;

import static com.driot.bookplayer.imports.BookLoadingWorkLauncher.BOOK_LOADING_WORKERS;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.work.WorkManager;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.helpers.FileHelper;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.helpers.StorageHelper;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;


import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    private static final ExecutorService CLEANUP_EXEC =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "WF-Cleanup");
                t.setDaemon(true);
                return t;
            });

    public static void cancelAllOngoingTasks(Context context) {
        Context app = context.getApplicationContext();
        myLog("...cancelAllOngoingTasks() - called from " + context.getClass().getSimpleName());

        // Fast, non-blocking things only:
        LoadBookTaskState state = Pref.getLoadBookTaskState();
        try { Pref.clearLoadBookTaskState(app); } catch (Exception e) { myLogEE(e, "clearLoadBookTaskState fast"); }

        // WorkManager cancel is async; safe to trigger here
        WorkManager.getInstance(app).cancelAllWorkByTag(BOOK_LOADING_WORKERS);

        // Defer all disk I/O
        CLEANUP_EXEC.execute(() -> doCancelAllOngoingTasks(app, state));
    }

    private static void doCancelAllOngoingTasks(Context context, @Nullable LoadBookTaskState state) {
        Thread.currentThread().setPriority(Thread.NORM_PRIORITY - 1);
        myLogD("Cleanup starting (bg)…");

        // 1) Delete partial/lock files (specific, not the whole folder)
        try {
            if (state != null && state.downloadFileUrl != null && state.downloadDestinationFolder != null) {
                String fileName = com.driot.bookplayer.utils.Tonio.getFileNameFromUrl(state.downloadFileUrl);
                File downloadDir = new File(state.downloadDestinationFolder);
                File partial = new File(downloadDir, fileName);
                File lock = new File(partial.getAbsolutePath() + ".lock");
                if (lock.exists()) { try { lock.delete(); } catch (Throwable ignored) {} }
                if (partial.exists()) { try { partial.delete(); } catch (Throwable ignored) {} }
            } else {
                myLogD("Skip partial delete: state missing url/destination");
            }
        } catch (Exception e) {
            myLogEE(e, "cleanup - delete partial/lock");
        }

        // 3) Delete the unzip/working folder iff it's internal AND not referenced in DB
        try {
            if (state != null) {
                String folderToDeletePath = state.futureFolderPath;
                if (folderToDeletePath != null && folderToDeletePath.length() > 5) {
                    if (StorageHelper.isInInternalMemory(folderToDeletePath)) {
                        if (FileHelper.exists(folderToDeletePath)) {
                            // DB check on DB executor, then delete in THIS bg thread.
                            AppDatabase.databaseReadExecutor.execute(() -> {
                                boolean safeToDelete =
                                        AppDatabase.getDatabase(context)
                                                .folderDao()
                                                .folderAlreadyExist_checkFolderPath(folderToDeletePath) == 0;
                                if (safeToDelete) {
                                    myLogI("deleting internal audio folder [" + folderToDeletePath + "]");
                                    try { FileHelper.deleteFolderRecursive(folderToDeletePath); }
                                    catch (Exception e) { myLogEE(e, "delete internal audio folder"); }
                                } else {
                                    myLogW("folder still in DB : [" + folderToDeletePath + "]");
                                }
                            });
                        } else {
                            myLogD("folderToDeletePath does not exist : [" + folderToDeletePath + "]");
                        }
                    } else {
                        myLogD("no delete for non internal folder : [" + folderToDeletePath + "]");
                    }
                } else {
                    myLogW("bad futureFolderPath length or null");
                }
            } else {
                myLogD("cleanup - state=null");
            }
        } catch (Exception e) {
            myLogEE(e, "cleanup - delete Internal (unzip) Folder");
        }

        // 2) Optional: tidy the app’s Download folder CONTENTS (not the folder itself)
        try {
            String downloadDirPath = StorageHelper.getDownloadFolderPath(context);
            File dl = new File(downloadDirPath);
            if (dl.exists() && dl.isDirectory()) {
                FileHelper.deleteFolderChildren(dl); // implement: deletes children only
            } else if (!dl.exists()) {
                // Create if your pipeline assumes existence (still background, so OK)
                if (!dl.mkdirs()) myLogW("cleanup - mkdirs failed for " + dl.getAbsolutePath());
            }
        } catch (Exception e) {
            myLogEE(e, "cleanup - tidy Download folder");
        }

        // 4) Temp image
        try { ImageHelper.deleteTempImportImage(context); }
        catch (Exception e) { myLogEE(e, "cleanup - delete Temp Import Image"); }

        // 5) Final fast things (prefs/viewmodels) — still safe in bg
        try { Pref.clearLoadBookTaskState(context); } catch (Exception e) { myLogEE(e, "cleanup - clearLoadBookTaskState (bg)"); }
        try { AppViewModelStoreOwner.clear(); } catch (Exception e) { myLogEE(e, "cleanup - clear AppViewModelStoreOwner"); }

        myLogD("Cleanup finished (bg).");
    }
}

/*
    public static void cancelAllOngoingTasks(Context context) {
        myLog("...cancelAllOngoingTasks() - called from " + context.getClass().getSimpleName());

        LoadBookTaskState state = Pref.getLoadBookTaskState();

        try {
            Pref.clearLoadBookTaskState(context);
        } catch (Exception e) {
            myLogEE(e, "cancelAllOngoingTasks - clearLoadBookTaskState");
        }

        WorkManager.getInstance(context).cancelAllWorkByTag(BOOK_LOADING_WORKERS);


        // Delete ONLY the expected partial file for this workflow (and its .lock), not the whole folder
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
*/