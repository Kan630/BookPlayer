package com.driot.bookplayer.services;

import static com.driot.bookplayer.global.Var.FOREGROUND_DOWNLOAD_SERVICE_TAG;
import static com.driot.bookplayer.utils.Tonio.formatSizeMB;
import static com.driot.bookplayer.utils.Tonio.getFileNameFromUrl;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.objects.LoadBookTaskState;
import com.driot.bookplayer.utils.AnalyticsHelper;
import com.driot.bookplayer.utils.NetworkUtils;
import com.driot.bookplayer.utils.TaskStateManager;
import com.driot.bookplayer.utils.TaskUiManager;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.WorkFlow;
import com.driot.bookplayer.utils.log.LoggingService;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;

public class DownloadForegroundService extends LoggingService {

    public static final String CHANNEL_ID = "DownloadChannel";
    public static final int NOTIF_ID = 1;

    public static final String ACTION_PAUSE = "pause";
    public static final String ACTION_CANCEL = "cancel";
    public static final String ACTION_RESUME = "resume";

    private static final int MAX_RETRIES = 3;
    private static final long MIN_UPDATE_INTERVAL = 250;
    private static final long POLICY_TIMEOUT_MS = 30 * 60 * 1000;

    private String title;
    private String fileUrl;
    private String destinationFolder;
    private int retryCount;
    private long downloadStartTime;

