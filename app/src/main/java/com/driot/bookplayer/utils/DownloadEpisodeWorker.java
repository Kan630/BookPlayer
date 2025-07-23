package com.driot.bookplayer.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class DownloadEpisodeWorker extends Worker {
    public static final String KEY_URL = "url";
    public static final String KEY_DEST_PATH = "dest_path";

    public DownloadEpisodeWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String urlStr = getInputData().getString(KEY_URL);
        String destPath = getInputData().getString(KEY_DEST_PATH);

        if (urlStr == null || destPath == null) {
            myLogE("Missing input data");
            return Result.failure();
        }

        HttpURLConnection conn = null;
        try {
            File finalFile = new File(destPath);
            if (finalFile.exists() && finalFile.length() > 1000) {
                myLogW("Already downloaded: " + destPath);
                return Result.success();
            }

            File tempFile = new File(destPath + ".part");

            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.connect();

            try (InputStream in = new BufferedInputStream(conn.getInputStream());
                 FileOutputStream out = new FileOutputStream(tempFile)) {

                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                }
            }

            if (finalFile.exists()) finalFile.delete();
            tempFile.renameTo(finalFile);
            myLog("Download complete: " + destPath);
            return Result.success();

        } catch (Exception e) {
            myLogEE(e, "Download failed");
            return Result.retry();

        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    //--- FULL LOG --------------------------
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogInFile(String str) { KanLogger.myLogInFile(this.getClass().getName(), str); }
    private void myLogD(String str) { KanLogger.myLogD(this.getClass().getName(), str); }
    private void myLogI(String str) { KanLogger.myLogI(this.getClass().getName(), str); }
    private void myLogW(String str) { KanLogger.myLogW(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
    private void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, this.getClass().getName(), str); }
    private void myToast(String str) { KanLogger.myToast(this.getClass().getName(), str); }
    private void myToastE(String str) { KanLogger.myToastE(this.getClass().getName(), str); }
    private void myKeyFirebase(String strKey, String strValue) {KanLogger.myKeyFirebase(strKey, strValue);}
    private void myLogFirebase(String strLog) {KanLogger.myLogFirebase(strLog);}

}

