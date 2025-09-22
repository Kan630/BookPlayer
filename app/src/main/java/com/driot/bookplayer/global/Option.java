package com.driot.bookplayer.global;

import static android.content.Context.MODE_PRIVATE;
import static com.driot.bookplayer.utils.KanMail.DEFAULT_SEND_MAIL_METHOD_DEFAULT;

import android.content.Context;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.driot.bookplayer.R;
import com.driot.bookplayer.utils.NetworkUtils;


public class Option {

    public static final String SHARED_PREFERENCES_OPTIONS = "SHARED_PREFERENCES_OPTIONS"; // shared prefs xml file

    public static final int DEFAULT_FORWARD_SECONDS = 5;
    public static final int DEFAULT_TIME_BEFORE_SLEEP = 60;
    private static final boolean DEFAULT_UNZIP_LOCAL  = true;
    private static final boolean DEFAULT_COPY_ZIP_LOCAL  = true;
    private static final boolean DEFAULT_SCREEN_ORIENTATION_LOCK  = true;
    private static final boolean DEFAULT_BEEP_CHAPTER = true;
    private static final boolean DEFAULT_BEEP_BOOKEND = true;
    private static final boolean DEFAULT_BEEP_AUTOSTOP = true;
    private static final boolean DEFAULT_DELETE_SOURCE_FILE = false;
    private static final boolean DEFAULT_VISUALIZER_ON = true;
    private static final boolean DEFAULT_REWIND_AFTER_PAUSE = true;
    private static final String DEFAULT_THEME_KEY = "purple"; //needs a string as resource ID are not stables between releases
    private static final boolean DEFAULT_COPY_FILES = true;
    private static final boolean DEFAULT_CLICK_VISUALIZER_PLAYPAUSE = false;
    private static final boolean DEFAULT_TECH_LOG = false;
    private static final boolean DEFAULT_OPEN_WITH = true;
    private static final boolean DEFAULT_OPEN_WITH_ALL = false;
    private static final boolean DEFAULT_SPLIT_M4B = true;
    private static final boolean DEFAULT_USE_SD_CARD = true;
    private static final NetworkUtils.NetworkPolicyManual DEFAULT_MANUAL_DOWNLOAD_POLICY = NetworkUtils.NetworkPolicyManual.NEVER_ASK;
    private static final NetworkUtils.NetworkPolicyAuto DEFAULT_AUTO_DOWNLOAD_POLICY = NetworkUtils.NetworkPolicyAuto.WIFI;
    private static final boolean DEFAULT_PODCAST_AUTO_DELETE = false;
    public static final int DEFAULT_PODCAST_DELAY_AUTO_DELETE = 7;
    public static final int DEFAULT_PODCAST_COMPLETION_PERCENTAGE_AUTO_DELETE = 90;
    public static final int DEFAULT_PODCAST_AUTO_DOWNLOAD_LAST_N_EPISODES = 5;
    public static final int DEFAULT_PODCAST_AUTO_DOWNLOAD_MAX_N_PODCASTS = 10;
    public static final int DEFAULT_PODCAST_AUTO_DOWNLOAD_DELAY_BETWEEN_CHECKS_IN_MIN = 60;
    public static final boolean DEFAULT_PODCAST_AUTO_DOWNLOADED_AT_THE_TOP = false;
    public static final boolean DEFAULT_PODCAST_EPISODES_SORT_NEWEST_TOP = true;
    public static final boolean DEFAULT_PODCAST_EPISODES_DESCRIPTION_EXPAND = true;
    public static final boolean DEFAULT_PODCAST_OPEN_SPECIFIC_VIEW = true;
    public static final boolean DEFAULT_CREATE_COVER = true;
    private static final String DEFAULT_LANGUAGE = "system";
    public static final String DEFAULT_FONT_FAMILY = "sans-serif"; // neutre
    public static final float  DEFAULT_TEXT_SIZE_SP = 18f;
    public static final float  MIN_TEXT_SIZE_SP = 12f;
    public static final float  MAX_TEXT_SIZE_SP = 36f;


