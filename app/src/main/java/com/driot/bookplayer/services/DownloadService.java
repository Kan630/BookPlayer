package com.driot.bookplayer.services;

import static com.driot.bookplayer.global.Var.FOLDER_DOWNLOAD;
import static com.driot.bookplayer.utils.KanFiles.deleteFolderRecursive;
import static com.driot.bookplayer.utils.Tonio.formatMemPadding;

import android.app.Service;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PersistableBundle;

import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.utils.log.LoggingService;

import java.io.File;
import java.net.InetAddress;

public class DownloadService extends LoggingService {

    public static boolean isBusy;

    private String fileUrl;
    private String destinationFolder;
    private String audioBookTitle;
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
        audioBookTitle = intent.getStringExtra("audioBookTitle");
        myLog("parseIntent() ..   " +
                "\n.    fileUrl = [" + fileUrl + "]" +
                "\n.    destinationFolder = [" + destinationFolder + "]" +
                "\n.    audioBookTitle = [" + audioBookTitle + "]"
        );
        if (fileUrl==null || destinationFolder== null) {
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
            tellError("No Internet");
            isBusy = false;
            return;
        }

        //Ensure clean state
        String downloadDirPath = getFilesDir().getAbsolutePath() + "/" + FOLDER_DOWNLOAD;
        deleteFolderRecursive(downloadDirPath);
        File outputDir = new File(downloadDirPath);
        if (!outputDir.exists()) outputDir.mkdirs();

        startDownloadJob(this, fileUrl, destinationFolder, audioBookTitle);
/*
        InputStream input = null;
        FileOutputStream output = null;
        HttpURLConnection connection = null;
        String destinationFileName = getFileNameFromPath(fileUrl);
        lopperForLog=0;
        

        String destFullPath;
        try {
            startForegroundNotification();
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
            destFullPath = destinationFolder + "/" + destinationFileName;
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
        } catch (Exception e) {
            err_txt = "Bookplayer Test Server not available ??"
                    + "\n" + "Error downloading file [" + destinationFileName + "] into [" + destinationFolder + "]"
                    + "\n\n" + e.getMessage();
            //LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(ACTION_ERROR).putExtra(EXTRA_ERROR_STRING, err_txt));
            destFullPath = null;
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
            stopForeground(true);
            myLog("downloadFile() - END - reaching Finally.... => isBusy = false");
        }
        if (destFullPath != null) {
            downloadService_tellEnd(destFullPath);
        } else {
            tellError("destination empty");
        }

 */
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
            myLogEE(e,"checkFolderExist");
            return false;
        }
        return true;
    }

    public void startDownloadJob(Context context, String fileUrl, String folderPath, String title) {
        ComponentName componentName = new ComponentName(context, DownloadJobService.class);
        PersistableBundle extras = new PersistableBundle();
        extras.putString("fileUrl", fileUrl);
        extras.putString("destinationFolder", folderPath);
        extras.putString("audioBookTitle", title);

        JobInfo.Builder builder = new JobInfo.Builder(123, componentName)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setExtras(extras);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setExpedited(true); // User-initiated, fast execution
        } else {
            builder.setOverrideDeadline(0); // For older devices, trigger immediately
        }

        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        int resultCode = scheduler.schedule(builder.build());

        if (resultCode == JobScheduler.RESULT_SUCCESS) {
            myLogI("Download job scheduled");
        } else {
            myLogE("Download job failed to schedule");
        }
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
        stopForeground(true);
        stopSelf();
    }
    private void downloadService_tellEnd(String downloadedFileFullPath) {
        mCallBacks.downloadService_tellEnd(downloadedFileFullPath);
        myLog("killing Service");
        stopForeground(true);
        stopSelf();
    }
    public void tellProgress(int progressVal, String progressText) {
        mCallBacks.downloadService_tellProgress(progressText, progressVal);
    }
    public void tellProgressNoLog(int progressVal, String progressText) {
        mCallBacks.downloadService_tellProgressNoLog(progressText, progressVal);
    }

}