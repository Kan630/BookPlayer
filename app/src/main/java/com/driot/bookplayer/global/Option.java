package com.driot.bookplayer.global;

import static android.content.Context.MODE_PRIVATE;
import static com.driot.bookplayer.utils.KanMail.DEFAULT_SEND_MAIL_METHOD_DEFAULT;

import android.content.Context;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;

import com.driot.bookplayer.R;
import com.driot.bookplayer.helpers.NetworkHelper;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

public class Option {

    public static final String SHARED_PREFERENCES_OPTIONS = "SHARED_PREFERENCES_OPTIONS"; // shared prefs xml file

    private static final String DEFAULT_SORT_MODE = "last_played";
    private static final String DEFAULT_SORT_DIRECTION = "desc";

    public static final int MIN_TIME_BEFORE_SLEEP = 1;
    public static final int MAX_TIME_BEFORE_SLEEP = 60 * 24; // 1440

    public static final int DEFAULT_FORWARD_SECONDS = 10;
    public static final int DEFAULT_TIME_BEFORE_SLEEP = 120;
    public static final int DEFAULT_TIME_BEFORE_SLEEP_RADIO = 300;
    private static final boolean DEFAULT_UNZIP_LOCAL = true;
    private static final boolean DEFAULT_COPY_ZIP_LOCAL = true;
    private static final boolean DEFAULT_SCREEN_ORIENTATION_LOCK = false;
    private static final boolean DEFAULT_BEEP_CHAPTER = true;
    private static final boolean DEFAULT_BEEP_BOOKEND = true;
    private static final boolean DEFAULT_BEEP_AUTOSTOP = true;
    private static final boolean DEFAULT_DELETE_SOURCE_FILE = false;
    private static final boolean DEFAULT_VISUALIZER_ON = true;
    private static final boolean DEFAULT_REWIND_AFTER_PAUSE = true;
    private static final boolean DEFAULT_START_AT_ZERO_NEXT_TRACK = true;
    private static final boolean DEFAULT_STOP_AUDIO_IF_USER_CLOSES_APP = true;
    private static final boolean DEFAULT_OPEN_PLAY_ACTIVITY = true;
    private static final boolean DEFAULT_RELOAD_PLAYLIST_FROM_STORAGE = true;
    private static final String DEFAULT_VISUALIZER_TYPE = Var.VISUALIZER_TYPE_LEGACY;
    private static final String DEFAULT_THEME_KEY = "purple"; // needs a string as resource ID are not stables between
                                                              // releases
    private static final boolean DEFAULT_COPY_FILES = true;
    private static final boolean DEFAULT_CLICK_VISUALIZER_PLAYPAUSE = false;
    public static final boolean DEFAULT_TECH_LOG = false;
    private static final boolean DEFAULT_OPEN_WITH = true;
    private static final boolean DEFAULT_OPEN_WITH_ALL = false;
    private static final boolean DEFAULT_SPLIT_M4B = true;
    private static final boolean DEFAULT_USE_SD_CARD = true;
    public static final NetworkHelper.NetworkPolicyManual DEFAULT_MANUAL_DOWNLOAD_POLICY = NetworkHelper.NetworkPolicyManual.NETWORK_POLICY_NOT_ROAMING;
    public static final NetworkHelper.NetworkPolicyAuto DEFAULT_AUTO_DOWNLOAD_POLICY = NetworkHelper.NetworkPolicyAuto.NETWORK_POLICY_UNMETERED;
    private static final boolean DEFAULT_PODCAST_AUTO_DELETE = false;
    public static final int DEFAULT_PODCAST_DELAY_AUTO_DELETE = 7;
    public static final int DEFAULT_PODCAST_COMPLETION_PERCENTAGE_AUTO_DELETE = 90;
    public static final int DEFAULT_PODCAST_AUTO_DOWNLOAD_LAST_N_EPISODES = 5;
    public static final int DEFAULT_PODCAST_AUTO_DOWNLOAD_MAX_N_PODCASTS = 10;
    public static final int DEFAULT_PODCAST_AUTO_DOWNLOAD_DELAY_BETWEEN_CHECKS_IN_MIN = 60;
    public static final boolean DEFAULT_PODCAST_AUTO_DOWNLOADED_AT_THE_TOP = false;
    public static final boolean DEFAULT_PODCAST_EPISODES_SORT_NEWEST_TOP = true;
    public static final boolean DEFAULT_PODCAST_EPISODES_DESCRIPTION_EXPAND = false;
    public static final boolean DEFAULT_PODCAST_OPEN_SPECIFIC_VIEW = false;
    public static final boolean DEFAULT_PODCAST_ADD_DATE_TO_EPISODE_NAME = true;
    public static final boolean DEFAULT_CREATE_COVER = true;
    private static final boolean DEFAULT_MASS_IMPORT_DISPLAY_STORAGE_BAR = false;
    private static final boolean DEFAULT_MASS_IMPORT_INCLUDE_SUBFOLDERS = true;
    public static final String DEFAULT_LANGUAGE = "system";
    public static final String DEFAULT_VOICE = "system";
    public static final String DEFAULT_FONT_FAMILY = "sans-serif"; // neutre
    public static final float DEFAULT_TEXT_SIZE_SP = 18f;
    public static final float MIN_TEXT_SIZE_SP = 12f;
    public static final float MAX_TEXT_SIZE_SP = 36f;
    public static final boolean DEFAULT_AUTOMOTIVE_ON = true;
    public static final boolean DEFAULT_AUTOMOTIVE_AUTO_RESUME_ON_CAR_CONNECT = true;
    public static final boolean DEFAULT_AUTOMOTIVE_LET_CAR_AUTOPLAY = false;
    public static final boolean DEFAULT_AUTOMOTIVE_KEEP_PHONE_PLAYBACK_ON_CAR_CONNECT = false;
    public static final int DEFAULT_LIBRIVOX_API_NB_RESULTS = 200;
    public static final int DEFAULT_PODCAST_INDEX_ORG_API_NB_RESULTS = 200;
    public static final int DEFAULT_RADIO_API_NB_RESULTS = 200;
    public static final int DEFAULT_TTS_HIGHLIGHT_DELAY_MS = 100;
    public static final int DEFAULT_TTS_CHUNK_SIZE = 1800;
    public static final String DEFAULT_EPUB_SPLIT_MODE = "auto"; // "auto", "toc", "spine"
    private static final boolean DEFAULT_EBOOK_REMOVE_REFERENCES = false;
    public static final boolean DEFAULT_RADIO_RENEW_URL = false;
    public static final boolean DEFAULT_USE_HEATMAP_FOR_TRACKS_ACTIVITY = true;
    private static final boolean DEFAULT_USE_HEATMAP_SEEKBAR_IN_PLAY_ACTIVITY = false;
    public static final boolean DEFAULT_RADIO_SLEEP_COPY = false;
    public static final boolean DEFAULT_RADIO_USE_CLOUDFARE = false;
    public static final boolean DEFAULT_RADIO_REMOVE_SPAM_STATIONS = true;
    public static final boolean DEFAULT_RADIO_REMOVE_DUBIOUS_STATIONS = true;
    private static final boolean DEFAULT_RADIO_OPEN_FAVORITES_FIRST = false;
    private static final boolean DEFAULT_PODCAST_OPEN_FAVORITES_FIRST = false;
    private static final boolean DEFAULT_SCREENSAVER_ENABLED = false;
    private static final int DEFAULT_SCREENSAVER_DELAY_SECONDS = 10;
    private static final int MIN_SCREENSAVER_DELAY_SECONDS = 2;
    private static final int MAX_SCREENSAVER_DELAY_SECONDS = 60;
    private static final String DEFAULT_SCREENSAVER_VISUALIZER_TYPE = Var.VISUALIZER_TYPE_WAVE;
    private static final boolean DEFAULT_SCREENSAVER_FORCE_ORIENTATION = true;
    private static final String DEFAULT_SCREENSAVER_ORIENTATION_MODE = "LANDSCAPE";

