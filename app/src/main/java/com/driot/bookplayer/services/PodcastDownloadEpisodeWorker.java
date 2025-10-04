package com.driot.bookplayer.services;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.utils.KanLogger;
import com.driot.bookplayer.utils.Tonio;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;

import javax.net.ssl.SSLException;

public class PodcastDownloadEpisodeWorker extends Worker {
    public static final String KEY_URL = "url";
    public static final String KEY_DEST_PATH = "dest_path";

    public PodcastDownloadEpisodeWorker(@NonNull Context context, @NonNull WorkerParameters params) {
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
            URL url;
            try {
                url = new URL(urlStr.replace("http://", "https://"));
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.connect();
            } catch (SSLException | UnknownHostException e) {
                myLogW("HTTPS failed : " + e.getMessage());
                return Result.failure();
            }


            try (InputStream in = new BufferedInputStream(conn.getInputStream());
                 FileOutputStream out = new FileOutputStream(tempFile)) {

                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                }
            }

            if (tempFile.exists()) {
                if (tempFile.length() > 0) {
                    if (finalFile.exists()) finalFile.delete();
                    tempFile.renameTo(finalFile);
                    myLog("Download complete: " + destPath + "\nfile size = " + Tonio.getReadableSize(finalFile.length()));
                    return Result.success();
                } else {
                    myToastE("Download failed, length = 0");
                    return Result.failure();
                }
            } else {
                return Result.success();
            }


        } catch (Exception e) {
            myLogEE(e, "Download failed - retrying");
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
    private void myKeyFirebase(String strKey, String strValue) {
        FirebaseAnalyticsHelper.setCustomKeyCrashlytics(strKey, strValue);}
    private void myLogFirebase(String strLog) {
        FirebaseAnalyticsHelper.logCrashlytics(strLog);}

}

