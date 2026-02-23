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
import com.driot.bookplayer.helpers.StrictModeHelper;
import com.driot.bookplayer.player.MediaControllerHolder;
import com.driot.bookplayer.radio.RadioBrowserServiceFactory;
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { // 28
            // StrictModeHelper.enableStrictModeForDebugBuild();
        }

        // Centralized Prefs warm-up in background
        Executors.newSingleThreadExecutor().execute(() -> {
            Option.init(getApplicationContext());
            Pref.init(getApplicationContext());
            Option.warmUp();
            Pref.warmUp();
            FirebaseAnalyticsHelper.init(getApplicationContext());
            myLog("SharedPreferences warmed up in background");
        });

        myLogNoPrefix("oooooooooooooooooooooooooooooooooooooooooooooooooooooo");
        myLogNoPrefix("ooooooooooooooooooo BOOKPLAYER ooooooooooooooooooooooo");
        myLogNoPrefix("oooooooooooooooooooooooooooooooooooooooooooooooooooooo");

        // TaskStateRepository.get().hydrateFromPrefs();

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

        // Initialize storage info cache calculation (runs in background)
        Executors.newSingleThreadExecutor().execute(() -> {
            com.driot.bookplayer.helpers.StorageInfoCacheHelper.init(getApplicationContext());
        });
    }

}
