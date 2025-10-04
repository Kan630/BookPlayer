package com.driot.bookplayer.utils.log;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.utils.KanLogger;
import com.google.common.util.concurrent.ListenableFuture;


import static com.driot.bookplayer.utils.KanLogger.LOG_LIFECYCLE_TRACE;

public abstract class LoggingWorker extends Worker {

    protected final String TAG_FROM_BRACKET = "[" + getClass().getSimpleName() + "]: ";
    protected final String TAG_FROM = "." + getClass().getSimpleName();

    public LoggingWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        myInsideLogD("Constructor");
    }

    @Override
    public void onStopped() {
        super.onStopped();
        myInsideLogD("onStopped");
    }

    /**
     * Newer API: WorkManager may call this to enter foreground
     */
    @NonNull @Override
    public ListenableFuture<ForegroundInfo> getForegroundInfoAsync() {
        myInsideLogD("getForegroundInfoAsync() requested");
        return super.getForegroundInfoAsync();
    }

    /** Older/sync API still called in some paths */
    @NonNull @Override
    public ForegroundInfo getForegroundInfo() {
        myInsideLogD("getForegroundInfo() requested");
        return super.getForegroundInfo(); // or provide a common default
    }


    //////////////////////////////////////////////////////////////////////////////////////////
    // PUBLIC LOGGER HELPERS (like LoggingService)
    //////////////////////////////////////////////////////////////////////////////////////////

    protected void myLog(String str) {
        KanLogger.myLog(TAG_FROM, str);
    }

    protected void myLogD(String str) {
        KanLogger.myLogD(TAG_FROM, str);
    }

    protected void myLogI(String str) {
        KanLogger.myLogI(TAG_FROM, str);
    }

    protected void myLogW(String str) {
        KanLogger.myLogW(TAG_FROM, str);
    }

    protected void myLogE(String str) {
        KanLogger.myLogE(TAG_FROM, str);
    }

    protected void myLogEE(Throwable t, String str) {
        KanLogger.myLogEE(t, TAG_FROM, str);
    }

    protected void myLogInFile(String str) {
        KanLogger.myLogInFile(TAG_FROM, str);
    }

    protected void myToast(String str) {
        KanLogger.myToast(TAG_FROM, str);
    }

    protected void myToastE(String str) {
        KanLogger.myToastE(TAG_FROM, str);
    }

    protected void myToastEE(Throwable t, String str) {
        KanLogger.myToastEE(t, TAG_FROM, str);
    }

    protected void myLongToast(String str) {
        KanLogger.myToastLong(TAG_FROM, str);
    }

    protected void myKeyFirebase(String strKey, String strValue) {
        FirebaseAnalyticsHelper.setCustomKeyCrashlytics(strKey, strValue);
    }

    protected void myLogFirebase(String strLog) {
        FirebaseAnalyticsHelper.logCrashlytics(strLog);
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    // INTERNAL WORKER LOGGER (like Lifecycle logs)
    //////////////////////////////////////////////////////////////////////////////////////////

    private void myInsideLog(String str) {
        if (LOG_LIFECYCLE_TRACE)
            KanLogger.myLog("Lifecycle", TAG_FROM_BRACKET + str);
    }

    private void myInsideLogD(String str) {
        if (LOG_LIFECYCLE_TRACE)
            KanLogger.myLogD("Lifecycle", TAG_FROM_BRACKET + str);
    }

    private void myInsideLogW(String str) {
        if (LOG_LIFECYCLE_TRACE)
            KanLogger.myLogW("Lifecycle", TAG_FROM_BRACKET + str);
    }

    private void myInsideLogE(String str) {
        if (LOG_LIFECYCLE_TRACE)
            KanLogger.myLogE("Lifecycle", TAG_FROM_BRACKET + str);
    }

    private void myInsideLogEE(Throwable t, String str) {
        if (LOG_LIFECYCLE_TRACE)
            KanLogger.myLogEE(t, "Lifecycle", TAG_FROM_BRACKET + str);
    }
}
