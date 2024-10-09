package com.driot.bookplayer;

import android.content.Context;
import android.os.Build;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static com.driot.bookplayer.utils.KanLogger.myLog;
import static com.driot.bookplayer.utils.TonioCommonStuff.MD5;
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
        assertEquals("com.driot.bookplayer", appContext.getPackageName());
    }
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


}