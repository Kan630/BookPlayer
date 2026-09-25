package com.driot.bookplayer.services;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.helpers.FileHelper;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.podcasts.PodcastHelper;
import com.driot.bookplayer.utils.log.LoggingWorker;

import java.io.File;

public class DeleteFolderWorker extends LoggingWorker {

    public static final String KEY_FOLDER_ID = "key_folder_id";
    public static final String KEY_FOLDER_NAME = "key_folder_name";

    public DeleteFolderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    private static String stackToString(Throwable t) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        t.printStackTrace(pw);
        return sw.toString();
    }

    @NonNull
    @Override
    public Result doWorkBody() {
        long folderId = getInputData().getLong(KEY_FOLDER_ID, -1L);
        String folderName = getInputData().getString(KEY_FOLDER_NAME);
        if (folderId < 0) {
            myLogEE(null, "DeleteFolderWorker - folderId < 0");
            return Result.failure(new Data.Builder()
                    .putString("error", "Bad input: folderId < 0")
                    .build());
        }

        myLogD("init done");

        try {
            Context appCtx = getApplicationContext();
            AppDatabase db = AppDatabase.getDatabase(appCtx);

            String folderPath = db.zikFileDao().getFolderPath((int) folderId);
            long sharing = folderPath == null ? 0 : db.folderDao().countOtherFoldersWithPath(folderPath, folderId);
            if (sharing > 0) {
                // A duplicate import could store two books in one folder: erasing it would take the
                // other book's files too - only drop this book's rows.
                myLogW(sharing + " other book(s) use [" + folderPath + "] - keeping the files on disk");
            } else if (!eraseFolderAndFiles(appCtx, folderPath)) {
                myLogEE(null, "Disk delete error");
            } else {
                myLogD("Disk delete done");
            }

            PodcastHelper.deletePodcastFolder((int) folderId, appCtx);

            db.folderDao().delete((int) folderId);
            db.zikFileDao().deleteAllZikFilesInFolder((int) folderId);
            PodcastHelper.cancelAutoDownload(appCtx, (int) folderId);

            myLog("delete finished");

            return Result.success();

        } catch (Exception e) {
            myLogEE(e, "general exception");
            return Result.failure(new Data.Builder()
                    .putString("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())
                    .putString("stack", stackToString(e))
                    .build());
        }
    }

    private boolean eraseFolderAndFiles(Context ctx, String strPath) {
        myLogD("erasing [" + strPath + "]");
        if (strPath == null)
            return false;

        if (strPath.endsWith("files/unzipped") || strPath.endsWith("files/unzipped/")) {
            // guard-rail
            return false;
        }
        if (strPath.length() <= 5)
            return false;

        String starter = "file:///";
        if (!StorageHelper.isInInternalMemory(strPath)) {
            // Not in app user-data zone: don't delete from disk, but consider DB cleanup
            // OK.
            myLogD("not in app memory => no disk delete");
            return true;
        } else {
            strPath = strPath.replace(starter, "");
            try {
                File folderToDelete = new File(strPath);

                FileHelper.recursiveRemove(folderToDelete, (count, itemName) -> {
                    // Update progress
                    // We can throttle if needed, but for now reporting all
                    setProgressAsync(new Data.Builder()
                            .putInt("p_count", count)
                            .putString("p_name", itemName)
                            .build());
                });
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }
}
