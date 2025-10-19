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
    private static final boolean DEFAULT_START_AT_ZERO_NEXT_TRACK = true;
    private static final boolean DEFAULT_STOP_AUDIO_IF_USER_CLOSES_APP = true;
    private static final boolean DEFAULT_AUTO_PLAY_ON_MAIN_PLAYER = true;
    private static final boolean DEFAULT_OPEN_PLAY_ACTIVITY = true;
    private static final String DEFAULT_THEME_KEY = "purple"; //needs a string as resource ID are not stables between releases
    private static final boolean DEFAULT_COPY_FILES = true;
    private static final boolean DEFAULT_CLICK_VISUALIZER_PLAYPAUSE = false;
    private static final boolean DEFAULT_TECH_LOG = false;
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
    public static final boolean DEFAULT_PODCAST_EPISODES_DESCRIPTION_EXPAND = true;
    public static final boolean DEFAULT_PODCAST_OPEN_SPECIFIC_VIEW = true;
    public static final boolean DEFAULT_CREATE_COVER = true;
    private static final String DEFAULT_LANGUAGE = "system";
    public static final String DEFAULT_FONT_FAMILY = "sans-serif"; // neutre
    public static final float  DEFAULT_TEXT_SIZE_SP = 18f;
    public static final float  MIN_TEXT_SIZE_SP = 12f;
    public static final float  MAX_TEXT_SIZE_SP = 36f;
    public static final boolean DEFAULT_AUTOMOTIVE_ON = true;
    public static final boolean DEFAULT_AUTOMOTIVE_LET_CAR_AUTOPLAY = true;
    public static final boolean DEFAULT_AUTOMOTIVE_AUTO_RESUME_ON_CAR_CONNECT = true;
    public static final boolean DEFAULT_AUTOMOTIVE_KEEP_PHONE_PLAYBACK_ON_CAR_CONNECT = false;
    public static final int DEFAULT_LIBRIVOX_API_NB_RESULTS = 200;
    public static final int DEFAULT_PODCAST_INDEX_ORG_API_NB_RESULTS = 200;
    public static final int DEFAULT_TTS_HIGHLIGHT_DELAY_MS = 100;
    public static final int DEFAULT_TTS_CHUNK_SIZE = 1800;


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
    public static void setThemeColor(String themeColor) {
        prefs.edit().putString("CUSTOM_THEME_KEY", themeColor).apply();
    }
    public static int getThemeColor() {
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

    public static void setFontFamilyKey(String key) {
        prefs.edit().putString("FONT_FAMILY_KEY", key).apply();
    }
    public static String getFontFamilyKey() {
        return prefs.getString("FONT_FAMILY_KEY", "sans-serif");
    }

    public static @StyleRes int getThemeFontOverlay() {
        String themeKey = prefs.getString("FONT_FAMILY_KEY", "sans-serif");
        switch (themeKey) {
            case "serif":                 return R.style.ThemeOverlay_BookPlayer_Font_Serif;
            case "monospace":             return R.style.ThemeOverlay_BookPlayer_Font_Mono;
            case "casual":                return R.style.ThemeOverlay_BookPlayer_Font_Casual;
            case "cursive":               return R.style.ThemeOverlay_BookPlayer_Font_Cursive;
            case "serif-monospace":       return R.style.ThemeOverlay_BookPlayer_Font_SerifMono;
            case "sans-serif-condensed":  return R.style.ThemeOverlay_BookPlayer_Font_SansCondensed;
            case "sans-serif-medium":     return R.style.ThemeOverlay_BookPlayer_Font_SansMedium;
            case "sans-serif-smallcaps":  return R.style.ThemeOverlay_BookPlayer_Font_Smallcaps;
            case "sans-serif":
            default:                      return R.style.ThemeOverlay_BookPlayer_Font_Sans;
        }
    }


    /////////////////// REWIND AFTER PAUSE option ///////////////////
    public static void setRewindAfterPause(boolean bool) {prefs.edit().putBoolean("REWIND_AFTER_PAUSE",bool).apply();}
    public static boolean getRewindAfterPause() {return prefs.getBoolean("REWIND_AFTER_PAUSE", DEFAULT_REWIND_AFTER_PAUSE);}

    /////////////////// START AT ZERO NEXT TRACK option ///////////////////
    public static void setStartAtZeroNextTrack(boolean bool) {prefs.edit().putBoolean("START_AT_ZERO_NEXT_TRACK",bool).apply();}
    public static boolean getStartAtZeroNextTrack() {return prefs.getBoolean("START_AT_ZERO_NEXT_TRACK", DEFAULT_START_AT_ZERO_NEXT_TRACK);}

    /////////////////// STOP AUDIO IF USER CLOSES APP option ///////////////////
    public static void setStopAudioIfUserClosesApp(boolean bool) {prefs.edit().putBoolean("STOP_AUDIO_IF_USER_CLOSES_APP",bool).apply();}
    public static boolean getStopAudioIfUserClosesApp() {return prefs.getBoolean("STOP_AUDIO_IF_USER_CLOSES_APP", DEFAULT_STOP_AUDIO_IF_USER_CLOSES_APP);}

    /////////////////// AUTO_PLAY_ON_MAIN_PLAYER option ///////////////////
    public static void setAutoPlayOnMainPlayer(boolean bool) {prefs.edit().putBoolean("AUTO_PLAY_ON_MAIN_PLAYER",bool).apply();}
    public static boolean getAutoPlayOnMainPlayer() {return prefs.getBoolean("AUTO_PLAY_ON_MAIN_PLAYER", DEFAULT_AUTO_PLAY_ON_MAIN_PLAYER);}

    public static void setOpenPlayActivity(boolean bool) {prefs.edit().putBoolean("OPEN_PLAY_ACTIVITY",bool).apply();}
    public static boolean getOpenPlayActivity() {return prefs.getBoolean("OPEN_PLAY_ACTIVITY", DEFAULT_OPEN_PLAY_ACTIVITY);}


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
    public static void setNetworkPolicyManualDownload(NetworkHelper.NetworkPolicyManual policy) {prefs.edit().putInt("MANUAL_DOWNLOAD_POLICY_KEY", policy.ordinal()).apply();}
    public static NetworkHelper.NetworkPolicyManual getNetworkPolicyManualDownload() {
        int index = prefs.getInt("MANUAL_DOWNLOAD_POLICY_KEY", DEFAULT_MANUAL_DOWNLOAD_POLICY.ordinal());
        return NetworkHelper.NetworkPolicyManual.values()[Math.max(0, Math.min(index, NetworkHelper.NetworkPolicyManual.values().length - 1))];
    }
    public static void setNetworkPolicyAutoDownload(NetworkHelper.NetworkPolicyAuto policy) {prefs.edit().putInt("AUTO_DOWNLOAD_POLICY_KEY", policy.ordinal()).apply();}
    public static NetworkHelper.NetworkPolicyAuto getNetworkPolicyAutoDownload() {
        int index = prefs.getInt("AUTO_DOWNLOAD_POLICY_KEY", DEFAULT_AUTO_DOWNLOAD_POLICY.ordinal());
        return NetworkHelper.NetworkPolicyAuto.values()[Math.max(0, Math.min(index, NetworkHelper.NetworkPolicyAuto.values().length - 1))];
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



    /////////////////// APP LANGUAGE ///////////////////
    public static void setAppLanguage(String language) {prefs.edit().putString("APP_LANGUAGE",language).apply();}
    public static String getAppLanguage() {return prefs.getString("APP_LANGUAGE", DEFAULT_LANGUAGE);}

    /////////////////// TTS ///////////////////
    public static void setTtsVoice(String voice) {prefs.edit().putString("TTS_VOICE",voice).apply();}
    public static String getTtsVoice() {return prefs.getString("TTS_VOICE", DEFAULT_LANGUAGE);}

    public static void setTtsHighlightDelayMs(int delayMs) {prefs.edit().putInt("TTS_HIGHLIGHT_DELAY_MS", delayMs).apply();}
    public static int getTtsHighlightDelayMs() {return prefs.getInt("TTS_HIGHLIGHT_DELAY_MS", DEFAULT_TTS_HIGHLIGHT_DELAY_MS);}

    public static void setTtsChunkSize(int chunkSize) {prefs.edit().putInt("TTS_CHUNK_SIZE", chunkSize).apply();}
    public static int getTtsChunkSize() {return prefs.getInt("TTS_CHUNK_SIZE", DEFAULT_TTS_CHUNK_SIZE);}



    /////////////////// AUTOMOTIVE ///////////////////

    public static void setAutomotiveOn(boolean bool) {prefs.edit().putBoolean("AUTOMOTIVE_ON",bool).apply();}
    public static boolean getAutomotiveOn() {return prefs.getBoolean("AUTOMOTIVE_ON", DEFAULT_AUTOMOTIVE_ON);}


    public static void setAutomotiveLetCarAutoplay(boolean bool) {prefs.edit().putBoolean("AUTOMOTIVE_LET_CAR_AUTOPLAY",bool).apply();}
    public static boolean getAutomotiveLetCarAutoplay() {return prefs.getBoolean("AUTOMOTIVE_LET_CAR_AUTOPLAY", DEFAULT_AUTOMOTIVE_LET_CAR_AUTOPLAY);}

    public static void setAutomotiveAutoResumeOnCarConnect(boolean bool) {prefs.edit().putBoolean("AUTOMOTIVE_AUTO_RESUME_ON_CAR_CONNECT",bool).apply();}
    public static boolean getAutomotiveAutoResumeOnCarConnect() {return prefs.getBoolean("AUTOMOTIVE_AUTO_RESUME_ON_CAR_CONNECT", DEFAULT_AUTOMOTIVE_AUTO_RESUME_ON_CAR_CONNECT);}

    public static void setAutomotiveKeepPhonePlaybackOnCarConnect(boolean bool) {prefs.edit().putBoolean("AUTOMOTIVE_KEEP_PHONE_PLAYBACK_ON_CAR_CONNECT",bool).apply();}
    public static boolean getAutomotiveKeepPhonePlaybackOnCarConnect() {return prefs.getBoolean("AUTOMOTIVE_KEEP_PHONE_PLAYBACK_ON_CAR_CONNECT", DEFAULT_AUTOMOTIVE_KEEP_PHONE_PLAYBACK_ON_CAR_CONNECT);}

    public static void setLibrivoxApiNbResults(int i) {prefs.edit().putInt("LIBRIVOX_API_NB_RESULTS",i).apply();}
    public static int getLibrivoxApiNbResults() {return prefs.getInt("LIBRIVOX_API_NB_RESULTS", DEFAULT_LIBRIVOX_API_NB_RESULTS);}
    public static void setPodcastIndexOrgApiNbResults(int i) {prefs.edit().putInt("PODCAST_INDEX_ORG_API_NB_RESULTS",i).apply();}
    public static int getPodcastIndexOrgApiNbResults() {return prefs.getInt("PODCAST_INDEX_ORG_API_NB_RESULTS", DEFAULT_PODCAST_INDEX_ORG_API_NB_RESULTS);}


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


    public static int clampInt(
            Context context,
            EditText et,
            int min,
            int max,
            int def,
            String featureName   // e.g. getString(R.string.librivox)
    ) {
        if (et == null) return def;

        String str = et.getText().toString().trim();
        int val;
        try {
            val = Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return def;
        }

        if (val < min) {
            myToast(context.getString(R.string.minimum_number_of_results_for_) + " " + featureName + " " + context.getString(R.string.too_low));
            return min;
        }

        if (val > max) {
            myToast(context.getString(R.string.maximum_number_of_results_for_) + " " + featureName + " " + context.getString(R.string.too_high));
            return max;
        }

        return val;
    }

}
