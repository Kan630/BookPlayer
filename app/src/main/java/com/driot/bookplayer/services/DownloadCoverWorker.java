package com.driot.bookplayer.services;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.helpers.FileHelper;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.utils.log.LoggingWorker;

/**
 * Downloads a remote image URL and saves it as the Folder cover.
 * - Reuses ImageHelper pipeline (download + compress + max size cap)
 * - Updates DB
 * - Deletes the old local file if replaced
 */
public class DownloadCoverWorker extends LoggingWorker {

    public static final String KEY_FOLDER_ID = "folderId";
    public static final String KEY_IMAGE_URL = "imageUrl";

    public static final String ACTION_FOLDER_IMAGE_UPDATED = "com.driot.bookplayer.ACTION_FOLDER_IMAGE_UPDATED";
    public static final String EXTRA_FOLDER_ID = "folderId";

    public DownloadCoverWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        long folderId = getInputData().getLong(KEY_FOLDER_ID, -1L);
        String imageUrl = getInputData().getString(KEY_IMAGE_URL);

        if (folderId <= 0 || imageUrl == null || imageUrl.trim().isEmpty()) {
            myLogE("DownloadCoverWorker: bad args (folderId=" + folderId + ", url=" + imageUrl + ")");
            return Result.failure(new Data.Builder()
                    .putString("error", "Bad arguments")
                    .build());
        }

        try {
            setProgressAsync(new Data.Builder().putString("state", "starting").build());

            AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
            Folder folder = db.folderDao().getById(folderId);
            if (folder == null) {
                myLogE("DownloadCoverWorker: folder not found id=" + folderId);
                return Result.failure(new Data.Builder()
                        .putString("error", "Folder not found")
                        .build());
            }

            final String oldPath = folder.image;

            myLog("Downloading cover for folderId=" + folderId + " from " + imageUrl);
            setProgressAsync(new Data.Builder().putString("state", "downloading").build());

            // Uses your internal pipeline (download -> validate image -> compress -> save)
            String savedAbsPath = ImageHelper.downloadRemoteToBookCoverVersioned(getApplicationContext(), folderId, imageUrl);

            if (savedAbsPath == null || savedAbsPath.trim().isEmpty()) {
                myLogE("DownloadCoverWorker: download/compress failed");
                return Result.failure(new Data.Builder()
                        .putString("error", "Download or compression failed")
                        .build());
            }

            // Update DB
            db.folderDao().updateImage((int) folderId, null); // force room observer
            db.folderDao().updateImage((int) folderId, savedAbsPath);
            myLog("Cover saved at: " + savedAbsPath);

            // Clean up old local image if different
            if (oldPath != null && !oldPath.equals(savedAbsPath)) {
                try {
                    FileHelper.deleteFile(getApplicationContext(), oldPath);
                    myLogD("Old cover deleted: " + oldPath);
                } catch (Throwable t) {
                    myLogEE(t, "Failed deleting old cover: " + oldPath);
                }
            }

            setProgressAsync(new Data.Builder().putString("state", "done").build());

            return Result.success(new Data.Builder()
                    .putString("path", savedAbsPath)
                    .build());

        } catch (Throwable t) {
            myLogEE(t, "DownloadCoverWorker exception");
            return Result.failure(new Data.Builder()
                    .putString("error", t.getMessage() != null ? t.getMessage() : "unknown error")
                    .build());
        }
    }
}
