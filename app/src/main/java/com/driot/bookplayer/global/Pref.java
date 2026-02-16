package com.driot.bookplayer.global;
// created by Antoine Driot -- antoine.driot.com -- on 06/06/2025

import static android.content.Context.MODE_PRIVATE;

import static com.driot.bookplayer.global.Var.PODCAST_DETAIL_ANIMATION_COUNT;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;
import com.driot.bookplayer.utils.Tonio;

import java.util.concurrent.Executors;

public class Pref {

    private static final String SHARED_PREFERENCES_DIVERSE = "SHARED_PREFERENCES_DIVERSE";
    private static final String SHARED_PREFERENCES_STATS = "SHARED_PREFERENCES_STATS";
    private static final String SHARED_PREFERENCE_INTRO_CUT = "SHARED_PREFERENCE_INTRO_CUT";
    private static final String SHARED_PREFERENCE_TIMESTAMP = "SHARED_PREFERENCE_TIMESTAMP";
    public static final String SHARED_PREFERENCE_MIGRATION = "SHARED_PREFERENCE_MIGRATION";

    private static Context appContext;
    private static android.content.SharedPreferences prefs;
    private static android.content.SharedPreferences stats;
    private static android.content.SharedPreferences timeStamp;

    public static void init(Context context) {
        appContext = context.getApplicationContext();
        PrefMigration.run(appContext);

        prefs = appContext.getSharedPreferences(SHARED_PREFERENCES_DIVERSE, MODE_PRIVATE);
        stats = appContext.getSharedPreferences(SHARED_PREFERENCES_STATS, MODE_PRIVATE);
        timeStamp = appContext.getSharedPreferences(SHARED_PREFERENCE_TIMESTAMP, MODE_PRIVATE);

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                if (getFirstOpenTimeStamp() == 0)
                    setFirstOpen();
            } catch (Exception ignored) {
            }
        });
    }

    public static long getLastDbClean() {
        return timeStamp.getLong("DB_CLEAN", 0);
    }

    public static void setLastDbClean() {
        timeStamp.edit().putLong("DB_CLEAN", System.currentTimeMillis()).apply();
    }

    public static String get_radio_mirror() {
        return prefs.getString("RADIO_MIRROR", Var.DEFAULT_RADIO_MIRROR);
    }

    public static void set_radio_mirror(String strValue) {
        prefs.edit().putString("RADIO_MIRROR", strValue).apply();
    }

    /////////////////// HAS BEEN PAUSED FOR ///////////////////
    public static void setPauseTime(long value) {
        prefs.edit().putLong("PAUSE_TIME", value).apply();
    }

    public static void setPauseTime() {
        prefs.edit().putLong("PAUSE_TIME", System.currentTimeMillis()).apply();
        myLog("pause time set");
    }

    public static long getPauseTime() {
        return prefs.getLong("PAUSE_TIME", 0);
    }

    /////////////////// PER BOOK ID ///////////////////

    public static void saveIntroCutToPref(Context c, int idFolder, int introCut) {
        try {
            SharedPreferences.Editor editor = c.getSharedPreferences(SHARED_PREFERENCE_INTRO_CUT, MODE_PRIVATE).edit();
            editor.putInt(Integer.toString(idFolder), introCut).apply();
        } catch (Exception e) {
            myLogEE(e, "error saving introCut in prefs");
        }
    }

    public static int getIntroCutFromPref(Context c, int idFolder) {
        try {
            SharedPreferences prefs = c.getSharedPreferences(SHARED_PREFERENCE_INTRO_CUT, MODE_PRIVATE);
            return prefs.getInt(String.valueOf(idFolder), 0);
        } catch (Exception e) {
            myLogEE(e, "error getting introCut from prefs");
            return 0;
        }
    }

    public static void saveSpeedToPref(int idFolder, double speed) {
        try {
            SharedPreferences.Editor editor = appContext.getSharedPreferences("SHARED_PREFERENCE_SPEED", MODE_PRIVATE)
                    .edit();
            editor.putString(String.valueOf(idFolder), Double.toString(speed)).apply();
        } catch (Exception e) {
            myLogEE(e, "error saving speed in prefs");
        }
    }

    public static double getSpeedFromPref(int idFolder) {
        try {
            SharedPreferences prefs = appContext.getSharedPreferences("SHARED_PREFERENCE_SPEED", MODE_PRIVATE);
            return Double.parseDouble(prefs.getString(String.valueOf(idFolder), "1.0"));
        } catch (Exception e) {
            myLogEE(e, "error getting speed from prefs");
            return 1.0;
        }
    }

    /////////////////// LANGUAGE SPINNER ///////////////////
    ///
    public static void set_Audio_Language_Librivox(Context c, String audioLanguage) {
        c.getSharedPreferences(SHARED_PREFERENCES_DIVERSE, MODE_PRIVATE).edit()
                .putString("AUDIO_LANGUAGE_LIBRIVOX", audioLanguage).apply();
    }

    public static String get_Audio_Language_Librivox(Context c) {
        return c.getSharedPreferences(SHARED_PREFERENCES_DIVERSE, MODE_PRIVATE).getString("AUDIO_LANGUAGE_LIBRIVOX",
                "eng");
    }

    public static void set_Audio_Language_Podcast(Context c, String audioLanguage) {
        c.getSharedPreferences(SHARED_PREFERENCES_DIVERSE, MODE_PRIVATE).edit()
                .putString("AUDIO_LANGUAGE_PODCAST", audioLanguage).apply();
    }

    public static String get_Audio_Language_Podcast(Context c) {
        return c.getSharedPreferences(SHARED_PREFERENCES_DIVERSE, MODE_PRIVATE).getString("AUDIO_LANGUAGE_PODCAST",
                "en");
    }

    public static void set_Audio_Language_Ebook(Context c, String audioLanguage) {
        c.getSharedPreferences(SHARED_PREFERENCES_DIVERSE, MODE_PRIVATE).edit()
                .putString("AUDIO_LANGUAGE_EBOOK", audioLanguage).apply();
    }

    public static String get_Audio_Language_Ebook(Context c) {
        return c.getSharedPreferences(SHARED_PREFERENCES_DIVERSE, MODE_PRIVATE).getString("AUDIO_LANGUAGE_EBOOK", "en");
    }

    public static void set_Audio_Language_Radio(Context c, String audioLanguage) {
        c.getSharedPreferences(SHARED_PREFERENCES_DIVERSE, MODE_PRIVATE)
                .edit()
                .putString("AUDIO_LANGUAGE_RADIO", audioLanguage)
                .apply();
    }

    public static String get_Audio_Language_Radio(Context c) {
        return c.getSharedPreferences(SHARED_PREFERENCES_DIVERSE, MODE_PRIVATE)
                .getString("AUDIO_LANGUAGE_RADIO", "en");
    }

    public static void setBookTtsVoiceName(Context c, int folderId, String voiceName) {
        c.getSharedPreferences("book_prefs", Context.MODE_PRIVATE).edit()
                .putString("BOOK_TTS_VOICE_" + folderId, voiceName).apply();
    }

    public static String getBookTtsVoiceName(Context c, int folderId) {
        return c.getSharedPreferences("book_prefs", Context.MODE_PRIVATE).getString("BOOK_TTS_VOICE_" + folderId, null);
    }

    /////////////////// STATS ///////////////////
    ///
    public static void setFirstOpen() {
        stats.edit().putLong("FIRST_OPEN_TIMESTAMP", System.currentTimeMillis()).apply();
        stats.edit().putString("FIRST_OPEN_DATE", Tonio.getCurrentDateTimeString()).apply();
    }

    public static Long getFirstOpenTimeStamp() {
        return stats.getLong("FIRST_OPEN_TIMESTAMP", 0);
    }

    public static String getFirstOpenDate() {
        return stats.getString("FIRST_OPEN_DATE", "");
    }

    public static void addToTotalMsPlayed(String playMode, long ms) {
        stats.edit().putLong("TOTAL_PLAY_IN_MS", stats.getLong("TOTAL_PLAY_IN_MS", 0) + ms).apply();
        stats.edit().putLong("TOTAL_PLAY_IN_MS_" + playMode, stats.getLong("TOTAL_PLAY_IN_MS_" + playMode, 0) + ms)
                .apply();
    }

    public static Long getTotalMsPlayed() {
        return stats.getLong("TOTAL_PLAY_IN_MS", 0);
    }

    public static Long getTotalMsPlayed(String playMode) {
        return stats.getLong("TOTAL_PLAY_IN_MS_" + playMode, 0);
    }

    /////////////////// PODCAST DETAIL FAVORITE and AUTODOWNLOAD animations
    /////////////////// ///////////////////
    ///
    public enum AnimatedButton {
        FAVORITE,
        AUTO_DOWNLOAD
    }

    public static boolean shouldAnimateButtons(AnimatedButton button) {
        int opens = prefs.getInt("ANIMATE_BUTTON_COUNT_" + button, 0);
        if (opens < PODCAST_DETAIL_ANIMATION_COUNT) {
            prefs.edit().putInt("ANIMATE_BUTTON_COUNT_" + button, opens + 1).apply();
            return true;
        }
        return false;
    }

    public static void stopAnimateButtons(AnimatedButton button) {
        prefs.edit().putInt("ANIMATE_BUTTON_COUNT_" + button, PODCAST_DETAIL_ANIMATION_COUNT).apply();
    }

    /////////////////// LAST PODCAST API CHECK ///////////////////

    public static void setLastCheckPodcastAutoDownload(long value) {
        prefs.edit().putLong("LAST_PODCASTINDEXORG_API_AUTO_CHECK_TIMESTAMP", value).apply();
    }

    public static long getLastCheckPodcastAutoDownload() {
        return prefs.getLong("LAST_PODCASTINDEXORG_API_AUTO_CHECK_TIMESTAMP",
                Option.getPodcastAutoDownloadDelayBetweenChecks());
    }

    public static boolean doCheckForPodcastAutoDownload() {
        long lastCheck = getLastCheckPodcastAutoDownload();
        long now = System.currentTimeMillis();
        long diffInMinutes = (now - lastCheck) / (60 * 1000);
        long minDelayBetweenCheck = (long) Option.getPodcastAutoDownloadDelayBetweenChecks();
        if (now - lastCheck > minDelayBetweenCheck * 60 * 1000) {
            setLastCheckPodcastAutoDownload(now);
            // myLogD("shouldCheckApiForAutoDownload() => true - last check = " +
            // diffInMinutes + " min ago... (min delay = " + minDelayBetweenCheck + "
            // min.)");
            return true;
        } else {
            // myLogD("shouldCheckApiForAutoDownload() => false - last check = " +
            // diffInMinutes + " min ago... (min delay = " + minDelayBetweenCheck + "
            // min.)");
            return false;
        }
    }

    /////////////////// BOOK COVER CREATION ///////////////////

    // Keys
    private static String kCoverInitials(long folderId) {
        return "BOOK_COVER_INITIALS_" + folderId;
    }

    private static String kCoverColor(long folderId) {
        return "BOOK_COVER_COLOR_" + folderId;
    }

    private static String kCoverRounded(long folderId) {
        return "BOOK_COVER_ROUNDED_" + folderId;
    }

    // Setters
    public static void setBookCoverInitials(Context c, long folderId, String initials) {
        c.getSharedPreferences("book_prefs", Context.MODE_PRIVATE)
                .edit().putString(kCoverInitials(folderId), initials).apply();
    }

    public static void setBookCoverColor(Context c, long folderId, int color) {
        c.getSharedPreferences("book_prefs", Context.MODE_PRIVATE)
                .edit().putInt(kCoverColor(folderId), color).apply();
    }

    public static void setBookCoverRounded(Context c, long folderId, boolean rounded) {
        c.getSharedPreferences("book_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean(kCoverRounded(folderId), rounded).apply();
    }

    // Getters
    @Nullable
    public static String getBookCoverInitials(Context c, long folderId) {
        return c.getSharedPreferences("book_prefs", Context.MODE_PRIVATE)
                .getString(kCoverInitials(folderId), null);
    }

    public static Integer getBookCoverColorOrNull(Context c, long folderId) {
        String key = kCoverColor(folderId);
        if (!c.getSharedPreferences("book_prefs", Context.MODE_PRIVATE).contains(key))
            return null;
        return c.getSharedPreferences("book_prefs", Context.MODE_PRIVATE).getInt(key, 0);
    }

    public static Boolean getBookCoverRoundedOrNull(Context c, long folderId) {
        String key = kCoverRounded(folderId);
        if (!c.getSharedPreferences("book_prefs", Context.MODE_PRIVATE).contains(key))
            return null;
        return c.getSharedPreferences("book_prefs", Context.MODE_PRIVATE).getBoolean(key, true);
    }

    // New Params
    private static String kCoverTextSize(long folderId) {
        return "BOOK_COVER_TEXT_SIZE_" + folderId;
    }

    public static void setBookCoverTextSize(Context c, long folderId, int size) {
        c.getSharedPreferences("book_prefs", Context.MODE_PRIVATE)
                .edit().putInt(kCoverTextSize(folderId), size).apply();
    }

    public static int getBookCoverTextSize(Context c, long folderId) {
        return c.getSharedPreferences("book_prefs", Context.MODE_PRIVATE)
                .getInt(kCoverTextSize(folderId), 16); // Default 16 (mid of 12-20)
    }

    /////////////////// STORAGE INFO CACHE ///////////////////

    // Internal Storage
    public static void setStorageInternalTotal(long value) {
        prefs.edit().putLong("STORAGE_INTERNAL_TOTAL", value).apply();
    }

    public static long getStorageInternalTotal() {
        return prefs.getLong("STORAGE_INTERNAL_TOTAL", 0);
    }

    public static void setStorageInternalUsedByOthers(long value) {
        prefs.edit().putLong("STORAGE_INTERNAL_USED_BY_OTHERS", value).apply();
    }

    public static long getStorageInternalUsedByOthers() {
        return prefs.getLong("STORAGE_INTERNAL_USED_BY_OTHERS", 0);
    }

    public static void setStorageInternalUsedByBookPlayer(long value) {
        prefs.edit().putLong("STORAGE_INTERNAL_USED_BY_BOOKPLAYER", value).apply();
    }

    public static long getStorageInternalUsedByBookPlayer() {
        return prefs.getLong("STORAGE_INTERNAL_USED_BY_BOOKPLAYER", 0);
    }

    public static void setStorageInternalTimestamp(long value) {
        prefs.edit().putLong("STORAGE_INTERNAL_TIMESTAMP", value).apply();
    }

    public static long getStorageInternalTimestamp() {
        return prefs.getLong("STORAGE_INTERNAL_TIMESTAMP", 0);
    }

    // SD Card Storage
    public static void setStorageSDCardTotal(long value) {
        prefs.edit().putLong("STORAGE_SDCARD_TOTAL", value).apply();
    }

    public static long getStorageSDCardTotal() {
        return prefs.getLong("STORAGE_SDCARD_TOTAL", 0);
    }

    public static void setStorageSDCardUsedByOthers(long value) {
        prefs.edit().putLong("STORAGE_SDCARD_USED_BY_OTHERS", value).apply();
    }

    public static long getStorageSDCardUsedByOthers() {
        return prefs.getLong("STORAGE_SDCARD_USED_BY_OTHERS", 0);
    }

    public static void setStorageSDCardUsedByBookPlayer(long value) {
        prefs.edit().putLong("STORAGE_SDCARD_USED_BY_BOOKPLAYER", value).apply();
    }

    public static long getStorageSDCardUsedByBookPlayer() {
        return prefs.getLong("STORAGE_SDCARD_USED_BY_BOOKPLAYER", 0);
    }

    public static void setStorageSDCardTimestamp(long value) {
        prefs.edit().putLong("STORAGE_SDCARD_TIMESTAMP", value).apply();
    }

    public static long getStorageSDCardTimestamp() {
        return prefs.getLong("STORAGE_SDCARD_TIMESTAMP", 0);
    }

    // Linked Audios (files outside BookPlayer reserved space)
    public static void setStorageInternalLinkedAudios(long value) {
        prefs.edit().putLong("STORAGE_INTERNAL_LINKED_AUDIOS", value).apply();
    }

    public static long getStorageInternalLinkedAudios() {
        return prefs.getLong("STORAGE_INTERNAL_LINKED_AUDIOS", 0);
    }

    public static void setStorageSDCardLinkedAudios(long value) {
        prefs.edit().putLong("STORAGE_SDCARD_LINKED_AUDIOS", value).apply();
    }

    public static long getStorageSDCardLinkedAudios() {
        return prefs.getLong("STORAGE_SDCARD_LINKED_AUDIOS", 0);
    }

    // Internal App Storage (app + db + logs + images, excluding audio files)
    public static void setStorageInternalApp(long value) {
        prefs.edit().putLong("STORAGE_INTERNAL_APP", value).apply();
    }

    public static long getStorageInternalApp() {
        return prefs.getLong("STORAGE_INTERNAL_APP", 0);
    }

    // Per-folder sizes for Clean Memory (internal unzip subfolders), JSON: path -> size in bytes
    public static void setStorageInternalFolderSizesJson(String value) {
        prefs.edit().putString("STORAGE_INTERNAL_FOLDER_SIZES_JSON", value != null ? value : "").apply();
    }

    public static String getStorageInternalFolderSizesJson() {
        return prefs.getString("STORAGE_INTERNAL_FOLDER_SIZES_JSON", "");
    }

    // Per-folder sizes for Clean Memory (SD card unzip subfolders), JSON: path -> size in bytes
    public static void setStorageSDCardFolderSizesJson(String value) {
        prefs.edit().putString("STORAGE_SDCARD_FOLDER_SIZES_JSON", value != null ? value : "").apply();
    }

    public static String getStorageSDCardFolderSizesJson() {
        return prefs.getString("STORAGE_SDCARD_FOLDER_SIZES_JSON", "");
    }
}
