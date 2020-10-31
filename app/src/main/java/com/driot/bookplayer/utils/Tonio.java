package com.driot.bookplayer.utils;

import android.util.Log;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 31/10/20
 */
class Tonio {

    private void myLog(String str) {
        String TAG = this.getClass().getName().substring(this.getClass().getName().lastIndexOf(".")+1);
        Log.d("titi " + TAG + " ",str);
        System.out.println(str);
    }

}
