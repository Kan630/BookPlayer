package com.driot.bookplayer.services;

import static com.driot.bookplayer.global.Var.FOREGROUND_DOWNLOAD_SERVICE_TAG;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.objects.LoadBookTaskState;
import com.driot.bookplayer.utils.log.LoggingService;

import java.net.InetAddress;

public class DownloadService extends LoggingService {

    public static boolean isBusy;

    private String err_txt;

    private final IBinder binder = new DownloadService.DownloadServiceBackgroundBinder();
    Callbacks mCallBacks;

    // Callbacks
    //-----------------------------
    public interface Callbacks {
        void downloadService_tellProgress(String progressText, int progressVal);
        void downloadService_tellProgressNoLog(String progressText, int progressVal);
        void downloadService_tellError(String errorText);
        void downloadService_tellEnd(String downloadedFileFullPath);
    }
    public void registerClient(Service service){
        this.mCallBacks = (DownloadService.Callbacks) service;
    }
    // binder
    //-----------------------------
    public class DownloadServiceBackgroundBinder extends Binder {
        public DownloadService getService() {
            return DownloadService.this;
        }
    }
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        myLog("onBind()    intent:" + intent.getDataString());
        parseIntent(intent);
        return binder;
    }
    private void parseIntent(Intent intent) {
        String fileUrl = intent.getStringExtra("fileUrl");
        String destinationFolder = intent.getStringExtra("destinationFolder");
        String audioBookTitle = intent.getStringExtra("audioBookTitle");
        myLog("parseIntent() ..   " +
                "\n.    fileUrl = [" + fileUrl + "]" +
                "\n.    destinationFolder = [" + destinationFolder + "]" +
                "\n.    audioBookTitle = [" + audioBookTitle + "]"
        );
        if (fileUrl ==null || destinationFolder == null) {
            myLogE("Null Intents !!");
            stopSelf();
        }
    }
    @Override
    public void onCreate() {
        myLog("onCreate()");
    }

    private boolean isInternetAvailable() {
        try {
            InetAddress ipAddr = InetAddress.getByName("www.google.com");
            //You can replace it with your name
            myLog("isInternetAvailable() - ipAdress=[" + ipAddr + "]");
            err_txt = "Internet DNS resolution failed";
            return !ipAddr.toString().equals("");

        } catch (SecurityException e) {
            myLogEE(e,"isInternetAvailable() - [" + e.getClass() + "]");
            err_txt = "No internet Permission";
            return false;
        } catch (Exception e) {
            myLogEE(e,"isInternetAvailable() - [" + e.getClass() + "]");
            err_txt = "Not connected to Internet";
            return false;
        }
    }
    
    public void init() {
        myLog("init()");
        isBusy = true;
        Thread backgroundThread = new Thread(this::downloadFile);
        backgroundThread.start();
    }

    private void downloadFile() {
        myLog("downloadFile()");

        if (!isInternetAvailable()) {
            tellError(err_txt);
            isBusy = false;
            return;
        }

        /*
        downloadDirPath = StorageHelper.getDownloadFolder(this);
        deleteFolderRecursive(downloadDirPath);
        File outputDir = new File(downloadDirPath);
        if (!outputDir.exists()) outputDir.mkdirs();

         */

        LoadBookTaskState state = Pref.getLoadBookTaskState();
        if (state == null || state.downloadFileUrl == null || state.downloadDestinationFolder == null || state.title == null) {
            tellError("Invalid LoadBookTaskState");
            isBusy = false;
            return;
        }

        long startTime = System.currentTimeMillis();
        state.downloadStartTime = startTime;
        state.downloadRetryCount = 0;
        Pref.setLoadBookTaskState(state);

        Data inputData = new Data.Builder()
                .putString("stateRef", "use_shared_prefs")
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(DownloadRetryWorker.class)
                .setInputData(inputData)
                .addTag(FOREGROUND_DOWNLOAD_SERVICE_TAG)
                .build();

        WorkManager.getInstance(this)
                //.enqueueUniqueWork(FOREGROUND_DOWNLOAD_SERVICE_TAG + "_" + state.downloadFileUrl.hashCode(), androidx.work.ExistingWorkPolicy.REPLACE, request);
                .enqueueUniqueWork(FOREGROUND_DOWNLOAD_SERVICE_TAG, androidx.work.ExistingWorkPolicy.REPLACE, request);

        myLogI("Download work enqueued with WorkManager for: " + state.title);
    }


    //////////////////////////////////////////////////////////////////////////////////////////
    // Callbacks
    //////////////////////////////////////////////////////////////////////////////////////////

    private void tellError(String errorText) {
        myLogE(errorText);
        if (mCallBacks != null) {
            mCallBacks.downloadService_tellError(errorText);
        }
        myLog("killing Service");
        stopForeground(true);
        stopSelf();
    }

}