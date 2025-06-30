package com.driot.bookplayer.utils.log;

import static com.driot.bookplayer.utils.KanLogger.LOG_LIFECYCLE_TRACE;

import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.driot.bookplayer.utils.KanLogger;


public abstract class LoggingService extends Service {

    private static final String LOG_TAG = "Lifecycle";
    protected final String TAG_FROM_BRACKET = "[" + getClass().getSimpleName() + "]: ";
    protected final String TAG_FROM = "." + getClass().getSimpleName();

    @Override
    public void onCreate() {
        myInsideLog("onCreate()");
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        myInsideLog("onStartCommand() -Intent=[" + intent.toString() + "]");
        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        myInsideLog("onBind() -Intent=[" + intent.toString() + "]");
        return null; //TODO sure ?? not super ??
    }

    @Override
    public void onRebind(Intent intent) {
        myInsideLog("onRebind() -Intent=[" + intent.toString() + "]");
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        myInsideLog("onUnbind() -Intent=[" + intent.toString() + "]");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        myInsideLog("onDestroy()");
        super.onDestroy();
    }

    @Override
    public void onTrimMemory (int level) {
        myInsideLogE("onTrimMemory() - level=[" + level + "]");
        super.onTrimMemory(level);
    }

    @Override
    public void onLowMemory() {
        myInsideLogE("onLowMemory()");
        super.onLowMemory();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        myInsideLog("onConfigurationChanged() - newConfig=[" + newConfig.toString() + "]");
    }

    @Override
    public void onTimeout(int startId) {
        myInsideLogE("onTimeout() - startId=[" + startId + "]");
        super.onTimeout(startId);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        myInsideLog("onTaskRemoved() -Intent=[" + rootIntent.toString() + "]");
        super.onTaskRemoved(rootIntent);
    }

    /// ///////////////////////////////////////////////////////////////////
    ///            LOGGER                  (Extend Activity)
    /// ///////////////////////////////////////////////////////////////////

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
        KanLogger.myKeyFirebase(strKey, strValue);
    }

    protected void myLogFirebase(String strLog) {
        KanLogger.myLogFirebase(strLog);
    }

    /// ///////////////////////////////////////////////////////////////////
    ///            LOGGER        (For this specific Helper Class)
    /// ///////////////////////////////////////////////////////////////////

    private void myInsideLog(String str) { if (LOG_LIFECYCLE_TRACE) KanLogger.myLog(LOG_TAG, TAG_FROM_BRACKET + str); }
    private void myInsideLogE(String str) { if (LOG_LIFECYCLE_TRACE) KanLogger.myLogE(LOG_TAG, TAG_FROM_BRACKET + str); }
    private void myInsideLogEE(Throwable t, String str) { if (LOG_LIFECYCLE_TRACE) KanLogger.myLogEE(t, LOG_TAG,TAG_FROM_BRACKET + str); }

}
