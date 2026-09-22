package com.driot.bookplayer.test;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import androidx.work.Configuration;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.MainActivity;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.testutil.LogSupport;
import com.driot.bookplayer.testutil.LoggingWatcher;
import com.driot.bookplayer.testutil.TestNavUtils;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.KanLogger;

import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * InputScreensCrashSurfaceTest: generic crash-surface sweep for the app's "content source" input
 * screens (Add book hub + Other/DirectLink/LibriVox/Ebook, Radio, Podcast) - the screens that got
 * moved from Activity- to Fragment-hosting in the September nav rewrite ({@code
 * radio_nav_architecture_phase1}) and where a Fragment-hosted view calling
 * {@code (Activity) getContext()} first started throwing ClassCastException in production
 * (Crashlytics issue 253a2d96..., EditText1lineWithSearch/EditText2linesWithPaste, v319-320).
 * <p>
 * Unlike DeepSettingsTest's EditText handling (replaceText(), which sets text programmatically
 * and does not reliably fire OnFocusChangeListener), every EditText here is given real focus via
 * click(), typed into, then defocused via the screen's own "tap outside" mechanism
 * (focusGuard/mainScroll, both declared focusable in the layouts) - this is what actually
 * reproduces a focus-driven crash like the one above. Settings is intentionally NOT touched by
 * this test; DeepSettingsTest already covers it, with the above caveat noted as a follow-up.
 * <p>
 * Navigation is done by tapping the real UI (bottom nav, hub buttons) rather than jumping the
 * NavController directly to a destination, so this also exercises the actual entry path a user
 * takes to reach each screen.
 * <p>
 * Scope deliberately excludes: MainLibraryFragment and its overflow-reachable screens (already
 * touched by BasicNavTest; blindly clicking library items risks the existing book/progress data
 * the testing-data policy requires tests to preserve), and every Results/Detail/Favorites screen
 * across the 4 content-source nav graphs (they need live network data or existing
 * favorited/downloaded items to reach - higher setup cost and flake risk; a reasonable follow-up,
 * not folded into this pass).
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class InputScreensCrashSurfaceTest implements LogSupport {

    private static final int WAIT_AFTER_FOCUS_CHANGE_MS = 500; // long enough for a postDelayed(300) crash to surface
    private static final int WAIT_AFTER_BUTTON_CLICK_MS = 500;
    private static final int WAIT_AFTER_TOGGLE_MS = 150;
    private static final int MAX_BACK_PRESSES_TO_RECOVER = 5;

    // Deliberately nasty values: empty, whitespace, very long, unicode/emoji/RTL, markup-looking.
    // Not String.repeat(): that needs API 30+, this app's minSdk is 26.
    private static final String[] STRESS_VALUES = {
            "",
            "   ",
            buildLongString(300),
            "Ω ünïcödé 你好 مرحبا 🎧🔥",
            "\"'<script>alert(1)</script>&amp;",
            "https://example.com/../../etc/passwd?x=1&y=2#frag",
    };

    private static String buildLongString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append('a');
        return sb.toString();
    }

    private Context appContext;

    @Rule
    public LoggingWatcher logs = new LoggingWatcher();

    @Rule
    public ActivityScenarioRule<MainActivity> activityRule = new ActivityScenarioRule<>(MainActivity.class);

    @Before
    public void setUp() {
        myLog("----------------- setUp: InputScreensCrashSurfaceTest -----------------");
        appContext = ApplicationProvider.getApplicationContext();
        KanLogger.init(appContext);
        Option.setTechLog(true);

        // A button on these screens can trigger a runtime permission prompt (a system dialog
        // outside this app) which would otherwise strand Espresso with no RESUMED activity of
        // ours. Grant everything dangerous up front, same rationale as DeepSettingsTest.
        grantAllDangerousPermissionsIfPossible();

        Configuration config = new Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .setExecutor(Executors.newSingleThreadExecutor())
                .setTaskExecutor(new SynchronousExecutor())
                .build();
        WorkManagerTestInitHelper.initializeTestWorkManager(appContext, config);

        TestNavUtils.assertWaitForActivity(MainActivity.class, 5_000, "MainActivity did not come to foreground");
    }

    @Test
    public void addBookScreens_editTextsCheckboxesButtons_doNotCrash() {
        clickBottomNavTab(R.id.nav_add);

        if (Tonio.isPure(appContext)) {
            // pure skips the hub and lands straight on the local-file screen - no LibriVox/Ebook/
            // DirectLink screens exist in this flavor at all.
            stressScreen("GetOther (pure)");
            return;
        }

        TestNavUtils.waitForViewVisible(R.id.root, 5_000, "Add-book hub did not appear");
        navigateHubButtonAndStress(R.id.bOpenOther, "GetOther");
        navigateHubButtonAndStress(R.id.bDirectLink, "GetDirectLink");
        navigateHubButtonAndStress(R.id.bOpenAudiobooks, "GetLibrivox");
        navigateHubButtonAndStress(R.id.bOpenEbooks, "GetEbook");
    }

    @Test
    public void radioScreen_editTextsCheckboxesButtons_doNotCrash() {
        if (Tonio.isPure(appContext)) {
            myLog("Radio tab does not exist on pure flavor - skipping");
            return;
        }
        clickBottomNavTab(R.id.nav_radio);
        stressScreen("GetRadio");
    }

    @Test
    public void podcastScreen_editTextsCheckboxesButtons_doNotCrash() {
        if (Tonio.isPure(appContext)) {
            myLog("Podcast tab does not exist on pure flavor - skipping");
            return;
        }
        clickBottomNavTab(R.id.nav_podcast);
        stressScreen("GetPodcast");
    }

    // ---------------------------------------------------------------------------------------
    // Navigation
    // ---------------------------------------------------------------------------------------

    private void clickBottomNavTab(int tabId) {
        myLogI("Clicking bottom nav tab: " + getResourceName(tabId));
        onView(withId(tabId)).perform(click());
        TestNavUtils.sleep(500, "settle after tab switch");
    }

    /** Clicks a hub button to enter a sub-screen, stresses it, then returns to the hub. */
    private void navigateHubButtonAndStress(int hubButtonId, String screenName) {
        myLogI("=== Entering " + screenName + " via " + getResourceName(hubButtonId) + " ===");
        onView(withId(hubButtonId)).perform(scrollTo(), click());
        stressScreen(screenName);

        Espresso.pressBack();
        TestNavUtils.waitForViewVisible(R.id.root, 5_000, "did not return to Add-book hub after " + screenName);
    }

    // ---------------------------------------------------------------------------------------
    // Generic per-screen widget sweep
    // ---------------------------------------------------------------------------------------

    /**
     * Collects every visible, enabled EditText/CheckBox/Spinner/Button(+ImageButton) currently
     * under R.id.nav_host_container and interacts with each, asserting the app is still alive
     * (and, for buttons, that we can get back to this same screen) after every single one.
     */
    private void stressScreen(String screenName) {
        TestNavUtils.waitForViewVisible(R.id.mainScroll, 8_000, screenName + " root (mainScroll) did not appear");
        TestNavUtils.sleep(300, "let " + screenName + " finish laying out");

        Widgets found = collectWidgets();
        myLogI(screenName + ": found " + found.editTexts.size() + " EditTexts, " + found.checkBoxes.size()
                + " CheckBoxes, " + found.spinners.size() + " Spinners, " + found.clickables.size()
                + " Buttons/ImageButtons");

        int i = 0;
        for (int id : found.editTexts) {
            interactEditText(id, STRESS_VALUES[i++ % STRESS_VALUES.length], screenName);
        }
        for (int id : found.checkBoxes) {
            interactCheckBox(id, screenName);
        }
        for (int id : found.spinners) {
            interactSpinner(id, screenName);
        }
        for (int id : found.clickables) {
            interactButton(id, screenName);
        }
    }

    private void interactEditText(int id, String value, String screenName) {
        String name = getResourceName(id);
        myLogD(screenName + ": focusing+typing EditText " + name + " with [" + truncate(value) + "]");
        try {
            onView(withId(id)).perform(scrollTo(), click()); // real focus, fires OnFocusChangeListener(true)
            onView(withId(id)).perform(typeText(value), closeSoftKeyboard());
        } catch (Exception e) {
            myLogW("EditText " + name + " on " + screenName + " threw during perform(): " + e);
        }
        unfocus();
        // Give any focus-triggered postDelayed callback (like the one that actually crashed in
        // prod) time to fire before we move on and possibly mask it.
        TestNavUtils.sleep(WAIT_AFTER_FOCUS_CHANGE_MS, "settle after EditText focus/unfocus");
        assertScreenAlive(screenName, name);
    }

    private void interactCheckBox(int id, String screenName) {
        String name = getResourceName(id);
        myLogD(screenName + ": toggling CheckBox " + name);
        try {
            onView(withId(id)).perform(scrollTo(), click());
            TestNavUtils.sleep(WAIT_AFTER_TOGGLE_MS, "after checkbox toggle");
            onView(withId(id)).perform(click()); // toggle back - leave state as found
        } catch (Exception e) {
            myLogW("CheckBox " + name + " on " + screenName + " threw during perform(): " + e);
        }
        assertScreenAlive(screenName, name);
    }

    private void interactSpinner(int id, String screenName) {
        String name = getResourceName(id);
        myLogD(screenName + ": selecting in Spinner " + name);
        try {
            onView(withId(id)).perform(scrollTo());
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                try {
                    android.app.Activity activity = TestNavUtils.getCurrentResumedActivity();
                    if (activity == null) return;
                    Spinner spinner = activity.findViewById(id);
                    if (spinner != null && spinner.getCount() > 0) {
                        spinner.setSelection((spinner.getSelectedItemPosition() + 1) % spinner.getCount());
                    }
                } catch (Exception ignored) {
                }
            });
        } catch (Exception e) {
            myLogW("Spinner " + name + " on " + screenName + " threw during perform(): " + e);
        }
        assertScreenAlive(screenName, name);
    }

    private void interactButton(int id, String screenName) {
        String name = getResourceName(id);
        myLogI(screenName + ": clicking " + name);
        try {
            onView(withId(id)).perform(scrollTo(), click());
        } catch (Exception e) {
            myLogW("Button " + name + " on " + screenName + " threw during perform(): " + e);
        }
        TestNavUtils.sleep(WAIT_AFTER_BUTTON_CLICK_MS, "settle after button click");
        // Buttons can launch a system file/folder picker or navigate deeper in-app; recover to
        // this same screen either way before moving on to the next widget.
        recoverToScreen(screenName, name);
    }

    // ---------------------------------------------------------------------------------------
    // Recovery / liveness
    // ---------------------------------------------------------------------------------------

    /** Non-navigating widgets (EditText/CheckBox/Spinner) should never leave the screen. */
    private void assertScreenAlive(String screenName, String causeWidget) {
        try {
            onView(withId(R.id.mainScroll)).check(matches(isDisplayed()));
        } catch (Throwable t) {
            failWithLivenessCheck(screenName, causeWidget);
        }
    }

    /**
     * Buttons may open a system picker (SAF file/folder chooser) or navigate to another in-app
     * destination. Escape either case and return to this screen before the sweep continues.
     */
    private void recoverToScreen(String screenName, String causeWidget) {
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        String pkg = appContext.getPackageName();

        // Step 1: back out of anything that isn't even our app (SAF picker, share sheet, ...).
        for (int i = 0; i < MAX_BACK_PRESSES_TO_RECOVER && !pkg.equals(device.getCurrentPackageName()); i++) {
            device.pressBack();
            SystemClock.sleep(300);
        }

        // Step 2: back out of in-app navigation until this screen's root reappears.
        for (int i = 0; i < MAX_BACK_PRESSES_TO_RECOVER; i++) {
            try {
                onView(withId(R.id.mainScroll)).check(matches(isDisplayed()));
                return;
            } catch (Throwable notYet) {
                Espresso.pressBack();
                SystemClock.sleep(300);
            }
        }

        failWithLivenessCheck(screenName, causeWidget);
    }

    /**
     * The screen didn't come back on its own. Distinguish "the app actually crashed" (real bug -
     * fail loud and specific) from "we simply couldn't navigate back" by checking whether
     * MainActivity is even still alive.
     */
    private void failWithLivenessCheck(String screenName, String causeWidget) {
        TestNavUtils.assertWaitForActivity(MainActivity.class, 3_000,
                "App did not survive interacting with [" + causeWidget + "] on screen [" + screenName + "]");
        throw new AssertionError("Could not return to screen [" + screenName + "] after interacting with ["
                + causeWidget + "] - app is alive but its root view never reappeared after "
                + MAX_BACK_PRESSES_TO_RECOVER + " back presses");
    }

    /** requestFocus() on the screen's own tap-outside target, mirroring how a real user would
     *  dismiss the keyboard/defocus a field - this is what actually fires
     *  OnFocusChangeListener(false) on whatever EditText currently holds focus. */
    private void unfocus() {
        if (!tryRequestFocus(R.id.focusGuard)) {
            tryRequestFocus(R.id.mainScroll);
        }
        try {
            Espresso.closeSoftKeyboard();
        } catch (Exception ignored) {
        }
    }

    private boolean tryRequestFocus(int viewId) {
        try {
            onView(withId(viewId)).perform(new ViewAction() {
                @Override
                public Matcher<View> getConstraints() {
                    return isDisplayed();
                }

                @Override
                public String getDescription() {
                    return "requestFocus() to defocus the currently focused field";
                }

                @Override
                public void perform(UiController uiController, View view) {
                    view.requestFocus();
                    uiController.loopMainThreadUntilIdle();
                }
            });
            return true;
        } catch (NoMatchingViewException e) {
            return false;
        }
    }

    // ---------------------------------------------------------------------------------------
    // Widget discovery
    // ---------------------------------------------------------------------------------------

    private static class Widgets {
        final List<Integer> editTexts = new ArrayList<>();
        final List<Integer> checkBoxes = new ArrayList<>();
        final List<Integer> spinners = new ArrayList<>();
        final List<Integer> clickables = new ArrayList<>(); // Button (incl. MaterialButton) + ImageButton
    }

    private Widgets collectWidgets() {
        final Widgets widgets = new Widgets();
        onView(withId(R.id.nav_host_container)).perform(new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return isDisplayed();
            }

            @Override
            public String getDescription() {
                return "collect interactable widget ids";
            }

            @Override
            public void perform(UiController uiController, View view) {
                collect(view, widgets);
            }

            private void collect(View view, Widgets out) {
                if (view.isShown() && view.isEnabled() && view.getId() != View.NO_ID) {
                    if (view instanceof EditText) {
                        out.editTexts.add(view.getId());
                    } else if (view instanceof CheckBox) {
                        out.checkBoxes.add(view.getId());
                    } else if (view instanceof Spinner) {
                        out.spinners.add(view.getId());
                    } else if (view instanceof Button || view instanceof ImageButton) {
                        out.clickables.add(view.getId());
                    }
                }
                if (view instanceof ViewGroup) {
                    ViewGroup group = (ViewGroup) view;
                    for (int i = 0; i < group.getChildCount(); i++) {
                        collect(group.getChildAt(i), out);
                    }
                }
            }
        });
        return widgets;
    }

    // ---------------------------------------------------------------------------------------
    // Small helpers
    // ---------------------------------------------------------------------------------------

    private String getResourceName(int id) {
        try {
            return appContext.getResources().getResourceEntryName(id);
        } catch (Exception e) {
            return String.valueOf(id);
        }
    }

    private static String truncate(String s) {
        return s.length() > 24 ? s.substring(0, 24) + "..." : s;
    }

    // Every dangerous permission the app can request from these screens (mirrors
    // DeepSettingsTest.DANGEROUS_PERMISSIONS). Each grant is attempted independently since a
    // permission gated to a higher/lower minSdkVersion than the current device throws here.
    private static final String[] DANGEROUS_PERMISSIONS = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.READ_MEDIA_AUDIO,
    };

    private void grantAllDangerousPermissionsIfPossible() {
        for (String permission : DANGEROUS_PERMISSIONS) {
            try {
                InstrumentationRegistry.getInstrumentation().getUiAutomation()
                        .grantRuntimePermission(appContext.getPackageName(), permission);
            } catch (Exception e) {
                myLogD("Could not grant " + permission + ": " + e.getMessage());
            }
        }
    }
}
