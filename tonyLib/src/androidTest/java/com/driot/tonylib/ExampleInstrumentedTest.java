package com.driot.tonylib;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static com.driot.tonylib.KanLogger.myLog;
import static com.driot.tonylib.TonioCommonStuff.MD5;
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
        assertEquals("com.driot.tonylib.test", appContext.getPackageName());
    }

    @Test
    public void getFingerPrint() {
        // Context of the app under test.
        //Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String str = "";
        str = Build.FINGERPRINT;
        assertEquals("xxxxxxxx", str);

    }
    @Test
    public void getFingerPrint2() {
        // Context of the app under test.
        //Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String str = ""; String str2 = "";
        str = Build.FINGERPRINT;
        str2 = MD5(str);
        Log.i("toto","MD5 Phone : " + str2);
        assertEquals("xxxxxxxx2", str2);
    }

}