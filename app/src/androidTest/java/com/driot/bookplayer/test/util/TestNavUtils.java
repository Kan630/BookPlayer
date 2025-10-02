package com.driot.bookplayer.test.util;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.widget.ScrollView;

import androidx.test.espresso.Espresso;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import java.util.Collection;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import com.driot.bookplayer.Var;
import com.driot.bookplayer.utils.KanLogger;

import org.hamcrest.Matcher;

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

    public static void logCurrentActivity() {
        Activity a = getCurrentResumedActivity();
        myLog("Current Activity: " + (a != null ? a.getClass().getName() : "none"));
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

    public static ViewAction scrollScrollViewToBottom() {
        return new ViewAction() {
            @Override public Matcher<View> getConstraints() {
                return ViewMatchers.isAssignableFrom(ScrollView.class);
            }
            @Override public String getDescription() { return "Scroll ScrollView to bottom"; }
            @Override public void perform(UiController ui, View v) {
                ((ScrollView) v).post(() -> ((ScrollView) v).fullScroll(View.FOCUS_DOWN));
                ui.loopMainThreadUntilIdle();
            }
        };
    }

    public static ViewAction scrollScrollViewToTop() {
        return new ViewAction() {
            @Override public Matcher<View> getConstraints() {
                return ViewMatchers.isAssignableFrom(ScrollView.class);
            }
            @Override public String getDescription() { return "Scroll ScrollView to top"; }
            @Override public void perform(UiController ui, View v) {
                ((ScrollView) v).post(() -> ((ScrollView) v).fullScroll(View.FOCUS_UP));
                ui.loopMainThreadUntilIdle();
            }
        };
    }

    public static void assertPressBackTo(Class<? extends Activity> targetActivity,
                                         int maxPresses,
                                         int waitStepMs) {
        if (!TestNavUtils.pressBackTo(targetActivity, maxPresses, waitStepMs)) {
            throw new AssertionError("Could not navigate back to " + targetActivity.getSimpleName());
        }
    }

    public static void assertPressBackTo(Class<? extends Activity> targetActivity) {
        assertPressBackTo(targetActivity, Var.BACK_MAX_PRESSES, Var.BACK_WAIT_STEP_MS);
    }


    public static void sleep(long millis) {
        myLog("sleep " + millis + "ms");
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // restore flag
        }
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
