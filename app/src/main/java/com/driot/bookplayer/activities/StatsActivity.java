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
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.Nullable;
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
import com.driot.bookplayer.helpers.GoogleServicesHelper;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.imports.ImportHelper;
import com.driot.bookplayer.utils.MsgBox;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.BaseActivity;

import java.io.File;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class StatsActivity extends BaseActivity {

    private static final int REQ_DELETE_LOGS = 2001;
    private static final int REQ_DELETE_CACHED_IMAGES = 2002;

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

        // Secret triple-tap (top-end) to open AdminActivity (same as LogListActivity)
        View secretEntry = findViewById(R.id.viewSecretEntry);
        final long[] taps = new long[3];
        secretEntry.setOnClickListener(v -> {
            System.arraycopy(taps, 1, taps, 0, taps.length - 1);
            taps[taps.length - 1] = System.currentTimeMillis();
            if (taps[0] >= System.currentTimeMillis() - 1000) {
                startActivity(new Intent(this, AdminActivity.class));
            }
        });

        // ----------------------------------------

        String zeText2 = "Android SDK version = " + Build.VERSION.SDK_INT
                + "\n" + "\n" + "Android version = " + Build.VERSION.RELEASE
                + "\n" + "\n" + "Android version name = " + getVersionName(Build.VERSION.SDK_INT)
                + "\n" + "\n" + "SQL lite version = " + getSqlLiteVersion()
                + "\n" + "\n" + "Google play Service = " + GoogleServicesHelper.getPlayServicesStatus(this)
                + "\n" + "\n" + "Play Service version = " + GoogleServicesHelper.getPlayServicesVersion(this)
                + "\n" + "\n" + "---"
                + "\n" + "\n" + "Bookplayer package = " + getPackageName()
                + "\n" + "\n" + "Bookplayer version number = " + BuildConfig.VERSION_CODE
                + "\n" + "\n" + "Bookplayer version label = " + BuildConfig.VERSION_NAME
                + "\n" + "\n" + "Bookplayer DB version = " + APP_DATABASE_VERSION;

        TextView tv_head2 = findViewById(R.id.tv2_head);
        TextView tv_body2 = findViewById(R.id.tv2_body);
        tv_head2.setText(R.string.Version);
        tv_body2.setText(zeText2);

        // ----------------------------------------

        String zeText3 = "Region Locale = " + Locale.getDefault().getCountry()
                + "\n" + "\n" + "Region TimeZone = " + TimeZone.getDefault().getID()
                + "\n" + "\n" + "Region SimCard = " + getCountryFromTelephonyManager(this)
                + "\n" + "\n" + "---"
                + "\n" + "\n" + "Theme = " + getKindOfTheme();

        TextView tv_head3 = findViewById(R.id.tv3_head);
        TextView tv_body3 = findViewById(R.id.tv3_body);
        tv_head3.setText(R.string.Miscellaneous);
        tv_body3.setText(zeText3);

        // ----------------------------------------

        // Format install date nicely (date only, using Locale)
        String installDateFormatted = formatInstallDate(Pref.getFirstOpenDate());

        // Check if install date is prior to December 1, 2025
        boolean showStatsStartedNote = isInstallDateBefore(Pref.getFirstOpenDate(), 2025, 12, 1);

        // Get duration values (raw ms for percentage calculation)
        long totalMs = Pref.getTotalMsPlayed();
        long bookMs = Pref.getTotalMsPlayed(Var.PLAY_MODE_BOOK);
        long ttsMs = Pref.getTotalMsPlayed(Var.PLAY_MODE_TTS);
        long radioMs = Pref.getTotalMsPlayed(Var.PLAY_MODE_RADIO);
        long podcastMs = Pref.getTotalMsPlayed(Var.PLAY_MODE_PODCAST);

        String bookTime = Tonio.formatTime(bookMs);
        String ttsTime = Tonio.formatTime(ttsMs);
        String radioTime = Tonio.formatTime(radioMs);
        String podcastTime = Tonio.formatTime(podcastMs);

        // Build text for main body (without Audio Time, it will be in table header)
        String zeText4 = getString(R.string.stats_install_date_label) + " " + installDateFormatted;

        TextView tv_stats_head = findViewById(R.id.tv_stats_head);
        TextView tv_stats_install_date = findViewById(R.id.tv_stats_install_date);
        TableLayout tableDurationDetails = findViewById(R.id.tableDurationDetails);
        TextView tv_duration_stats_note = findViewById(R.id.tv_duration_stats_note);

        tv_stats_head.setText(R.string.Usage);
        tv_stats_install_date.setText(zeText4);

        // Populate table with duration details (including Audio Time header and
        // percentage bars)
        String totalAudioTime = Tonio.formatTime(totalMs);
        populateDurationTable(tableDurationDetails, totalAudioTime, totalMs,
                bookTime, bookMs, ttsTime, ttsMs, radioTime, radioMs, podcastTime, podcastMs);

        // Show stats note if needed
        if (showStatsStartedNote) {
            tv_duration_stats_note.setText(R.string.stats_note);
            tv_duration_stats_note.setVisibility(View.VISIBLE);
        } else {
            tv_duration_stats_note.setVisibility(View.GONE);
        }

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
        MsgBox.ask(this,
                getString(R.string.AskDelete_popupTitle),
                getString(R.string.DeleteLogs_AskConfirm),
                null,
                getString(android.R.string.ok),
                getString(android.R.string.cancel),
                REQ_DELETE_LOGS);
    }

    private void deleteLogs() {
        File dir = new File(this.getFilesDir(), "log");
        FileHelper.recursiveRemove(dir);
        recreate();
    }

    private void deleteCachedImagesClick() {
        myLogI("--- user clicks DELETE CACHED IMAGES ---");
        MsgBox.ask(this,
                getString(R.string.AskDelete_popupTitle),
                getString(R.string.DeleteImages_AskConfirm),
                null,
                getString(android.R.string.ok),
                getString(android.R.string.cancel),
                REQ_DELETE_CACHED_IMAGES);
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
        myToast(getString(R.string.app_reset_done));
    }

    /**
     * Format install date string to display only the date (no time) using Locale
     * 
     * @param dateTimeString Date string in format "yyyy-MM-dd-HH'h'mm'm'ss's'" or
     *                       "yyyy-MM-dd HH:mm"
     * @return Formatted date string using Locale, or original string if parsing
     *         fails
     */
    private String formatInstallDate(String dateTimeString) {
        if (dateTimeString == null || dateTimeString.isEmpty()) {
            return "";
        }

        try {
            // Try to parse the format "yyyy-MM-dd-HH'h'mm'm'ss's'" (from
            // getCurrentDateTimeString)
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd-HH'h'mm'm'ss's'", Locale.US);
            Date date = inputFormat.parse(dateTimeString);

            if (date != null) {
                // Format using Locale with date only (no time)
                DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault());
                return dateFormat.format(date);
            }
        } catch (ParseException e) {
            // Try alternative format "yyyy-MM-dd HH:mm"
            try {
                SimpleDateFormat inputFormat2 = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
                Date date = inputFormat2.parse(dateTimeString);

                if (date != null) {
                    DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault());
                    return dateFormat.format(date);
                }
            } catch (ParseException e2) {
                // If both formats fail, try to extract just the date part (yyyy-MM-dd)
                if (dateTimeString.length() >= 10) {
                    String datePart = dateTimeString.substring(0, 10);
                    try {
                        SimpleDateFormat inputFormat3 = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                        Date date = inputFormat3.parse(datePart);

                        if (date != null) {
                            DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault());
                            return dateFormat.format(date);
                        }
                    } catch (ParseException e3) {
                        // Fall through to return original string
                    }
                }
            }
        }

        // If all parsing fails, return original string
        return dateTimeString;
    }

    /**
     * Check if install date is before a specified date
     * 
     * @param dateTimeString Date string to parse
     * @param year           Year to compare
     * @param month          Month to compare (1-12)
     * @param day            Day to compare
     * @return true if install date is before the specified date, false otherwise
     */
    private boolean isInstallDateBefore(String dateTimeString, int year, int month, int day) {
        if (dateTimeString == null || dateTimeString.isEmpty()) {
            return false;
        }

        try {
            Date installDate = null;

            // Try to parse the format "yyyy-MM-dd-HH'h'mm'm'ss's'"
            try {
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd-HH'h'mm'm'ss's'", Locale.US);
                installDate = inputFormat.parse(dateTimeString);
            } catch (ParseException e) {
                // Try alternative format "yyyy-MM-dd HH:mm"
                try {
                    SimpleDateFormat inputFormat2 = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
                    installDate = inputFormat2.parse(dateTimeString);
                } catch (ParseException e2) {
                    // Try to extract just the date part (yyyy-MM-dd)
                    if (dateTimeString.length() >= 10) {
                        String datePart = dateTimeString.substring(0, 10);
                        SimpleDateFormat inputFormat3 = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                        installDate = inputFormat3.parse(datePart);
                    }
                }
            }

            if (installDate != null) {
                Calendar installCalendar = Calendar.getInstance();
                installCalendar.setTime(installDate);

                Calendar compareCalendar = Calendar.getInstance();
                compareCalendar.set(year, month - 1, day, 0, 0, 0); // month is 0-based
                compareCalendar.set(Calendar.MILLISECOND, 0);

                return installCalendar.before(compareCalendar);
            }
        } catch (Exception e) {
            myLogEE(e, "Error checking install date");
        }

        return false;
    }

    /**
     * Populate TableLayout with duration details in a 2-column table format.
     * Detail rows show a gray bar whose length is the percentage of total audio
     * time.
     * 
     * @param tableLayout    The TableLayout to populate
     * @param totalAudioTime Total audio time string (for header)
     * @param totalMs        Total audio time in milliseconds (for percentage)
     * @param bookTime       Book time string
     * @param bookMs         Book time in ms
     * @param ttsTime        TTS time string
     * @param ttsMs          TTS time in ms
     * @param radioTime      Radio time string
     * @param radioMs        Radio time in ms
     * @param podcastTime    Podcast time string
     * @param podcastMs      Podcast time in ms
     */
    private void populateDurationTable(TableLayout tableLayout, String totalAudioTime, long totalMs,
            String bookTime, long bookMs, String ttsTime, long ttsMs, String radioTime, long radioMs,
            String podcastTime, long podcastMs) {
        if (tableLayout == null) {
            return;
        }

        // Clear existing rows
        tableLayout.removeAllViews();

        // Add header row with "Audio Time" in bold
        addTableHeaderRow(tableLayout, getString(R.string.stats_audio_time), totalAudioTime);

        // Create rows for each duration type with percentage bar
        addTableRowWithPercentage(tableLayout, getString(R.string.stats_book_time), bookTime, totalMs, bookMs);
        addTableRowWithPercentage(tableLayout, getString(R.string.stats_tts_time), ttsTime, totalMs, ttsMs);
        addTableRowWithPercentage(tableLayout, getString(R.string.stats_radio_time), radioTime, totalMs, radioMs);
        addTableRowWithPercentage(tableLayout, getString(R.string.stats_podcast_time), podcastTime, totalMs, podcastMs);
    }

    /**
     * Add a header row to the table with bold text
     * 
     * @param tableLayout The TableLayout
     * @param label       The label text (left column)
     * @param value       The value text (right column)
     */
    private void addTableHeaderRow(TableLayout tableLayout, String label, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }

        TableRow row = new TableRow(this);

        // Label TextView (left column) - bold
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setPadding(0, 4, 16, 4); // top, right, bottom, left
        labelView.setTextAppearance(this, R.style.simpleText);
        labelView.setTypeface(null, android.graphics.Typeface.BOLD);
        row.addView(labelView);

        // Value TextView (right column) - bold
        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setPadding(0, 4, 0, 4);
        valueView.setTextAppearance(this, R.style.simpleText);
        valueView.setTypeface(null, android.graphics.Typeface.BOLD);
        valueView.setGravity(android.view.Gravity.END); // Right-align values
        row.addView(valueView);

        tableLayout.addView(row);
    }

    /**
     * Add a row to the table with label and value, and a gray bar showing
     * percentage of total.
     * 
     * @param tableLayout The TableLayout
     * @param label       The label text (left column)
     * @param value       The value text (right column)
     * @param totalMs     Total audio time in ms (for percentage)
     * @param valueMs     This row's time in ms
     */
    private void addTableRowWithPercentage(TableLayout tableLayout, String label, String value, long totalMs,
            long valueMs) {
        if (valueMs <= 0)
            return;

        TableRow row = new TableRow(this);

        // Single cell: label + value, then a very fine underline (length = percentage
        // of total)
        int percentage = (totalMs > 0 && valueMs >= 0) ? (int) Math.round(100.0 * valueMs / totalMs) : 0;
        percentage = Math.min(100, Math.max(0, percentage));

        android.widget.LinearLayout cell = new android.widget.LinearLayout(this);
        cell.setOrientation(android.widget.LinearLayout.VERTICAL);

        // Text row: label + value
        android.widget.LinearLayout contentLayout = new android.widget.LinearLayout(this);
        contentLayout.setOrientation(android.widget.LinearLayout.HORIZONTAL);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setPadding(0, 4, 16, 4);
        labelView.setTextAppearance(this, R.style.simpleText);
        contentLayout.addView(labelView);

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setPadding(0, 4, 8, 4);
        valueView.setTextAppearance(this, R.style.simpleText);
        valueView.setGravity(android.view.Gravity.END);
        android.widget.LinearLayout.LayoutParams valueParams = new android.widget.LinearLayout.LayoutParams(0,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        contentLayout.addView(valueView, valueParams);

        cell.addView(contentLayout);

        // Very fine underline: length = percentage of row width
        View underline = new View(this);
        underline.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.gray_300));
        int lineHeightPx = (int) (1f * getResources().getDisplayMetrics().density); // 1dp
        android.widget.LinearLayout.LayoutParams lineParams = new android.widget.LinearLayout.LayoutParams(0,
                lineHeightPx, percentage);
        lineParams.topMargin = 2;
        android.widget.LinearLayout.LayoutParams lineSpacer = new android.widget.LinearLayout.LayoutParams(0,
                lineHeightPx, 100 - percentage);
        lineSpacer.topMargin = 2;

        android.widget.LinearLayout lineRow = new android.widget.LinearLayout(this);
        lineRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        lineRow.addView(underline, lineParams);
        lineRow.addView(new View(this), lineSpacer);
        cell.addView(lineRow);

        TableRow.LayoutParams spanParams = new TableRow.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        spanParams.span = 2;
        row.addView(cell, spanParams);

        tableLayout.addView(row);
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQ_DELETE_LOGS) {
                deleteLogs();
            } else if (requestCode == REQ_DELETE_CACHED_IMAGES) {
                deleteCachedImages();
            }
        }
    }
}
