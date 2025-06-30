package com.driot.bookplayer.services;

import static com.driot.bookplayer.utils.WorkFlow.setDownloadFinished;

import android.app.NotificationChannel;
import android.content.Intent;

import android.app.NotificationManager;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.driot.bookplayer.R;
import com.driot.bookplayer.utils.KanLogger;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.Objects;

public class DownloadJobService extends JobService {

    private static final int ID_NOTIFICATION_DOWNLOAD_INT = 1;
    public static final String ID_NOTIFICATION_DOWNLOAD_CHANNEL = "bookplayer_download_channel";


    private long lastUpdateTime = 0;
    private int lastPercentProgress = 0;
    private static final long MIN_UPDATE_INTERVAL = 250; // milliseconds

    public static volatile boolean isJobRunning = false;

    String audioBookTitle;

    @Override
    public boolean onStartJob(JobParameters params) {
        myLog("onStartJob");
        // Verify that job actually has to run (can be called after a crash !)
        if (isJobRunning) {
            myLog("Job already running");
            return false;
        }

        isJobRunning = true;

        String fileUrl = params.getExtras().getString("fileUrl");
        String destinationFolder = params.getExtras().getString("destinationFolder");
        audioBookTitle = params.getExtras().getString("audioBookTitle");

        myKeyFirebase("workflow", "download");
        myLogFirebase("download url : " + fileUrl);

        new Thread(() -> {

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
        myLog("onStopJob");
        isJobRunning = false;
        cancelDownloadNotification();
        return true; // Retry the job if it was killed
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        myLog("onTrimMemory - level = " + level);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        myLog("onLowMemory");
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        myLog("onConfigurationChanged " + newConfig);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        myLog("onDestroy");
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        myLog("onTaskRemoved " + rootIntent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        myLog("onUnbind " + intent);
        return super.onUnbind(intent);
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
        myLog("onRebind");
    }

    @Override
    public void onNetworkChanged(@NonNull JobParameters params) {
        super.onNetworkChanged(params);
        myLog("onNetworkChanged " + params);
    }

    @Override
    public void onTimeout(int startId) {
        super.onTimeout(startId);
        myLog("onTimeout " + startId);
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
                if (!isJobRunning) {
                    myLogE("================= Download cancelled by user.");
                    tellError(getString(R.string.Download_cancelled));
                    return false;
                }

                total += count;
                output.write(data, 0, count);

                if (fileLength > 0) {
                    int progress = (int) (total * 100 / fileLength);
                    //if (progress != lastProgress) {
                    //    lastProgress = progress;

                        String strSize = formatSizeMB(total) + " / " + formatSizeMB(fileLength);
                        showDownloadNotification(title, progress, strSize);
                    //}
                }
            }

            myLogI("Downloaded to " + destFile.getAbsolutePath());

            cancelDownloadNotification();

            return true;

        } catch (Exception e) {
            myLogE("Download failed: " + e.getMessage());
            tellError( getString(R.string.Download_failed) + e.getMessage());
            cancelDownloadNotification();
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
            String txtProgress = progress + "% " + getString(R.string.downloaded) + " (" + strSize + ")";
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, ID_NOTIFICATION_DOWNLOAD_CHANNEL)
                    .setContentTitle(getString(R.string.Downloading) + ": " + title)
                    .setContentText(txtProgress)
                    .setSmallIcon(R.drawable.ic_download_24dp)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOnlyAlertOnce(true)
                    .setProgress(100, progress, false);

            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.notify(ID_NOTIFICATION_DOWNLOAD_INT, builder.build());
            }

            //Update UI
            Intent intent = new Intent("BOOKPLAYER_DOWNLOAD_PROGRESS");
            intent.putExtra("progress", progress);
            intent.putExtra("txtProgress", txtProgress);
            intent.putExtra("audioBookTitle", audioBookTitle);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

            if (lastPercentProgress!=progress) myLogD("tellProgress : " + progress + " - " + txtProgress);

            lastUpdateTime = currentTime;
            lastPercentProgress = progress;
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
                    ID_NOTIFICATION_DOWNLOAD_CHANNEL,
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

    private void cancelDownloadNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(ID_NOTIFICATION_DOWNLOAD_INT); // Match the ID used in showDownloadNotification()
        }
    }

    //--- LOG --------------------------
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogInFile(String str) { KanLogger.myLogInFile(this.getClass().getName(), str); }
    private void myLogD(String str) { KanLogger.myLogD(this.getClass().getName(), str); }
    private void myLogI(String str) { KanLogger.myLogI(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
    private void myToast(String str) { KanLogger.myToast(this.getClass().getName(), str); }
    private void myToastE(String str) { KanLogger.myToastE(this.getClass().getName(), str); }
    private void myKeyFirebase(String strKey, String strValue) {KanLogger.myKeyFirebase(strKey, strValue);}
    private void myLogFirebase(String strLog) {KanLogger.myLogFirebase(strLog);}

}
