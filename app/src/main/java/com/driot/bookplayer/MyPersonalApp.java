package com.driot.bookplayer;

import static com.driot.bookplayer.utils.ComponentUtils.setOpenWithProxyEnabled;

import android.app.Application;
import android.os.Environment;

import java.io.File;
import java.io.IOException;

import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.objects.PlayList;
import com.driot.bookplayer.utils.KanLogger;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 15/06/21
 * from https://stackoverflow.com/questions/19565685/saving-logcat-to-a-text-file-in-android-device
 */
public class MyPersonalApp extends Application {

    public static final String APP_FOLDER = "zeAppFolder";
    public static final String LOG_FOLDER = "logs";
    /**
     * Called when the application is starting, before any activity, service, or receiver objects (excluding content providers) have been created.
     */
    public void onCreate() {
        super.onCreate();
        myLog("onCreate()... for myLogExtendApp");

        PlayList.initContext(getApplicationContext());
        KanLogger.init(getApplicationContext());
        Option.init(getApplicationContext());

        myLog("Context has been initialized");

        boolean openWithEnabled = Option.getOpenWith();
        setOpenWithProxyEnabled(this, openWithEnabled);


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
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }

}
