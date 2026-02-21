package com.driot.bookplayer.db;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.global.Option;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

public final class AppUpgrade {

    // Set this to the app version where NOT_ROAMING was introduced
    private static final long VERSION_INTRODUCING_NOT_ROAMING = 170L;
    private static final long VERSION_INTRODUCING_HEATMAPS = 223L;
    private static final long VERSION_DISABLING_CLOUDFARE = 223L;

    private static final String KEY_LAST_MIGRATED_VERSION = "last_migrated_app_version";

    private AppUpgrade() {
    }

    public static void runMigrations(Context context) {
        myLogD("runMigrations");
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(Option.SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE);

        long current = getCurrentAppVersionCode(context);
        long last = prefs.getLong(KEY_LAST_MIGRATED_VERSION, 0L);

        // ALWAYS run the BookPrefs DB migration on version 26+ if not done yet
        final String KEY_MIGRATED_BOOK_PREFS = "migrated_book_prefs_to_db";
        if (!prefs.getBoolean(KEY_MIGRATED_BOOK_PREFS, false) && AppDatabase.APP_DATABASE_VERSION >= 26) {
            myLogI("Migrating book prefs to Folder DB");
            migrateBookPrefsToDb(context);
            prefs.edit().putBoolean(KEY_MIGRATED_BOOK_PREFS, true).apply();
        }

        // ---- Migration: introduce NOT_ROAMING (only if app was previously < XXX) ----
        if (last < VERSION_INTRODUCING_NOT_ROAMING) {
            myLogI("Migrating from version " + last + " to " + current);
            migrateAddNotRoaming(context, prefs);
            // Mark this migration as done
            prefs.edit().putLong(KEY_LAST_MIGRATED_VERSION, VERSION_INTRODUCING_NOT_ROAMING).apply();
        }

        if (last < VERSION_INTRODUCING_HEATMAPS) {
            if (!Option.getUseHeatmapForTracksActivityInitialized()) {
                Option.setUseHeatmapForTracksActivity(true);
            }
        }

        if (last < VERSION_DISABLING_CLOUDFARE) {
            Option.setRadioUseCloudflare(false);
        }

        // Optionally: record the current version (useful for future gates)
        if (current > last) {
            prefs.edit().putLong(KEY_LAST_MIGRATED_VERSION, Math.max(
                    prefs.getLong(KEY_LAST_MIGRATED_VERSION, 0L), current)).apply();
        }
    }

    private static void migrateAddNotRoaming(Context context, SharedPreferences prefs) {
        myLogD("migrateAddNotRoaming");
        // We only want to adjust old installs where enum ordinals shifted.
        // Old mapping (2 options): 0=ANY, 1=UNMETERED
        // New mapping (3 options): 0=ANY, 1=NOT_ROAMING, 2=UNMETERED
        // So if a user had stored 1 (UNMETERED), we must shift it to 2.

        // AUTO
        final String KEY_AUTO = "AUTO_DOWNLOAD_POLICY_KEY";
        int autoIdx = prefs.getInt(KEY_AUTO, Option.DEFAULT_AUTO_DOWNLOAD_POLICY.ordinal());
        if (autoIdx == 1) {
            myLogW("Old option value detected for " + KEY_AUTO + ", shifting : 1 => 2");
            prefs.edit().putInt(KEY_AUTO, 2).apply(); // shift UNMETERED -> index 2
        } else {
            clampEnumIndex(prefs, KEY_AUTO, NetworkHelper.NetworkPolicyAuto.values().length);
        }

        // MANUAL — if you also added NOT_ROAMING there, do the same; otherwise keep
        // as-is
        final String KEY_MANUAL = "MANUAL_DOWNLOAD_POLICY_KEY";
        int manualIdx = prefs.getInt(KEY_MANUAL, Option.DEFAULT_MANUAL_DOWNLOAD_POLICY.ordinal());
        // If manual enum also gained NOT_ROAMING in the middle:
        if (manualIdx == 1) {
            myLogW("Old option value detected for " + KEY_MANUAL + ", shifting : 1 => 2");
            prefs.edit().putInt(KEY_MANUAL, 2).apply();
        } else {
            clampEnumIndex(prefs, KEY_MANUAL, NetworkHelper.NetworkPolicyManual.values().length);
        }

        // If you also want to set *new defaults* only when the user never changed them,
        // you can detect "unset" by keeping a separate boolean or by comparing to your
        // old default.
        // Example (only set if the stored value equals the old default):
        // if (autoIdx == OLD_DEFAULT_AUTO.ordinal()) {
        // prefs.edit().putInt(KEY_AUTO, NEW_DEFAULT_AUTO.ordinal()).apply();
        // }
    }

