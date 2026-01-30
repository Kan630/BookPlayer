package com.driot.bookplayer.activities;

import static com.driot.bookplayer.db.AppDatabase.APP_DATABASE_VERSION;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;

import com.driot.bookplayer.widgets.StorageBarView;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.driot.bookplayer.BuildConfig;
import com.driot.bookplayer.R;
import com.driot.bookplayer.db.DatabaseBackupHelper;
import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.FileHelper;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.imports.ImportHelper;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.io.File;
import java.util.Locale;
import java.util.TimeZone;

public class StatsActivity extends LoggingActivity {

    private StatsViewModel viewModel;
    private StorageBarView storageBarInternal;
    private StorageBarView storageBarSDCard;
    private TextView tv1_body_internal;
    private TextView tv1_body_sdcard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);
        InsetHelper.apply(this);

        String strPowerManagement = getStringPowerManagement();
        myLogI("Power Management :" + strPowerManagement);

        // Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(StatsViewModel.class);

        // Setup UI references
        TextView tv_head_internal = findViewById(R.id.tv1_head);
        tv_head_internal.setText(R.string.physical_storage_memory);
        tv1_body_internal = findViewById(R.id.tv1_body_internal);
        tv1_body_sdcard = findViewById(R.id.tv1_body_sdcard);
        storageBarInternal = findViewById(R.id.storageBarInternal);
        storageBarSDCard = findViewById(R.id.storageBarSDCard);
        LinearLayout llSDCardStorage = findViewById(R.id.llSDCardStorage);

        // Observe internal storage data
        viewModel.getInternalStorageText().observe(this, text -> {
            if (text != null && tv1_body_internal != null) {
                tv1_body_internal.setText(text); // text is CharSequence (SpannableString) with colored linked audios
                tv1_body_internal.setVisibility(View.VISIBLE);
            }
        });

        viewModel.getInternalStorageInfo().observe(this, storageInfo -> {
            if (storageInfo != null && storageBarInternal != null) {
                storageBarInternal.setStorageValues(
                    storageInfo.totalStorageBytes,
                    storageInfo.usedByOthersBytes,
                    storageInfo.usedByBookPlayerBytes,
                    0, // expectedAddedMemory
                    storageInfo.linkedAudiosBytes, // linked audios
                    storageInfo.appStorageBytes // app storage (dark blue)
                );
                storageBarInternal.setVisibility(View.VISIBLE);
                storageBarInternal.invalidate();
            }
        });

        // Observe SD card storage data
        viewModel.getSdCardStorageText().observe(this, text -> {
            if (text != null && tv1_body_sdcard != null) {
                tv1_body_sdcard.setText(text); // text is CharSequence (SpannableString) with colored linked audios
            }
        });

        viewModel.getSdCardStorageInfo().observe(this, storageInfo -> {
            if (storageInfo != null && storageBarSDCard != null && llSDCardStorage != null) {
                storageBarSDCard.setStorageValues(
                    storageInfo.totalStorageBytes,
                    storageInfo.usedByOthersBytes,
                    storageInfo.usedByBookPlayerBytes,
                    0, // expectedAddedMemory
                    storageInfo.linkedAudiosBytes, // linked audios
                    0 // appStorage (only for internal storage)
                );
                // Force redraw to ensure the bar updates
                storageBarSDCard.invalidate();
                // Show the entire SD card storage section
                llSDCardStorage.setVisibility(View.VISIBLE);
                tv_head_internal.setText(R.string.device_storage_memory);
            }
        });

        // ----------------------------------------

        String zeText2 = "Android SDK version = " + Build.VERSION.SDK_INT
                + "\n" + "\n" + "Android version = " + Build.VERSION.RELEASE
                + "\n" + "\n" + "Android version name = " + getVersionName(Build.VERSION.SDK_INT)
                + "\n" + "\n" + "SQL lite version = " + getSqlLiteVersion()
                + "\n" + "\n" + "---"
                + "\n" + "\n" + "Bookplayer version number = " + BuildConfig.VERSION_CODE
                + "\n" + "\n" + "Bookplayer version label = " + BuildConfig.VERSION_NAME
                + "\n" + "\n" + "Bookplayer db version = " + APP_DATABASE_VERSION;

        TextView tv_head2 = findViewById(R.id.tv2_head);
        TextView tv_body2 = findViewById(R.id.tv2_body);
        tv_head2.setText(R.string.version);
        tv_body2.setText(zeText2);

        // ----------------------------------------

        String zeText3 = "Region Locale = " + Locale.getDefault().getCountry()
                + "\n" + "\n" + "Region TimeZone = " + TimeZone.getDefault().getID()
                + "\n" + "\n" + "Region SimCard = " + getCountryFromTelephonyManager(this)
                + "\n" + "\n" + "---"
                + "\n" + "\n" + "Theme = " + getKindOfTheme();

        TextView tv_head3 = findViewById(R.id.tv3_head);
        TextView tv_body3 = findViewById(R.id.tv3_body);
        tv_head3.setText(R.string.miscellaneous);
        tv_body3.setText(zeText3);

        // ----------------------------------------

        String zeText4 = "Install Date = " + Pref.getFirstOpenDate()
                + "\n" + "\n" + "Audio Time = " + Tonio.formatTime(Pref.getTotalMsPlayed())
                + "\n" + "* Book Time = " + Tonio.formatTime(Pref.getTotalMsPlayed(Var.PLAY_MODE_BOOK))
                + "\n" + "* TTS Time = " + Tonio.formatTime(Pref.getTotalMsPlayed(Var.PLAY_MODE_TTS))
                + "\n" + "* Radio Time = " + Tonio.formatTime(Pref.getTotalMsPlayed(Var.PLAY_MODE_RADIO))
                + "\n" + "* Podcast Time = " + Tonio.formatTime(Pref.getTotalMsPlayed(Var.PLAY_MODE_PODCAST))
                + "\n" + "\n" + "(These stats started in 2025 (oct-nov)";

        TextView tv_head4 = findViewById(R.id.tv4_head);
        TextView tv_body4 = findViewById(R.id.tv4_body);
        tv_head4.setText(R.string.menu_stats);
        tv_body4.setText(zeText4);

        // ----------------------------------------

        findViewById(R.id.bt_01).setOnClickListener(v -> openAppInfo());
        findViewById(R.id.bt_02).setOnClickListener(v -> deleteLogsClick());
        findViewById(R.id.bt_03).setOnClickListener(v -> deleteCachedImagesClick());
        findViewById(R.id.bt_04).setOnClickListener(v -> resetApp());

    }

    public void openAppInfo() {
        myLogI("--- user clicks OPEN APP INFO ---");
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", getPackageName(), null);
            intent.setData(uri);
            startActivity(intent);
        } catch (Exception e) {
            myLogEE(e, "openAppSettingsOnPhone()");
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
        myLogI("--- user clicks DELETE LOGS ---");
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.AskDelete_popupTitle))
                .setMessage(getString(R.string.DeleteLogs_AskConfirm))
                .setCancelable(false)
                .setPositiveButton(R.string.ok, (dialog, which) -> deleteLogs())
                .setNegativeButton(R.string.cancel, (dialogInterface, i) -> {
                })
                .show();
    }

    private void deleteLogs() {
        File dir = new File(this.getFilesDir(), "log");
        FileHelper.recursiveRemove(dir);
        recreate();
    }

    private void deleteCachedImagesClick() {
        myLogI("--- user clicks DELETE CACHED IMAGES ---");
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.AskDelete_popupTitle))
                .setMessage(getString(R.string.DeleteImages_AskConfirm))
                .setCancelable(false)
                .setPositiveButton(R.string.ok, (dialog, which) -> deleteCachedImages())
                .setNegativeButton(R.string.cancel, (dialogInterface, i) -> {
                })
                .show();
    }

    private void deleteCachedImages() {
        File dir = new File(this.getFilesDir(), "images");
        FileHelper.RemoveCachedImages(this, dir);
        recreate();
    }

    private void resetApp() {
        myLogI("--- user clicks RESET APP ---");
        ImportHelper.cancelCurrentImport(this);
        ImportHelper.cancelAll_in_DB(this);
        myToast(getString(com.driot.bookplayer.R.string.app_reset_done));
    }

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
            case Build.VERSION_CODES.O: // New minimum for BookPlayer as of 2024
                return "Oreo";
            case Build.VERSION_CODES.O_MR1:
                return "Oreo MR1";
            case Build.VERSION_CODES.P:
                return "Pie";
            case Build.VERSION_CODES.Q: // SDK 28 // Android 9
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
            case Build.VERSION_CODES.VANILLA_ICE_CREAM: // SDK 35 // Android 15
                return "Vanilla Ice Cream";
            case 36: // Android 16
                return "Baklava";
            default:
                return "Unknown";
        }
    }

    private String getStringPowerManagement() {
        // SDK23 min
        String strPowerManagement = "";
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null && powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
            strPowerManagement = getString(R.string.power_management_exempt);
        } else {
            strPowerManagement = getString(R.string.power_management_subject);
            // Consider prompting the user to disable optimizations
        }
        return strPowerManagement;
    }

    private String getSqlLiteVersion() {
        SupportSQLiteDatabase db = DatabaseClient.getInstance(getApplicationContext()).getAppDatabase().getOpenHelper()
                .getWritableDatabase();
        return DatabaseBackupHelper.getSQLiteVersion(db);
    }

}
