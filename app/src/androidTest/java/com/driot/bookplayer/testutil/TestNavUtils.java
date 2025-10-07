package com.driot.bookplayer.testutil;

import android.app.Activity;
import android.view.View;
import android.widget.ScrollView;

import androidx.test.espresso.Espresso;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import java.util.Collection;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import com.driot.bookplayer.Var;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

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

    public static boolean isOn(Class<? extends Activity> clazz) {
        Activity a = getCurrentResumedActivity();
        myLogD("isOn? resumed=" + (a == null ? "none" : a.getClass().getSimpleName()));
        return a != null && clazz.isAssignableFrom(a.getClass());
    }

    public static boolean isTextVisible(String text) {
        // Only ask Espresso once the window is focused, or bail fast.
        if (!waitForWindowFocus(2_000)) return false;

        try {
            onView(withText(text)).check(matches(isDisplayed()));
            return true;
        } catch (Exception e) {
            // Covers NoMatchingViewException, AssertionError, PerformException, RootViewWithoutFocusException, etc.
            return false;
        }
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

    public static void assertWaitForActivity(Class<? extends Activity> target, long timeoutMs, String errorMsg) {
        if (waitForActivity(target, timeoutMs)) return;

        // Build a small lifecycle snapshot for the failure message
        final String[] snapshot = new String[1];
        getInstrumentation().runOnMainSync(() -> {
            StringBuilder sb = new StringBuilder();
            for (Stage s : Stage.values()) {
                Collection<Activity> acts = ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(s);
                if (acts != null && !acts.isEmpty()) {
                    sb.append(s).append(": ");
                    for (Activity a : acts) sb.append(a.getClass().getSimpleName()).append(' ');
                    sb.append(" | ");
                }
            }
            snapshot[0] = sb.toString();
        });

        throw new AssertionError(errorMsg + " - Timeout waiting for " + target.getSimpleName()
                + " after " + timeoutMs + "ms. Lifecycle snapshot -> " + snapshot[0]);
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

    // --- ANY-OF helpers ---

    /** Wait until the current RESUMED activity is one of the target classes. Returns the matched class or null on timeout. */
    @SafeVarargs
    public static Class<? extends Activity> waitForAnyActivity(long timeoutMs,
                                                               Class<? extends Activity>... targets) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            Activity a = getCurrentResumedActivity();
            if (a != null) {
                for (Class<? extends Activity> t : targets) {
                    if (t.isAssignableFrom(a.getClass())) return t;
                }
            }
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        }
        return null;
    }

    /** Assert variant: succeeds if *any* of the targets is RESUMED within timeout; throws with a lifecycle snapshot otherwise. */
    @SafeVarargs
    public static void assertWaitForAnyActivity(long timeoutMs,
                                                Class<? extends Activity>... targets) {
        Class<? extends Activity> hit = waitForAnyActivity(timeoutMs, targets);
        if (hit != null) {
            myLog("Reached activity: " + hit.getSimpleName());
            return;
        }

        // Build lifecycle snapshot for the failure message
        final String[] snapshot = new String[1];
        getInstrumentation().runOnMainSync(() -> {
            StringBuilder sb = new StringBuilder();
            for (Stage s : Stage.values()) {
                Collection<Activity> acts = ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(s);
                if (acts != null && !acts.isEmpty()) {
                    sb.append(s).append(": ");
                    for (Activity a : acts) sb.append(a.getClass().getSimpleName()).append(' ');
                    sb.append(" | ");
                }
            }
            snapshot[0] = sb.toString();
        });

        StringBuilder want = new StringBuilder();
        for (Class<? extends Activity> t : targets) {
            if (want.length() > 0) want.append(" or ");
            want.append(t.getSimpleName());
        }
        throw new AssertionError("Timeout waiting for any of [" + want + "] after " + timeoutMs
                + "ms. Lifecycle snapshot -> " + snapshot[0]);
    }
    public static void assertButtonWithTextExistsIfOnActivity(
            Class<? extends Activity> target, String buttonText) {

        Activity a = getCurrentResumedActivity();
        if (a == null) {
            throw new AssertionError("No current activity found.");
        }

        if (target.isAssignableFrom(a.getClass())) {
            try {
                onView(withText(buttonText))
                        .check(matches(isDisplayed()));
                myLog("Verified button with text \"" + buttonText + "\" on " + a.getClass().getSimpleName());
            } catch (Exception e) {
                throw new AssertionError("Button with text \"" + buttonText + "\" not found or not visible on "
                        + a.getClass().getSimpleName(), e);
            }
        } else {
            myLog("Current activity is " + a.getClass().getSimpleName() +
                    ", not " + target.getSimpleName() + " — skipping button check.");
        }
    }
    public static void clickButtonIfOnActivity(Class<? extends Activity> target, String buttonText) {
        Activity a = getCurrentResumedActivity();
        if (a == null) {
            throw new AssertionError("No current activity found.");
        }

        if (target.isAssignableFrom(a.getClass())) {
            myLog("Current activity is " + a.getClass().getSimpleName() +
                    ", looking for button \"" + buttonText + "\"…");

            try {
                onView(withText(buttonText))
                        .check(matches(isDisplayed()))
                        .perform(androidx.test.espresso.action.ViewActions.click());

                myLog("Clicked button \"" + buttonText + "\" on " + a.getClass().getSimpleName());
            } catch (Exception e) {
                myLogW("Button with text \"" + buttonText + "\" not found or not clickable on "
                        + a.getClass().getSimpleName() + ": " + e.getMessage());
            }
        } else {
            myLog("Current activity is " + a.getClass().getSimpleName() +
                    ", not " + target.getSimpleName() + " — skipping click.");
        }
    }

    // Add this helper
    public static boolean waitForWindowFocus(long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        final boolean[] hasFocus = new boolean[1];

        while (System.currentTimeMillis() < end) {
            final Activity a = getCurrentResumedActivity();
            if (a != null) {
                getInstrumentation().runOnMainSync(() -> {
                    View decor = a.getWindow() != null ? a.getWindow().getDecorView() : null;
                    hasFocus[0] = (decor != null) && decor.hasWindowFocus();
                });
                if (hasFocus[0]) return true;
            }
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        }
        return false;
    }


}
