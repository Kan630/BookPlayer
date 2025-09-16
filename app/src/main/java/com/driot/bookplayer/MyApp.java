package com.driot.bookplayer;

import static com.driot.bookplayer.utils.ComponentUtils.setOpenWithProxyEnabled;
import static com.driot.bookplayer.utils.ComponentUtils.setOpenWithProxyEnabled_all;

import android.app.Application;
import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.IOException;

import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.LocaleHelper;
import com.driot.bookplayer.objects.BookToAdd;
import com.driot.bookplayer.objects.PlayList;
import com.driot.bookplayer.objects.TaskStateManager;
import com.driot.bookplayer.services.InAppPeriodicTaskManager;
import com.driot.bookplayer.utils.KanLogger;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 15/06/21
 * from https://stackoverflow.com/questions/19565685/saving-logcat-to-a-text-file-in-android-device
 */
public class MyApp extends Application {

    public static final String APP_FOLDER = "zeAppFolder";
    public static final String LOG_FOLDER = "logs";

    private static InAppPeriodicTaskManager periodicTaskManager;

    public static InAppPeriodicTaskManager getPeriodicTaskManager(Context context) {
        if (periodicTaskManager == null) {
            periodicTaskManager = new InAppPeriodicTaskManager(context, Var.PERIODIC_TASK_MANAGER_DELAY_IN_MINUTES);
        }
        return periodicTaskManager;
    }
    /**
     * Called when the application is starting, before any activity, service, or receiver objects (excluding content providers) have been created.
     */
    public void onCreate() {
        super.onCreate();
        KanLogger.init(getApplicationContext());
        BookToAdd.init(getApplicationContext());
        PlayList.initContext(getApplicationContext());
        Option.init(getApplicationContext());
        Pref.init(getApplicationContext());
        TaskStateManager.init(getApplicationContext());

        Option.applyNightMode();

        myLog("Context has been initialized");

        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG);
        myLogD("Crashlytics : " + !BuildConfig.DEBUG);

        LocaleHelper.applyAppLocale(Option.getAppLanguage());

        setOpenWithProxyEnabled_all(this, false);
        setOpenWithProxyEnabled(this, false);
        boolean openWithEnabled = Option.getOpenWith();
        boolean openWithEnabledAll = Option.getOpenWith_all();
        if (openWithEnabledAll) {
            setOpenWithProxyEnabled_all(this, true);
        } else if (openWithEnabled) {
            setOpenWithProxyEnabled(this, true);
        }
        myLog("Proxy setup: openWith=" + openWithEnabled + " / all=" + openWithEnabledAll);


        if ( isExternalStorageWritable() ) {

            File appDirectory = new File( Environment.getExternalStorageDirectory() + "/" + APP_FOLDER );
            File logDirectory = new File( appDirectory + "/" + LOG_FOLDER );
            File logFile = new File( logDirectory, "logcat_" + System.currentTimeMillis() + ".txt" );

            // create app folder
            if ( !appDirectory.exists() ) {
                appDirectory.mkdir();
            }

            // create log folder
            if ( !logDirectory.exists() ) {
                logDirectory.mkdir();
            }

            // clear the previous logcat and then write the new one to the file
            try {
                Process process = Runtime.getRuntime().exec("logcat -c");
                process = Runtime.getRuntime().exec("logcat -f " + logFile);
            } catch ( IOException e ) {
                e.printStackTrace();
            }

        } else if ( isExternalStorageReadable() ) {
            // only readable
        } else {
            // not accessible
        }
    }

    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    /* Checks if external storage is available to at least read */
    public boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state);
    }


    //--- LOG --------------------------
    private void myLogD(String str) { KanLogger.myLogD(this.getClass().getName(), str); }
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }

}
