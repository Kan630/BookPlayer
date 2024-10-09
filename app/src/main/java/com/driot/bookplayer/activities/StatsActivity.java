package com.driot.bookplayer.activities;

import static com.driot.bookplayer.utils.Tonio.formatMem;
import static com.driot.bookplayer.utils.Tonio.getAppSize;
import static com.driot.bookplayer.utils.Tonio.getAvailableInternalMemorySize;
import static com.driot.bookplayer.utils.Tonio.getFolderSize;
import static com.driot.bookplayer.utils.Tonio.getTotaLInternalMemorySize;
import static com.driot.bookplayer.utils.Utils.recursiveRemove;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.driot.bookplayer.BuildConfig;
import com.driot.bookplayer.R;
import com.driot.bookplayer.utils.KanLogger;

import java.io.File;
import java.util.Locale;
import java.util.TimeZone;

public class StatsActivity extends LifecycleLoggingActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        String zeText;
        TextView tv_head;
        TextView tv_body;

        long totalMemory = getTotaLInternalMemorySize() / 1048576L;
        long availableMegs2 = getAvailableInternalMemorySize() / 1048576L;
        long currentAppSize = getAppSize(this) / 1048576L;

        // Hard Coded                        => "/data/data/com.driot.bookplayer/files/"
        // Context.getFilesDir().getPath()   =>  /data/user/0/com.driot.bookplayer/files/

        long currentAudiosSize = getFolderSize(this.getFilesDir().getPath() + "/unzipped") / 1048576L;
        long currentLogsSize = getFolderSize(this.getFilesDir().getPath() + "/log") / 1048576L;

        zeText = formatMem(currentAppSize) + " Mo taken by BookPlayer app" + "\n" + "\n" +
                formatMem(currentAudiosSize) + " Mo taken by Audios files" + "\n" + "\n" +
                formatMem(currentLogsSize) + " Mo taken by Logs" + "\n" + "\n" +
                "----" + "\n" +
                formatMem(availableMegs2) + " Mo : available on device" + "\n" + "\n" +
                formatMem(totalMemory) + " Mo : Device memory"
                ;

        tv_head = findViewById(R.id.tv1_head);
        tv_body = findViewById(R.id.tv1_body);
        tv_head.setText(R.string.physical_storage_memory);
        tv_body.setText(zeText);

        zeText =
                "Android SDK version = "  + Build.VERSION.SDK_INT + "\n" + "\n"
                        + "Android version = " + Build.VERSION.RELEASE + "\n" + "\n"
                        + "Android version name = " + getVersionName(Build.VERSION.SDK_INT) + "\n" + "\n"
                        + "---" + "\n" + "\n"
                        + "Bookplayer version number = " + BuildConfig.VERSION_CODE + "\n" + "\n"
                        + "Bookplayer version label = " + BuildConfig.VERSION_NAME
        ;

        tv_head = findViewById(R.id.tv2_head);
        tv_body = findViewById(R.id.tv2_body);
        tv_head.setText(R.string.version);
        tv_body.setText(zeText);

        zeText = "Region Locale = " + Locale.getDefault().getCountry()
                + "\n" + "\n" + "Region TimeZone = " + TimeZone.getDefault().getID()
                + "\n" + "\n" + "Region SimCard = " + getCountryFromTelephonyManager(this)
                + "\n" + "\n" + "---"
                + "\n" + "\n" + "Theme = " + getKindOfTheme()
                ;

        tv_head = findViewById(R.id.tv3_head);
        tv_body = findViewById(R.id.tv3_body);
        tv_head.setText(R.string.miscellaneous);
        tv_body.setText(zeText);

        findViewById(R.id.bt_01).setOnClickListener(v -> openAppInfo());
        findViewById(R.id.bt_02).setOnClickListener(v -> deleteLogsClick());
    }

    public void openAppInfo() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", getPackageName(), null);
            intent.setData(uri);
            startActivity(intent);
        } catch (Exception e) {
            myLogE("openAppSettingsOnPhone() => " + e.getMessage());
        }
    }
    private static String getCountryFromTelephonyManager(Context context) {
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        String countryIso = telephonyManager.getNetworkCountryIso(); // returns the country code, e.g., "us"
        return countryIso != null ? countryIso.toUpperCase() : null;
    }

    private String getKindOfTheme() {
        int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
            return "Dark";
        } else {
            return "Light";
        }
    }

    private void deleteLogsClick() {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.AskDelete_popupTitle))
                    .setMessage(getString(R.string.DeleteLogs_AskConfirm))
                    .setCancelable(false)
                    .setPositiveButton("ok", (dialog, which) -> deleteLogs())
                    .setNegativeButton("cancel", (dialogInterface, i) -> {})
                    .show();
    }
    private void deleteLogs() {
        File dir = new File(this.getFilesDir(), "log");
        recursiveRemove(dir);
        recreate();
    }
    //minSdkVersion
    public static String getVersionName(int sdkVersion) {
        switch (sdkVersion) {
            case Build.VERSION_CODES.BASE:
                return "Base";
            case Build.VERSION_CODES.BASE_1_1:
                return "Base 1.1";
            case Build.VERSION_CODES.CUPCAKE:
                return "Cupcake";
            case Build.VERSION_CODES.DONUT:
                return "Donut";
            case Build.VERSION_CODES.ECLAIR:
                return "Eclair";
            case Build.VERSION_CODES.ECLAIR_0_1:
                return "Eclair 0.1";
            case Build.VERSION_CODES.ECLAIR_MR1:
                return "Eclair MR1";
            case Build.VERSION_CODES.FROYO:
                return "Froyo";
            case Build.VERSION_CODES.GINGERBREAD:
                return "Gingerbread";
            case Build.VERSION_CODES.GINGERBREAD_MR1:
                return "Gingerbread MR1";
            case Build.VERSION_CODES.HONEYCOMB:
                return "Honeycomb";
            case Build.VERSION_CODES.HONEYCOMB_MR1:
                return "Honeycomb MR1";
            case Build.VERSION_CODES.HONEYCOMB_MR2:
                return "Honeycomb MR2";
            case Build.VERSION_CODES.ICE_CREAM_SANDWICH:
                return "Ice Cream Sandwich";
            case Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1:
                return "Ice Cream Sandwich MR1";
            case Build.VERSION_CODES.JELLY_BEAN:
                return "Jelly Bean";
            case Build.VERSION_CODES.JELLY_BEAN_MR1:
                return "Jelly Bean MR1";
            case Build.VERSION_CODES.JELLY_BEAN_MR2:
                return "Jelly Bean MR2";
            case Build.VERSION_CODES.KITKAT:
                return "KitKat";
            case Build.VERSION_CODES.KITKAT_WATCH:
                return "KitKat Watch";
            case Build.VERSION_CODES.LOLLIPOP:
                return "Lollipop";
            case Build.VERSION_CODES.LOLLIPOP_MR1:
                return "Lollipop MR1";
            case Build.VERSION_CODES.M:
                return "Marshmallow";
            case Build.VERSION_CODES.N:
                return "Nougat";
            case Build.VERSION_CODES.N_MR1:
                return "Nougat MR1";
            case Build.VERSION_CODES.O: //New minimum for BookPlayer as of 2024
                return "Oreo";
            case Build.VERSION_CODES.O_MR1:
                return "Oreo MR1";
            case Build.VERSION_CODES.P:
                return "Pie";
            case Build.VERSION_CODES.Q:
                return "Android 10";
            case Build.VERSION_CODES.R:
                return "Android 11";
            case Build.VERSION_CODES.S:
                return "Android 12";
            case Build.VERSION_CODES.S_V2:
                return "Android 12.1";
            case Build.VERSION_CODES.TIRAMISU:
                return "Tiramisu";
            case Build.VERSION_CODES.UPSIDE_DOWN_CAKE:
                return "Upside Down Cake";
            default:
                return "Unknown";
        }
    }

    //--- LOG --------------------------
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogD(String str) { KanLogger.myLogD(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
}
