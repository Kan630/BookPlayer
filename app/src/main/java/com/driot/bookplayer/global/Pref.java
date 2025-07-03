package com.driot.bookplayer.global;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 06/06/2025
 */

import static android.content.Context.MODE_PRIVATE;

import static com.driot.bookplayer.utils.KanLogger.myLogE;
import static com.driot.bookplayer.utils.KanMail.DEFAULT_SEND_MAIL_METHOD_DEFAULT;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;
import android.util.Base64;

import com.driot.bookplayer.objects.LoadBookTaskState;
import com.driot.bookplayer.utils.KanLogger;

public class Pref {


    private static final String SHARED_PREFERENCES_DIVERSE = "SHARED_PREFERENCES_DIVERSE";
    private static final String SHARED_PREFERENCE_INTROCUT = "SHARED_PREFERENCE_INTROCUT";

    private static final String SHARED_PREFERENCES_DOWNLOAD = "SHARED_PREFERENCES_DOWNLOAD";
    private static final String KEY_LOAD_BOOK_TASK_STATE = "loadBookTaskState";

    private static Context appContext;
    private static android.content.SharedPreferences prefs;
    public static void init(Context context) {
        appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(SHARED_PREFERENCES_DIVERSE, MODE_PRIVATE);
    }



    /////////////////// HAS BEEN PAUSED FOR  ///////////////////
    public static void setPauseTime(long value) {prefs.edit().putLong("PAUSE_TIME", value).apply();}
    public static void setPauseTime() {prefs.edit().putLong("PAUSE_TIME", System.currentTimeMillis()).apply();myLog("pause time set");}
    public static long getPauseTime() {return prefs.getLong("PAUSE_TIME", 0);}





    /////////////////// OPEN WITH ... LAST IMPORTED FILE ///////////////////
    public static void set_Last_OpenWith_FileUri(Context c, String last_uri) {c.getSharedPreferences(SHARED_PREFERENCES_DIVERSE, MODE_PRIVATE).edit().putString("LAST_URI",last_uri).apply();}
    public static String get_Last_OpenWith_FileUri(Context c) {return c.getSharedPreferences(SHARED_PREFERENCES_DIVERSE, MODE_PRIVATE).getString("LAST_URI", "none");}

    public static void set_Last_OpenWith_File_Time(Context c) {c.getSharedPreferences(SHARED_PREFERENCES_DIVERSE, MODE_PRIVATE).edit().putLong("LAST_URI_TIME",System.currentTimeMillis()).apply();}
    public static long get_Last_OpenWith_File_Time(Context c) {return c.getSharedPreferences(SHARED_PREFERENCES_DIVERSE, MODE_PRIVATE).getLong("LAST_URI_TIME", 0);}




    public static void saveIntroCutToPref(Context c, int idFolder, int introCut) {
        try {
            SharedPreferences.Editor editor = c.getSharedPreferences(SHARED_PREFERENCE_INTROCUT, MODE_PRIVATE).edit();
            editor.putInt(Integer.toString(idFolder), introCut).apply();
        } catch (Exception e) {
            myLogE("error saving introCut in prefs - " + e.getMessage());
        }
    }

    public static int getIntroCutFromPref(Context c, int idFolder) {
        try {
            SharedPreferences prefs = c.getSharedPreferences(SHARED_PREFERENCE_INTROCUT, MODE_PRIVATE);
            return prefs.getInt(String.valueOf(idFolder), 0);
        } catch (Exception e) {
            myLogE("error getting introCut from prefs - " + e.getMessage());
            return 0;
        }
    }



    public static void setLoadBookTaskState(Context context, LoadBookTaskState loadBookTaskState) {
        Parcel parcel = Parcel.obtain();
        loadBookTaskState.writeToParcel(parcel, 0);
        byte[] bytes = parcel.marshall();
        parcel.recycle();

        String encoded = Base64.encodeToString(bytes, Base64.DEFAULT);

        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREFERENCES_DOWNLOAD, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LOAD_BOOK_TASK_STATE, encoded).apply();
    }

    public static LoadBookTaskState getLoadBookTaskState(Context context) {
        return getLoadBookTaskState(context, false);
    }
    public static LoadBookTaskState getLoadBookTaskState(Context context, boolean doPrint) {
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREFERENCES_DOWNLOAD, Context.MODE_PRIVATE);
        String encoded = prefs.getString(KEY_LOAD_BOOK_TASK_STATE, null);

        if (encoded == null) return null;

        byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
        Parcel parcel = Parcel.obtain();
        parcel.unmarshall(bytes, 0, bytes.length);
        parcel.setDataPosition(0);

        LoadBookTaskState result = LoadBookTaskState.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        if (doPrint) myLog(result.toString());

        return result;
    }

    public static void clearLoadBookTaskState(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREFERENCES_DOWNLOAD, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_LOAD_BOOK_TASK_STATE).apply();
    }





    /////////////////// OPEN WITH ... LAST IMPORTED FILE ///////////////////
    public static void set_Audio_Language(Context c, String audioLanguage) {c.getSharedPreferences(SHARED_PREFERENCES_DIVERSE, MODE_PRIVATE).edit().putString("AUDIO_LANGUAGE",audioLanguage).apply();}
    public static String get_Audio_Language(Context c) {return c.getSharedPreferences(SHARED_PREFERENCES_DIVERSE, MODE_PRIVATE).getString("AUDIO_LANGUAGE", "eng");}





    private static void myLog(String str) { KanLogger.myLog("Pref", str); }


}
