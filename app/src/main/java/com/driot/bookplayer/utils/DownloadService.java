package com.driot.bookplayer.utils;

import static com.driot.bookplayer.utils.Tonio.formatMem;
import static com.driot.bookplayer.utils.Tonio.getFileNameFromPath;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.LifecycleLoggingService;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;

public class DownloadService extends LifecycleLoggingService {

    public static final String EXTRA_URL = "file_url";
    public static final String EXTRA_DESTINATION_FOLDER = "destination_folder";
    public static final String ACTION_PROGRESS = "download_progress";
    public static final String EXTRA_PROGRESS_VALUE = "progress_value";
    public static final String EXTRA_PROGRESS_TEXT = "progress_text";
    public static final String ACTION_COMPLETE = "download_complete";
    public static final String ACTION_ERROR = "download_error";
    public static final String EXTRA_ERROR_STRING = "EXTRA_ERROR_STRING";
    public static boolean isBusy;

    private String fileUrl;
    private String destinationFolder;
    private String err_txt;

    private int lopperForLog;

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
        fileUrl = intent.getStringExtra("fileUrl");
        destinationFolder = intent.getStringExtra("destinationFolder");
        myLog("parseIntent() ..   " +
                "\n.    fileUrl = [" + fileUrl + "]" +
                "\n.    destinationFolder = [" + destinationFolder + "]"
        );
        if (fileUrl==null || destinationFolder== null) {
            myLogE("Null Intents !!");
            stopSelf();
        }
    }
    
    private boolean isInternetAvailable() {
        try {
            InetAddress ipAddr = InetAddress.getByName("www.google.com");
            //You can replace it with your name
            myLog("isInternetAvailable() - ipAdress=[" + ipAddr + "]");
            err_txt = "Internet DNS resolution failed";
            return !ipAddr.toString().equals("");

        } catch (SecurityException e) {
            myLogE("isInternetAvailable() - [" + e.getClass() + "] - [" +  e.getMessage() + "]");
            err_txt = "No internet Permission";
            return false;
        } catch (Exception e) {
            myLogE("isInternetAvailable() - [" + e.getClass() + "] - [" +  e.getMessage() + "]");
            err_txt = "Not connected to Internet";
            return false;
        }
    }
    
    public void init() {
        myLog("init()");
        isBusy = true;

        Thread backgroundThread = new Thread(() -> {
            downloadFile();
        });
        backgroundThread.start();
    }
