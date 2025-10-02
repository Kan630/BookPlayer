package com.driot.bookplayer;


import android.content.pm.ActivityInfo;

public final class BasicNavPortraitTest extends BasicNavTest {
    @Override protected int desiredOrientation() {
        return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
    }
}