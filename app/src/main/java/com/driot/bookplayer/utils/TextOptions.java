package com.driot.bookplayer.utils;

import android.content.Context;

import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.utils.log.LoggerHelper;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 02/12/20
 */
public class TextOptions extends LoggerHelper {

    public TextOptions(Class<?> clazz) {
        super(clazz);
    }

    /**
     * RecyclerView
     */

    public void saveScrollPosition(Context c, String file, int posRecyclerView) {
        Pref.setTextScrollPos(file, (float) posRecyclerView);
        // myLog("saving " + posRecyclerView);
    }

    public void setScrollPosition(Context c, String file, RecyclerView recyclerView) {
        try {
            float spot = Pref.getTextScrollPos(file);
            // myLog("scroll to " + spot);
            recyclerView.scrollToPosition((int) spot);
        } catch (Exception e) {
            myLogEE(e, "sharedPref setScrollPosition recyclerview");
        }
    }

}
