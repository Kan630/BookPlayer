package com.driot.bookplayer.utils;

import android.util.Log;

import java.util.concurrent.TimeUnit;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 31/10/20
 */
public class Tonio {

    private void myLog(String str) {
        String TAG = this.getClass().getName().substring(this.getClass().getName().lastIndexOf(".")+1);
        Log.d("titi " + TAG + " ",str);
        System.out.println(str);
    }

    public static String FormatTime(double doubleTime) {
        String s;
        if (doubleTime>0) {
            s= String.format("%d min, %d sec",
                    TimeUnit.MILLISECONDS.toMinutes((long) doubleTime),
                    TimeUnit.MILLISECONDS.toSeconds((long) doubleTime) -
                            TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes((long)
                                    doubleTime)));
        } else {
            s = "";
        }
        return s;
    }
}
