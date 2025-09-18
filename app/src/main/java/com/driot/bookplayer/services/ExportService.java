package com.driot.bookplayer.services;

import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.os.Parcelable;

import androidx.core.content.FileProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.ExportActivity;
import com.driot.bookplayer.utils.log.LoggingService;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ExportService extends LoggingService {

    private int totalFiles = 0;
    private long totalSize = 0;

    private long lastUpdateTime = 0;
    private static final long MIN_UPDATE_INTERVAL = 100; // milliseconds

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String folderPath = intent.getStringExtra(ExportActivity.EXTRA_FOLDER_PATH);
        if (folderPath != null) {
            new Thread(() -> zipFolder(folderPath)).start();
        }
        return START_NOT_STICKY;
    }

    private void zipFolder(String folderPath) {
        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) return;

        File[] files = folder.listFiles((f) -> f.isFile());
        totalFiles = files == null ? 0 : files.length;
        totalSize = 0;
        if (files != null) {
            for (File file : files) {
                totalSize += file.length();
            }
        }

        File output = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "BookplayerExport_" + folder.getName() + ".zip");

        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output)))) {
            long zippedSoFar = 0;
            int currentIndex = 0;
            for (File file : files) {
                currentIndex++;
                sendProgress(file.getName(), zippedSoFar, currentIndex);
                FileInputStream fis = new FileInputStream(file);
                ZipEntry entry = new ZipEntry(file.getName());
                zos.putNextEntry(entry);

                byte[] buffer = new byte[4096];
                int length;
                while ((length = fis.read(buffer)) != -1) {
                    zos.write(buffer, 0, length);
                    zippedSoFar += length;
                    sendProgress(file.getName(), zippedSoFar, currentIndex);
                }
                fis.close();
            }

            zos.flush();
            sendProgress(getString(R.string.Export_done), totalSize, totalFiles);

        } catch (IOException e) {
            myToastEE(e, getString(R.string.Export_error) + ": " + e.getMessage());
        }

        long minSize = 1024; // 1 KB

        if (output.exists() && output.length() >= minSize) {
            Uri zipUri = FileProvider.getUriForFile(
                    this,
                    "com.driot.bookplayer.FileProvider",
                    output
            );
            Intent doneIntent = new Intent("EXPORT_DONE");
            doneIntent.putExtra("zipUri", zipUri);
            doneIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            LocalBroadcastManager.getInstance(this).sendBroadcast(doneIntent);

        } else {
            myLogEE(null,"Export failed or incomplete: file too small or missing.");

            // Clean up the bad file
            if (output.exists() && output.length() < minSize) {
                boolean deleted = output.delete();
                myLog("Deleted bad ZIP: " + deleted);
            }

            // Inform UI of failure
            Intent failIntent = new Intent("EXPORT_FAIL");
            failIntent.putExtra("zipUri", (Parcelable) null);
            LocalBroadcastManager.getInstance(this).sendBroadcast(failIntent);
        }

/*
// Notification => To intrusive, Toast Okay, but right now Sharing opens...so useless
        String notifTxt = getString(R.string.Export_notification_text) + " (" + output.getName() + ")";
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                NotificationManagerCompat.from(this).areNotificationsEnabled()) {

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "export_channel")
                    .setSmallIcon(R.drawable.ic_download_24dp)
                    .setContentTitle(getString(R.string.Export_notification_title))
                    .setContentText(notifTxt)
                    .setPriority(NotificationCompat.PRIORITY_HIGH);

            NotificationManagerCompat.from(this).notify(1001, builder.build());
        } else {
            myToast(notifTxt);
        }
 */
    }

    private void sendProgress(String currentTrack, long zippedSoFar, int fileIndex) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime > MIN_UPDATE_INTERVAL || zippedSoFar == totalSize) {
            int percent = totalSize == 0 ? 0 : (int) ((zippedSoFar * 100) / totalSize);
            String display = "Track " + fileIndex + " of " + totalFiles +
                    "\n" + (zippedSoFar / 1024 / 1024) + " MB / " + (totalSize / 1024 / 1024) + " MB";

            Intent intent = new Intent("EXPORT_PROGRESS");
            intent.putExtra("currentTrack", currentTrack);
            intent.putExtra("progressPercent", percent);
            intent.putExtra("displayText", display);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
            lastUpdateTime = currentTime;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