    private static void clampEnumIndex(SharedPreferences prefs, String key, int length) {
        // defensive to avoid crash if there is less ordinals in the new options than
        // before => will switch to a new value inside range
        int idx = prefs.getInt(key, 0);
        int clamped = Math.max(0, Math.min(idx, length - 1));
        if (clamped != idx)
            prefs.edit().putInt(key, clamped).apply();
    }

    private static void migrateBookPrefsToDb(Context context) {
        myLogD("migrateBookPrefsToDb");
        SharedPreferences introCutPrefs = context.getSharedPreferences("SHARED_PREFERENCE_INTRO_CUT", MODE_PRIVATE);
        SharedPreferences speedPrefs = context.getSharedPreferences("SHARED_PREFERENCE_SPEED", MODE_PRIVATE);
        SharedPreferences bookPrefs = context.getSharedPreferences("book_prefs", MODE_PRIVATE);

        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(context);
            FolderDao folderDao = db.folderDao();

            java.util.List<Folder> folders = folderDao.getAll();
            if (folders != null) {
                for (Folder folder : folders) {
                    boolean changed = false;
                    long id = folder.getId();

                    // 1) introCut
                    if (introCutPrefs.contains(String.valueOf(id))) {
                        folder.cutIntro = introCutPrefs.getInt(String.valueOf(id), 0);
                        changed = true;
                    }

                    // 2) speed
                    if (speedPrefs.contains(String.valueOf(id))) {
                        try {
                            folder.speed = Double.parseDouble(speedPrefs.getString(String.valueOf(id), "1.0"));
                            changed = true;
                        } catch (Exception e) {
                            myLogE("Error parsing speed for folder " + id);
                        }
                    }

                    // 3) ttsVoice
                    String voiceKey = "BOOK_TTS_VOICE_" + id;
                    if (bookPrefs.contains(voiceKey)) {
                        folder.ttsVoice = bookPrefs.getString(voiceKey, null);
                        changed = true;
                    }

                    // 4) cover JSON
                    String initKey = "BOOK_COVER_INITIALS_" + id;
                    String colorKey = "BOOK_COVER_COLOR_" + id;
                    String roundedKey = "BOOK_COVER_ROUNDED_" + id;
                    String sizeKey = "BOOK_COVER_TEXT_SIZE_" + id;

                    if (bookPrefs.contains(initKey) || bookPrefs.contains(colorKey) || bookPrefs.contains(roundedKey)
                            || bookPrefs.contains(sizeKey)) {
                        try {
                            org.json.JSONObject coverObj = new org.json.JSONObject();
                            coverObj.put("initials", bookPrefs.getString(initKey, null));
                            if (bookPrefs.contains(colorKey)) {
                                coverObj.put("color", bookPrefs.getInt(colorKey, 0));
                            }
                            coverObj.put("rounded", bookPrefs.getBoolean(roundedKey, true));
                            coverObj.put("textSize", bookPrefs.getInt(sizeKey, 16));

                            org.json.JSONObject rootObj = new org.json.JSONObject();
                            rootObj.put("cover", coverObj);

                            folder.jsonData = rootObj.toString();
                            changed = true;
                        } catch (Exception e) {
                            myLogEE(e, "Error creating JSON for folder " + id);
                        }
                    }

                    if (changed) {
                        folderDao.update(folder);
                    }
                }
            }

            introCutPrefs.edit().clear().apply();
            speedPrefs.edit().clear().apply();
            bookPrefs.edit().clear().apply();

            SharedPreferences migPrefs = context.getSharedPreferences("MigrationSettings", MODE_PRIVATE);
            migPrefs.edit().putBoolean("KEY_MIGRATED_BOOK_PREFS", true).apply();
            myLogI("Migrated book preferences to database successfully.");
        });
    }

    private static long getCurrentAppVersionCode(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
            // longVersionCode on API 28+, versionCode on older
            return (android.os.Build.VERSION.SDK_INT >= 28) ? pi.getLongVersionCode() : pi.versionCode;
        } catch (Exception e) {
            return 0L;
        }
    }
}