    private long lastUpdateTime = 0;
    private int lastPercentProgress = 0;
    private long lastProgressBytes = 0;
    private long lastProgressTotal = 0;
    private boolean pauseForPolicy = false;
    private volatile boolean isPaused = false;
    private volatile boolean isCancelled = false;
    private Thread downloadThread;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIF_ID, buildNotification("Starting download…"));
        myLogD("onStartCommand ... " + intent.toString());

        LoadBookTaskState state = Pref.getLoadBookTaskState(this);
        if (state == null ) {
            myLogE("LoadBookTaskState == null");
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!state.onGoingLoading ) {
            myLogE("onGoingLoading = false");
            stopSelf();
            return START_NOT_STICKY;
        }


        fileUrl = state.downloadFileUrl;
        destinationFolder = state.downloadDestinationFolder;
        title = state.title;
        retryCount = state.downloadRetryCount;
        downloadStartTime = state.downloadStartTime;
        lastPercentProgress = state.progressPercent;

        String action = intent.getAction();
        myLog("action = " + action);

        if (ACTION_PAUSE.equals(action)) {
            isPaused = true;
            //TaskStateManager.markDownloadPaused(this, lastPercentProgress, lastProgressBytes, lastProgressTotal);
            TaskStateManager.markIsPaused(this);
            updateNotification(lastPercentProgress, getString(R.string.Download_paused_by_user));
            return START_NOT_STICKY;
        } else if (ACTION_CANCEL.equals(action)) {
            isCancelled = true;
            //TaskStateManager.markDownloadCancelled(this, lastPercentProgress, lastProgressBytes, lastProgressTotal);

            updateNotification(lastPercentProgress, getString(R.string.Download_cancelled_by_user));
            if (fileUrl != null) {
                File file = new File(destinationFolder, getFileNameFromUrl(fileUrl));
                if (file.exists()) file.delete();
            } else {
                myLogE("ACTION_CANCEL and fileUrl == null");
            }
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        } else if (ACTION_RESUME.equals(action)) {
            isPaused = false;
            TaskUiManager.getInstance().updateProgressText("resuming...");
            TaskStateManager.markDownloadResuming(this);
        }

        if (downloadThread != null && downloadThread.isAlive()) {
            myLogW("Download already in progress; ignoring new start command.");
            return START_NOT_STICKY;
        }

        downloadThread = new Thread(() -> {
            boolean success = performDownload(fileUrl, destinationFolder);
            stopForeground(true);
            stopSelf();
            downloadThread = null;

            if (success) {
                String filePath = new File(destinationFolder, getFileNameFromUrl(fileUrl)).getAbsolutePath();
                myLog("Download success => sending Broadcast - storing in SharedPrefs: " + filePath);
                WorkFlow.setDownloadFinished(this, filePath);

                Intent doneIntent = new Intent("BOOKPLAYER_DOWNLOAD_FINISHED");
                doneIntent.putExtra("downloadedFileFullPath", filePath);
                doneIntent.putExtra("audioBookTitle", title);
                LocalBroadcastManager.getInstance(this).sendBroadcast(doneIntent);
            } else if (!isPaused && !isCancelled) {
                String errorMsg = getString(R.string.Download_failed);
                myLogE(errorMsg);

                Intent errorIntent = new Intent("BOOKPLAYER_DOWNLOAD_ERROR");
                errorIntent.putExtra("errorText", errorMsg);
                errorIntent.putExtra("audioBookTitle", title);
                LocalBroadcastManager.getInstance(this).sendBroadcast(errorIntent);

                if (retryCount < MAX_RETRIES) {
                    LoadBookTaskState retryState = Pref.getLoadBookTaskState(this);
                    retryState.downloadRetryCount = retryCount + 1;
                    Pref.setLoadBookTaskState(this, retryState);

                    Data inputData = new Data.Builder()
                            .putString("stateRef", "use_shared_prefs") // Optional marker
                            .build();

                    OneTimeWorkRequest retryRequest = new OneTimeWorkRequest.Builder(DownloadRetryWorker.class)
                            .setInputData(inputData)
                            .addTag(FOREGROUND_DOWNLOAD_SERVICE_TAG)
                            .build();

                    WorkManager.getInstance(this)
                            .enqueueUniqueWork(FOREGROUND_DOWNLOAD_SERVICE_TAG + "_" + fileUrl.hashCode(), androidx.work.ExistingWorkPolicy.REPLACE, retryRequest);
                }
            }
        });

        downloadThread.start();

        return START_NOT_STICKY;
    }

    private boolean performDownload(String fileUrl, String destinationFolder) {
        myLog("performDownload  -  " + fileUrl + " => " + destinationFolder);
        if (fileUrl == null || destinationFolder == null) {
            myLogE("Null arguments");
            return false;
        }
        InputStream input = null;
        FileOutputStream output = null;
        HttpURLConnection connection = null;

        try {
            if (!NetworkUtils.isNetworkAvailable(this)) {
                TellHimWhyPause("No internet connection");
                return false;
            }

            boolean enforceManual = System.currentTimeMillis() - downloadStartTime < POLICY_TIMEOUT_MS;
            NetworkUtils.NetworkPolicyManual manualPolicy = Option.getNetworkPolicyManualDownload();
            NetworkUtils.NetworkPolicyAuto autoPolicy = Option.getNetworkPolicyAutoDownload();

            boolean allow = enforceManual
                    ? (manualPolicy == NetworkUtils.NetworkPolicyManual.NEVER_ASK ||
                    (manualPolicy == NetworkUtils.NetworkPolicyManual.ASK_IF_NOT_WIFI && NetworkUtils.isWifiConnected(this)) ||
                    (manualPolicy == NetworkUtils.NetworkPolicyManual.ASK_IF_NOT_UNMETERED && NetworkUtils.isUnmeteredConnected(this)))
                    : (autoPolicy == NetworkUtils.NetworkPolicyAuto.ANY ||
                    (autoPolicy == NetworkUtils.NetworkPolicyAuto.WIFI && NetworkUtils.isWifiConnected(this)) ||
                    (autoPolicy == NetworkUtils.NetworkPolicyAuto.UNMETERED && NetworkUtils.isUnmeteredConnected(this)));

            if (!allow) { //TODO allow process to start again automatically when user get free internet
                TellHimWhyPause(getString(R.string.Download_paused_due_to_network_policy));
                return false;
                /*
                //pauseForPolicy = true;
                isPaused = true;
                updateNotification(lastPercentProgress, lastProgressBytes, lastProgressTotal, getString(R.string.Download_paused_due_to_network_policy));
                TaskStateManager.markDownloadPausedDueToNetworkPolicy(this, lastPercentProgress, lastProgressBytes, lastProgressTotal);
                myLog("Paused due to network policy");
                return false;

                 */
            }

            String fileName = getFileNameFromUrl(fileUrl);
            myLog("file name " + fileName);

            File destFile = new File(destinationFolder, fileName);
            File parentFolder = destFile.getParentFile();
            if (parentFolder != null && !parentFolder.exists()) {
                myLogW("Creating parent folder: " + parentFolder.getAbsolutePath());
                boolean created = parentFolder.mkdirs();
                if (!created) {
                    myLogE("Failed to create destination folder: " + parentFolder.getAbsolutePath());
                    return false;
                }
            }

            long downloaded = destFile.exists() ? destFile.length() : 0;
            myLog("already downloaded " + Tonio.getReadableSize(downloaded));

            AnalyticsHelper.tellAnalyticsManualDownload(this, fileUrl, destinationFolder, downloaded);

            URL url = new URL(fileUrl);
            connection = (HttpURLConnection) url.openConnection();
            if (downloaded > 0) {
                connection.setRequestProperty("Range", "bytes=" + downloaded + "-");
            }
            connection.connect();

            if ((connection.getResponseCode() != HttpURLConnection.HTTP_OK) &&
                    (connection.getResponseCode() != HttpURLConnection.HTTP_PARTIAL)) {
                myLogE("Server returned HTTP " + connection.getResponseCode());
                return false;
            }

            int fileLength = connection.getContentLength() + (int) downloaded;
            input = new BufferedInputStream(connection.getInputStream());
            output = new FileOutputStream(destFile, true);

            byte[] data = new byte[4096];
            int count;
            long total = downloaded;

            while ((count = input.read(data)) != -1) {
                while (isPaused && !isCancelled) {
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        myLogEE(e, "Paused wait interrupted");
                    }
                }

                if (isCancelled) {
                    myLogW("Download cancelled during execution");
                    return false;
                }

                total += count;
                output.write(data, 0, count);

                if (fileLength > 0) {
                    int progress = (int) (total * 100 / fileLength);
                    if (System.currentTimeMillis() - lastUpdateTime > MIN_UPDATE_INTERVAL || progress == 100) {
                        String strSize = formatSizeMB(total) + " / " + formatSizeMB(fileLength);
                        updateNotification(progress, strSize);
                        if (lastPercentProgress != progress) {
                            myLogD("tellProgress : " + progress + " - " + strSize);
                            /*
                        lastPercentProgress = progress;
                        lastProgressBytes = total;
                        lastProgressTotal = fileLength;
                            TaskStateManager.updateProgressAndNotify(this, lastPercentProgress, lastProgressBytes, lastProgressTotal, "downloading");
                             */
                        }
                        lastPercentProgress = progress;
                        lastProgressBytes = total;
                        lastProgressTotal = fileLength;
                        lastUpdateTime = System.currentTimeMillis();
                        TaskStateManager.updateTaskStateAndNotifyUiOfDownloadProgress(this, lastPercentProgress, lastProgressBytes, lastProgressTotal);
                    }
                }
            }

            myLogI("Downloaded to " + destFile.getAbsolutePath());
            cancelDownloadNotification();
            return true;

        } catch (UnknownHostException e) {
            TellHimWhyPause("No internet connection");
            return false;
        } catch (SocketException e) {
            TellHimWhyPause("Connection aborted");
            return false;
        } catch (IOException e) {
            TellHimWhyPause("IO error");
            return false;
        } catch (Exception e) {
            myLogEE(e,"Unexpected error");
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

    private void updateNotification(int progress, String strText) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.Downloading) + ": " + title)
                .setContentText(strText)
                .setSmallIcon(R.drawable.ic_download_action_24)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setProgress(100, progress, false);

        NotificationManagerCompat manager = NotificationManagerCompat.from(this);
        if (manager.areNotificationsEnabled()) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.notify(NOTIF_ID, builder.build());
            }
        }
/*
        Intent intent = new Intent("BOOKPLAYER_DOWNLOAD_PROGRESS");
        intent.putExtra("progress", progress);
        intent.putExtra("txtProgress", strText);
        intent.putExtra("audioBookTitle", title);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

 */
    }

    private void cancelDownloadNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(NOTIF_ID);
        }
    }

    private Notification buildNotification(String text) {
        createNotificationChannel();
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("BookPlayer")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_download_action_24)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Download Channel",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void TellHimWhyPause(String whyPause) {
        isPaused = true;
        myLogE(whyPause);
        TaskUiManager.getInstance().updateProgressText(whyPause);
        TaskStateManager.markIsPaused(this);
    }


}
