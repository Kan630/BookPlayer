package com.driot.bookplayer.redownload;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.BookSource;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Downloads a BookSource address (a zip of tracks, or a single audio file) and puts each
 *  track back where its ZikFile row says it lives. */
public class RedownloadBookWorker extends Worker {

    public RedownloadBookWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        long folderId = getInputData().getLong(RedownloadHelper.KEY_FOLDER_ID, -1);
        BookSource bs = AppDatabase.getDatabase(ctx).bookSourceDao().getByFolderId(folderId);
        if (bs == null || bs.source_url == null || bs.source_url.isEmpty()) {
            return Result.failure();
        }
        List<RedownloadHelper.MissingTrack> missing = RedownloadHelper.prepareMissingTracks(ctx, folderId);
        if (missing.isEmpty()) {
            return Result.success();
        }

        File tmp = new File(ctx.getCacheDir(), "redownload_" + folderId + ".tmp");
        try {
            permanentFailure = false;
            if (!download(bs.source_url, tmp)) {
                return permanentFailure ? giveUp(ctx) : retryOrGiveUp(ctx);
            }
            int restored = isZip(tmp) ? extractFromZip(tmp, missing) : placeSingleFile(tmp, missing);
            myLogI("redownload folder " + folderId + ": restored " + restored + "/" + missing.size());
            if (restored == missing.size()) {
                myToast(ctx.getString(R.string.redownload_done));
                return Result.success();
            }
            // What the address serves no longer matches the book - trying again cannot fix that.
            return giveUp(ctx);
        } catch (Exception e) {
            myLogEE(e, "redownload failed for folder " + folderId);
            return retryOrGiveUp(ctx);
        } finally {
            tmp.delete();
        }
    }

    // Set by download() when the server answers with an error a retry cannot fix (404, 403...).
    private boolean permanentFailure;

    private Result giveUp(Context ctx) {
        myToastE(ctx.getString(R.string.redownload_failed));
        return Result.failure();
    }

    /** Network trouble is worth a few more tries; after that the user is told. */
    private Result retryOrGiveUp(Context ctx) {
        return getRunAttemptCount() >= 3 ? giveUp(ctx) : Result.retry();
    }

    private boolean download(String address, File out) throws Exception {
        String current = address;
        for (int hop = 0; hop < 6; hop++) {
            HttpURLConnection conn = (HttpURLConnection) new URL(current).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            try {
                int code = conn.getResponseCode();
                if (code >= 300 && code < 400 && conn.getHeaderField("Location") != null) {
                    current = new URL(new URL(current), conn.getHeaderField("Location")).toString();
                    continue;
                }
                if (code != 200) {
                    myLogW("redownload: HTTP " + code + " for " + current);
                    permanentFailure = code >= 400 && code < 500 && code != 408 && code != 429;
                    return false;
                }
                try (InputStream in = conn.getInputStream(); OutputStream o = new FileOutputStream(out)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        if (isStopped()) {
                            return false;
                        }
                        o.write(buf, 0, n);
                    }
                }
                return out.length() > 0;
            } finally {
                conn.disconnect();
            }
        }
        return false;
    }

    private static boolean isZip(File f) throws Exception {
        try (FileInputStream in = new FileInputStream(f)) {
            return in.read() == 'P' && in.read() == 'K';
        }
    }

    /** Import may have cleaned up file names, so compare on letters and digits only. */
    private static String norm(String name) {
        return name.toLowerCase().replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private int extractFromZip(File zipFile, List<RedownloadHelper.MissingTrack> missing) throws Exception {
        int restored = 0;
        try (ZipFile zip = new ZipFile(zipFile)) {
            java.util.Map<String, ZipEntry> byName = new java.util.HashMap<>();
            java.util.Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (!e.isDirectory()) {
                    byName.put(norm(new File(e.getName()).getName()), e);
                }
            }
            for (RedownloadHelper.MissingTrack t : missing) {
                ZipEntry e = byName.get(norm(t.dest.getName()));
                if (e == null) {
                    myLogW("redownload: no zip entry for " + t.dest.getName());
                    continue;
                }
                File part = new File(t.dest.getPath() + ".part");
                t.dest.getParentFile().mkdirs();
                try (InputStream in = zip.getInputStream(e); OutputStream o = new FileOutputStream(part)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        o.write(buf, 0, n);
                    }
                }
                if (t.dest.exists()) {
                    t.dest.delete();
                }
                if (part.renameTo(t.dest)) {
                    restored++;
                }
            }
        }
        return restored;
    }

    private int placeSingleFile(File downloaded, List<RedownloadHelper.MissingTrack> missing) {
        if (missing.size() != 1) {
            return 0;
        }
        File dest = missing.get(0).dest;
        // The download sits in the internal cache and the book may live on the SD card, so a
        // plain rename (which cannot cross storage volumes) is not an option - copy instead.
        File part = new File(dest.getPath() + ".part");
        try (InputStream in = new FileInputStream(downloaded); OutputStream o = new FileOutputStream(part)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                o.write(buf, 0, n);
            }
        } catch (java.io.IOException e) {
            myLogEE(e, "redownload: could not write " + dest);
            part.delete();
            return 0;
        }
        if (dest.exists()) {
            dest.delete();
        }
        return part.renameTo(dest) ? 1 : 0;
    }
}
