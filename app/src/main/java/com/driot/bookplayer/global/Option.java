package com.driot.bookplayer.global;

import static android.content.Context.MODE_PRIVATE;
import static com.driot.bookplayer.utils.KanMail.DEFAULT_SEND_MAIL_METHOD_DEFAULT;

import android.content.Context;
import android.util.TypedValue;


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
    private static final int DEFAULT_CUSTOM_THEME = 0;
    private static final boolean DEFAULT_COPY_FILES = true;
    private static final boolean DEFAULT_CLICK_VISUALIZER_PLAYPAUSE = false;
    private static final boolean DEFAULT_TECH_LOG = false;
    private static final boolean DEFAULT_OPEN_WITH = true;
    private static final boolean DEFAULT_SPLIT_M4B = true;

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
    public static void setTheme(int i) {prefs.edit().putInt("CUSTOM_THEME",i).apply();}
    public static int getTheme() {
        int themeId = prefs.getInt("CUSTOM_THEME", DEFAULT_CUSTOM_THEME);
        if (themeId == 0) {
            TypedValue typedValue = new TypedValue();
            appContext.getTheme().resolveAttribute(android.R.attr.theme, typedValue, true);
            return typedValue.resourceId;
        } else {
            return themeId;
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

    /////////////////// SPLIT M4B ///////////////////
    public static void setSplitM4b(boolean bool) {prefs.edit().putBoolean("SPLIT_M4B",bool).apply();}
    public static boolean getSplitM4b() {return prefs.getBoolean("SPLIT_M4B", DEFAULT_SPLIT_M4B);}


}
