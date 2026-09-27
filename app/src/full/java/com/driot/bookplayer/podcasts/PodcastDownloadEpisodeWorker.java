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
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.SSLException;

public class PodcastDownloadEpisodeWorker extends Worker {
    public static final String KEY_URL = "url";
    public static final String KEY_DEST_PATH = "dest_path";

    // Same cap as before: runs 0..MAX_RETRIES-1 retry, then the episode is skipped.
    private static final int MAX_RETRIES = 5;
    private static final int MAX_REDIRECTS = 5;

    /** A status that trying again cannot fix (404, 410, 403...). */
    private static class PermanentHttpException extends IOException {
        PermanentHttpException(int code) {
            super("HTTP " + code);
        }
    }

    public PodcastDownloadEpisodeWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /**
     * Downloads one episode to dest_path (through dest_path + ".part"). This worker is a link in a
     * sequential chain (PodcastDownloadManager: episode, sync, episode, sync...): a failure result
     * would cancel every later link, so an episode that cannot be downloaded is skipped (logged,
     * .part removed, success returned) and the rest of the batch carries on. Network trouble is
     * retried first.
     */
    @NonNull
    @Override
    public Result doWork() {
        String urlStr = getInputData().getString(KEY_URL);
        String destPath = getInputData().getString(KEY_DEST_PATH);

        if (urlStr == null || destPath == null) {
            myLogE("Missing input data - skipped");
            return Result.success();
        }

        File finalFile = new File(destPath);
        if (finalFile.exists() && finalFile.length() > 1000) {
            myLogW("Already downloaded: " + destPath);
            return Result.success();
        }

        File tempFile = new File(destPath + ".part");
        String httpsUrl = urlStr.replace("http://", "https://");
        try {
            try {
                download(httpsUrl, tempFile);
            } catch (SSLException e) {
                if (httpsUrl.equals(urlStr))
                    return skip(tempFile, "SSL error: " + e.getMessage(), urlStr);
                // The feed gave http and the host has no working https: use the address as given.
                myLogW("HTTPS failed (" + e.getMessage() + "), trying plain http: " + urlStr);
                download(urlStr, tempFile);
            }

            if (finalFile.exists())
                finalFile.delete();
            if (!tempFile.renameTo(finalFile))
                throw new IOException("could not rename " + tempFile + " to " + finalFile);
            myLog("Download complete: " + destPath + "\nfile size = " + Tonio.getReadableSize(finalFile.length()));
            return Result.success();

        } catch (PermanentHttpException e) {
            return skip(tempFile, e.getMessage(), urlStr);
        } catch (java.net.UnknownHostException e) {
            // A short network drop looks like this too: one retry, not five - later episodes of the
            // chain wait during the backoff, and a dead host would hold them for minutes.
            tempFile.delete();
            if (getRunAttemptCount() >= 1)
                return skip(tempFile, "unknown host: " + e.getMessage(), urlStr);
            myLogW("Unknown host (" + e.getMessage() + ") - one retry");
            return Result.retry();
        } catch (Exception e) {
            tempFile.delete(); // a retry starts from scratch anyway
            if (getRunAttemptCount() >= MAX_RETRIES)
                return skip(tempFile, "gave up after " + (getRunAttemptCount() + 1) + " attempts: " + e, urlStr);
            myLogEE(e, "Download failed - retrying (attempt " + (getRunAttemptCount() + 1) + ")");
            return Result.retry();
        }
    }

    private Result skip(File tempFile, String why, String url) {
        tempFile.delete();
        myLogE("Download SKIPPED (" + why + "): " + url);
        return Result.success();
    }

    /** Streams url into tempFile. Throws PermanentHttpException for a status retrying cannot fix,
     *  IOException for anything worth a retry (network, 5xx, 429, cut-short body). */
    private void download(String urlStr, File tempFile) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            int code = 0;
            // HttpURLConnection only follows redirects within one protocol: an https -> http hop
            // (common with podcast tracking prefixes) comes back as a 3xx and is followed here.
            for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                code = conn.getResponseCode();
                if (code < 300 || code >= 400)
                    break;
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                conn = null;
                if (location == null)
                    throw new IOException("HTTP " + code + " without Location");
                url = new URL(url, location);
            }
            if (conn == null)
                throw new IOException("too many redirects");
            if (code >= 400 && code < 500 && code != 408 && code != 429)
                throw new PermanentHttpException(code);
            if (code >= 400)
                throw new IOException("HTTP " + code);

            long totalBytes = conn.getContentLengthLong();
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

            // A connection closed early without an error would otherwise leave a cut-off episode
            // that looks downloaded (and is never fetched again).
            if (totalBytes > 0 && downloadedBytes != totalBytes)
                throw new IOException("incomplete download: " + downloadedBytes + "/" + totalBytes + " bytes");
            if (downloadedBytes == 0)
                throw new IOException("empty download");
        } finally {
            if (conn != null)
                conn.disconnect();
        }
    }

}
