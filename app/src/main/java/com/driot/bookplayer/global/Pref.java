package com.driot.bookplayer.global;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 06/06/2025
 */

import static android.content.Context.MODE_PRIVATE;

import static com.driot.bookplayer.utils.KanLogger.myLogE;

import android.content.Context;
import android.content.SharedPreferences;

public class Pref {


    private static final String SHARED_PREFERENCES_DIVERSE = "SHARED_PREFERENCES_DIVERSE";
    private static final String SHARED_PREFERENCE_INTROCUT = "SHARED_PREFERENCE_INTROCUT";


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


}
