package com.driot.bookplayer.test;


import android.content.pm.ActivityInfo;

public final class BasicNavLandscapeTest extends BasicNavTest {
    @Override protected int desiredOrientation() {
        return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
    }
}