package com.driot.bookplayer.helpers;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.myLog;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.myLogD;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.myLogE;

import android.os.Build;
import android.os.Looper;
import android.os.StrictMode;

import androidx.annotation.RequiresApi;

import com.driot.bookplayer.BuildConfig;

public class StrictModeHelper {

    /// STRICT MODE

    @RequiresApi(api = Build.VERSION_CODES.P) // 28
    public static void enableStrictModeForDebugBuild() {
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
