package com.driot.bookplayer.utils;

import com.driot.bookplayer.R;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.driot.bookplayer.activities.ExportActivity;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ExportService extends Service {

    private int totalFiles = 0;
    private long totalSize = 0;

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
                folder.getName() + ".zip");

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
            sendProgress("Done", totalSize, totalFiles);

        } catch (IOException e) {
            e.printStackTrace();
        }

        Intent doneIntent = new Intent("EXPORT_DONE");
        doneIntent.putExtra("zipUri", Uri.fromFile(output));
        LocalBroadcastManager.getInstance(this).sendBroadcast(doneIntent);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "export_channel")
                .setSmallIcon(R.drawable.ic_download_24dp)
                .setContentTitle("Export complete")
                .setContentText("Audiobook exported to ZIP")
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        try {
            notificationManager.notify(1001, builder.build());
        } catch (Exception e) {
            myLogE("notificationManager - no right ??   - Exception : " + e.getMessage());
        }

    }

    private void sendProgress(String currentTrack, long zippedSoFar, int fileIndex) {
        int percent = totalSize == 0 ? 0 : (int) ((zippedSoFar * 100) / totalSize);
        String display = "Track " + fileIndex + " of " + totalFiles +
                "\n" + (zippedSoFar / 1024) + " KB / " + (totalSize / 1024) + " KB";

        Intent intent = new Intent("EXPORT_PROGRESS");
        intent.putExtra("currentTrack", currentTrack);
        intent.putExtra("progressPercent", percent);
        intent.putExtra("displayText", display);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    //--- LOG --------------------------
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogD(String str) { KanLogger.myLogD(this.getClass().getName(), str); }
    private void myLogI(String str) { KanLogger.myLogI(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
}