    private static Context appContext;
    private static android.content.SharedPreferences prefs;
    public static void init(Context context) {
        appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE);
    }


    /////////////////// SLEEP - AUTOMATIC PAUSE ///////////////////
    public static void setTimeBeforeSleep(int i) {prefs.edit().putInt("TIME_BEFORE_SLEEP",i).apply();}
    public static int getTimeBeforeSleep() {return prefs.getInt("TIME_BEFORE_SLEEP", DEFAULT_TIME_BEFORE_SLEEP);}

    /////////////////// FORWARD-BACKWARD DURATION ///////////////////
    public static void set_ForwardSeconds(int i) {prefs.edit().putInt("FORWARD_SECONDS",i).apply();}
    public static int get_ForwardSeconds() {return prefs.getInt("FORWARD_SECONDS", DEFAULT_FORWARD_SECONDS);}

    /////////////////// ZIP options ///////////////////
    public static void setUnZipLocal(boolean bool) {prefs.edit().putBoolean("UNZIP_LOCAL",bool).apply();}
    public static void setCopyZipLocal(boolean bool) {prefs.edit().putBoolean("COPY_ZIP_LOCAL",bool).apply();}
    public static boolean getUnZipLocal() {return prefs.getBoolean("UNZIP_LOCAL", DEFAULT_UNZIP_LOCAL);}
    public static boolean getCopyZipLocal() {return prefs.getBoolean("COPY_ZIP_LOCAL", DEFAULT_COPY_ZIP_LOCAL);}

    /////////////////// SCREEN ORIENTATION options ///////////////////
    public static void setScreenOrientationLock(boolean bool) {prefs.edit().putBoolean("LOCK_SCREEN_ORIENTATION",bool).apply();}
    public static boolean getScreenOrientationLock() {return prefs.getBoolean("LOCK_SCREEN_ORIENTATION", DEFAULT_SCREEN_ORIENTATION_LOCK);}

    /////////////////// SEND MAIL options ///////////////////
    public static void setMailMethod(boolean bool) {prefs.edit().putBoolean("SEND_MAIL_METHOD_DEFAULT",bool).apply();}
    public static boolean getMailMethod() {return prefs.getBoolean("SEND_MAIL_METHOD_DEFAULT", DEFAULT_SEND_MAIL_METHOD_DEFAULT);}

    /////////////////// BEEP options ///////////////////
    public static void setBeepChapter(boolean bool) {prefs.edit().putBoolean("BEEP_CHAPTER",bool).apply();}
    public static boolean getBeepChapter() {return prefs.getBoolean("BEEP_CHAPTER", DEFAULT_BEEP_CHAPTER);}

    public static void setBeepBookEnd(boolean bool) {prefs.edit().putBoolean("BEEP_BOOKEND",bool).apply();}
    public static boolean getBeepBookEnd() {return prefs.getBoolean("BEEP_BOOKEND", DEFAULT_BEEP_BOOKEND);}
    public static void setBeepAutoStop(boolean bool) {prefs.edit().putBoolean("BEEP_AUTOSTOP",bool).apply();}
    public static boolean getBeepAutoStop() {return prefs.getBoolean("BEEP_AUTOSTOP", DEFAULT_BEEP_AUTOSTOP);}

    /////////////////// DELETE SOURCE FILE option ///////////////////
    public static void setDeleteSourceFile(boolean bool) {prefs.edit().putBoolean("DELETE_SOURCE_FILE",bool).apply();}
    public static boolean getDeleteSourceFile() {return prefs.getBoolean("DELETE_SOURCE_FILE", DEFAULT_DELETE_SOURCE_FILE);}

    /////////////////// VISUALIZER option ///////////////////
    public static void setVisualizerOn(boolean bool) {prefs.edit().putBoolean("VISUALIZER_ON",bool).apply();}
    public static boolean getVisualizerOn() {return prefs.getBoolean("VISUALIZER_ON", DEFAULT_VISUALIZER_ON);}

    public static void setClickVisualizerPlayPause(boolean bool) {prefs.edit().putBoolean("CLICK_VISUALIZER_PLAYPAUSE",bool).apply();}
    public static boolean getClickVisualizerPlayPause() {return prefs.getBoolean("CLICK_VISUALIZER_PLAYPAUSE", DEFAULT_CLICK_VISUALIZER_PLAYPAUSE);}

    /////////////////// THEME ///////////////////
    public static void setTheme(String themeKey) {
        prefs.edit().putString("CUSTOM_THEME_KEY", themeKey).apply();
    }

    public static int getTheme() {
        String themeKey = prefs.getString("CUSTOM_THEME_KEY", DEFAULT_THEME_KEY);
        switch (themeKey) {
            case "purple": return R.style.Theme_BookPlayer_Purple;
            case "brown": return R.style.Theme_BookPlayer_Brown;
            case "blue": return R.style.Theme_BookPlayer_Blue;
            case "cyan": return R.style.Theme_BookPlayer_Cyan;
            case "turquoise": return R.style.Theme_BookPlayer_Turquoise;
            case "orange": return R.style.Theme_BookPlayer_Orange;
            case "yellow": return R.style.Theme_BookPlayer_Yellow;
            case "yellowDark": return R.style.Theme_BookPlayer_YellowDark;
            case "red": return R.style.Theme_BookPlayer_Red;
            case "redDark": return R.style.Theme_BookPlayer_RedDark;
            case "indigo": return R.style.Theme_BookPlayer_Indigo;
            case "pinkLight": return R.style.Theme_BookPlayer_PinkLight;
            case "pink": return R.style.Theme_BookPlayer_Pink;
            case "pinkDark": return R.style.Theme_BookPlayer_PinkDark;
            case "greenLight": return R.style.Theme_BookPlayer_GreenLight;
            case "green": return R.style.Theme_BookPlayer_Green;
            case "greenDark": return R.style.Theme_BookPlayer_GreenDark;
            case "gray":
            default:
                return R.style.Theme_BookPlayer_Gray;
        }
    }


    /////////////////// REWIND AFTER PAUSE option ///////////////////
    public static void setRewindAfterPause(boolean bool) {prefs.edit().putBoolean("REWIND_AFTER_PAUSE",bool).apply();}
    public static boolean getRewindAfterPause() {return prefs.getBoolean("REWIND_AFTER_PAUSE", DEFAULT_REWIND_AFTER_PAUSE);}

    /////////////////// COPY FILES ///////////////////
    public static void setCopyFile(boolean bool) {prefs.edit().putBoolean("COPY_FILES",bool).apply();}
    public static boolean getCopyFile() {return prefs.getBoolean("COPY_FILES", DEFAULT_COPY_FILES);}

    /////////////////// LOG ///////////////////
    public static void setTechLog(boolean bool) {prefs.edit().putBoolean("TECH_LOG",bool).apply();}
    public static boolean getTechLog() {return prefs.getBoolean("TECH_LOG", DEFAULT_TECH_LOG);}

    /////////////////// OPEN WITH ///////////////////
    public static void setOpenWith(boolean bool) {prefs.edit().putBoolean("OPEN_WITH",bool).apply();}
    public static boolean getOpenWith() {return prefs.getBoolean("OPEN_WITH", DEFAULT_OPEN_WITH);}
    public static void setOpenWith_all(boolean bool) {prefs.edit().putBoolean("OPEN_WITH_ALL",bool).apply();}
    public static boolean getOpenWith_all() {return prefs.getBoolean("OPEN_WITH_ALL", DEFAULT_OPEN_WITH_ALL);}

    /////////////////// SPLIT M4B ///////////////////
    public static void setSplitM4b(boolean bool) {prefs.edit().putBoolean("SPLIT_M4B",bool).apply();}
    public static boolean getSplitM4b() {return prefs.getBoolean("SPLIT_M4B", DEFAULT_SPLIT_M4B);}

    /////////////////// USE SD CARD ///////////////////
    public static void setUseSdCard(boolean bool) {prefs.edit().putBoolean("USE_SD_CARD",bool).apply();}
    public static boolean getUseSdCard() {return prefs.getBoolean("USE_SD_CARD", DEFAULT_USE_SD_CARD);}

    /////////////////// DOWNLOAD ON WIFI ///////////////////
    public static void setNetworkPolicyManualDownload(NetworkUtils.NetworkPolicyManual policy) {prefs.edit().putInt("MANUAL_DOWNLOAD_POLICY_KEY", policy.ordinal()).apply();}
    public static NetworkUtils.NetworkPolicyManual getNetworkPolicyManualDownload() {
        int index = prefs.getInt("MANUAL_DOWNLOAD_POLICY_KEY", DEFAULT_MANUAL_DOWNLOAD_POLICY.ordinal());
        return NetworkUtils.NetworkPolicyManual.values()[Math.max(0, Math.min(index, NetworkUtils.NetworkPolicyManual.values().length - 1))];
    }
    public static void setNetworkPolicyAutoDownload(NetworkUtils.NetworkPolicyAuto policy) {prefs.edit().putInt("AUTO_DOWNLOAD_POLICY_KEY", policy.ordinal()).apply();}
    public static NetworkUtils.NetworkPolicyAuto getNetworkPolicyAutoDownload() {
        int index = prefs.getInt("AUTO_DOWNLOAD_POLICY_KEY", DEFAULT_AUTO_DOWNLOAD_POLICY.ordinal());
        return NetworkUtils.NetworkPolicyAuto.values()[Math.max(0, Math.min(index, NetworkUtils.NetworkPolicyAuto.values().length - 1))];
    }

    /////////////////// PODCAST ///////////////////
    public static void setPodcastAutoDelete(boolean bool) {prefs.edit().putBoolean("PODCAST_AUTO_DELETE",bool).apply();}
    public static boolean getPodcastAutoDelete() {return prefs.getBoolean("PODCAST_AUTO_DELETE", DEFAULT_PODCAST_AUTO_DELETE);}

    public static void setPodcastAutoDeleteCompletionPercentage(int i) {prefs.edit().putInt("PODCAST_COMPLETION_PERCENTAGE_AUTO_DELETE",i).apply();}
    public static int getPodcastAutoDeleteCompletionPercentage() {return prefs.getInt("PODCAST_COMPLETION_PERCENTAGE_AUTO_DELETE", DEFAULT_PODCAST_COMPLETION_PERCENTAGE_AUTO_DELETE);}

    public static void setPodcastAutoDeleteDelay(int i) {prefs.edit().putInt("PODCAST_DELAY_AUTO_DELETE",i).apply();}
    public static int getPodcastAutoDeleteDelay() {return prefs.getInt("PODCAST_DELAY_AUTO_DELETE", DEFAULT_PODCAST_DELAY_AUTO_DELETE);}

    public static void setPodcastAutoDownloadLastNbEpisode(int i) {prefs.edit().putInt("PODCAST_AUTO_DOWNLOAD_LAST_N_EPISODES",i).apply();}
    public static int getPodcastAutoDownloadLastNbEpisode() {return prefs.getInt("PODCAST_AUTO_DOWNLOAD_LAST_N_EPISODES", DEFAULT_PODCAST_AUTO_DOWNLOAD_LAST_N_EPISODES);}

    public static void setPodcastAutoDownloadMaxNbPodcast(int i) {prefs.edit().putInt("PODCAST_AUTO_DOWNLOAD_MAX_N_PODCASTS",i).apply();}
    public static int getPodcastAutoDownloadMaxNbPodcast() {return prefs.getInt("PODCAST_AUTO_DOWNLOAD_MAX_N_PODCASTS", DEFAULT_PODCAST_AUTO_DOWNLOAD_MAX_N_PODCASTS);}

    public static void setPodcastAutoDownloadDelayBetweenChecks(int i) {prefs.edit().putInt("PODCAST_AUTO_DOWNLOAD_DELAY_BETWEEN_CHECKS_IN_MIN",i).apply();}
    public static int getPodcastAutoDownloadDelayBetweenChecks() {return prefs.getInt("PODCAST_AUTO_DOWNLOAD_DELAY_BETWEEN_CHECKS_IN_MIN", DEFAULT_PODCAST_AUTO_DOWNLOAD_DELAY_BETWEEN_CHECKS_IN_MIN);}

    public static void setPodcastAutoDownloadedAtTheTop(boolean bool) {prefs.edit().putBoolean("PODCAST_AUTO_DOWNLOADED_AT_THE_TOP",bool).apply();}
    public static boolean getPodcastAutoDownloadedAtTheTop() {return prefs.getBoolean("PODCAST_AUTO_DOWNLOADED_AT_THE_TOP", DEFAULT_PODCAST_AUTO_DOWNLOADED_AT_THE_TOP);}

    public static void setPodcastEpisodesSortOrder(boolean bool) {prefs.edit().putBoolean("PODCAST_EPISODES_SORT_NEWEST_TOP",bool).apply();}
    public static boolean getPodcastEpisodesSortOrder() {return prefs.getBoolean("PODCAST_EPISODES_SORT_NEWEST_TOP", DEFAULT_PODCAST_EPISODES_SORT_NEWEST_TOP);}

    public static void setPodcastEpisodesDescriptionExpand(boolean bool) {prefs.edit().putBoolean("PODCAST_EPISODES_DESCRIPTION_EXPAND",bool).apply();}
    public static boolean getPodcastEpisodesDescriptionExpand() {return prefs.getBoolean("PODCAST_EPISODES_DESCRIPTION_EXPAND", DEFAULT_PODCAST_EPISODES_DESCRIPTION_EXPAND);}

    public static void setPodcastOpenSpecificView(boolean bool) {prefs.edit().putBoolean("PODCAST_OPEN_SPECIFIC_VIEW",bool).apply();}
    public static boolean getPodcastOpenSpecificView() {return prefs.getBoolean("PODCAST_OPEN_SPECIFIC_VIEW", DEFAULT_PODCAST_OPEN_SPECIFIC_VIEW);}



    public static void setCreateCover(boolean bool) {prefs.edit().putBoolean("CREATE_COVER",bool).apply();}
    public static boolean getCreateCover() {return prefs.getBoolean("CREATE_COVER", DEFAULT_CREATE_COVER);}



    /////////////////// LANGUAGE ///////////////////
    public static void setAppLanguage(String language) {prefs.edit().putString("APP_LANGUAGE",language).apply();}
    public static String getAppLanguage() {return prefs.getString("APP_LANGUAGE", DEFAULT_LANGUAGE);}

    public static void setTtsVoice(String voice) {prefs.edit().putString("TTS_VOICE",voice).apply();}
    public static String getTtsVoice() {return prefs.getString("TTS_VOICE", DEFAULT_LANGUAGE);}

    /////////////////// NIGHT MODE ///////////////////

    public static String getNightMode() {return prefs.getString("KEY_NIGHT_MODE", "SYSTEM");}
    public static void setNightMode(String nightMode) { prefs.edit().putString("KEY_NIGHT_MODE", nightMode).apply();}

    public static void applyNightMode() {
        String nightMode = getNightMode();
        int appCompatMode = switch (nightMode) {
            case "LIGHT" -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
            case "DARK" -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES;
            default -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        };
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(appCompatMode);
    }

    /////////////////// TEXT APPEARANCE ///////////////////
    public static void setFontFamilyKey(@NonNull String family) { prefs.edit().putString("TEXT_FONT_FAMILY_KEY", family).apply(); }
    @NonNull public static String getFontFamilyKey() {
        String v = prefs.getString("TEXT_FONT_FAMILY_KEY", DEFAULT_FONT_FAMILY);
        return (v.isEmpty()) ? DEFAULT_FONT_FAMILY : v;
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
            float sizeSp  = Option.getTextSizeSp();
            tv.setTypeface(android.graphics.Typeface.create(family, android.graphics.Typeface.NORMAL));
            tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sizeSp);
        } catch (Throwable ignored) {}
    }




}
