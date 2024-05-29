package com.driot.bookplayer.utils;

import static com.driot.bookplayer.global.Var.FOLDER_UNZIPPED;
import static com.driot.bookplayer.utils.Tonio.formatMem;
import static com.driot.bookplayer.utils.Tonio.getFileNameFromPath;
import static com.driot.bookplayer.utils.Tonio.stripFileName;

import android.app.IntentService;
import android.content.Intent;
import android.os.Environment;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.driot.bookplayer.R;
import com.driot.tonylib.KanLogger;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class DownloadService extends IntentService {

    public static final String EXTRA_URL = "file_url";
    public static final String EXTRA_DESTINATION_FOLDER = "destination_folder";
    public static final String ACTION_PROGRESS = "download_progress";
    public static final String EXTRA_PROGRESS_VALUE = "progress_value";
    public static final String EXTRA_PROGRESS_TEXT = "progress_text";
    public static final String ACTION_COMPLETE = "download_complete";
    public DownloadService() {
        super("DownloadService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            String fileUrl = intent.getStringExtra(EXTRA_URL);
            String destinationFolder = intent.getStringExtra(EXTRA_DESTINATION_FOLDER);
            downloadFile(fileUrl, destinationFolder);
        }
    }

    private void downloadFile(String fileUrl, String destinationFolder) {
        InputStream input = null;
        FileOutputStream output = null;
        HttpURLConnection connection = null;
        String destinationFileName = getFileNameFromPath(fileUrl);

        try {
            URL url = new URL(fileUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.connect();
            myLog("trying to connect to server  - fileUrl = [" + fileUrl + "]");

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                myLogE("Server returned HTTP " + connection.getResponseCode() + " " + connection.getResponseMessage());
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
            while ((count = input.read(data)) != -1) {
                total += count;
                if (fileLength > 0) {
                    int progress = (int) (total * 100 / fileLength);
                    //String progress_text = "book name : [" + destinationFileName + "]\n"
                    String progress_text = "book name : [" + destinationFileName + "]\n"
                            + "book size : " + formatMem(fileLength/1024/1024) + " Mo\n"
                            + progress + "%    *copied=" + formatMem(total/1024/1024,1) + " Mo";
                    sendProgressUpdate(progress, progress_text);
                }
                output.write(data, 0, count);
            }
            sendDownloadComplete(destFullPath);
            myLog("File downloaded: [" + destinationFileName + "] into [" + destinationFolder + "]");
        } catch (Exception e) {
            myLogE("Error downloading file [" + destinationFileName + "]" + e.getMessage());
            e.printStackTrace();
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
            e.printStackTrace();
            return false;
        }
        return true;
    }
    private void sendProgressUpdate(int progress_value, String progress_text) {
        Intent intent = new Intent(ACTION_PROGRESS);
        intent.putExtra(EXTRA_PROGRESS_VALUE, progress_value);
        intent.putExtra(EXTRA_PROGRESS_TEXT, progress_text);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void sendDownloadComplete(String destFullPath) {
        myLog("sendDownloadComplete()");
        Intent intent = new Intent(ACTION_COMPLETE);
        intent.putExtra(EXTRA_URL, destFullPath);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
}