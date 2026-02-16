package com.driot.bookplayer.testutil;

import android.app.Activity;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import java.util.Collection;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import com.driot.bookplayer.Var;
import com.driot.bookplayer.utils.Tonio;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import org.hamcrest.Matcher;

public class TestNavUtils {

    public static Activity getCurrentResumedActivity() {
        final Activity[] current = new Activity[1];
        getInstrumentation().runOnMainSync(() -> {
            Collection<Activity> activities = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED);
            if (!activities.isEmpty())
                current[0] = activities.iterator().next();
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
        if (!waitForWindowFocus(2_000))
            return false;

        try {
            onView(withText(text)).check(matches(isDisplayed()));
            return true;
        } catch (Exception e) {
            // Covers NoMatchingViewException, AssertionError, PerformException,
            // RootViewWithoutFocusException, etc.
            return false;
        }
    }

    public static boolean waitForActivity(Class<? extends Activity> target, long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            Activity a = getCurrentResumedActivity();
            if (a != null && target.isAssignableFrom(a.getClass()))
                return true;
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
            }
        }
        return false;
    }

    public static void assertWaitForActivity(Class<? extends Activity> target, long timeoutMs, String errorMsg) {
        if (waitForActivity(target, timeoutMs))
            return;

        // Build a small lifecycle snapshot for the failure message
        final String[] snapshot = new String[1];
        getInstrumentation().runOnMainSync(() -> {
            StringBuilder sb = new StringBuilder();
            for (Stage s : Stage.values()) {
                Collection<Activity> acts = ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(s);
                if (acts != null && !acts.isEmpty()) {
                    sb.append(s).append(": ");
                    for (Activity a : acts)
                        sb.append(a.getClass().getSimpleName()).append(' ');
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
            myLogD("press back to reach " + target.getSimpleName());
            if (waitForActivity(target, perStepWaitMs))
                return true;
        }
        return false;
    }

    public static boolean maybePressBackTo(Class<? extends Activity> target, int maxPresses, long perStepWaitMs) {
        for (int i = 0; i < maxPresses; i++) {
            if (waitForActivity(target, perStepWaitMs))
                return true;
            Espresso.pressBack(); // simulate back button
            myLogD("pressed back (" + i + ") to reach " + target.getSimpleName());
        }
        return waitForActivity(target, perStepWaitMs);
    }

    public static ViewAction scrollScrollViewToBottom() {
        return new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return ViewMatchers.isAssignableFrom(ScrollView.class);
            }

            @Override
            public String getDescription() {
                return "Scroll ScrollView to bottom";
            }

            @Override
            public void perform(UiController ui, View v) {
                ((ScrollView) v).post(() -> ((ScrollView) v).fullScroll(View.FOCUS_DOWN));
                ui.loopMainThreadUntilIdle();
            }
        };
    }

    public static ViewAction scrollScrollViewToTop() {
        return new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return ViewMatchers.isAssignableFrom(ScrollView.class);
            }

            @Override
            public String getDescription() {
                return "Scroll ScrollView to top";
            }

            @Override
            public void perform(UiController ui, View v) {
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

    public static void sleep(long millis, String customLogMessage) {
        String log = "sleep " + Tonio.formatMS(millis);
        if (!customLogMessage.isEmpty())
            log += " - " + customLogMessage;
        myLog(log);
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // restore flag
        }
    }

    public static void sleep(long millis) {
        sleep(millis, "");
    }

    // --- ANY-OF helpers ---

    /**
     * Wait until the current RESUMED activity is one of the target classes. Returns
     * the matched class or null on timeout.
     */
    @SafeVarargs
    public static Class<? extends Activity> waitForAnyActivity(long timeoutMs,
            Class<? extends Activity>... targets) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            Activity a = getCurrentResumedActivity();
            if (a != null) {
                for (Class<? extends Activity> t : targets) {
                    if (t.isAssignableFrom(a.getClass()))
                        return t;
                }
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
            }
        }
        return null;
    }

    /**
     * Assert variant: succeeds if *any* of the targets is RESUMED within timeout;
     * throws with a lifecycle snapshot otherwise.
     */
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
                    for (Activity a : acts)
                        sb.append(a.getClass().getSimpleName()).append(' ');
                    sb.append(" | ");
                }
            }
            snapshot[0] = sb.toString();
        });

        StringBuilder want = new StringBuilder();
        for (Class<? extends Activity> t : targets) {
            if (want.length() > 0)
                want.append(" or ");
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
                if (hasFocus[0])
                    return true;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
            }
        }
        return false;
    }

    public static void waitForViewVisible(@IdRes int viewId, long timeoutMs, String err) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (TestNavUtils.waitForWindowFocus(300)) {
                try {
                    onView(withId(viewId)).check(matches(isDisplayed()));
                    return;
                } catch (NoMatchingViewException | AssertionError ignored) {
                }
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
            }
        }
        throw new AssertionError(err + " (id=" + viewId + ")");
    }

    public static void waitForTextVisible(String text, long timeoutMs, String err) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (TestNavUtils.isTextVisible(text))
                return;
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
            }
        }
        throw new AssertionError(err + " (text=\"" + text + "\")");
    }

    // --- RecyclerView item count helpers ---

    /**
     * Returns adapter.getItemCount() for the RecyclerView, or 0 if view/adapter not
     * found.
     */
    public static int getRecyclerItemCount(@IdRes int recyclerId) {
        final int[] out = { 0 };
        final Activity a = getCurrentResumedActivity();
        if (a == null)
            return 0;

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            RecyclerView rv = a.findViewById(recyclerId);
            if (rv != null && rv.getAdapter() != null) {
                out[0] = rv.getAdapter().getItemCount();
            } else {
                out[0] = 0;
            }
        });
        return out[0];
    }

    /**
     * Waits until adapter is non-null and its itemCount equals expected, or times
     * out.
     * Returns true on success, false on timeout. Uses short sleeps to avoid ANR.
     */
    public static boolean waitForRecyclerItemCountEquals(@IdRes int recyclerId,
            int expected,
            long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (waitForWindowFocus(300)) {
                final int[] count = { -1 };
                final boolean[] ok = { false };

                final Activity a = getCurrentResumedActivity();
                if (a != null) {
                    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                        RecyclerView rv = a.findViewById(recyclerId);
                        if (rv != null && rv.getAdapter() != null) {
                            count[0] = rv.getAdapter().getItemCount();
                            ok[0] = true;
                        }
                    });
                    if (ok[0]) {
                        if (count[0] == expected)
                            return true;
                    }
                }
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
            }
        }
        return false;
    }

    /**
     * Waits until adapter is non-null and itemCount >= minCount (useful while data
     * is loading).
     * Returns the last observed count (>= minCount on success, otherwise what we
     * saw at timeout).
     */
    public static int waitForRecyclerItemCountAtLeast(@IdRes int recyclerId,
            int minCount,
            long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        int last = -1;
        while (System.currentTimeMillis() < end) {
            if (waitForWindowFocus(300)) {
                final int[] count = { -1 };
                final Activity a = getCurrentResumedActivity();
                if (a != null) {
                    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                        RecyclerView rv = a.findViewById(recyclerId);
                        if (rv != null && rv.getAdapter() != null) {
                            count[0] = rv.getAdapter().getItemCount();
                        }
                    });
                    if (count[0] >= 0) {
                        last = count[0];
                        if (last >= minCount)
                            return last;
                    }
                }
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
            }
        }
        return last; // may be < minCount if we timed out
    }

    /**
     * Returns the adapter after the UI thread is idle (reduces race with DiffUtil).
     */
    @Nullable
    private static RecyclerView.Adapter<?> getRecyclerAdapterIdle(@IdRes int recyclerId) {
        final Activity a = getCurrentResumedActivity();
        if (a == null)
            return null;
        final RecyclerView.Adapter<?>[] out = { null };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            RecyclerView rv = a.findViewById(recyclerId);
            if (rv != null)
                out[0] = rv.getAdapter();
        });
        // Let pending layout/diff work settle
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        return out[0];
    }

    /**
     * True if this adapter is a known non-content wrapper like Paging3
     * LoadStateAdapter.
     */
    private static boolean isNonContentAdapter(RecyclerView.Adapter<?> a) {
        if (a == null)
            return false;
        // Paging3: androidx.paging.LoadStateAdapter
        try {
            Class<?> loadState = Class.forName("androidx.paging.LoadStateAdapter");
            if (loadState.isAssignableFrom(a.getClass()))
                return true;
        } catch (ClassNotFoundException ignore) {
        }
        // Add your own header/footer adapter classes here if you have them:
        // if (a instanceof MyHeaderAdapter) return true;
        // if (a instanceof MyFooterAdapter) return true;
        return false;
    }

    /**
     * Counts only "content" items.
     * - If adapter is a ConcatAdapter, sums itemCount of child adapters excluding
     * non-content ones.
     * - Otherwise returns adapter.getItemCount().
     */
    public static int getRecyclerContentItemCount(@IdRes int recyclerId) {
        RecyclerView.Adapter<?> adapter = getRecyclerAdapterIdle(recyclerId);
        if (adapter == null)
            return 0;

        // Handle ConcatAdapter by summing children and skipping non-content wrappers.
        try {
            Class<?> concatCls = Class.forName("androidx.recyclerview.widget.ConcatAdapter");
            if (concatCls.isInstance(adapter)) {
                int sum = 0;
                // Call concatAdapter.getAdapters()
                @SuppressWarnings("unchecked")
                java.util.List<RecyclerView.Adapter<?>> children = (java.util.List<RecyclerView.Adapter<?>>) concatCls
                        .getMethod("getAdapters").invoke(adapter);
                for (RecyclerView.Adapter<?> child : children) {
                    if (!isNonContentAdapter(child)) {
                        sum += child.getItemCount();
                    }
                }
                return sum;
            }
        } catch (Throwable ignore) {
            // Reflection failed → fall back to plain count
        }
        // Non-concat: just return the adapter's count (may include header/footer if
        // present).
        return adapter.getItemCount();
    }

    /**
     * Assertion wrapper that throws with a clear message + current lifecycle
     * snapshot on failure.
     */
    public static void assertRecyclerItemCountEquals(@IdRes int recyclerId,
            int expected,
            long timeoutMs,
            String errorMsg) {
        if (waitForRecyclerItemCountEquals(recyclerId, expected, timeoutMs)) {
            myLogD("Recycler(" + recyclerId + ") itemCount == " + expected);
            return;
        }
        int seenInRecycler = getRecyclerContentItemCount(recyclerId);

        // Build a small lifecycle snapshot for context
        final String[] snapshot = new String[1];
        getInstrumentation().runOnMainSync(() -> {
            StringBuilder sb = new StringBuilder();
            for (Stage s : Stage.values()) {
                Collection<Activity> acts = ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(s);
                if (acts != null && !acts.isEmpty()) {
                    sb.append(s).append(": ");
                    for (Activity a : acts)
                        sb.append(a.getClass().getSimpleName()).append(' ');
                    sb.append(" | ");
                }
            }
            snapshot[0] = sb.toString();
        });

        // throw new AssertionError(errorMsg
        myLogI(""
                + "\nexpected : " + expected
                + "\nseen in RecyclerView : " + seenInRecycler
                + "\nLifecycle -> " + snapshot[0]);
    }

    public static String getText(Matcher<View> matcher) {
        final String[] out = { null };
        onView(matcher).perform(new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return ViewMatchers.isAssignableFrom(TextView.class);
            }

            @Override
            public String getDescription() {
                return "get text from a TextView";
            }

            @Override
            public void perform(UiController uiController, View view) {
                TextView tv = (TextView) view;
                out[0] = tv.getText().toString();
            }
        });
        return out[0];
    }

}
