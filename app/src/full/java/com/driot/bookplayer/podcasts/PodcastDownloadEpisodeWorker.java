package com.driot.bookplayer.podcasts;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.driot.bookplayer.utils.Tonio;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

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

            long totalBytes = conn.getContentLength();
            long downloadedBytes = 0;
            int lastProgress = -1;

            try (InputStream in = new BufferedInputStream(conn.getInputStream());
                    FileOutputStream out = new FileOutputStream(tempFile)) {

                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                    downloadedBytes += count;

                    if (totalBytes > 0) {
                        int progress = (int) ((downloadedBytes * 100) / totalBytes);
                        if (progress != lastProgress) {
                            lastProgress = progress;
                            setProgressAsync(new androidx.work.Data.Builder().putInt("progress", progress).build());
                        }
                    }
                }
            }

            if (tempFile.exists()) {
                if (tempFile.length() > 0) {
                    if (finalFile.exists())
                        finalFile.delete();
                    tempFile.renameTo(finalFile);
                    myLog("Download complete: " + destPath + "\nfile size = "
                            + Tonio.getReadableSize(finalFile.length()));
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
            if (conn != null)
                conn.disconnect();
        }
    }

}
