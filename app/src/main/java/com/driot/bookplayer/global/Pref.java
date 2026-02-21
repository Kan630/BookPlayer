package com.driot.bookplayer.global;
// created by Antoine Driot -- antoine.driot.com -- on 06/06/2025

import static android.content.Context.MODE_PRIVATE;

import static com.driot.bookplayer.global.Var.PODCAST_DETAIL_ANIMATION_COUNT;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;
import com.driot.bookplayer.utils.Tonio;

public class Pref {

    private static final String SHARED_PREFERENCES_DIVERSE = "SHARED_PREFERENCES_DIVERSE";
    private static final String SHARED_PREFERENCES_STATS = "SHARED_PREFERENCES_STATS";
    private static final String SHARED_PREFERENCE_TIMESTAMP = "SHARED_PREFERENCE_TIMESTAMP";
    private static final String SHARED_PREFERENCE_ADMIN = "SHARED_PREFERENCES_ADMIN";
    private static final String SHARED_PREFERENCE_SEARCH_HISTORY = "search_history_store";
    public static final String SHARED_PREFERENCE_CHAR_SIZE = "SHARED_PREFERENCE_CHAR_SIZE";
    public static final String SHARED_PREFERENCE_POS_SCROLLVIEW = "SHARED_PREFERENCE_POSITION_SCROLLVIEW";
    private static final String SHARED_PREFERENCE_IN_APP_MSG = "inapp_msgs";
    private static final String SHARED_PREFERENCE_RADIO_FAVORITES = "radio_favorites_store";
    private static final String SHARED_PREFERENCE_PLAYLIST = "SHARED_PREFERENCE_CURRENT_PLAYLIST";
    public static final String SHARED_PREFERENCE_MIGRATION = "SHARED_PREFERENCE_MIGRATION";

    private static final String SHARED_PREFERENCE_INTRO_CUT = "SHARED_PREFERENCE_INTRO_CUT";
    private static final String SHARED_PREFERENCE_SPEED = "SHARED_PREFERENCE_SPEED";
    private static final String SHARED_PREFERENCE_BOOK = "book_prefs";

    private static final boolean DEFAULT_SHOW_LIVE_LOGS = false; // percentage
    private static final int DEFAULT_LIVE_LOG_HEIGHT = 50; // percentage

    private static Context appContext;
    private static SharedPreferences prefs;
    private static SharedPreferences stats;
    private static SharedPreferences timeStamp;
    private static SharedPreferences introCut;
    private static SharedPreferences speed;
    private static SharedPreferences admin;
    private static SharedPreferences book;
    private static SharedPreferences searchHistory;
    private static SharedPreferences charSize;
    private static SharedPreferences inAppMsgs;
    private static SharedPreferences radioFavorites;
    private static SharedPreferences migration;
    private static SharedPreferences playlist;

    public static void init(Context context) {
        appContext = context.getApplicationContext();
        PrefMigration.run(appContext);
        prefs = appContext.getSharedPreferences(SHARED_PREFERENCES_DIVERSE, MODE_PRIVATE);
        stats = appContext.getSharedPreferences(SHARED_PREFERENCES_STATS, MODE_PRIVATE);
        timeStamp = appContext.getSharedPreferences(SHARED_PREFERENCE_TIMESTAMP, MODE_PRIVATE);
        introCut = appContext.getSharedPreferences(SHARED_PREFERENCE_INTRO_CUT, MODE_PRIVATE);
        speed = appContext.getSharedPreferences(SHARED_PREFERENCE_SPEED, MODE_PRIVATE);
        admin = appContext.getSharedPreferences(SHARED_PREFERENCE_ADMIN, MODE_PRIVATE);
        book = appContext.getSharedPreferences(SHARED_PREFERENCE_BOOK, MODE_PRIVATE);
        searchHistory = appContext.getSharedPreferences(SHARED_PREFERENCE_SEARCH_HISTORY, MODE_PRIVATE);
        charSize = appContext.getSharedPreferences(SHARED_PREFERENCE_CHAR_SIZE, MODE_PRIVATE);
        inAppMsgs = appContext.getSharedPreferences(SHARED_PREFERENCE_IN_APP_MSG, MODE_PRIVATE);
        radioFavorites = appContext.getSharedPreferences(SHARED_PREFERENCE_RADIO_FAVORITES, MODE_PRIVATE);
        migration = appContext.getSharedPreferences(SHARED_PREFERENCE_MIGRATION, MODE_PRIVATE);
        playlist = appContext.getSharedPreferences(SHARED_PREFERENCE_PLAYLIST, MODE_PRIVATE);

        if (getFirstOpenTimeStamp(appContext) == 0)
            setFirstOpen();
    }

    public static void warmUp() {
        if (prefs != null)
            prefs.getAll();
        if (stats != null)
            stats.getAll();
        if (timeStamp != null)
            timeStamp.getAll();
        if (introCut != null)
            introCut.getAll();
        if (speed != null)
            speed.getAll();
        if (admin != null)
            admin.getAll();
        if (book != null)
            book.getAll();
        if (searchHistory != null)
            searchHistory.getAll();
        if (charSize != null)
            charSize.getAll();
        if (inAppMsgs != null)
            inAppMsgs.getAll();
        if (radioFavorites != null)
            radioFavorites.getAll();
        if (migration != null)
            migration.getAll();
        if (playlist != null)
            playlist.getAll();
    }

