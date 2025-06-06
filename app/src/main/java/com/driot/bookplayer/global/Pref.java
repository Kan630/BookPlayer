package com.driot.bookplayer.global;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 06/06/2025
 */

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;

public class Pref {


    private static final String SHARED_PREFERENCES_DIVERSE = "SHARED_PREFERENCES_DIVERSE";

    /////////////////// OPEN WITH ... LAST IMPORTED FILE ///////////////////
    public static void set_Last_OpenWith_FileUri(Context c, String last_uri) {c.getSharedPreferences(SHARED_PREFERENCES_DIVERSE, MODE_PRIVATE).edit().putString("LAST_URI",last_uri).apply();}
    public static String get_Last_OpenWith_FileUri(Context c) {return c.getSharedPreferences(SHARED_PREFERENCES_DIVERSE, MODE_PRIVATE).getString("LAST_URI", "none");}

    public static void set_Last_OpenWith_File_Time(Context c) {c.getSharedPreferences(SHARED_PREFERENCES_DIVERSE, MODE_PRIVATE).edit().putLong("LAST_URI_TIME",System.currentTimeMillis()).apply();}
    public static long get_Last_OpenWith_File_Time(Context c) {return c.getSharedPreferences(SHARED_PREFERENCES_DIVERSE, MODE_PRIVATE).getLong("LAST_URI_TIME", 0);}

}
