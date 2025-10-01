package com.driot.bookplayer.test.util;

import android.app.Activity;
import android.util.Log;

import androidx.test.espresso.Espresso;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import java.util.Collection;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import com.driot.bookplayer.utils.KanLogger;

public class TestNavUtils {

    public static Activity getCurrentResumedActivity() {
        final Activity[] current = new Activity[1];
        getInstrumentation().runOnMainSync(() -> {
            Collection<Activity> activities =
                    ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED);
            if (!activities.isEmpty()) current[0] = activities.iterator().next();
        });
        return current[0];
    }

    public static void logCurrentActivity(String tag) {
        Activity a = getCurrentResumedActivity();
        myLogI("Current Activity: " + (a != null ? a.getClass().getName() : "none"));
    }

    public static boolean waitForActivity(Class<? extends Activity> target, long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            Activity a = getCurrentResumedActivity();
            if (a != null && target.isAssignableFrom(a.getClass())) return true;
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        }
        return false;
    }

    /** Press back up to maxPresses times, waiting after each press. */
    public static boolean pressBackTo(Class<? extends Activity> target, int maxPresses, long perStepWaitMs) {
        for (int i = 0; i < maxPresses; i++) {
            Espresso.pressBack(); // simulate back button
            if (waitForActivity(target, perStepWaitMs)) return true;
        }
        return false;
    }

    // ----------------------- LOG -----------------------
    private static final String TAG = "TestNavUtils";
    // ----------------------- LOG -----------------------
    private static void myLog(String str) { KanLogger.myLog(TAG, str); }
    private static void myLogD(String str) { Log.d(TAG, str); }
    private static void myLogI(String str) { KanLogger.myLogI(TAG, str); }
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }

}
