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
    private static final boolean DEFAULT_SPLIT_M4B = false;


    /////////////////// SLEEP - AUTOMATIC PAUSE ///////////////////
    public static void setTimeBeforeSleep(Context context, int i) {context.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).edit().putInt("TIME_BEFORE_SLEEP",i).apply();}
    public static int getTimeBeforeSleep(Context context) {return context.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).getInt("TIME_BEFORE_SLEEP", DEFAULT_TIME_BEFORE_SLEEP);}

    /////////////////// FORWARD-BACKWARD DURATION ///////////////////
    public static void set_ForwardSeconds(Context context, int i) {context.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).edit().putInt("FORWARD_SECONDS",i).apply();}
    public static int get_ForwardSeconds(Context context) {return context.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).getInt("FORWARD_SECONDS", DEFAULT_FORWARD_SECONDS);}

    /////////////////// ZIP options ///////////////////
    public static void setUnZipLocal(Context context, boolean bool) {context.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).edit().putBoolean("UNZIP_LOCAL",bool).apply();}
    public static void setCopyZipLocal(Context context, boolean bool) {context.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).edit().putBoolean("COPY_ZIP_LOCAL",bool).apply();}
    public static boolean getUnZipLocal(Context context) {return context.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).getBoolean("UNZIP_LOCAL", DEFAULT_UNZIP_LOCAL);}
    public static boolean getCopyZipLocal(Context context) {return context.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).getBoolean("COPY_ZIP_LOCAL", DEFAULT_COPY_ZIP_LOCAL);}

    /////////////////// SCREEN ORIENTATION options ///////////////////
    public static void setScreenOrientationLock(Context context, boolean bool) {context.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).edit().putBoolean("LOCK_SCREEN_ORIENTATION",bool).apply();}
    public static boolean getScreenOrientationLock(Context context) {return context.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).getBoolean("LOCK_SCREEN_ORIENTATION", DEFAULT_SCREEN_ORIENTATION_LOCK);}

    /////////////////// SEND MAIL options ///////////////////
    public static void setMailMethod(Context context, boolean bool) {context.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).edit().putBoolean("SEND_MAIL_METHOD_DEFAULT",bool).apply();}
    public static Boolean getMailMethod(Context context) {return context.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).getBoolean("SEND_MAIL_METHOD_DEFAULT", DEFAULT_SEND_MAIL_METHOD_DEFAULT);}

    /////////////////// BEEP options ///////////////////
    public static void setBeepChapter(Context context, boolean bool) {context.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).edit().putBoolean("BEEP_CHAPTER",bool).apply();}
    public static Boolean getBeepChapter(Context context) {return context.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).getBoolean("BEEP_CHAPTER", DEFAULT_BEEP_CHAPTER);}

    public static void setBeepBookEnd(Context context, boolean bool) {context.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).edit().putBoolean("BEEP_BOOKEND",bool).apply();}
    public static Boolean getBeepBookEnd(Context context) {return context.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).getBoolean("BEEP_BOOKEND", DEFAULT_BEEP_BOOKEND);}
    public static void setBeepAutoStop(Context context, boolean bool) {context.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).edit().putBoolean("BEEP_AUTOSTOP",bool).apply();}
    public static Boolean getBeepAutoStop(Context context) {return context.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).getBoolean("BEEP_AUTOSTOP", DEFAULT_BEEP_AUTOSTOP);}

    /////////////////// DELETE SOURCE FILE option ///////////////////
    public static void setDeleteSourceFile(Context context, boolean bool) {context.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).edit().putBoolean("DELETE_SOURCE_FILE",bool).apply();}
    public static Boolean getDeleteSourceFile(Context context) {return context.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).getBoolean("DELETE_SOURCE_FILE", DEFAULT_DELETE_SOURCE_FILE);}

    /////////////////// VISUALIZER option ///////////////////
    public static void setVisualizerOn(Context context, boolean bool) {context.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).edit().putBoolean("VISUALIZER_ON",bool).apply();}
    public static Boolean getVisualizerOn(Context context) {return context.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).getBoolean("VISUALIZER_ON", DEFAULT_VISUALIZER_ON);}

    public static void setClickVisualizerPlayPause(Context context, boolean bool) {context.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).edit().putBoolean("CLICK_VISUALIZER_PLAYPAUSE",bool).apply();}
    public static Boolean getClickVisualizerPlayPause(Context context) {return context.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).getBoolean("CLICK_VISUALIZER_PLAYPAUSE", DEFAULT_CLICK_VISUALIZER_PLAYPAUSE);}

    /////////////////// THEME ///////////////////
    public static void setTheme(Context context, int i) {context.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).edit().putInt("CUSTOM_THEME",i).apply();}
    public static int getTheme(Context context) {
        int themeId = context.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).getInt("CUSTOM_THEME", DEFAULT_CUSTOM_THEME);
        if (themeId == 0) {
            TypedValue typedValue = new TypedValue();
            context.getTheme().resolveAttribute(android.R.attr.theme, typedValue, true);
            return typedValue.resourceId;
        } else {
            return themeId;
        }
    }


    /////////////////// REWIND AFTER PAUSE option ///////////////////
    public static void setRewindAfterPause(Context context, boolean bool) {context.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).edit().putBoolean("REWIND_AFTER_PAUSE",bool).apply();}
    public static Boolean getRewindAfterPause(Context context) {return context.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).getBoolean("REWIND_AFTER_PAUSE", DEFAULT_REWIND_AFTER_PAUSE);}

    /////////////////// COPY FILES ///////////////////
    public static void setCopyFile(Context context, boolean bool) {context.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).edit().putBoolean("COPY_FILES",bool).apply();}
    public static Boolean getCopyFile(Context context) {return context.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).getBoolean("COPY_FILES", DEFAULT_COPY_FILES);}

    /////////////////// LOG ///////////////////
    public static void setTechLog(Context context, boolean bool) {context.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).edit().putBoolean("TECH_LOG",bool).apply();}
    public static Boolean getTechLog(Context context) {return context.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).getBoolean("TECH_LOG", DEFAULT_TECH_LOG);}

    /////////////////// OPEN WITH ///////////////////
    public static void setOpenWith(Context context, boolean bool) {context.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).edit().putBoolean("OPEN_WITH",bool).apply();}
    public static Boolean getOpenWith(Context context) {return context.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).getBoolean("OPEN_WITH", DEFAULT_OPEN_WITH);}

    /////////////////// SPLIT M4B ///////////////////
    public static void setSplitM4b(Context context, boolean bool) {context.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).edit().putBoolean("SPLIT_M4B",bool).apply();}
    public static Boolean getSplitM4b(Context context) {return context.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).getBoolean("SPLIT_M4B", DEFAULT_SPLIT_M4B);}


}