    private static Context appContext;
    private static android.content.SharedPreferences prefs;

    public static void init(Context context) {
        if (appContext == null) {
            appContext = context.getApplicationContext();
        }
        if (prefs == null) {
            prefs = appContext.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE);
        }
    }

    public static void warmUp() {
        if (prefs != null)
            prefs.getAll();
    }

    public static android.content.SharedPreferences getSharedPrefs(Context context) {
        if (prefs == null)
            init(context);
        return prefs;
    }

    // in Option.java
    public static void resetToDefaults(@NonNull Context ctx) {
        Context app = ctx.getApplicationContext();
        app.deleteSharedPreferences(SHARED_PREFERENCES_OPTIONS);
        prefs = app.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE);

        // Context app = ctx.getApplicationContext();
        // android.content.SharedPreferences sp =
        // app.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE);
        // sp.edit().clear().commit();
        /*
         * Context app = ctx.getApplicationContext();
         * String name = SHARED_PREFERENCES_OPTIONS;
         * 
         * // 1) Clear synchronously (prevents pending apply() from writing back later)
         * android.content.SharedPreferences sp = app.getSharedPreferences(name,
         * MODE_PRIVATE);
         * sp.edit().clear().commit();
         * 
         * // 2) Try both CE and DE stores, since some apps move prefs to DE on boot
         * boolean deletedCE = false, deletedDE = false;
         * 
         * if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
         * deletedCE = app.deleteSharedPreferences(name);
         * 
         * Context de = app.createDeviceProtectedStorageContext();
         * if (de != null) {
         * // ignore if it doesn't exist there; just try
         * deletedDE = de.deleteSharedPreferences(name);
         * }
         * }
         * 
         * // 3) Last-resort: physical delete of the XML file(s) for CE & DE paths
         * try {
         * java.io.File ceFile = new java.io.File(app.getApplicationInfo().dataDir +
         * "/shared_prefs/" + name + ".xml");
         * if (ceFile.exists()) ceFile.delete();
         * } catch (Throwable ignored) {}
         * 
         * try {
         * if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
         * Context de = app.createDeviceProtectedStorageContext();
         * if (de != null) {
         * java.io.File deFile = new java.io.File(de.getApplicationInfo().dataDir +
         * "/shared_prefs/" + name + ".xml");
         * if (deFile.exists()) deFile.delete();
         * }
         * }
         * } catch (Throwable ignored) {}
         * 
         * // 4) Rebind the cached instance to a fresh, empty prefs
         * prefs = app.getSharedPreferences(name, MODE_PRIVATE);
         * 
         */
    }

    /////////////////// SORT BOOK LIST ///////////////////
    public static String getSortMode() {
        return prefs.getString("sort_mode", DEFAULT_SORT_MODE);
    }

    public static void setSortMode(String mode) {
        prefs.edit().putString("sort_mode", mode).apply();
    }

    public static String getSortDirection() {
        return prefs.getString("sort_direction", DEFAULT_SORT_DIRECTION);
    }

    public static void setSortDirection(String direction) {
        prefs.edit().putString("sort_direction", direction).apply();
    }

    /////////////////// RADIO ///////////////////
    public static void setRadioUseCloudflare(boolean bool) {
        prefs.edit().putBoolean("RADIO_USE_CLOUDFARE", bool).apply();
    }

    public static boolean getRadioUseCloudflare() {
        return prefs.getBoolean("RADIO_USE_CLOUDFARE", DEFAULT_RADIO_USE_CLOUDFARE);
    }

    public static boolean getRadioSleepCopy() {
        return prefs.getBoolean("RADIO_SLEEP_COPY", DEFAULT_RADIO_SLEEP_COPY);
    }

    public static void setRadioSleepCopy(boolean bool) {
        prefs.edit().putBoolean("RADIO_SLEEP_COPY", bool).apply();
    }

    public static boolean getRadioRemoveSpamStations() {
        return prefs.getBoolean("RADIO_REMOVE_SPAM_STATIONS", DEFAULT_RADIO_REMOVE_SPAM_STATIONS);
    }

    public static void setRadioRemoveSpamStations(boolean bool) {
        prefs.edit().putBoolean("RADIO_REMOVE_SPAM_STATIONS", bool).apply();
    }

    public static boolean getRadioRemoveDubiousStations() {
        return prefs.getBoolean("RADIO_REMOVE_DUBIOUS_STATIONS", DEFAULT_RADIO_REMOVE_DUBIOUS_STATIONS);
    }

    public static void setRadioRemoveDubiousStations(boolean bool) {
        prefs.edit().putBoolean("RADIO_REMOVE_DUBIOUS_STATIONS", bool).apply();
    }

    public static boolean getRadioOpenFavoritesFirst() {
        return prefs.getBoolean("RADIO_OPEN_FAVORITES_FIRST", DEFAULT_RADIO_OPEN_FAVORITES_FIRST);
    }

    public static void setRadioOpenFavoritesFirst(boolean bool) {
        prefs.edit().putBoolean("RADIO_OPEN_FAVORITES_FIRST", bool).apply();
    }

    /////////////////// HEAT_MAPS ///////////////////
    public static boolean getUseHeatmapForTracksActivity() {
        return prefs.getBoolean("USE_HEATMAP_FOR_TRACKS_ACTIVITY", DEFAULT_USE_HEATMAP_FOR_TRACKS_ACTIVITY);
    }

    public static boolean getUseHeatmapForTracksActivityInitialized() {
        return prefs.contains("USE_HEATMAP_FOR_TRACKS_ACTIVITY");
    }

    public static void setUseHeatmapForTracksActivity(boolean bool) {
        prefs.edit().putBoolean("USE_HEATMAP_FOR_TRACKS_ACTIVITY", bool).apply();
    }

    public static boolean getUseHeatmapSeekbarInPlayActivity() {
        return prefs.getBoolean("USE_HEATMAP_SEEKBAR_IN_PLAY_ACTIVITY", DEFAULT_USE_HEATMAP_SEEKBAR_IN_PLAY_ACTIVITY);
    }

    public static void setUseHeatmapSeekbarInPlayActivity(boolean bool) {
        prefs.edit().putBoolean("USE_HEATMAP_SEEKBAR_IN_PLAY_ACTIVITY", bool).apply();
    }

    /////////////////// SLEEP - AUTOMATIC PAUSE ///////////////////
    public static void setTimeBeforeSleep(int i) {
        prefs.edit().putInt("TIME_BEFORE_SLEEP", i).apply();
    }

    public static int getTimeBeforeSleep() {
        return prefs.getInt("TIME_BEFORE_SLEEP", DEFAULT_TIME_BEFORE_SLEEP);
    }

    public static void setTimeBeforeSleepRadio(int i) {
        prefs.edit().putInt("TIME_BEFORE_SLEEP_RADIO", i).apply();
    }

    public static int getTimeBeforeSleepRadio() {
        return prefs.getInt("TIME_BEFORE_SLEEP_RADIO", DEFAULT_TIME_BEFORE_SLEEP_RADIO);
    }

    /////////////////// FORWARD-BACKWARD DURATION ///////////////////
    public static void set_ForwardSeconds(int i) {
        prefs.edit().putInt("FORWARD_SECONDS", i).apply();
    }

    public static int get_ForwardSeconds() {
        return prefs.getInt("FORWARD_SECONDS", DEFAULT_FORWARD_SECONDS);
    }

    /////////////////// ZIP options ///////////////////
    public static void setUnZipLocal(boolean bool) {
        prefs.edit().putBoolean("UNZIP_LOCAL", bool).apply();
    }

    public static void setCopyZipLocal(boolean bool) {
        prefs.edit().putBoolean("COPY_ZIP_LOCAL", bool).apply();
    }

    public static boolean getUnZipLocal() {
        return prefs.getBoolean("UNZIP_LOCAL", DEFAULT_UNZIP_LOCAL);
    }

    public static boolean getCopyZipLocal() {
        return prefs.getBoolean("COPY_ZIP_LOCAL", DEFAULT_COPY_ZIP_LOCAL);
    }

    /////////////////// SCREEN ORIENTATION options ///////////////////
    public static void setScreenOrientationLock(boolean bool) {
        prefs.edit().putBoolean("LOCK_SCREEN_ORIENTATION", bool).apply();
    }

    public static boolean getScreenOrientationLock() {
        return prefs.getBoolean("LOCK_SCREEN_ORIENTATION", DEFAULT_SCREEN_ORIENTATION_LOCK);
    }

    /////////////////// SEND MAIL options ///////////////////
    public static void setMailMethod(boolean bool) {
        prefs.edit().putBoolean("SEND_MAIL_METHOD_DEFAULT", bool).apply();
    }

    public static boolean getMailMethod() {
        return prefs.getBoolean("SEND_MAIL_METHOD_DEFAULT", DEFAULT_SEND_MAIL_METHOD_DEFAULT);
    }

    public static boolean getMailMethod(Context context) {
        return getSharedPrefs(context).getBoolean("SEND_MAIL_METHOD_DEFAULT", DEFAULT_SEND_MAIL_METHOD_DEFAULT);
    }

    /////////////////// BEEP options ///////////////////
    public static void setBeepChapter(boolean bool) {
        prefs.edit().putBoolean("BEEP_CHAPTER", bool).apply();
    }

    public static boolean getBeepChapter() {
        return prefs.getBoolean("BEEP_CHAPTER", DEFAULT_BEEP_CHAPTER);
    }

    public static void setBeepBookEnd(boolean bool) {
        prefs.edit().putBoolean("BEEP_BOOKEND", bool).apply();
    }

    public static boolean getBeepBookEnd() {
        return prefs.getBoolean("BEEP_BOOKEND", DEFAULT_BEEP_BOOKEND);
    }

    public static void setBeepAutoStop(boolean bool) {
        prefs.edit().putBoolean("BEEP_AUTOSTOP", bool).apply();
    }

    public static boolean getBeepAutoStop() {
        return prefs.getBoolean("BEEP_AUTOSTOP", DEFAULT_BEEP_AUTOSTOP);
    }

    /////////////////// DELETE SOURCE FILE option ///////////////////
    public static void setDeleteSourceFile(boolean bool) {
        prefs.edit().putBoolean("DELETE_SOURCE_FILE", bool).apply();
    }

    public static boolean getDeleteSourceFile() {
        return prefs.getBoolean("DELETE_SOURCE_FILE", DEFAULT_DELETE_SOURCE_FILE);
    }

    /////////////////// VISUALIZER option ///////////////////
    public static void setVisualizerOn(boolean bool) {
        prefs.edit().putBoolean("VISUALIZER_ON", bool).apply();
    }

    public static boolean getVisualizerOn() {
        return prefs.getBoolean("VISUALIZER_ON", DEFAULT_VISUALIZER_ON);
    }

    public static void setClickVisualizerPlayPause(boolean bool) {
        prefs.edit().putBoolean("CLICK_VISUALIZER_PLAYPAUSE", bool).apply();
    }

    public static boolean getClickMainContainerPlayPause() {
        return prefs.getBoolean("CLICK_VISUALIZER_PLAYPAUSE", DEFAULT_CLICK_VISUALIZER_PLAYPAUSE);
    }

    /////////////////// THEME ///////////////////
    public static void setThemeColor(String themeColor) {
        prefs.edit().putString("CUSTOM_THEME_KEY", themeColor).apply();
    }

    public static int getThemeColor() {
        String themeKey = prefs.getString("CUSTOM_THEME_KEY", DEFAULT_THEME_KEY);
        // Map custom keys to a fallback or handle appropriately if used for Activity
        // Theme
        // If the app doesn't support dynamic activity theming, we might return a
        // default here
        // or the user might handle "custom_x" manually in their BaseActivity.
        // For now, preserving existing logic but adding cases if needed or just letting
        // it default.
        switch (themeKey) {
            case "purple":
                return R.style.Theme_BookPlayer_Purple;
            case "brown":
                return R.style.Theme_BookPlayer_Brown;
            case "blue":
                return R.style.Theme_BookPlayer_Blue;
            case "cyan":
                return R.style.Theme_BookPlayer_Cyan;
            case "turquoise":
                return R.style.Theme_BookPlayer_Turquoise;
            case "orange":
                return R.style.Theme_BookPlayer_Orange;
            case "yellow":
                return R.style.Theme_BookPlayer_Yellow;
            case "yellowDark":
                return R.style.Theme_BookPlayer_YellowDark;
            case "red":
                return R.style.Theme_BookPlayer_Red;
            case "redDark":
                return R.style.Theme_BookPlayer_RedDark;
            case "indigo":
                return R.style.Theme_BookPlayer_Indigo;
            case "pinkLight":
                return R.style.Theme_BookPlayer_PinkLight;
            case "pink":
                return R.style.Theme_BookPlayer_Pink;
            case "pinkDark":
                return R.style.Theme_BookPlayer_PinkDark;
            case "greenLight":
                return R.style.Theme_BookPlayer_GreenLight;
            case "green":
                return R.style.Theme_BookPlayer_Green;
            case "greenDark":
                return R.style.Theme_BookPlayer_GreenDark;
            case "gray":
            default:
                if (themeKey.startsWith("custom_")) {
                    // For now, return a default style (e.g. Purple) as the base
                    // The dynamic coloring will potentially happen via view overrides or a planned
                    // update
                    return R.style.Theme_BookPlayer_Purple;
                }
                return R.style.Theme_BookPlayer_Gray;
        }
    }

    // Custom Theme Storage
    public static void setCustomColor(int themeIdx, String key, int color) {
        prefs.edit().putInt("CUSTOM_THEME_" + themeIdx + "_" + key, color).apply();
    }

    public static int getCustomColor(int themeIdx, String key, int defaultColor) {
        return prefs.getInt("CUSTOM_THEME_" + themeIdx + "_" + key, defaultColor);
    }

    public static void setFontFamilyKey(String key) {
        prefs.edit().putString("FONT_FAMILY_KEY", key).apply();
    }

    public static String getFontFamilyKey() {
        return prefs.getString("FONT_FAMILY_KEY", DEFAULT_FONT_FAMILY);
    }

    public static @StyleRes int getThemeFontOverlay() {
        String themeKey = prefs.getString("FONT_FAMILY_KEY", DEFAULT_FONT_FAMILY);
        switch (themeKey) {
            case "serif":
                return R.style.ThemeOverlay_BookPlayer_Font_Serif;
            case "monospace":
                return R.style.ThemeOverlay_BookPlayer_Font_Mono;
            case "casual":
                return R.style.ThemeOverlay_BookPlayer_Font_Casual;
            case "cursive":
                return R.style.ThemeOverlay_BookPlayer_Font_Cursive;
            case "serif-monospace":
                return R.style.ThemeOverlay_BookPlayer_Font_SerifMono;
            case "sans-serif-condensed":
                return R.style.ThemeOverlay_BookPlayer_Font_SansCondensed;
            case "sans-serif-medium":
                return R.style.ThemeOverlay_BookPlayer_Font_SansMedium;
            case "sans-serif-smallcaps":
                return R.style.ThemeOverlay_BookPlayer_Font_Smallcaps;
            case "sans-serif":
            default:
                return R.style.ThemeOverlay_BookPlayer_Font_Sans;
        }
    }

    /////////////////// PLAY BEHAVIOUR ///////////////////
    public static void setRewindAfterPause(boolean bool) {
        prefs.edit().putBoolean("REWIND_AFTER_PAUSE", bool).apply();
    }

    public static boolean getRewindAfterPause() {
        return prefs.getBoolean("REWIND_AFTER_PAUSE", DEFAULT_REWIND_AFTER_PAUSE);
    }

    public static void setStartAtZeroNextTrack(boolean bool) {
        prefs.edit().putBoolean("START_AT_ZERO_NEXT_TRACK", bool).apply();
    }

    public static boolean getStartAtZeroNextTrack() {
        return prefs.getBoolean("START_AT_ZERO_NEXT_TRACK", DEFAULT_START_AT_ZERO_NEXT_TRACK);
    }

    public static void setStopAudioIfUserClosesApp(boolean bool) {
        prefs.edit().putBoolean("STOP_AUDIO_IF_USER_CLOSES_APP", bool).apply();
    }

    public static boolean getStopAudioIfUserClosesApp() {
        return prefs.getBoolean("STOP_AUDIO_IF_USER_CLOSES_APP", DEFAULT_STOP_AUDIO_IF_USER_CLOSES_APP);
    }

    public static void setOpenPlayActivity(boolean bool) {
        prefs.edit().putBoolean("OPEN_PLAY_ACTIVITY", bool).apply();
    }

    public static boolean getOpenPlayActivity() {
        return prefs.getBoolean("OPEN_PLAY_ACTIVITY", DEFAULT_OPEN_PLAY_ACTIVITY);
    }

    public static void setIfPlayListNullInLoadFileThenLoadFromStorage(boolean bool) {
        prefs.edit().putBoolean("RELOAD_PLAYLIST_FROM_STORAGE", bool).apply();
    }

    public static boolean getIfPlayListNullInLoadFileThenLoadFromStorage() {
        return prefs.getBoolean("RELOAD_PLAYLIST_FROM_STORAGE", DEFAULT_RELOAD_PLAYLIST_FROM_STORAGE);
    }

    public static void setVisualizerType(String str) {
        prefs.edit().putString("VISUALIZER_TYPE", str).apply();
    }

    public static String getVisualizerType() {
        return prefs.getString("VISUALIZER_TYPE", DEFAULT_VISUALIZER_TYPE);
    }

    /////////////////// SCREENSAVER ///////////////////
    public static void setScreensaverEnabled(boolean bool) {
        prefs.edit().putBoolean("SCREENSAVER_ENABLED", bool).apply();
    }

    public static boolean getScreensaverEnabled() {
        return prefs.getBoolean("SCREENSAVER_ENABLED", DEFAULT_SCREENSAVER_ENABLED);
    }

    public static void setScreensaverDelaySeconds(int seconds) {
        prefs.edit().putInt("SCREENSAVER_DELAY_SECONDS", seconds).apply();
    }

    public static int getScreensaverDelaySeconds() {
        return prefs.getInt("SCREENSAVER_DELAY_SECONDS", DEFAULT_SCREENSAVER_DELAY_SECONDS);
    }

    public static int getMinScreensaverDelaySeconds() {
        return MIN_SCREENSAVER_DELAY_SECONDS;
    }

    public static int getMaxScreensaverDelaySeconds() {
        return MAX_SCREENSAVER_DELAY_SECONDS;
    }

    public static void setScreensaverVisualizerType(String str) {
        prefs.edit().putString("SCREENSAVER_VISUALIZER_TYPE", str).apply();
    }

    public static String getScreensaverVisualizerType() {
        return prefs.getString("SCREENSAVER_VISUALIZER_TYPE", DEFAULT_SCREENSAVER_VISUALIZER_TYPE);
    }

    public static void setScreensaverForceOrientation(boolean bool) {
        prefs.edit().putBoolean("SCREENSAVER_FORCE_ORIENTATION", bool).apply();
    }

    public static boolean getScreensaverForceOrientation() {
        return prefs.getBoolean("SCREENSAVER_FORCE_ORIENTATION", DEFAULT_SCREENSAVER_FORCE_ORIENTATION);
    }

    public static void setScreensaverOrientationMode(String str) {
        prefs.edit().putString("SCREENSAVER_ORIENTATION_MODE", str).apply();
    }

    public static String getScreensaverOrientationMode() {
        return prefs.getString("SCREENSAVER_ORIENTATION_MODE", DEFAULT_SCREENSAVER_ORIENTATION_MODE);
    }

    /////////////////// COPY FILES ///////////////////
    public static void setCopyFile(boolean bool) {
        prefs.edit().putBoolean("COPY_FILES", bool).apply();
    }

    public static boolean getCopyFile() {
        return prefs.getBoolean("COPY_FILES", DEFAULT_COPY_FILES);
    }

    /////////////////// LOG ///////////////////
    public static void setTechLog(boolean bool) {
        prefs.edit().putBoolean("TECH_LOG", bool).apply();
    }

    public static boolean getTechLog() {
        return prefs.getBoolean("TECH_LOG", DEFAULT_TECH_LOG);
    }

    public static boolean getTechLog(Context context) {
        return getSharedPrefs(context).getBoolean("TECH_LOG", DEFAULT_TECH_LOG);
    }

    /////////////////// OPEN WITH ///////////////////
    public static void setOpenWith(boolean bool) {
        prefs.edit().putBoolean("OPEN_WITH", bool).apply();
    }

    public static boolean getOpenWith() {
        return prefs.getBoolean("OPEN_WITH", DEFAULT_OPEN_WITH);
    }

    public static void setOpenWith_all(boolean bool) {
        prefs.edit().putBoolean("OPEN_WITH_ALL", bool).apply();
    }

    public static boolean getOpenWith_all() {
        return prefs.getBoolean("OPEN_WITH_ALL", DEFAULT_OPEN_WITH_ALL);
    }

    /////////////////// SPLIT M4B ///////////////////
    public static void setSplitM4b(boolean bool) {
        prefs.edit().putBoolean("SPLIT_M4B", bool).apply();
    }

    public static boolean getSplitM4b() {
        return prefs.getBoolean("SPLIT_M4B", DEFAULT_SPLIT_M4B);
    }

    /////////////////// USE SD CARD ///////////////////
    public static void setUseSdCard(boolean bool) {
        prefs.edit().putBoolean("USE_SD_CARD", bool).apply();
    }

    public static boolean getUseSdCard() {
        return prefs.getBoolean("USE_SD_CARD", DEFAULT_USE_SD_CARD);
    }

    /////////////////// DOWNLOAD ON WIFI ///////////////////
    public static void setNetworkPolicyManualDownload(NetworkHelper.NetworkPolicyManual policy) {
        prefs.edit().putInt("MANUAL_DOWNLOAD_POLICY_KEY", policy.ordinal()).apply();
    }

    public static NetworkHelper.NetworkPolicyManual getNetworkPolicyManualDownload() {
        int index = prefs.getInt("MANUAL_DOWNLOAD_POLICY_KEY", DEFAULT_MANUAL_DOWNLOAD_POLICY.ordinal());
        return NetworkHelper.NetworkPolicyManual.values()[Math.max(0,
                Math.min(index, NetworkHelper.NetworkPolicyManual.values().length - 1))];
    }

    public static void setNetworkPolicyAutoDownload(NetworkHelper.NetworkPolicyAuto policy) {
        prefs.edit().putInt("AUTO_DOWNLOAD_POLICY_KEY", policy.ordinal()).apply();
    }

    public static NetworkHelper.NetworkPolicyAuto getNetworkPolicyAutoDownload() {
        int index = prefs.getInt("AUTO_DOWNLOAD_POLICY_KEY", DEFAULT_AUTO_DOWNLOAD_POLICY.ordinal());
        return NetworkHelper.NetworkPolicyAuto.values()[Math.max(0,
                Math.min(index, NetworkHelper.NetworkPolicyAuto.values().length - 1))];
    }

    /////////////////// PODCAST ///////////////////
    public static void setPodcastAutoDelete(boolean bool) {
        prefs.edit().putBoolean("PODCAST_AUTO_DELETE", bool).apply();
    }

    public static boolean getPodcastAutoDelete() {
        return prefs.getBoolean("PODCAST_AUTO_DELETE", DEFAULT_PODCAST_AUTO_DELETE);
    }

    public static void setPodcastAutoDeleteCompletionPercentage(int i) {
        prefs.edit().putInt("PODCAST_COMPLETION_PERCENTAGE_AUTO_DELETE", i).apply();
    }

    public static int getPodcastAutoDeleteCompletionPercentage() {
        return prefs.getInt("PODCAST_COMPLETION_PERCENTAGE_AUTO_DELETE",
                DEFAULT_PODCAST_COMPLETION_PERCENTAGE_AUTO_DELETE);
    }

    public static void setPodcastAutoDeleteDelay(int i) {
        prefs.edit().putInt("PODCAST_DELAY_AUTO_DELETE", i).apply();
    }

    public static int getPodcastAutoDeleteDelay() {
        return prefs.getInt("PODCAST_DELAY_AUTO_DELETE", DEFAULT_PODCAST_DELAY_AUTO_DELETE);
    }

    public static void setPodcastAutoDownloadLastNbEpisode(int i) {
        prefs.edit().putInt("PODCAST_AUTO_DOWNLOAD_LAST_N_EPISODES", i).apply();
    }

    public static int getPodcastAutoDownloadLastNbEpisode() {
        return prefs.getInt("PODCAST_AUTO_DOWNLOAD_LAST_N_EPISODES", DEFAULT_PODCAST_AUTO_DOWNLOAD_LAST_N_EPISODES);
    }

    public static void setPodcastAutoDownloadMaxNbPodcast(int i) {
        prefs.edit().putInt("PODCAST_AUTO_DOWNLOAD_MAX_N_PODCASTS", i).apply();
    }

    public static int getPodcastAutoDownloadMaxNbPodcast() {
        return prefs.getInt("PODCAST_AUTO_DOWNLOAD_MAX_N_PODCASTS", DEFAULT_PODCAST_AUTO_DOWNLOAD_MAX_N_PODCASTS);
    }

    public static void setPodcastAutoDownloadDelayBetweenChecks(int i) {
        prefs.edit().putInt("PODCAST_AUTO_DOWNLOAD_DELAY_BETWEEN_CHECKS_IN_MIN", i).apply();
    }

    public static int getPodcastAutoDownloadDelayBetweenChecks() {
        return prefs.getInt("PODCAST_AUTO_DOWNLOAD_DELAY_BETWEEN_CHECKS_IN_MIN",
                DEFAULT_PODCAST_AUTO_DOWNLOAD_DELAY_BETWEEN_CHECKS_IN_MIN);
    }

    public static void setPodcastAutoDownloadedAtTheTop(boolean bool) {
        prefs.edit().putBoolean("PODCAST_AUTO_DOWNLOADED_AT_THE_TOP", bool).apply();
    }

    public static boolean getPodcastAutoDownloadedAtTheTop() {
        return prefs.getBoolean("PODCAST_AUTO_DOWNLOADED_AT_THE_TOP", DEFAULT_PODCAST_AUTO_DOWNLOADED_AT_THE_TOP);
    }

    public static void setPodcastEpisodesSortOrder(boolean bool) {
        prefs.edit().putBoolean("PODCAST_EPISODES_SORT_NEWEST_TOP", bool).apply();
    }

    public static boolean getPodcastEpisodesSortOrder() {
        return prefs.getBoolean("PODCAST_EPISODES_SORT_NEWEST_TOP", DEFAULT_PODCAST_EPISODES_SORT_NEWEST_TOP);
    }

    public static void setPodcastEpisodesDescriptionExpand(boolean bool) {
        prefs.edit().putBoolean("PODCAST_EPISODES_DESCRIPTION_EXPAND", bool).apply();
    }

    public static boolean getPodcastEpisodesDescriptionExpand() {
        return prefs.getBoolean("PODCAST_EPISODES_DESCRIPTION_EXPAND", DEFAULT_PODCAST_EPISODES_DESCRIPTION_EXPAND);
    }

    public static void setPodcastOpenSpecificView(boolean bool) {
        prefs.edit().putBoolean("PODCAST_OPEN_SPECIFIC_VIEW", bool).apply();
    }

    public static boolean getPodcastOpenSpecificView() {
        return prefs.getBoolean("PODCAST_OPEN_SPECIFIC_VIEW", DEFAULT_PODCAST_OPEN_SPECIFIC_VIEW);
    }

    public static boolean getPodcastOpenFavoritesFirst() {
        return prefs.getBoolean("PODCAST_OPEN_FAVORITES_FIRST", DEFAULT_PODCAST_OPEN_FAVORITES_FIRST);
    }

    public static void setPodcastOpenFavoritesFirst(boolean bool) {
        prefs.edit().putBoolean("PODCAST_OPEN_FAVORITES_FIRST", bool).apply();
    }

    public static void setPodcastAddDateToEpisodeName(boolean bool) {
        prefs.edit().putBoolean("PODCAST_ADD_DATE_TO_EPISODE_NAME", bool).apply();
    }

    public static boolean getPodcastAddDateToEpisodeName() {
        return prefs.getBoolean("PODCAST_ADD_DATE_TO_EPISODE_NAME", DEFAULT_PODCAST_ADD_DATE_TO_EPISODE_NAME);
    }

    public static void setCreateCover(boolean bool) {
        prefs.edit().putBoolean("CREATE_COVER", bool).apply();
    }

    public static boolean getCreateCover() {
        return prefs.getBoolean("CREATE_COVER", DEFAULT_CREATE_COVER);
    }

    public static void setMassImportDisplayStorageBar(boolean value) {
        prefs.edit().putBoolean("MASS_IMPORT_DISPLAY_STORAGE_BAR", value).apply();
    }

    public static boolean getMassImportDisplayStorageBar() {
        return prefs.getBoolean("MASS_IMPORT_DISPLAY_STORAGE_BAR", DEFAULT_MASS_IMPORT_DISPLAY_STORAGE_BAR);
    }

    public static void setMassImportIncludeSubfolders(boolean value) {
        prefs.edit().putBoolean("MASS_IMPORT_INCLUDE_SUBFOLDERS", value).apply();
    }

    public static boolean getMassImportIncludeSubfolders() {
        return prefs.getBoolean("MASS_IMPORT_INCLUDE_SUBFOLDERS", DEFAULT_MASS_IMPORT_INCLUDE_SUBFOLDERS);
    }

    /////////////////// APP LANGUAGE ///////////////////
    public static void setAppLanguage(String language) {
        prefs.edit().putString("APP_LANGUAGE", language).apply();
    }

    public static String getAppLanguage() {
        return prefs.getString("APP_LANGUAGE", DEFAULT_LANGUAGE);
    }

    public static String getAppLanguage(Context context) {
        return getSharedPrefs(context).getString("APP_LANGUAGE", DEFAULT_LANGUAGE);
    }

    /////////////////// TTS ///////////////////
    public static void setTtsVoice(String voice) {
        prefs.edit().putString("TTS_VOICE", voice).apply();
    }

    public static String getTtsVoice() {
        return prefs.getString("TTS_VOICE", DEFAULT_VOICE);
    }

    public static void setTtsHighlightDelayMs(int delayMs) {
        prefs.edit().putInt("TTS_HIGHLIGHT_DELAY_MS", delayMs).apply();
    }

    public static int getTtsHighlightDelayMs() {
        return prefs.getInt("TTS_HIGHLIGHT_DELAY_MS", DEFAULT_TTS_HIGHLIGHT_DELAY_MS);
    }

    public static void setTtsChunkSize(int chunkSize) {
        prefs.edit().putInt("TTS_CHUNK_SIZE", chunkSize).apply();
    }

    public static int getTtsChunkSize() {
        return prefs.getInt("TTS_CHUNK_SIZE", DEFAULT_TTS_CHUNK_SIZE);
    }

    public static void setEpubSplitMode(String mode) {
        prefs.edit().putString("EPUB_SPLIT_MODE", mode).apply();
    }

    public static String getEpubSplitMode() {
        return prefs.getString("EPUB_SPLIT_MODE", DEFAULT_EPUB_SPLIT_MODE);
    }

    public static void setEbookRemoveReferences(boolean value) {
        prefs.edit().putBoolean("EBOOK_REMOVE_REFERENCES", value).apply();
    }

    public static boolean getEbookRemoveReferences() {
        return prefs.getBoolean("EBOOK_REMOVE_REFERENCES", DEFAULT_EBOOK_REMOVE_REFERENCES);
    }

    /////////////////// AUTOMOTIVE ///////////////////

    public static void setAutomotiveOn(boolean bool) {
        prefs.edit().putBoolean("AUTOMOTIVE_ON", bool).apply();
    }

    public static boolean getAutomotiveOn() {
        return prefs.getBoolean("AUTOMOTIVE_ON", DEFAULT_AUTOMOTIVE_ON);
    }

    public static void setAutomotiveLetCarAutoplay(boolean bool) {
        prefs.edit().putBoolean("AUTOMOTIVE_LET_CAR_AUTOPLAY", bool).apply();
    }

    public static boolean getAutomotiveLetCarAutoplay() {
        return prefs.getBoolean("AUTOMOTIVE_LET_CAR_AUTOPLAY", DEFAULT_AUTOMOTIVE_LET_CAR_AUTOPLAY);
    }

    public static void setAutomotiveAutoResumeOnCarConnect(boolean bool) {
        prefs.edit().putBoolean("AUTOMOTIVE_AUTO_RESUME_ON_CAR_CONNECT", bool).apply();
    }

    public static boolean getAutomotiveAutoResumeOnCarConnect() {
        return prefs.getBoolean("AUTOMOTIVE_AUTO_RESUME_ON_CAR_CONNECT", DEFAULT_AUTOMOTIVE_AUTO_RESUME_ON_CAR_CONNECT);
    }

    public static void setAutomotiveKeepPhonePlaybackOnCarConnect(boolean bool) {
        prefs.edit().putBoolean("AUTOMOTIVE_KEEP_PHONE_PLAYBACK_ON_CAR_CONNECT", bool).apply();
    }

    public static boolean getAutomotiveKeepPhonePlaybackOnCarConnect() {
        return prefs.getBoolean("AUTOMOTIVE_KEEP_PHONE_PLAYBACK_ON_CAR_CONNECT",
                DEFAULT_AUTOMOTIVE_KEEP_PHONE_PLAYBACK_ON_CAR_CONNECT);
    }

    public static void setLibrivoxApiNbResults(int i) {
        prefs.edit().putInt("LIBRIVOX_API_NB_RESULTS", i).apply();
    }

    public static int getLibrivoxApiNbResults() {
        return prefs.getInt("LIBRIVOX_API_NB_RESULTS", DEFAULT_LIBRIVOX_API_NB_RESULTS);
    }

    public static void setPodcastIndexOrgApiNbResults(int i) {
        prefs.edit().putInt("PODCAST_INDEX_ORG_API_NB_RESULTS", i).apply();
    }

    public static int getPodcastIndexOrgApiNbResults() {
        return prefs.getInt("PODCAST_INDEX_ORG_API_NB_RESULTS", DEFAULT_PODCAST_INDEX_ORG_API_NB_RESULTS);
    }

    public static void setRadioApiNbResults(int i) {
        prefs.edit().putInt("RADIO_API_NB_RESULTS", i).apply();
    }

    public static int getRadioApiNbResults() {
        return prefs.getInt("RADIO_API_NB_RESULTS", DEFAULT_RADIO_API_NB_RESULTS);
    }

    /////////////////// RADIO ///////////////////

    public static void setRadioRenewUrl(boolean bool) {
        prefs.edit().putBoolean("RADIO_RENEW_URL", bool).apply();
    }

    public static boolean getRadioRenewUrl() {
        return prefs.getBoolean("RADIO_RENEW_URL", DEFAULT_RADIO_RENEW_URL);
    }

    /////////////////// NIGHT MODE ///////////////////

    public static String getNightMode() {
        return prefs.getString("KEY_NIGHT_MODE", "SYSTEM");
    }

    public static void setNightMode(String nightMode) {
        prefs.edit().putString("KEY_NIGHT_MODE", nightMode).apply();
    }

    public static void applyNightMode() {
        String nightMode = getNightMode();
        myLog("applyNightMode : " + nightMode);
        int appCompatMode = switch (nightMode) {
            case "LIGHT" -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
            case "DARK" -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES;
            default -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        };
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(appCompatMode);
    }

    public static void setTextSizeSp(float sp) {
        // clamp
        float v = Math.max(MIN_TEXT_SIZE_SP, Math.min(MAX_TEXT_SIZE_SP, sp));
        prefs.edit().putFloat("TEXT_SIZE_SP", v).apply();
    }

    public static float getTextSizeSp() {
        return prefs.getFloat("TEXT_SIZE_SP", DEFAULT_TEXT_SIZE_SP);
    }

    public static void applyUserTextAppearance(@NonNull TextView tv) {
        try {
            String family = Option.getFontFamilyKey();
            float sizeSp = Option.getTextSizeSp();
            tv.setTypeface(android.graphics.Typeface.create(family, android.graphics.Typeface.NORMAL));
            tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sizeSp);
        } catch (Throwable ignored) {
        }
    }

    public static int clampInt(
            Context context,
            EditText et,
            int min,
            int max,
            int def,
            String featureName // e.g. getString(R.string.librivox)
    ) {
        if (et == null)
            return def;

        String str = et.getText().toString().trim();
        int val;
        try {
            val = Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return def;
        }

        if (val < min) {
            myToast(context.getString(R.string.minimum_number_of_results_for_) + " " + featureName + " "
                    + context.getString(R.string.too_low));
            return min;
        }

        if (val > max) {
            myToast(context.getString(R.string.maximum_number_of_results_for_) + " " + featureName + " "
                    + context.getString(R.string.too_high));
            return max;
        }

        return val;
    }

}
