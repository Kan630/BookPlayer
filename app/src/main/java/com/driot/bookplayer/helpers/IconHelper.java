package com.driot.bookplayer.helpers;

import android.view.View;
import android.widget.ImageView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.utils.KanLogger;

public class IconHelper {


    public static void setSourceIcon(ImageView ivSource, String sourceLocation, String playType) {
        if (sourceLocation==null) {
            myLogD("bad param for setSourceIcon - sourceLocation = " + sourceLocation);
            sourceLocation="***";
        }
        if (playType==null) {
            myLogD("bad param for setSourceIcon - playType = " + playType);
            playType="***";
        }

        if (sourceLocation.equals(Var.SOURCE_LOCATION_PODCAST)) {
            ivSource.setVisibility(View.VISIBLE);
            ivSource.setImageResource(R.drawable.ic_podcast_24);
        } else if (sourceLocation.equals(Var.SOURCE_LOCATION_LIBRIVOX)) {
            ivSource.setVisibility(View.VISIBLE);
            ivSource.setImageResource(R.drawable.ic_librivox_24);
        } else if (playType.equals(Var.PLAY_TYPE_TEXT)) {
            ivSource.setVisibility(View.VISIBLE);
            ivSource.setImageResource(R.drawable.ic_tts_24);
            myLog("coucou");
        } else {
            ivSource.setVisibility(View.GONE);
        }
    }


    // ----------------------- LOG -----------------------
    private static final String TAG = "IconHelper";
    private static void myLog(String str) { KanLogger.myLog(TAG, str); }
    private static void myLogD(String str) { KanLogger.myLogD(TAG, str); }
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogI(String str) { KanLogger.myLogI(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
    private static void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, TAG, str); }
    private static void myToastEE(Throwable t, String str) { KanLogger.myToastEE(t, TAG, str); }
}


