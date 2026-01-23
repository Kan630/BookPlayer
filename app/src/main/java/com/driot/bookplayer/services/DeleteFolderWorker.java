package com.driot.bookplayer.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.content.pm.ServiceInfo;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.WorkerParameters;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Episode;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.helpers.FileHelper;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.utils.log.LoggingWorker;

import java.io.File;
import java.util.List;

public class DeleteFolderWorker extends LoggingWorker {

    public static final String KEY_FOLDER_ID = "key_folder_id";
    public static final String KEY_FOLDER_NAME = "key_folder_name";

    private static final String CHANNEL_ID = "delete_channel";
    private static final int NOTIF_ID = 42007;

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

        // Optionally enter foreground:
        // setForegroundEarly(buildForegroundInfo());

        try {
            // Make sure foreground is set BEFORE any long/opportunistic crash point
            setForegroundAsync(createForegroundInfo(folderName != null ? folderName : "Deleting"));
        } catch (Exception e) {
            myLogEE(e, "setForegroundAsync ko");
            return Result.failure(new Data.Builder()
                    .putString("error", "Failed to start foreground: " + e.getMessage())
                    .putString("stack", stackToString(e))
                    .build());
        }
        myLogD("init done");

        try {
            Context appCtx = getApplicationContext();
            AppDatabase db = AppDatabase.getDatabase(appCtx);

            String folderPath = db.zikFileDao().getFolderPath((int) folderId);
            if (!eraseFolderAndFiles(appCtx, folderPath)) {
                myLogEE(null, "Disk delete error");
            } else {
                myLogD("Disk delete done");
            }

            Podcast podcast = db.podcastDao().getPodcastByFolderId(folderId);
            if (podcast == null) {
                Folder f = db.folderDao().getById(folderId); // ensure DAO exists
                if (f != null)
                    ImageHelper.deleteImage(appCtx, f);
            }

            List<ZikFile> zikFileList = db.zikFileDao().getZikFiles(folderId);
            for (ZikFile zikFile : zikFileList) {
                Episode episode = db.episodeDao().getByZikFileId(zikFile.getId());
                if (episode != null) {
                    episode.date_delete = System.currentTimeMillis();
                    db.episodeDao().update(episode);
                    myLogD("Podcast Episode date deleted set for " + episode.title);
                }
            }

            db.folderDao().delete((int) folderId);
            db.zikFileDao().deleteAllZikFilesInFolder((int) folderId);
            com.driot.bookplayer.helpers.PodcastHelper.cancelAutoDownload(appCtx, (int) folderId);

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

    private ForegroundInfo createForegroundInfo(String folderName) {
        String title = getApplicationContext().getString(R.string.app_name);
        String text = "Deleting \"" + folderName + "\"…";

        NotificationManager nm = (NotificationManager) getApplicationContext()
                .getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Deletions", NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(ch);

        int smallIcon = R.drawable.ic_delete_24;
        if (smallIcon == 0)
            smallIcon = R.mipmap.ic_launcher;

        Notification notif = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(smallIcon)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();

        int svcType = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ requires the explicit type; Android 14/15 enforces it
            svcType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
        }

        // IMPORTANT: use the 3-arg constructor so WorkManager starts FGS with the
        // declared type
        return new ForegroundInfo(NOTIF_ID, notif, svcType);
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
