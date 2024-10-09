package com.driot.bookplayer.activities;

import static com.driot.bookplayer.global.Var.LOG_LIFECYCLE_TRACE;

import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.driot.bookplayer.utils.KanLogger;


public abstract class LifecycleLoggingService extends Service {

    private static final String LOG_TAG = "LifecycleLoggingService"; //this.getClass().getName()
    protected final String TAG = "[" + getClass().getSimpleName() + "]: ";
//String TAG = this.getClass().getName().substring(this.getClass().getName().lastIndexOf(".")+1);

    @Override
    public void onCreate() {
        myLog("onCreate()");
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        myLog("onStartCommand() -Intent=[" + intent.toString() + "]");
        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        myLog("onUnbind() -Intent=[" + intent.toString() + "]");
        return null;
    }

    @Override
    public void onRebind(Intent intent) {
        myLog("onRebind() -Intent=[" + intent.toString() + "]");
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        myLog("onUnbind() -Intent=[" + intent.toString() + "]");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        myLog("onDestroy()");
        super.onDestroy();
    }

    @Override
    public void onTrimMemory (int level) {
        myLogE("onTrimMemory() - level=[" + level + "]  (should not be compared, cf doc)");
        super.onTrimMemory(level);
    }

    @Override
    public void onLowMemory() {
        myLogE("onLowMemory()");
        super.onLowMemory();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        myLog("onTrimMemory() - newConfig=[" + newConfig.toString() + "]");
    }

    @Override
    public void onTimeout(int startId) {
        myLogE("onTrimMemory() - startId=[" + startId + "]");
        super.onTimeout(startId);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        myLog("onTaskRemoved() -Intent=[" + rootIntent.toString() + "]");
        super.onTaskRemoved(rootIntent);
    }

    private void myLog(String str) { if (LOG_LIFECYCLE_TRACE) KanLogger.myLog(LOG_TAG, TAG + str); }
    private void myLogE(String str) { if (LOG_LIFECYCLE_TRACE) KanLogger.myLogE(LOG_TAG, TAG + str); }
}
