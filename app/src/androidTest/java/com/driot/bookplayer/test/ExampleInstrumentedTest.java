package com.driot.bookplayer.test;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static com.driot.bookplayer.utils.log.KanLogger.myLog;
import static org.junit.Assert.*;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    @Test
    public void useAppContext() {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // legacy = com.driot.bookplayer, full = ...bookplayerfull, pure = ...bookplayerpure; all + ".debug"
        String pkg = appContext.getPackageName();
        assertTrue(pkg, pkg.matches("com\\.driot\\.bookplayer(full|pure)?\\.debug"));
    }

    /*
    @Test
    public void test01() {
        // Context of the app under test.
        String str = Build.FINGERPRINT;
        assertEquals(str, "toto");
    }
    @Test
    public void test02() {
        // Context of the app under test.
        String str = MD5(Build.FINGERPRINT);
        assertEquals(str, "toto");

    }

     */


}