/*
    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            isBusy = true;
            String fileUrl = intent.getStringExtra(EXTRA_URL);
            String destinationFolder = intent.getStringExtra(EXTRA_DESTINATION_FOLDER);
            downloadFile(fileUrl, destinationFolder);
        }
    }
    
 */

    private void downloadFile() {
        myLog("downloadFile()");
        InputStream input = null;
        FileOutputStream output = null;
        HttpURLConnection connection = null;
        String destinationFileName = getFileNameFromPath(fileUrl);
        lopperForLog=0;
        
        if (!isInternetAvailable()) {
            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(ACTION_ERROR).putExtra(EXTRA_ERROR_STRING, err_txt));
            myLogE(err_txt);
            isBusy = false;
            return;
        }

        try {
            URL url = new URL(fileUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.connect();
            myLog("trying to connect to server  - fileUrl = [\" + fileUrl + \"]");

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                tellError("downloading [" + fileUrl + "]\nServer returned HTTP " + connection.getResponseCode() + " " + connection.getResponseMessage());
                return;
            } else {
                myLog("connected to server  - fileUrl = [" + fileUrl + "]");
            }

            int fileLength = connection.getContentLength();
            myLog("File length = [" + fileLength + "]");
            input = new BufferedInputStream(connection.getInputStream());
            String destFullPath = destinationFolder + "/" + destinationFileName;
            if (!(checkFolderExist(destinationFolder))) return;
            output = new FileOutputStream(destFullPath);
            myLog("streams open - destination = [" + destFullPath + "]");

            byte[] data = new byte[4096];
            long total = 0;
            int count;
            int lastLoggedProgress = -1;
            while ((count = input.read(data)) != -1) {
                total += count;
                if (fileLength > 0) {
                    int progress = (int) (total * 100 / fileLength);
                    String progress_text = "book name : [" + destinationFileName + "]\n"
                            + "book size : " + formatMem(fileLength/1024/1024) + " Mo\n"
                            + progress + "%    *downloaded=" + formatMem(total/1024/1024,1) + " Mo";
                    //sendProgressUpdate(progress, progress_text);
                    if (progress != lastLoggedProgress) {
                        lastLoggedProgress = progress;
                        String singleLineLog = ("..." + progress + "%\n" + progress_text).replace("\n", " - ");
                        myLog(singleLineLog);
                    }
                    tellProgressNoLog(progress, progress_text);
                }
                output.write(data, 0, count);
            }
            myLog("File downloaded: [" + destinationFileName + "] into [" + destinationFolder + "]");
            sendDownloadComplete(destFullPath);
        } catch (Exception e) {
            err_txt = "Bookplayer Test Server not available ??" + "\n" + "Error downloading file [" + destinationFileName + "]" + "\n\n" + e.getMessage();
            //LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(ACTION_ERROR).putExtra(EXTRA_ERROR_STRING, err_txt));
            tellError(err_txt);
        } finally {
            try {
                if (output != null) {
                    output.close();
                }
                if (input != null) {
                    input.close();
                }
            } catch (IOException ignored) {
            }

            if (connection != null) {
                connection.disconnect();
            }
            isBusy = false;
            myLog("downloadFile() - END - reaching Finally.... => isBusy = false");
        }
    }

    private boolean checkFolderExist(String destinationFolderPath) {
        File destinationFolderFile = new File(destinationFolderPath);
        try {
            if (!destinationFolderFile.exists()) {
                if (!destinationFolderFile.mkdirs()) {
                    //tellError(getResources().getString(R.string.Error_Import_Creating_Folders) + " for path : " + destinationFolderPath);
                    myLogE(getResources().getString(R.string.Error_Import_Creating_Folders) + " for path : " + destinationFolderPath);
                    return false;
                } else {
                    myLog("folder created : [" +  destinationFolderPath + "]");
                }
            } else {
                myLog("okay - destination folder already exists");
            }
        } catch (Exception e) {
            //tellError(getResources().getString(R.string.Error_Import_Creating_Folders));
            myLogE(getResources().getString(R.string.Error_Import_Creating_Folders) + " - " + e.getMessage());
            return false;
        }
        return true;
    }
    private void sendProgressUpdate(int progress_value, String progress_text) {
        lopperForLog = lopperForLog + 1;
        Intent intent = new Intent(ACTION_PROGRESS);
        intent.putExtra(EXTRA_PROGRESS_VALUE, progress_value);
        intent.putExtra(EXTRA_PROGRESS_TEXT, progress_text);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        if (lopperForLog%50==0) myLog("...downloading " + progress_value + "%");
    }

    private void sendDownloadComplete(String downloadedFileFullPath) {
        myLog("sendDownloadComplete()");

        // update download activity (if displayed.. aka not close by user)
        // LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(ACTION_COMPLETE).putExtra(EXTRA_URL, destFullPath));
        downloadService_tellEnd(downloadedFileFullPath);
/*
        // start integration
        Uri uri = null;
        try {
            uri = Uri.fromFile(new File(destFullPath));
        } catch (Exception e) {
            myLogE("cannot build Uri for [" + destFullPath + "] - " + e.getMessage());
            e.printStackTrace();
        }
        if (uri != null) {
            try {
                /*
                myLog("Launching AddResourceService");
                // TODO : If screen is black or user have BookPlayer only in background, the integration will run only when focus back on Bookplayer... it should launch the service directly and not the activity
                //boolean isDestroyed = AddResourceActivity.getLifecycleObserver().isActivityDestroyed();  un truc du style, mais je viens d'y passer 30min, j'y arrive pas

                Intent intentRunIntegration = new Intent(this, AddResourceService.class);
                intentRunIntegration.putExtra("Uri", uri);
                intentRunIntegration.putExtra("type", "ZIP");
                startService(intentRunIntegration);

                Intent intentRunIntegration = new Intent(this, AddResourceActivity.class);
                intentRunIntegration.putExtra("Uri", uri);
                intentRunIntegration.putExtra("type", "ZIP");
                intentRunIntegration.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intentRunIntegration);


            } catch (Exception e) {
                myLogE("cannot start Integration " + e.getMessage());
            }
        }

 */
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
        //if (backgroundThread != null && backgroundThread.isAlive()) {
        //    backgroundThread.interrupt();
        //}
        stopSelf();
    }
    private void downloadService_tellEnd(String downloadedFileFullPath) {
        mCallBacks.downloadService_tellEnd(downloadedFileFullPath);
        myLog("killing Service");
        stopSelf();
    }
    public void tellProgress(int progressVal, String progressText) {
        mCallBacks.downloadService_tellProgress(progressText, progressVal);
    }
    public void tellProgressNoLog(int progressVal, String progressText) {
        mCallBacks.downloadService_tellProgressNoLog(progressText, progressVal);
    }

    
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
}