    public static SharedPreferences getStats(Context context) {
        if (prefs == null)
            init(context);
        return prefs;
    }

    public static boolean getShowLiveLogs() {
        return admin.getBoolean("SHOW_LIVE_LOGS", DEFAULT_SHOW_LIVE_LOGS);
    }

    public static void setShowLiveLogs(boolean value) {
        admin.edit().putBoolean("SHOW_LIVE_LOGS", value).apply();
    }

    public static int getLiveLogsSavedHeight() {
        return admin.getInt("LIVE_LOG_HEIGHT", DEFAULT_LIVE_LOG_HEIGHT);
    }

    public static void setLiveLogsSavedHeight(int value) {
        admin.edit().putInt("LIVE_LOG_HEIGHT", value).apply();
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

    /////////////////// LANGUAGE SPINNER ///////////////////
    ///
    public static void set_Audio_Language_Librivox(Context c, String audioLanguage) {
        prefs.edit().putString("AUDIO_LANGUAGE_LIBRIVOX", audioLanguage).apply();
    }

    public static String get_Audio_Language_Librivox(Context c) {
        return prefs.getString("AUDIO_LANGUAGE_LIBRIVOX", "eng");
    }

    public static void set_Audio_Language_Podcast(Context c, String audioLanguage) {
        prefs.edit().putString("AUDIO_LANGUAGE_PODCAST", audioLanguage).apply();
    }

    public static String get_Audio_Language_Podcast(Context c) {
        return prefs.getString("AUDIO_LANGUAGE_PODCAST", "en");
    }

    public static void set_Audio_Language_Ebook(Context c, String audioLanguage) {
        prefs.edit().putString("AUDIO_LANGUAGE_EBOOK", audioLanguage).apply();
    }

    public static String get_Audio_Language_Ebook(Context c) {
        return prefs.getString("AUDIO_LANGUAGE_EBOOK", "en");
    }

    /////////////////// STATS ///////////////////
    ///
    public static void setFirstOpen() {
        stats.edit().putLong("FIRST_OPEN_TIMESTAMP", System.currentTimeMillis()).apply();
        stats.edit().putString("FIRST_OPEN_DATE", Tonio.getCurrentDateTimeString()).apply();
    }

    public static Long getFirstOpenTimeStamp(Context context) {
        return getStats(context).getLong("FIRST_OPEN_TIMESTAMP", 0);
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

    // Per-folder sizes for Clean Memory (internal unzip subfolders), JSON: path ->
    // size in bytes
    public static void setStorageInternalFolderSizesJson(String value) {
        prefs.edit().putString("STORAGE_INTERNAL_FOLDER_SIZES_JSON", value != null ? value : "").apply();
    }

    public static String getStorageInternalFolderSizesJson() {
        return prefs.getString("STORAGE_INTERNAL_FOLDER_SIZES_JSON", "");
    }

    // Per-folder sizes for Clean Memory (SD card unzip subfolders), JSON: path ->
    // size in bytes
    public static void setStorageSDCardFolderSizesJson(String value) {
        prefs.edit().putString("STORAGE_SDCARD_FOLDER_SIZES_JSON", value != null ? value : "").apply();
    }

    public static String getStorageSDCardFolderSizesJson() {
        return prefs.getString("STORAGE_SDCARD_FOLDER_SIZES_JSON", "");
    }

    /////////////////// SEARCH HISTORY ///////////////////

    public static java.util.List<String> getSearchHistory(String key) {
        String raw = searchHistory.getString(key, "");
        java.util.List<String> out = new java.util.ArrayList<>();
        if (raw.isEmpty())
            return out;
        String[] parts = raw.split("\\|\\|", -1);
        for (String p : parts) {
            if (!p.isEmpty())
                out.add(p);
        }
        return out;
    }

    public static void addSearchHistory(String key, String value, int max) {
        value = value.trim();
        if (value.isEmpty())
            return;

        java.util.List<String> current = getSearchHistory(key);

        // MRU: remove if exists, then add to front
        current.remove(value);
        current.add(0, value);

        // trim to max
        while (current.size() > max)
            current.remove(current.size() - 1);

        // dedupe while preserving order
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>(current);
        current.clear();
        current.addAll(set);

        saveSearchHistory(key, current);
    }

    public static void clearSearchHistory(String key) {
        searchHistory.edit().remove(key).apply();
    }

    private static void saveSearchHistory(String key, java.util.List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (String s : items) {
            if (sb.length() != 0)
                sb.append("||");
            sb.append(s.replace("||", "¦¦"));
        }
        searchHistory.edit().putString(key, sb.toString()).apply();
    }

    /////////////////// TEXT OPTIONS ///////////////////

    public static SharedPreferences getInAppMsgPrefs() {
        return inAppMsgs;
    }

    public static SharedPreferences getRadioFavoritesPrefs() {
        return radioFavorites;
    }

    public static SharedPreferences getMigrationPrefs(Context context) {
        if (migration == null)
            init(context);
        return migration;
    }

    public static SharedPreferences getPlaylistPrefs() {
        return playlist;
    }
}
