package com.driot.bookplayer.services;

import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.os.Parcelable;

import androidx.core.content.FileProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.driot.bookplayer.BuildConfig;
import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.ExportActivity;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingService;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ExportService extends LoggingService {

    private int totalFiles = 0;
    private long totalSize = 0;

    private long lastUpdateTime = 0;
    private static final long MIN_UPDATE_INTERVAL = 100; // ms

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Folder folder = intent.getParcelableExtra(Intents.EXTRA_BOOK_SOURCE_FOLDER);
        if (folder == null) {
            myLogEE(null, "folder is null");
            stopSelf();
            return START_NOT_STICKY;
        }
        String destFileFullPath = intent.getStringExtra(ExportActivity.EXTRA_DEST_FILE_FULL_PATH); // legacy path
        String destUriStr = intent.getStringExtra(ExportActivity.EXTRA_DEST_URI);                  // SAF Uri (preferred)

        myLogD("------------------------------------------------------------------------------------------------");
        myLog("folderPath: " + folder.getName());
        myLog("folderPath: " + folder.getPath());
        myLog("destFileFullPath: " + destFileFullPath);
        myLog("destUriStr: " + destUriStr);
        myLogD("------------------------------------------------------------------------------------------------");

        if (folder.getPath() != null) {
            new Thread(() -> zipFolder(folder, destFileFullPath, destUriStr)).start();
        } else {
            myLogEE(null, "path is null");
        }
        return START_NOT_STICKY;
    }

    private void zipFolder(Folder folder, String destFileFullPath, String destUriStr) {
        String folderPath = folder.getPath();
        File fileFolder = new File(folderPath);
        if (!fileFolder.exists() || !fileFolder.isDirectory()) {
            myLogEE(null, "Export aborted: invalid folderPath: " + folderPath);
            sendFail( "invalid folder path: " + folderPath);
            return;
        }

        File[] audioFiles = fileFolder.listFiles(File::isFile);
        List<File> filesList = new ArrayList<>();
        if (audioFiles == null || audioFiles.length == 0) {
            myLogEE(null, "no audio file found in folder");
            sendFail("no audio file found in folder");
            return;
        }
        Collections.addAll(filesList, audioFiles);

        totalFiles = filesList.size();
        totalSize = 0L;

        for (File f : filesList) {
            totalSize += f.length();
        }
        myLog("total audio size = " + Tonio.getReadableSize(totalSize));

        String pathImage = folder.image;
        if (pathImage != null && !pathImage.isEmpty()) {
            File fileImage = new File(pathImage);
            if (fileImage.exists()) {
                myLog("image found : " + Tonio.getFileNameFromPath(pathImage));
                filesList.add(fileImage);
            }
        }

        boolean useSaf = (destUriStr != null);
        Uri safDestUri = null;
        File outputFile = null;

        // Prepare output stream (SAF or legacy path)
        try {
            OutputStream rawOut;
            if (useSaf) {
                safDestUri = Uri.parse(destUriStr);
                rawOut = getContentResolver().openOutputStream(safDestUri, "w");
                if (rawOut == null) throw new IOException("openOutputStream returned null for " + safDestUri);
            } else {
                if (destFileFullPath == null) {
                    throw new IOException("Destination path is null (no SAF Uri, no legacy path)");
                }
                outputFile = new File(destFileFullPath);
                File parent = outputFile.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                rawOut = new FileOutputStream(outputFile);
            }

            try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(rawOut))) {
                long zippedSoFar = 0;
                int currentIndex = 0;

                for (File file : filesList) {
                    currentIndex++;
                    sendProgress(file.getName(), zippedSoFar, currentIndex);

                    try (FileInputStream fis = new FileInputStream(file)) {
                        ZipEntry entry = new ZipEntry(file.getName());
                        zos.putNextEntry(entry);

                        byte[] buffer = new byte[4096];
                        int length;
                        while ((length = fis.read(buffer)) != -1) {
                            zos.write(buffer, 0, length);
                            zippedSoFar += length;
                            sendProgress(file.getName(), zippedSoFar, currentIndex);
                        }
                        zos.closeEntry();
                    }
                }


                zos.flush();
                sendProgress(getString(R.string.Export_done_Excl), totalSize, totalFiles);
            }

            // Success: broadcast EXPORT_DONE with the right Uri
            if (useSaf) {
                // SAF: we already have the Uri
                Intent done = new Intent("EXPORT_DONE");
                done.putExtra("zipUri", safDestUri);
                done.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                LocalBroadcastManager.getInstance(this).sendBroadcast(done);
            } else {
                // Legacy path: verify min size, wrap with FileProvider
                long minSize = 1024; // 1 KB
                if (outputFile != null && outputFile.exists() && outputFile.length() >= minSize) {
                    Uri zipUri = FileProvider.getUriForFile(
                            this,
                            BuildConfig.APPLICATION_ID + ".FileProvider",
                            outputFile
                    );
                    Intent done = new Intent("EXPORT_DONE");
                    done.putExtra("zipUri", zipUri);
                    done.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(done);
                } else {
                    myLogEE(null, "Export failed or incomplete: file too small or missing.");
                    if (outputFile != null && outputFile.exists() && outputFile.length() < minSize) {
                        boolean deleted = outputFile.delete();
                        myLog("Deleted bad ZIP: " + deleted);
                    }
                    sendFail("Export failed or incomplete: file too small or missing.");
                }
            }

        } catch (Throwable e) {
            myLogEE(e,"export general error");
            e.printStackTrace();
            sendFail(getString(R.string.Export_error) + ": " + e.getMessage());
        }
    }

    private void sendFail(String errMessage) {
        Intent i = new Intent("EXPORT_FAIL");
        i.putExtra("zipUri", (Parcelable) null);
        i.putExtra("displayText", errMessage);
        LocalBroadcastManager.getInstance(this).sendBroadcast(i);
    }

    private void sendProgress(String currentTrack, long zippedSoFar, int fileIndex) {
        long now = System.currentTimeMillis();
        if (now - lastUpdateTime > MIN_UPDATE_INTERVAL || zippedSoFar == totalSize) {
            int percent = (totalSize == 0) ? 0 : (int) ((zippedSoFar * 100) / totalSize);
            String display = "Track " + fileIndex + " of " + totalFiles +
                    "\n" + (zippedSoFar / 1024 / 1024) + " MB / " + (totalSize / 1024 / 1024) + " MB";

            Intent intent = new Intent("EXPORT_PROGRESS");
            intent.putExtra("currentTrack", currentTrack);
            intent.putExtra("progressPercent", percent);
            intent.putExtra("displayText", display);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
            lastUpdateTime = now;
        }
        // (Optional) you can promote this to a foreground service with a progress notification if exports are large.
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
