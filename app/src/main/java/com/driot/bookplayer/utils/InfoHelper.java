package com.driot.bookplayer.utils;

import static com.driot.bookplayer.utils.KanLogger.isMyPhoneDev;
import static com.driot.bookplayer.utils.KanLogger.myKeyFirebase;
import static com.driot.bookplayer.utils.KanLogger.writeTechLogs;
import static com.driot.bookplayer.utils.TonioCommonStuff.MD5;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import androidx.core.app.NotificationManagerCompat;

import com.driot.bookplayer.BuildConfig;

import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;

public class InfoHelper {

    ////////////////////////////////////////////////////////////////////////////////////////
    // INIT
    ////////////////////////////////////////////////////////////////////////////////////////
    public static void printSomeStuffAboutDevice(Context context) {
        try {
            KanLogger.myLog("");
            KanLogger.myLog("========================== Fingerprint :");
            KanLogger.myLog("===");
            KanLogger.myLog("Build.FINGERPRINT = " + Build.FINGERPRINT);
            KanLogger.myLog("Build.FINGERPRINT MD5 = " + MD5(Build.FINGERPRINT));
            KanLogger.myLog("Phone is Dev ? => " + String.valueOf(isMyPhoneDev()));
            KanLogger.myLog("Write Tech Logs ? => " + String.valueOf(writeTechLogs()));
            KanLogger.myLog("========================== Device info :");
            KanLogger.myLog("Build.Version SDK = " + Build.VERSION.SDK_INT);
            KanLogger.myLog("Build.Release = " + Build.VERSION.RELEASE);
            KanLogger.myLog("Build.Base_OS = " + Build.VERSION.BASE_OS);
            KanLogger.myLog("========================== App info :");
            KanLogger.myLog("BuildConfig.VERSION_CODE = " + BuildConfig.VERSION_CODE);
            KanLogger.myLog("BuildConfig.VERSION_NAME = " + BuildConfig.VERSION_NAME);
            KanLogger.myLog("BuildConfig.BUILD_TYPE = " + BuildConfig.BUILD_TYPE);
            KanLogger.myLog("BuildConfig.APPLICATION_ID = " + BuildConfig.APPLICATION_ID);
            KanLogger.myLog("========================== Region :");
            KanLogger.myLog("Locale.getDefault = " + Locale.getDefault().getCountry());
            KanLogger.myLog("TimeZone.getDefault = " + TimeZone.getDefault().getID());
            KanLogger.myLog("TelephonyManager country = " + getCountryFromTelephonyManager(context));
            KanLogger.myLog("========================== Screen :");
            KanLogger.myLog("Width = " + getdisplayMetrics(context).widthPixels);
            KanLogger.myLog("Height = " + getdisplayMetrics(context).heightPixels);
            KanLogger.myLog("========================== Miscellaneous :");
            KanLogger.myLog("Notifications = " + getNotificationStatus(context));
            KanLogger.myLog("Theme = " + getKindOfTheme(context));
            KanLogger.myLog("===");
            KanLogger.myLog("==========================");
            KanLogger.myLog("");

            myKeyFirebase("Locale.getDefault", Locale.getDefault().getCountry());
            myKeyFirebase("TelephonyManager country", Objects.toString(getCountryFromTelephonyManager(context)));

        } catch (Exception e) {
            myLogEE(e, "printSomeStuffAboutDevice");
        }
    }

    private static String getNotificationStatus(Context context) {
        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        if (!manager.areNotificationsEnabled()) {
            return "disabled";
        } else {
            return "enabled";
        }
    }

    private static DisplayMetrics getdisplayMetrics(Context context) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager != null) {
            windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        }
        return displayMetrics;
    }

    private static String getCountryFromTelephonyManager(Context context) {
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        String countryIso = telephonyManager.getNetworkCountryIso(); // returns the country code, e.g., "us"
        return countryIso != null ? countryIso.toUpperCase() : null;
    }

    private static String getKindOfTheme(Context context) {
        int nightModeFlags =  context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
            return "Dark";
        } else {
            return "Light";
        }
    }
    // ----------------------- LOG -----------------------
    private static final String TAG = "Info";
    private static void myLog(String str) { KanLogger.myLog(TAG, str); }
    private static void myLogD(String str) { KanLogger.myLogD(TAG, str); }
    private static void myLogI(String str) { KanLogger.myLogI(TAG, str); }
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
    private static void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, TAG, str); }
    private static void myToastEE(Throwable t, String str) { KanLogger.myToastEE(t, TAG, str); }

}
