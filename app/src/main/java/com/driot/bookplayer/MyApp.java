package com.driot.bookplayer;

import static com.driot.bookplayer.utils.ComponentUtils.setOpenWithProxyEnabled;
import static com.driot.bookplayer.utils.ComponentUtils.setOpenWithProxyEnabled_all;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;

import com.driot.bookplayer.db.AppUpgrade;
import com.driot.bookplayer.db.DbClean;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.player.MediaControllerHolder;
import com.driot.bookplayer.radio.RadioBrowserServiceFactory;
import com.driot.bookplayer.tts.AppTtsManager;
import com.driot.bookplayer.utils.InAppMsgManager;
import com.driot.bookplayer.helpers.LocaleHelper;

import com.driot.bookplayer.services.InAppPeriodicTaskManager;
import com.driot.bookplayer.utils.SdCardChecker;
import com.driot.bookplayer.utils.log.KanLogger;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import androidx.annotation.RequiresApi;

import java.util.concurrent.Executors;

import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
public class MyApp extends Application {

    private static InAppPeriodicTaskManager periodicTaskManager;

    public static InAppPeriodicTaskManager getPeriodicTaskManager(Context context) {
        if (periodicTaskManager == null) {
            periodicTaskManager = new InAppPeriodicTaskManager(context, Var.PERIODIC_TASK_MANAGER_DELAY_IN_MINUTES);
        }
        return periodicTaskManager;
    }

    /**
     * Called when the application is starting, before any activity, service, or
     * receiver objects (excluding content providers) have been created.
     */
    public void onCreate() {
        super.onCreate();
        KanLogger.init(getApplicationContext());

        Option.init(getApplicationContext());
        Pref.init(getApplicationContext());
        FirebaseAnalyticsHelper.init(getApplicationContext()); // after pref

        myLogNoPrefix("oooooooooooooooooooooooooooooooooooooooooooooooooooooo");
        myLogNoPrefix("ooooooooooooooooooo BOOKPLAYER ooooooooooooooooooooooo");
        myLogNoPrefix("oooooooooooooooooooooooooooooooooooooooooooooooooooooo");

        // TaskStateRepository.get().hydrateFromPrefs();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { // 28
            enableStrictModeForDebugBuild();
        }

        Option.applyNightMode();

        SdCardChecker.isExternalSDCardAvailable(getApplicationContext()); // set cache

        InAppMsgManager.schedule(getApplicationContext());

        AppUpgrade.runMigrations(getApplicationContext());

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            DbClean.doClean(getApplicationContext(), true, true, false);
        }, Var.PERIODIC_DO_CLEAN_INITIAL_DELAY_IN_SECONDS * 1000);

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

        MediaControllerHolder.ensureConnected(getApplicationContext());

        RadioBrowserServiceFactory.init(getApplicationContext());

        Executors.newSingleThreadExecutor().execute(() -> {
            myLog("isNetworkAvailable : " + NetworkHelper.isNetworkAvailable(getApplicationContext()));
            myLog("isConnected : " + NetworkHelper.isConnected(getApplicationContext()));
            boolean hasInternet = NetworkHelper.hasInternet(getApplicationContext());
            myLog("hasInternet : " + hasInternet);
            boolean canPingBookPlayerWebSite = NetworkHelper.canReachUrl(Var.WEBSITE_URL);
            myLog("ping [" + Var.WEBSITE_URL + "] : " + canPingBookPlayerWebSite);
            if (hasInternet && !canPingBookPlayerWebSite) {
                myLogD("bookplayer website not reachable");
            }
        });

        AppTtsManager.init(getApplicationContext());

        // Initialize storage info cache calculation (runs in background)
        com.driot.bookplayer.helpers.StorageInfoCacheHelper.init(getApplicationContext());
    }
    /// STRICT MODE

    @RequiresApi(api = Build.VERSION_CODES.P) // 28
    private void enableStrictModeForDebugBuild() {
        if (!BuildConfig.DEBUG)
            return;

        java.util.concurrent.Executor direct = Runnable::run;

        myLog("DEBUG => Strict mode set");
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                // .detectAll() // catch disk/network/slow calls on main thread
                .penaltyListener(direct, v -> logStrict("ThreadPolicy", v))
                .penaltyFlashScreen() // (optional) flash the screen when violation happens
                .build());

        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectAll() // catch leaks, file descriptor misuse, etc.
                .penaltyListener(direct, v -> logStrict("VmPolicy", v))
                .build());
    }

    private static void logStrict(String policy, Throwable v) {

        boolean isMain = Looper.myLooper() == Looper.getMainLooper();

        String type = v.getClass().getSimpleName();
        String thread = Thread.currentThread().getName();
        String appPkg = "com.driot.bookplayer";

        StackTraceElement[] st = v.getStackTrace();
        StackTraceElement firstApp = (st != null && st.length > 0) ? st[0] : null;
        if (st != null) {
            for (StackTraceElement e : st) {
                if (e.getClassName().startsWith(appPkg)) {
                    firstApp = e;
                    break;
                }
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
        // myLogE(Log.getStackTraceString(v));
    }

}
