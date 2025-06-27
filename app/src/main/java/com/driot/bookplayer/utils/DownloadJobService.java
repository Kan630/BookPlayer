package com.driot.bookplayer.utils;

import static android.system.Os.mkdir;
import static com.driot.bookplayer.global.Var.FOLDER_DOWNLOAD;
import static com.driot.bookplayer.global.Var.FOLDER_UNZIPPED;
import static com.driot.bookplayer.utils.DownloadService.CHANNEL_ID_DOWNLOAD;
import static com.driot.bookplayer.utils.KanFiles.deleteFolderRecursive;
import static com.driot.bookplayer.utils.WorkFlow.setDownloadFinished;

import android.app.NotificationChannel;
import android.content.Intent;

import android.app.NotificationManager;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.driot.bookplayer.R;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public class DownloadJobService extends JobService {

    private long lastUpdateTime = 0;
    private static final long MIN_UPDATE_INTERVAL = 100; // milliseconds

    private boolean isJobRunning = false;

    String audioBookTitle;

    @Override
    public boolean onStartJob(JobParameters params) {
        isJobRunning = true;

        String fileUrl = params.getExtras().getString("fileUrl");
        String destinationFolder = params.getExtras().getString("destinationFolder");
        audioBookTitle = params.getExtras().getString("audioBookTitle");

        new Thread(() -> {

            //Ensure clean state
            String downloadDirPath = getFilesDir().getAbsolutePath() + "/" + FOLDER_DOWNLOAD;
            deleteFolderRecursive(downloadDirPath);
            File outputDir = new File(downloadDirPath);
            if (!outputDir.exists()) outputDir.mkdirs();

            boolean success = performDownload(fileUrl, destinationFolder, audioBookTitle);

            if (success) {
                myLog("download success => sending Broadcast - storing in SharedPrefs");

                //DEBUG
                String filePath = new File(destinationFolder, getFileNameFromPath(fileUrl)).getAbsolutePath();
                //String filePath = "/data/user/0/com.driot.bookplayer/files/download/Harry_Potter_1.zip";

                setDownloadFinished(this, filePath);

                Intent intent = new Intent("BOOKPLAYER_DOWNLOAD_FINISHED");
                intent.putExtra("downloadedFileFullPath", filePath);
                intent.putExtra("audioBookTitle", audioBookTitle);
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent); // LOCAL broadcast
            }
            jobFinished(params, !success); // retry if failed
        }).start();

        return true; // Work is still going
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        isJobRunning = false;
        return true; // Retry the job if it was killed
    }

    private boolean performDownload(String fileUrl, String destinationFolder, String title) {
        InputStream input = null;
        FileOutputStream output = null;
        HttpURLConnection connection = null;

        try {

            //Thread.sleep(10 * 60 * 1000);
            //Thread.sleep(5 * 1000);



            URL url = new URL(fileUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.connect();

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                myLogE("Server returned HTTP " + connection.getResponseCode());
                tellError("Server returned HTTP " + connection.getResponseCode());
                return false;
            }

            int fileLength = connection.getContentLength();
            File destFile = new File(destinationFolder, getFileNameFromPath(fileUrl));

            input = new BufferedInputStream(connection.getInputStream());
            output = new FileOutputStream(destFile);

            byte[] data = new byte[4096];
            int count;
            long total = 0;
            int lastProgress = -1;

            createNotificationChannel(); // Optional: ensure channel exists

            while ((count = input.read(data)) != -1) {
                if (!isJobRunning) return false;

                total += count;
                output.write(data, 0, count);

                if (fileLength > 0) {
                    int progress = (int) (total * 100 / fileLength);
                    if (progress != lastProgress) {
                        lastProgress = progress;

                        String strSize = formatSizeMB(total) + " / " + formatSizeMB(fileLength);
                        showDownloadNotification(title, progress, strSize);
                    }
                }
            }

            myLogI("Downloaded to " + destFile.getAbsolutePath());

            // Optional: cancel the notif at the end
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(1);

            return true;

        } catch (Exception e) {
            myLogE("Download failed: " + e.getMessage());
            tellError("Download failed: " + e.getMessage());
            return false;

        } finally {
            try {
                if (output != null) output.close();
                if (input != null) input.close();
            } catch (IOException ignored) {}

            if (connection != null) connection.disconnect();
        }
    }

    private String formatSizeMB(long bytes) {
        double mb = bytes / (1024.0 * 1024.0);
        return String.format(Locale.US, "%.1fMB", mb);
    }

    private String getFileNameFromPath(String url) {
        return Uri.parse(url).getLastPathSegment(); // same as your helper
    }

    private void showDownloadNotification(String title, int progress, String strSize) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime > MIN_UPDATE_INTERVAL || progress==100) {
            String txtProgress = progress + "% downloaded (" + strSize + ")";
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID_DOWNLOAD)
                    .setContentTitle("Downloading: " + title)
                    .setContentText(txtProgress)
                    .setSmallIcon(R.drawable.ic_download_24dp)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOnlyAlertOnce(true)
                    .setProgress(100, progress, false);

            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.notify(1, builder.build());
            }

            //Update UI
            Intent intent = new Intent("BOOKPLAYER_DOWNLOAD_PROGRESS");
            intent.putExtra("progress", progress);
            intent.putExtra("txtProgress", txtProgress);
            intent.putExtra("audioBookTitle", audioBookTitle);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

            lastUpdateTime = currentTime;
        }
    }

    private void tellError(String errorText) {
        Intent intent = new Intent("BOOKPLAYER_DOWNLOAD_ERROR");
        intent.putExtra("errorText", errorText);
        intent.putExtra("audioBookTitle", audioBookTitle);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelName = "Download Notifications";
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID_DOWNLOAD,
                    channelName,
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Used for download progress");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    //--- LOG --------------------------
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogInFile(String str) { KanLogger.myLogInFile(this.getClass().getName(), str); }
    private void myLogI(String str) { KanLogger.myLogI(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
    private void myToast(String str) { KanLogger.myToast(this.getClass().getName(), str); }
    private void myToastE(String str) { KanLogger.myToastE(this.getClass().getName(), str); }
}
