package com.driot.bookplayer;

import static com.driot.bookplayer.utils.ComponentUtils.setOpenWithProxyEnabled;
import static com.driot.bookplayer.utils.ComponentUtils.setOpenWithProxyEnabled_all;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.Looper;
import android.os.StrictMode;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;

import com.driot.bookplayer.db.AppUpgrade;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.imports.ImportHelper;
import com.driot.bookplayer.utils.ComponentUtils;
import com.driot.bookplayer.utils.InAppMsgManager;
import com.driot.bookplayer.helpers.LocaleHelper;
import com.driot.bookplayer.objects.BookToAdd;
import com.driot.bookplayer.services.InAppPeriodicTaskManager;
import com.driot.bookplayer.utils.log.KanLogger;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import androidx.annotation.RequiresApi;

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
        Option.init(getApplicationContext());
        Pref.init(getApplicationContext());
        FirebaseAnalyticsHelper.init(getApplicationContext());

        myLog("oooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo");
        myLog("ooooooooooooooooooo      BOOKPLAYER      ooooooooooooooooooooo");
        myLog("oooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo");

        //TaskStateRepository.get().hydrateFromPrefs();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { //28
            enableStrictModeForDebugBuild();
        }

        Option.applyNightMode();

        InAppMsgManager.schedule(getApplicationContext());

        AppUpgrade.runMigrations(getApplicationContext());

        myLog("Context has been initialized");

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

        ComponentUtils.setAutomotiveEnabled(this, Option.getAutomotiveOn());
        myLog("Android Auto, allow connect: " + Option.getAutomotiveOn());

        ImportHelper.checkImportJobsAtStartUp(getApplicationContext());


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

    @RequiresApi(api = Build.VERSION_CODES.P) //28
    private void enableStrictModeForDebugBuild() {
        if (BuildConfig.DEBUG) {
            myLog("DEBUG => Strict mode set");
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    //.detectAll()            // catch disk/network/slow calls on main thread
                    .penaltyListener(Executors.newSingleThreadExecutor(), v -> logStrict("ThreadPolicy", v))
                    .penaltyFlashScreen()   // (optional) flash the screen when violation happens
                    .build());

            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectAll()            // catch leaks, file descriptor misuse, etc.
                    .penaltyListener(Executors.newSingleThreadExecutor(), v -> logStrict("VmPolicy", v))
                    .build());
        }
    }
    private static void logStrict(String policy, Throwable v) {
        // Only care about main thread violations:
        boolean isMain = Looper.myLooper() == Looper.getMainLooper();

        String type = v.getClass().getSimpleName();
        String thread = Thread.currentThread().getName();
        String appPkg = "com.driot.bookplayer";

        StackTraceElement[] st = v.getStackTrace();
        StackTraceElement firstApp = (st != null && st.length > 0) ? st[0] : null;
        if (st != null) {
            for (StackTraceElement e : st) {
                if (e.getClassName().startsWith(appPkg)) { firstApp = e; break; }
            }
        }
        String logMsg = "⚠️ StrictMode " + policy + ": " + type +
                " on [" + thread + "] at " +
                (firstApp != null ? firstApp.getClassName() + ":" + firstApp.getLineNumber() : "<no stack>");

        if (isMain) {
            myLogE("on MAIN " + logMsg);
        } else {
            myLogD(logMsg);
        }

        // 3) Full stack for details
        //myLogE(Log.getStackTraceString(v));
    }






}
