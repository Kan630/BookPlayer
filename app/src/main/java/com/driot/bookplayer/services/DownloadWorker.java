package com.driot.bookplayer.services;

import static com.driot.bookplayer.utils.Tonio.formatSizeMB;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.work.Data;
import androidx.work.WorkInfo;
import androidx.work.WorkerParameters;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.HttpCodeHelper;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.imports.ImportJob;
import com.driot.bookplayer.imports.ImportWorker;
import com.driot.bookplayer.utils.Tonio;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * First step of an http import chain. The transfer itself is done by the system
 * {@link DownloadManager} (retries, network changes, reboots, notification are all the OS's job) -
 * this worker only enqueues it, mirrors its progress into the ImportJob row while it runs, and
 * moves the finished file into the app's download folder so the next steps of the chain
 * (uncompress / split / FinalParse) are unchanged.
 * <p>
 * It is a plain (non-foreground) worker: if WorkManager stops it (10 min execution limit, system
 * pressure), it returns retry and the next run resumes polling the same DownloadManager id,
 * persisted in ImportJob.downloadWorkId - the download keeps going in the meantime.
 */
public class DownloadWorker extends ImportWorker {

    private static final String TASK_NAME = Var.WORKER_TASK_LABEL_DOWNLOAD;

    public static final String TAG_DOWNLOAD = "DOWNLOAD_WORK";

    // === Progress keys ===
    public static final String PROG_PERCENT = "progress_percent";
    public static final String PROG_TEXT = "progress_text";
    public static final String OUT_FILEPATH = "out_filepath";

    // DownloadManager can't write to our private getFilesDir(), so it downloads into this
    // app-specific external folder first, then the file is moved to the real destination.
    private static final String STAGING_FOLDER = "DownloadStaging";

    private static final long POLL_INTERVAL_MS = 1000;
    private static final int CONNECT_TIMEOUT_MS = 30_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    private final Context context;

    public DownloadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        this.context = context.getApplicationContext();
    }

    @NonNull
    @Override
    public Result doWorkBody() {
        ImportJob j = jobOrFail();
        final String urlStr = j.downloadFileUrl;
        final String destFolder = j.downloadDestinationFolder;
        final String title = j.title;

        myLogD("----------------------------------------------------");
        myLog("Title: " + title);
        myLog("From: " + urlStr);
        myLog("To: " + destFolder);
        myLog("DownloadManager id: " + j.downloadWorkId + " - attempt " + getRunAttemptCount());
        myLogD("----------------------------------------------------");

        if (urlStr == null || destFolder == null) {
            myLogE("Missing input data: url or dest_folder");
            emitFailed(TASK_NAME, "Missing input data", null);
            return Result.failure();
        }

        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) {
            emitFailed(TASK_NAME, "DownloadManager unavailable",
                    context.getString(R.string.unexpected_error) + " (DownloadManager)");
            return Result.failure();
        }

        long dmId = parseId(j.downloadWorkId);
        if (dmId > 0 && queryStatus(dm, dmId) == null) {
            myLogW("DownloadManager id " + dmId + " no longer exists - enqueueing again");
            dmId = -1;
        }

        if (dmId <= 0) {
            emitTaskStart(TASK_NAME,
                    context.getString(R.string.download_noun) + " " + context.getString(R.string.import_task_start));
            Result early = checkFreeSpace(destFolder);
            if (early != null)
                return early;
            try {
                dmId = enqueue(dm, urlStr, destFolder, title);
            } catch (EnqueueFailure f) {
                return f.result;
            }
            repo.setDownloadManagerId(importId, dmId);
        } else {
            emitDownloadResuming();
        }

        return pollUntilDone(dm, dmId, destFolder);
    }

    // ---------------------------------------------------------------------------------------------
    // Enqueue
    // ---------------------------------------------------------------------------------------------

    private static class EnqueueFailure extends Exception {
        final Result result;

        EnqueueFailure(Result result) {
            this.result = result;
        }
    }

    private long enqueue(DownloadManager dm, String urlStr, String destFolder, String title) throws EnqueueFailure {
        if (!NetworkHelper.isNetworkAvailable(context)) {
            emitDownloadPause(context.getString(R.string.no_internet_connection));
            throw new EnqueueFailure(Result.retry());
        }

        // Probe in our own process first: resolves the real file name (redirect / Content-Disposition)
        // and surfaces a cleartext-blocked http url with a clear message, since DownloadManager
        // enforces our network security config too but would only report a generic error.
        String fileName;
        try {
            fileName = resolveFileName(urlStr);
        } catch (UnknownHostException e) {
            myLogE("No internet connection [" + e.getMessage() + "]");
            emitDownloadPause(context.getString(R.string.no_internet_connection));
            throw new EnqueueFailure(Result.retry());
        } catch (SocketException e) {
            myLogE("Connection aborted [" + e.getMessage() + "]");
            emitDownloadPause(context.getString(R.string.connection_aborted) + " ("
                    + context.getString(R.string.no_internet_connection) + "?)\n" + e.getMessage());
            throw new EnqueueFailure(Result.retry());
        } catch (IOException e) {
            if (NetworkHelper.isCleartextNotPermitted(e)) {
                emitFailed(TASK_NAME, "clear_text_not_permitted: [" + e.getMessage() + "]", cleartextMessage(urlStr));
                throw new EnqueueFailure(Result.failure());
            }
            // The probe is only a nicety - let DownloadManager have a go with the url's own name
            myLogW("Probe failed [" + e.getMessage() + "] - using name from url");
            fileName = Tonio.getFileNameFromUrl(urlStr);
        }

        File stagingDir = stagingDirFor(destFolder);
        if (stagingDir == null) {
            emitFailed(TASK_NAME, "no external files dir for staging",
                    context.getString(R.string.failed_to_create_destination_folder));
            throw new EnqueueFailure(Result.failure());
        }
        String displayTitle = title != null && !title.isEmpty() ? title : fileName;
        try {
            long id;
            try {
                id = dm.enqueue(buildRequest(urlStr, stagingDir, fileName, displayTitle));
            } catch (SecurityException | IllegalArgumentException e) {
                // Some DownloadManager builds only accept the primary volume's app dir
                File primary = ContextCompat.getExternalFilesDirs(context, null)[0];
                if (primary == null || stagingDir.getParentFile() == null
                        || stagingDir.getParentFile().equals(primary))
                    throw e;
                myLogW("enqueue refused for " + stagingDir + " [" + e.getMessage() + "] - retrying on primary volume");
                stagingDir = new File(primary, STAGING_FOLDER);
                if (!stagingDir.exists() && !stagingDir.mkdirs())
                    throw e;
                id = dm.enqueue(buildRequest(urlStr, stagingDir, fileName, displayTitle));
            }
            myLog("enqueued DownloadManager id " + id + " in " + stagingDir);
            repo.updateDownloadedFilePath(importId, new File(destFolder, fileName).getAbsolutePath());
            return id;
        } catch (Exception e) {
            myLogEE(e, "DownloadManager.enqueue failed");
            emitFailed(TASK_NAME, "enqueue failed: [" + e.getMessage() + "]",
                    context.getString(R.string.unexpected_error) + " [" + e.getMessage() + "]");
            throw new EnqueueFailure(Result.failure());
        }
    }

    private DownloadManager.Request buildRequest(String urlStr, File stagingDir, String fileName, String title) {
        // Prefix with the import id hash: two imports of the same file name must not collide
        File stagingFile = new File(stagingDir, Integer.toHexString(importId.hashCode()) + "_" + fileName);
        if (stagingFile.exists() && !stagingFile.delete())
            myLogW("could not delete stale staging file " + stagingFile);

        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(urlStr))
                .setTitle(title)
                .setDescription(context.getString(R.string.app_name))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationUri(Uri.fromFile(stagingFile))
                .addRequestHeader("User-Agent", Var.USER_AGENT_BOOKPLAYER);
        switch (Option.getNetworkPolicyManualDownload()) {
            case NETWORK_POLICY_UNMETERED -> req.setAllowedOverMetered(false);
            case NETWORK_POLICY_NOT_ROAMING -> req.setAllowedOverRoaming(false);
            default -> {
            }
        }
        return req;
    }

    /** App-specific external dir on the same volume as the destination when possible (cheap move). */
    @Nullable
    private File stagingDirFor(String destFolder) {
        File[] dirs = ContextCompat.getExternalFilesDirs(context, null);
        File chosen = null;
        for (File d : dirs) {
            if (d == null)
                continue;
            if (chosen == null)
                chosen = d; // primary
            String volumeRoot = d.getAbsolutePath().split("/Android/")[0];
            if (destFolder.startsWith(volumeRoot)) {
                chosen = d;
                break;
            }
        }
        if (chosen == null)
            return null;
        File staging = new File(chosen, STAGING_FOLDER);
        if (!staging.exists() && !staging.mkdirs())
            return null;
        return staging;
    }

    private String resolveFileName(String urlStr) throws IOException {
        URL probeUrl = new URL(urlStr);
        HttpURLConnection probe = (HttpURLConnection) probeUrl.openConnection();
        try {
            probe.setConnectTimeout(CONNECT_TIMEOUT_MS);
            probe.setReadTimeout(READ_TIMEOUT_MS);
            probe.setRequestProperty("User-Agent", Var.USER_AGENT_BOOKPLAYER);
            probe.setRequestMethod("HEAD");
            probe.setInstanceFollowRedirects(false); // capture Location
            probe.connect();

            int code = probe.getResponseCode();
            String location = probe.getHeaderField("Location");
            myLog("Probe response code: " + code + ", Location: " + location);

            if ((code == HttpURLConnection.HTTP_MOVED_TEMP || code == HttpURLConnection.HTTP_MOVED_PERM
                    || code == 307 || code == 308) && location != null && !location.isEmpty()) {
                String name = Tonio.getFileNameFromUrl(new URL(probeUrl, location).toString());
                myLog("Filename from redirect Location: " + name);
                return name;
            }
            String cdName = extractFileNameFromContentDisposition(probe.getHeaderField("Content-Disposition"));
            if (cdName != null && !cdName.isEmpty()) {
                myLog("Filename from Content-Disposition: " + cdName);
                return cdName;
            }
            return Tonio.getFileNameFromUrl(urlStr);
        } finally {
            probe.disconnect();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Poll
    // ---------------------------------------------------------------------------------------------

    private static class DmStatus {
        int status;
        int reason;
        long soFar;
        long total;
        String localUri;
    }

    private Result pollUntilDone(DownloadManager dm, long dmId, String destFolder) {
        int lastStatus = -1;
        int lastPercent = -1;
        long lastSoFar = -1;
        while (true) {
            if (isStopped()) {
                if (getStopReason() == WorkInfo.STOP_REASON_CANCELLED_BY_APP) {
                    myLogW("cancelled - removing DownloadManager id " + dmId);
                    dm.remove(dmId);
                } else {
                    myLogW("stopped by WorkManager (reason " + getStopReason()
                            + ") - DownloadManager keeps going, will resume polling");
                }
                return Result.retry();
            }

            DmStatus s = queryStatus(dm, dmId);
            if (s == null) {
                // Removed behind our back (user cleared it from the system downloads UI, or our cancel)
                myLogW("DownloadManager id " + dmId + " vanished - treating as cancelled");
                repo.setDownloadManagerId(importId, -1);
                emitCancelled(TASK_NAME);
                return Result.failure();
            }

            switch (s.status) {
                case DownloadManager.STATUS_SUCCESSFUL:
                    return onSuccess(dm, dmId, s, destFolder);

                case DownloadManager.STATUS_FAILED:
                    myLogE("DownloadManager failed, reason " + s.reason);
                    dm.remove(dmId); // also deletes the partial file
                    repo.setDownloadManagerId(importId, -1);
                    emitFailed(TASK_NAME, "DownloadManager reason " + s.reason, failureMessage(s.reason, destFolder));
                    return Result.failure();

                case DownloadManager.STATUS_PAUSED:
                    if (lastStatus != DownloadManager.STATUS_PAUSED)
                        emitDownloadPause(pausedMessage(s.reason));
                    break;

                default: // PENDING / RUNNING
                    if (lastStatus == DownloadManager.STATUS_PAUSED)
                        emitDownloadResuming();
                    int percent = s.total > 0 ? (int) (s.soFar * 100L / s.total) : 0;
                    if (percent != lastPercent || (s.total <= 0 && s.soFar != lastSoFar)) {
                        String text = progressText(s.soFar, s.total);
                        setProgressAsync(new Data.Builder()
                                .putInt(PROG_PERCENT, percent)
                                .putString(PROG_TEXT, text)
                                .build());
                        emitStepProgress(TASK_NAME, percent, text);
                        lastPercent = percent;
                        lastSoFar = s.soFar;
                    }
                    break;
            }
            lastStatus = s.status;

            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Result.retry();
            }
        }
    }

    private Result onSuccess(DownloadManager dm, long dmId, DmStatus s, String destFolder) {
        File staged = s.localUri != null ? new File(Uri.parse(s.localUri).getPath()) : null;
        if (staged == null || !staged.exists()) {
            dm.remove(dmId);
            repo.setDownloadManagerId(importId, -1);
            emitFailed(TASK_NAME, "downloaded file missing: " + s.localUri,
                    context.getString(R.string.io_error));
            return Result.failure();
        }

        // Strip the "<hash>_" staging prefix back off
        String name = staged.getName();
        int us = name.indexOf('_');
        if (us > 0)
            name = name.substring(us + 1);
        File outFile = new File(destFolder, name);

        try {
            File parent = outFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs())
                throw new IOException("cannot create " + parent);
            Files.move(staged.toPath(), outFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            myLogEE(e, "moving downloaded file failed");
            dm.remove(dmId);
            repo.setDownloadManagerId(importId, -1);
            emitFailed(TASK_NAME, "move failed: [" + e.getMessage() + "]",
                    context.getString(R.string.failed_to_create_destination_folder) + ": " + destFolder);
            return Result.failure();
        }
        // Only forgets the row now - the file it pointed to has already been moved away
        dm.remove(dmId);
        repo.setDownloadManagerId(importId, -1);

        myLog("download done -> " + outFile + " (" + formatSizeMB(outFile.length()) + ")");
        emitTaskCompleted(TASK_NAME, outFile.getAbsolutePath(),
                context.getString(R.string.download_noun) + " " + context.getString(R.string.done));
        setProgressAsync(new Data.Builder()
                .putInt(PROG_PERCENT, 100)
                .putString(OUT_FILEPATH, outFile.getAbsolutePath())
                .build());
        return Result.success(new Data.Builder()
                .putString(OUT_FILEPATH, outFile.getAbsolutePath())
                .build());
    }

    @Nullable
    private static DmStatus queryStatus(DownloadManager dm, long dmId) {
        try (Cursor c = dm.query(new DownloadManager.Query().setFilterById(dmId))) {
            if (c == null || !c.moveToFirst())
                return null;
            DmStatus s = new DmStatus();
            s.status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            s.reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
            s.soFar = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            s.total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            s.localUri = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI));
            return s;
        }
    }

    /** Removes a job's system download, if any (used on user cancel). Safe to call with null. */
    public static void removeSystemDownload(Context ctx, @Nullable String downloadManagerId) {
        long id = parseId(downloadManagerId);
        if (id <= 0)
            return;
        DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm != null)
            dm.remove(id);
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private static long parseId(@Nullable String s) {
        if (s == null || s.isEmpty())
            return -1;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Nullable
    private Result checkFreeSpace(String destFolder) {
        // App-controlled free-space check (user-configurable, see Settings > Download - separate
        // thresholds for internal vs SD card).
        int minFreeMb = StorageHelper.getMinFreeStorageMbForPath(context, destFolder);
        long freeBytes = StorageHelper.getUsableSpaceForPath(destFolder);
        long minFreeBytes = minFreeMb * 1024L * 1024L;
        if (freeBytes > 0 && freeBytes < minFreeBytes) {
            long freeMB = freeBytes / (1024 * 1024);
            myLogE("Not enough free storage: " + freeMB + "MB free, need " + minFreeMb + "MB");
            emitFailed(TASK_NAME, "Low storage: " + freeMB + "MB free",
                    context.getString(R.string.download_error_low_storage, freeMB, minFreeMb));
            return Result.failure();
        }
        return null;
    }

    private String pausedMessage(int reason) {
        return switch (reason) {
            case DownloadManager.PAUSED_WAITING_FOR_NETWORK, DownloadManager.PAUSED_QUEUED_FOR_WIFI ->
                context.getString(R.string.download_waiting_for_network);
            default -> context.getString(R.string.download_stopped_by_system_will_retry);
        };
    }

    private String failureMessage(int reason, String destFolder) {
        if (reason >= 400 && reason < 600) {
            return context.getString(R.string.server_returned_http) + " " + reason
                    + HttpCodeHelper.getTranslatedHttpCode(context, reason);
        }
        if (reason == DownloadManager.ERROR_INSUFFICIENT_SPACE) {
            long freeMB = StorageHelper.getUsableSpaceForPath(destFolder) / (1024 * 1024);
            return context.getString(R.string.download_error_low_storage, freeMB,
                    StorageHelper.getMinFreeStorageMbForPath(context, destFolder));
        }
        if (reason == DownloadManager.ERROR_FILE_ERROR || reason == DownloadManager.ERROR_DEVICE_NOT_FOUND) {
            return context.getString(R.string.io_error);
        }
        return context.getString(R.string.unexpected_error) + " [" + reason + "]";
    }

    private String cleartextMessage(String urlStr) {
        String host = null;
        try {
            host = Uri.parse(urlStr).getHost();
        } catch (Throwable ignore) {
        }
        String fix = context.getString(
                R.string.use_https_or_allow_cleartext_for_this_host_in_the_app_s_network_security_config);
        return (host != null)
                ? context.getString(R.string.http_cleartext_to) + " " + host + " "
                        + context.getString(R.string.is_blocked_by_android_s_network_security_policy) + ". " + fix
                : context.getString(R.string.http_is_blocked_by_android_s_network_security_policy) + " " + fix;
    }

    private static String progressText(long written, long totalIfKnown) {
        if (totalIfKnown > 0) {
            return String.format(Locale.US, "%s / %s", formatSizeMB(written), formatSizeMB(totalIfKnown));
        }
        return String.format(Locale.US, "%s", formatSizeMB(written));
    }

    /**
     * Extracts filename from Content-Disposition header.
     * Handles both:
     *   Content-Disposition: attachment; filename="pg13951-images-3.epub"
     *   Content-Disposition: attachment; filename*=UTF-8''pg13951-images-3.epub
     */
    private static String extractFileNameFromContentDisposition(String contentDisposition) {
        if (contentDisposition == null || contentDisposition.isEmpty()) return null;

        // Try filename*=UTF-8''<name> (RFC 5987, takes priority)
        int starIdx = contentDisposition.indexOf("filename*=");
        if (starIdx >= 0) {
            String val = contentDisposition.substring(starIdx + 10).trim();
            // Strip encoding prefix like UTF-8''
            int quoteIdx = val.indexOf("''");
            if (quoteIdx >= 0) val = val.substring(quoteIdx + 2);
            val = val.split(";")[0].trim();
            try {
                return java.net.URLDecoder.decode(val, "UTF-8");
            } catch (Exception ignored) {}
            return val;
        }

        // Try filename="<name>" or filename=<name>
        int idx = contentDisposition.indexOf("filename=");
        if (idx < 0) return null;

        String val = contentDisposition.substring(idx + 9).trim();
        if (val.startsWith("\"")) {
            int end = val.indexOf('"', 1);
            return end > 0 ? val.substring(1, end) : null;
        }
        // unquoted: take until ; or end
        return val.split(";")[0].trim();
    }

}
