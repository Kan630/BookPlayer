package com.driot.bookplayer.services;

import static com.driot.bookplayer.utils.Tonio.formatSizeMB;
import static com.driot.bookplayer.utils.Tonio.getFileNameFromUrl;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.WorkerParameters;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.imports.ImportJob;
import com.driot.bookplayer.imports.ImportWorker;
import com.driot.bookplayer.objects.LoadBookTaskState;
import com.driot.bookplayer.utils.Tonio;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class DownloadWorker extends ImportWorker {

    private static final String TASK_NAME = Var.WORKER_TASK_LABEL_DOWNLOAD;

    public static final int HTTP_REQUESTED_RANGE_NOT_SATISFIABLE = 416;
    public static final String TAG_DOWNLOAD = "DOWNLOAD_WORK";

    // === Input keys ===
    public static final String KEY_URL = "url";
    public static final String KEY_DEST_FOLDER = "dest_folder";
    public static final String KEY_TITLE = "title";
    public static final String KEY_IS_MANUAL = "is_manual"; // optional: if you still apply manual policy

    // === Progress keys ===
    public static final String PROG_PERCENT = "progress_percent";
    public static final String PROG_TEXT = "progress_text";
    public static final String OUT_FILEPATH = "out_filepath";

    // === Notification / actions ===
    private static final String CHANNEL_ID = "BookplayerDownloadChannel";
    private static final int NOTIF_ID_BASE = 1630; // you can offset with workId hash if running many in parallel

    public static final String ACTION_PAUSE  = "com.driot.bookplayer.ACTION_DOWNLOAD_PAUSE";
    public static final String ACTION_CANCEL = "com.driot.bookplayer.ACTION_DOWNLOAD_CANCEL";
    public static final String ACTION_RESUME = "com.driot.bookplayer.ACTION_DOWNLOAD_RESUME";
    public static final String EXTRA_WORK_ID = "work_id";
    // === Tuning ===
    private static final long MIN_UPDATE_INTERVAL_MS = 250;
    private static final int CONNECT_TIMEOUT_MS = 30_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    // Optional legacy policy window (if you still want it)
    private static final long MANUAL_POLICY_WINDOW_MS = 30 * 60 * 1000L;

    private FileLock downloadLock;
    private FileChannel lockChannel;
    private Path lockPath;

    private final AtomicBoolean pauseRequested = new AtomicBoolean(false);
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    private final AtomicBoolean resumeRequested = new AtomicBoolean(false);
    private final AtomicBoolean stoppedRequested = new AtomicBoolean(false);

    private long lastTick = 0L;
    private int lastPercent = 0;

    private final Context context;

    public DownloadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        this.context = context.getApplicationContext();
    }
    @NonNull
    @Override
    public Result doWork() {
        emitTaskStart(TASK_NAME, context.getString(R.string.import_task_ebook_split) + " " + context.getString(R.string.import_task_start));
        ImportJob j = jobOrFail();
        final String urlStr = j.downloadFileUrl;
        final String destFolder = j.downloadDestinationFolder;
        final String title = j.title;
        final boolean isManual = true;

        myLogD("----------------------------------------------------");
        myLog("Title: " + title);
        myLogD("----------------------------------------------------");
        myLog("From: " + urlStr);
        myLog("To: " + destFolder);
        myLog("isManual: " + isManual);
        myLogD("----------------------------------------------------");


        final Context ctx = getApplicationContext();

        // TODO, rewire, notably for auto download
        //final String urlStr = getInputData().getString(KEY_URL);
        //final String destFolder = getInputData().getString(KEY_DEST_FOLDER);
        //final String title = getInputData().getString(KEY_TITLE);
        //final boolean isManual = getInputData().getBoolean(KEY_IS_MANUAL, false);

        if (urlStr == null || destFolder == null) {
            myLogE("Missing input data: url or dest_folder");
            emitFailed(TASK_NAME, "Missing input data", null);
            return Result.failure();
        }

        final String workId = getId().toString();
        final int notifId = NOTIF_ID_BASE + Math.abs(workId.hashCode() % 1000);

        // Create channel and move worker to foreground immediately
        createNotificationChannel(ctx);
        try {
                    setForegroundAsync(buildForegroundInfo(ctx, notifId, 0, ctx.getString(R.string.starting_download), title)).get();
        } catch (Exception ignored) {}

        // Register actions receiver (dynamic, in-process only)
        BroadcastReceiver controls = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (intent == null) return;
                String act = intent.getAction();
                String target = intent.getStringExtra(EXTRA_WORK_ID);
                if (target == null || !workId.equals(target)) return; // ignore other workers

                if (ACTION_PAUSE.equals(act)) {
                    pauseRequested.set(true);
                    resumeRequested.set(false);
                    myLogI("Pause requested");
                } else if (ACTION_CANCEL.equals(act)) {
                    cancelRequested.set(true);
                    resumeRequested.set(false);
                    myLogW("Cancel requested");
                } else if (ACTION_RESUME.equals(act)) {
                    resumeRequested.set(true);
                    pauseRequested.set(false);
                    myLogI("Resume requested");
                }
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(ACTION_PAUSE);
        f.addAction(ACTION_CANCEL);
        f.addAction(ACTION_RESUME);
        ContextCompat.registerReceiver(ctx, controls, f, ContextCompat.RECEIVER_NOT_EXPORTED);

        try {
            // Respect network policy (Constraints are preferred, this is a secondary guard)
            if (!NetworkHelper.isNetworkAvailable(ctx)) {
                emitDownloadPause(ctx.getString(R.string.no_internet_connection));
                return Result.retry();
            }

            // Optional: apply your manual vs auto policy (constraints should make most of this unnecessary)
            if (!isPolicyAllowed(ctx, isManual)) {
                emitDownloadPause(ctx.getString(R.string.Download_paused_due_to_network_policy));
                return Result.retry();
            }

            final String fileName = Tonio.getFileNameFromUrl(urlStr);
            final File outFile = new File(destFolder, fileName);

            // Ensure parent folder exists
            File parent = outFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                emitFailed(TASK_NAME,
                        "failed_to_create_destination_folder : " + parent.getAbsolutePath()
                        , ctx.getString(R.string.failed_to_create_destination_folder) + ": " + parent.getAbsolutePath());
                return Result.failure();
            }

            if (!acquireDownloadLock(outFile)) {
                myLogW("Another DownloadWorker holds the lock for " + outFile.getName() + " — retry later");
                // Small courtesy delay reduces hammering if user taps RESUME a lot
                try { Thread.sleep(300); } catch (InterruptedException ignored) {}
                return Result.retry();
            }

            // Range resume
            long already = outFile.exists() ? outFile.length() : 0L;
            myLog("already downloaded : " + Tonio.formatSizeMB(already) + " for " + outFile.getName());

            HttpURLConnection conn = null;
            InputStream in = null;
            FileOutputStream out = null;

            try {
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                conn.setRequestProperty("Accept-Encoding", "identity"); // avoid gzip/chunked altering lengths
                conn.setRequestProperty("User-Agent", Var.USER_AGENT_BOOKPLAYER);

                if (already > 0L) {
                    conn.setRequestProperty("Range", "bytes=" + already + "-");
                }
                conn.connect();

                int code = conn.getResponseCode();
                if (already > 0L && code == HttpURLConnection.HTTP_OK) {
                    // Server ignored Range; start fresh
                    myLogW("Server ignored Range; restarting download from 0");
                    already = 0L;
                } else if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                    emitFailed(TASK_NAME
                            , "server returned: " + code + " - " + conn.getResponseMessage()
                            , ctx.getString(R.string.server_returned_http) + " " + code);
                    return Result.failure();
                }

                /*
                LoadBookTaskState s = Pref.getLoadBookTaskState();
                if (s != null && s.isLoadingPaused) {
                    emitDownloadResuming();
                }
                 */

                long contentLen = getContentLengthLongCompat(conn); // may be -1
                long totalLen = (contentLen > 0 ? contentLen : -1L);
                long fileLenIfKnown = (totalLen > 0 ? (already + totalLen) : -1L);

                in = new BufferedInputStream(conn.getInputStream());
                // Append if resuming, overwrite if not
                out = new FileOutputStream(outFile, already > 0L);

                // Initial foreground tick
                updateForeground(ctx, notifId, 0, progressText(already, fileLenIfKnown), title);

                byte[] buf = new byte[16 * 1024];
                long written = already;
                for (;;) {
                    if (isStopped()) {
                        /*
                        LoadBookTaskState state = Pref.getLoadBookTaskState();
                        if (state == null) {
                            myLogW("Stopped after cancelled");
                            emitCancelled(TASK_NAME);
                            return Result.failure();
                        }
                         */
                        myLogW("Stopped by WM/constraints — keeping partial and retrying");
                        emitDownloadPause(getApplicationContext().getString(R.string.download_stopped_by_system_will_retry));
                        return Result.retry(); // partial file kept; WM will reschedule when constraints are met
                    }
                    if (stoppedRequested.get()) { //happens if we hit onStopped()... constraints ?
                        myLogW("Stop requested");
                        emitDownloadPause(getApplicationContext().getString(R.string.download_stop_requested));
                        return Result.retry(); // partial file kept; WM will reschedule when constraints are met
                    }
                    if (cancelRequested.get()) {
                        myLogW("Cancel acknowledged in loop");
                        pauseRequested.set(false);
                        resumeRequested.set(false);
                        safeDelete(outFile);
                        emitCancelled(TASK_NAME);
                        return Result.failure();
                    }
                    if (pauseRequested.get()) {
                        myLogI("Pause acknowledged in loop — entering paused state");
                        enterPausedState(ctx, notifId, title, ctx.getString(R.string.Download_paused_by_user));

                        // Close current connection so the server doesn't time out while we wait.
                        safeClose(in);
                        safeClose(out);
                        if (conn != null) try { conn.disconnect(); } catch (Throwable ignore) {}

                        // Wait here until resume or cancel or stop
                        while (true) {
                            //LoadBookTaskState state = Pref.getLoadBookTaskState();
                            //if (state == null) {
                            //    return Result.failure();
                            //}
                            if (isStopped()) {
                                myLogW("Paused → stopped");
                                emitDownloadPause(getApplicationContext().getString(R.string.download_stopped_by_system_will_retry));
                                return Result.retry();
                            }
                            if (cancelRequested.get()) {
                                myLogW("Paused → cancelled");
                                safeClose(in);
                                safeClose(out);
                                safeDelete(outFile);
                                emitCancelled(TASK_NAME);
                                return Result.failure();
                            }
                            if (resumeRequested.get()) {
                                myLogI("Resuming from paused state");
                                // flip flags and rebuild the running notification
                                resumeRequested.set(false);
                                pauseRequested.set(false);
                                updateForeground(ctx, notifId, lastPercent, progressText(written, fileLenIfKnown), title);
                                emitDownloadResuming();
                                break; // continue the download loop
                            }
                            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
                        }

                        // Re-establish connection with Range starting at 'written'
                        already = written;
                        url = new URL(urlStr);
                        conn = (HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                        conn.setReadTimeout(READ_TIMEOUT_MS);
                        conn.setRequestProperty("Accept-Encoding", "identity"); // avoid gzip/chunked altering lengths
                        conn.setRequestProperty("User-Agent", Var.USER_AGENT_BOOKPLAYER);
                        if (already > 0L) conn.setRequestProperty("Range", "bytes=" + already + "-");
                        conn.connect();

                        code = conn.getResponseCode();
                        if (already > 0L && code == HttpURLConnection.HTTP_OK) {
                            myLogW("Server ignored Range after resume; restarting from 0");
                            already = 0L;
                        } else if (code == HTTP_REQUESTED_RANGE_NOT_SATISFIABLE) {
                            myLogW("416 Range not satisfiable — restarting from 0");
                            already = 0L;
                        } else if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                            emitFailed(TASK_NAME
                                    , "server returned: " + code + " - " + conn.getResponseMessage()
                                    , ctx.getString(R.string.server_returned_http) + " " + code);

                            return Result.failure();
                        }

                        contentLen = getContentLengthLongCompat(conn);
                        totalLen = (contentLen > 0 ? contentLen : -1L);
                        fileLenIfKnown = computeTotalFromHeadersOrFallback(conn, already, totalLen);

                        in = new BufferedInputStream(conn.getInputStream());
                        out = new FileOutputStream(outFile, already > 0L);

                        written = already;

                        lastPercent = (fileLenIfKnown > 0)
                                ? (int) ((written * 100L) / fileLenIfKnown)
                                : 0;

                        String txtNow = progressText(written, fileLenIfKnown);
                        setProgressAsync(new Data.Builder()
                                .putInt(PROG_PERCENT, lastPercent)
                                .putString(PROG_TEXT, txtNow)
                                .build());
                        updateForeground(ctx, notifId, lastPercent, txtNow, title);
                        emitStepProgress(TASK_NAME, lastPercent, txtNow);
                    }


                    int read = in.read(buf);
                    if (read == -1) break;
                    out.write(buf, 0, read);
                    written += read;

                    maybeUpdateProgress(ctx, notifId, written, fileLenIfKnown, title);
                }

                // Success
                emitTaskCompleted(TASK_NAME, outFile.getAbsolutePath(), ctx.getString(R.string.import_task_download) + " " + ctx.getString(R.string.import_task_complete));
                setProgressAsync(new Data.Builder()
                        .putInt(PROG_PERCENT, 100)
                        .putString(PROG_TEXT, progressText(written, fileLenIfKnown))
                        .putString(OUT_FILEPATH, outFile.getAbsolutePath())
                        .build());

                // Final “completed” notification tick
                updateForeground(ctx, notifId, 100, ctx.getString(R.string.downloaded), title);

                return Result.success(new Data.Builder()
                        .putString(OUT_FILEPATH, outFile.getAbsolutePath())
                        .build());

            } finally {
                try { if (out != null) out.flush(); } catch (Throwable ignored) {}
                safeClose(in);
                safeClose(out);
                if (conn != null) conn.disconnect();
                releaseDownloadLock();
            }

        } catch (UnknownHostException e) {
            TellHimWhyPause(ctx.getString(R.string.no_internet_connection));
            myLogE("No internet connection [" + e.getMessage() + "]");
            return Result.retry();
        } catch (SocketException e) {
            TellHimWhyPause(ctx.getString(R.string.connection_aborted) + " (" + ctx.getString(R.string.no_internet_connection) + "?)\n" + e.getMessage());
            myLogE("Connection aborted [" + e.getMessage() + "]");
            return Result.retry();
        } catch (IOException e) {
            if (NetworkHelper.isCleartextNotPermitted(e)) {
                String host = null;
                try { host = Uri.parse(urlStr).getHost(); } catch (Throwable ignore) {}
                String why = (host != null)
                        ? ctx.getString(R.string.http_cleartext_to) + " " + host + " " + ctx.getString(R.string.is_blocked_by_android_s_network_security_policy) + ". " +
                        ctx.getString(R.string.use_https_or_allow_cleartext_for_this_host_in_the_app_s_network_security_config)
                        : ctx.getString(R.string.http_is_blocked_by_android_s_network_security_policy)  + " " +
                        ctx.getString(R.string.use_https_or_allow_cleartext_for_this_host_in_the_app_s_network_security_config);
                emitFailed(TASK_NAME, "clear_text_not_permitted: [" + e.getMessage() + "]", why);
                return Result.failure();
            }
            TellHimWhyPause(ctx.getString(R.string.io_error) + " (" + ctx.getString(R.string.no_internet_connection) + "?)\n" + e.getMessage());
            myLogE("IO error [" + e.getMessage() + "]");
            return Result.retry();
        } catch (Exception e) {
            myLogEE(e, "Unexpected error in DownloadWorker");
            emitFailed(TASK_NAME
                    ,"unexpected_error: [" + e.getMessage() + "]"
                    ,getApplicationContext().getString(R.string.unexpected_error) + " [" + e.getMessage() + "]");
            return Result.retry(); // treat as transient
        } finally {
            try {
                getApplicationContext().unregisterReceiver(controls);
            } catch (Throwable ignored) {}
            releaseDownloadLock();
        }
    }

    @Override
    public void onStopped() {
        super.onStopped();
        myLogW("onStopped");
        stoppedRequested.set(true);
    }

    private void TellHimWhyPause(String whyPause) {
        pauseRequested.set(true); //TODO useless : You don’t need to switch the notification to the paused layout here because the Worker is exiting with Result.retry(); your UI gets the paused reason via TaskStateManager.
        resumeRequested.set(false);
        emitDownloadPause(whyPause);
    }
    // === Helpers ===

    private boolean isPolicyAllowed(Context ctx, boolean isManual) {
        // If you rely solely on Constraints, you can return true here.
        // If you still want your existing Option/NetworkUtils policy logic:
/*
        if (isManual) {
            Option.NetworkPolicyManual p = Option.getNetworkPolicyManualDownload();
            if (p == Option.NetworkPolicyManual.NEVER_ASK) return true;
            if (p == Option.NetworkPolicyManual.ASK_IF_NOT_WIFI) return NetworkUtils.isWifiConnected(ctx);
            if (p == Option.NetworkPolicyManual.ASK_IF_NOT_UNMETERED) return NetworkUtils.isUnmeteredConnected(ctx);
            return true;
        } else {
            Option.NetworkPolicyAuto p = Option.getNetworkPolicyAutoDownload();
            if (p == Option.NetworkPolicyAuto.ANY) return NetworkUtils.isNetworkAvailable(ctx);
            if (p == Option.NetworkPolicyAuto.WIFI) return NetworkUtils.isWifiConnected(ctx);
            if (p == Option.NetworkPolicyAuto.UNMETERED) return NetworkUtils.isUnmeteredConnected(ctx);
            return NetworkUtils.isNetworkAvailable(ctx);
        }
 */
        return NetworkHelper.isNetworkAvailable(ctx);
    }

    private void maybeUpdateProgress(Context ctx, int notifId, long written, long fileLenIfKnown, String title) {
        long now = System.currentTimeMillis();
        if (now - lastTick < MIN_UPDATE_INTERVAL_MS && fileLenIfKnown <= 0) return;

        String text = progressText(written, fileLenIfKnown);
        int percent = (fileLenIfKnown > 0) ? (int) ((written * 100L) / fileLenIfKnown) : 0;
        if (percent != lastPercent || now - lastTick >= MIN_UPDATE_INTERVAL_MS) {
            setProgressAsync(new Data.Builder()
                    .putInt(PROG_PERCENT, percent)
                    .putString(PROG_TEXT, text)
                    .build());
            updateForeground(ctx, notifId, percent, text, title);
            emitStepProgress(TASK_NAME, percent, text);
            lastPercent = percent;
            lastTick = now;
            if (percent > 0) myLogD("Progress " + percent + "% - " + text);
        }
    }

    private String progressText(long written, long totalIfKnown) {
        if (totalIfKnown > 0) {
            return String.format(Locale.US, "%s / %s",
                    formatSizeMB(written), formatSizeMB(totalIfKnown));
        }
        return String.format(Locale.US, "%s", formatSizeMB(written));
    }

    private ForegroundInfo buildForegroundInfo(Context ctx, int notifId, int percent, String text, String title) {
        Notification notif = buildNotification(ctx, notifId, percent, text, title);
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            return new ForegroundInfo(
                    notifId,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            );
        } else {
            return new ForegroundInfo(notifId, notif);
        }
    }
    private ForegroundInfo buildForegroundInfoPaused(Context ctx, int notifId, String title, String text) {
        Notification notif = buildNotificationPaused(ctx, notifId, text, title);
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            return new ForegroundInfo(
                    notifId,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            );
        } else {
            return new ForegroundInfo(notifId, notif);
        }
    }


    private void updateForeground(Context ctx, int notifId, int percent, String text, String title) {
        try {
            setForegroundAsync(buildForegroundInfo(ctx, notifId, percent, text, title));
        } catch (Exception ignored) {}
    }

    private Notification buildNotification(Context ctx, int notifId, int percent, String text, String title) {
        String contentTitle = (title != null && !title.isEmpty())
                ? ctx.getString(R.string.Downloading) + ": " + title
                : ctx.getString(R.string.Downloading);

        PendingIntent pausePI = actionPI(ctx, ACTION_PAUSE, notifId);
        PendingIntent cancelPI = actionPI(ctx, ACTION_CANCEL, notifId);

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setContentTitle(contentTitle)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_download_action_24)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setProgress(100, Math.max(0, Math.min(100, percent)), false)
                .addAction(new NotificationCompat.Action(0, ctx.getString(R.string.pause), pausePI))
                .addAction(new NotificationCompat.Action(0, ctx.getString(R.string.Cancel), cancelPI));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            b.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return b.build();
    }

    private Notification buildNotificationPaused(Context ctx, int notifId, String text, String title) {
        String contentTitle = (title != null && !title.isEmpty())
                ? ctx.getString(R.string.Downloading) + ": " + title
                : ctx.getString(R.string.Downloading);

        PendingIntent resumePI = actionPI(ctx, ACTION_RESUME, notifId);
        PendingIntent cancelPI = actionPI(ctx, ACTION_CANCEL, notifId);

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setContentTitle(contentTitle)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_download_action_24)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setProgress(100, 0, false)
                .addAction(new NotificationCompat.Action(0, ctx.getString(R.string.Resume), resumePI))
                .addAction(new NotificationCompat.Action(0, ctx.getString(R.string.Cancel), cancelPI));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            b.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return b.build();
    }

    private void enterPausedState(Context ctx, int notifId, String title, String why) {
        emitDownloadPause(why);
        // Rebuild notification to show RESUME/CANCEL only
        try {
            setForegroundAsync(buildForegroundInfoPaused(ctx, notifId, title, why)).get();
        } catch (Exception ignored) {}
    }

    private PendingIntent actionPI(Context ctx, String action, int requestCodeSeed) {
        Intent i = new Intent(action);
        i.setPackage(ctx.getPackageName()); // app-scoped implicit broadcast
        i.putExtra(EXTRA_WORK_ID, getId().toString());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(ctx, (requestCodeSeed ^ action.hashCode()), i, flags);
    }

    private void createNotificationChannel(Context ctx) {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Download Channel", NotificationManager.IMPORTANCE_LOW);
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.createNotificationChannel(ch);
    }

    private static long getContentLengthLongCompat(HttpURLConnection conn) {
        try {
            return conn.getContentLengthLong();
        } catch (Throwable ignored) {}
        // Fallback: parse header (may be -1)
        try {
            String v = conn.getHeaderField("Content-Length");
            if (v != null) return Long.parseLong(v);
        } catch (Throwable ignored) {}
        return -1L;
    }

    private static void safeClose(Object c) {
        try {
            if (c instanceof InputStream) ((InputStream) c).close();
            if (c instanceof FileOutputStream) ((FileOutputStream) c).close();
        } catch (Throwable ignored) {}
    }

    private static void safeDelete(File f) {
        try { if (f != null && f.exists()) f.delete(); } catch (Throwable ignored) {}
    }

    private long computeTotalFromHeadersOrFallback(HttpURLConnection conn, long already, long remainingLen) {
        // Try Content-Range: bytes start-end/total
        try {
            String cr = conn.getHeaderField("Content-Range");
            if (cr != null && cr.startsWith("bytes")) {
                int slash = cr.lastIndexOf('/');
                if (slash > 0) {
                    return Long.parseLong(cr.substring(slash + 1).trim()); // full size
                }
            }
        } catch (Throwable ignored) {}
        return (remainingLen > 0) ? (already + remainingLen) : -1L;
    }


    private boolean acquireDownloadLock(File outFile) {
        try {
            lockPath = Paths.get(outFile.getAbsolutePath() + ".lock");
            Path parent = lockPath.getParent();
            if (parent != null) Files.createDirectories(parent);

            // Keep channel open for the lifetime of the Worker; close in releaseDownloadLock()
            lockChannel = FileChannel.open(
                    lockPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE
                    // You could add StandardOpenOption.DELETE_ON_CLOSE and skip the manual delete below,
                    // but manual delete is fine and explicit.
            );

            try {
                downloadLock = lockChannel.tryLock(); // exclusive & non-blocking
                if (downloadLock == null) {
                    closeLockResources();
                    return false;
                }
                return true;
            } catch (OverlappingFileLockException e) {
                // same-process overlap
                closeLockResources();
                return false;
            }
        } catch (Exception e) {
            myLogW("Lock acquisition failed: " + e.getMessage());
            closeLockResources();
            return false;
        }
    }

    private void closeLockResources() {
        try { if (downloadLock != null && downloadLock.isValid()) downloadLock.release(); } catch (Exception ignored) {}
        try { if (lockChannel != null && lockChannel.isOpen()) lockChannel.close(); } catch (Exception ignored) {}
        try { if (lockPath != null) Files.deleteIfExists(lockPath); } catch (Exception ignored) {}
        downloadLock = null;
        lockChannel  = null;
        lockPath     = null;
    }

    private void releaseDownloadLock() {
        // same cleanup when you’re done
        closeLockResources();
    }

}
