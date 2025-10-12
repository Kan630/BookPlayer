package com.driot.bookplayer.test;

//import static com.driot.bookplayer.utils.log.KanLogger.*;
//import com.driot.bookplayer.testutil.LogSupport;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.testutil.LogSupport;
import com.driot.bookplayer.utils.log.KanLogger;
//import com.driot.bookplayer.utils.log.KanLogger;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;



import org.junit.After;
import org.junit.Before;
import org.junit.Test;


public class JustOpenAndWait implements LogSupport {

    private static final int WAIT_IN_SEC = 5;

    private ActivityScenario<?> scenario;
    private Context appContext;

    @Before
    public void launchApp() {
        appContext = ApplicationProvider.getApplicationContext();
        KanLogger.init(appContext);
        Option.setTechLog(true);

        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent launch = ctx.getPackageManager()
                .getLaunchIntentForPackage(ctx.getPackageName());
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        scenario = ActivityScenario.launch(launch);   // stays open until @After
    }

    @After
    public void tearDown() {
        if (scenario != null) scenario.close();
    }

    @Test
    public void navigate() {

        myLog("------------------------- Log Test");
        if (android.util.Log.isLoggable(TAG, android.util.Log.DEBUG)) {
            myLog("DEBUG isLoggable OK     - for TAG = " + TAG);
        } else {
            myLogE("DEBUG isLoggable KO     - for TAG = " + TAG);
        }
        myLog("------------------------- Log Test");
        android.util.Log.d(TAG, "DEBUG 0");
        myLogD("DEBUG");
        myLogD1("DEBUG 1");
        myLogD2("DEBUG 2");
        myLogD3("DEBUG 3");
        myLogD4("DEBUG 4");
        myLog("VERBOSE");
        myLogI("INFO");
        myLogW("WARNING");
        myLogE("ERROR");
        myLog("-------------------------");

        // your Espresso steps here (click buttons, scroll, etc.)
        SystemClock.sleep(1000 * WAIT_IN_SEC); // optional visual pause during debugging
    }
    // ----------------------- LOG -----------------------
    private static final String TAG = "JustOpenAndWait";
    /*
    private static void myLog(String str) { KanLogger.myLog(TAG, str); }
    private static void myLogD1(String str) { Log.d(TAG, str); }
    private static void myLogD2(String str) { KanLogger.myLogD(TAG, str); }
    private static void myLogI(String str) { KanLogger.myLogI(TAG, str); }
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }

     */
}
