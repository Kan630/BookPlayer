package com.driot.bookplayer.test;


import android.content.pm.ActivityInfo;

import com.driot.bookplayer.testutil.OrientationRule;

import org.junit.Rule;

public final class BasicNavPortraitTest extends BasicNavTest {
    @Rule
    public OrientationRule orientation = new OrientationRule(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    @Override protected int desiredOrientation() {
        return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
    }
}