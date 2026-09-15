package com.driot.bookplayer.test;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;

import android.Manifest;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.Configuration;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.SettingsActivity;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.testutil.LogSupport;
import com.driot.bookplayer.testutil.LoggingWatcher;
import com.driot.bookplayer.testutil.TestNavUtils;
import com.driot.bookplayer.utils.log.KanLogger;
import com.driot.bookplayer.views.SettingsSectionView;

import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;

/**
 * DeepSettingsTest: Stress test for SettingsActivity.
 * Pass 1: Open/close every category's detail screen, scroll while open.
 * Pass 2: Interact with every CheckBox and EditText with random values.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class DeepSettingsTest implements LogSupport {

    private final int WAIT_DELAY_AFTER_CHECKBOX_CLICK_MS = 50;
    private final int WAIT_DELAY_DIALOG_MS = 100;
    private final int WAIT_DELAY_SECTION_INTERACTION_START = 500;
    private final int WAIT_DELAY_SECTION_INTERACTION_END = 500;
    private final int WAIT_DELAY_SECTION_AFTER_SCROLL = 500;

    private Context appContext;
    private final Random random = new Random();

    @Rule
    public LoggingWatcher logs = new LoggingWatcher();

    @Rule
    public ActivityScenarioRule<SettingsActivity> activityRule = new ActivityScenarioRule<>(SettingsActivity.class);

    private static final List<Integer> SECTION_IDS = Arrays.asList(
            R.id.section_language,
            R.id.section_play_behaviour,
            R.id.section_design,
            R.id.section_import,
            R.id.section_massive_import,
            R.id.section_librivox,
            R.id.section_tts,
            R.id.section_radio,
            R.id.section_podcast,
            R.id.section_automotive,
            R.id.section_network,
            R.id.section_utilities);

    @Before
    public void setUp() {
        myLog("ooooooooooooooooooooooooooooooooooooooooo");
        myLog("----------------- setUp -----------------");
        myLog("ooooooooooooooooooooooooooooooooooooooooo");

        appContext = ApplicationProvider.getApplicationContext();
        KanLogger.init(appContext);
        Option.setTechLog(true);

        // Pass 2 randomly toggles every checkbox, including ones that trigger a runtime
        // permission request (e.g. the Play Behaviour visualizer needs RECORD_AUDIO). That pops
        // a system permission dialog belonging to a different package, which leaves this app
        // with no RESUMED activity and fails every subsequent Espresso call with
        // NoActivityResumedException - dismissAnyDialog() below only knows how to dismiss this
        // app's own AlertDialogs, not the OS permission UI. Grant every dangerous permission the
        // app can request up front so the dialog never appears. Wrapped per-permission since a
        // permission gated to a higher/lower minSdkVersion than the current device throws here.
        grantAllDangerousPermissionsIfPossible();

        Configuration config = new Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .setExecutor(Executors.newSingleThreadExecutor())
                .setTaskExecutor(new SynchronousExecutor())
                .build();
        WorkManagerTestInitHelper.initializeTestWorkManager(appContext, config);

        TestNavUtils.logCurrentActivity();
        TestNavUtils.assertWaitForActivity(SettingsActivity.class, 5_000, "SettingsActivity not loaded");
    }

    @Test
    public void deepSettingsTest() throws Exception {
        myLogI("Starting DeepSettingsTest");

        // PASS 1: Stability check (Expand/Collapse/Scroll)
        myLogI("--- Starting PASS 1: Section Stability ---");
        for (int sectionId : SECTION_IDS) {
            testSectionStability(sectionId);
        }

        // PASS 2: Stress check (CheckBoxes, EditTexts)
        myLogI("--- Starting PASS 2: Control Stress Test ---");
        for (int sectionId : SECTION_IDS) {
            testSectionInteractions(sectionId);
        }

        myLogI("DeepSettingsTest completed successfully");
    }

    private void testSectionStability(int sectionId) {
        String sectionName = appContext.getResources().getResourceEntryName(sectionId);
        myLogD("Testing stability for section: " + sectionName);

        // Scroll to the row in the category list and open its detail screen
        onView(withId(sectionId)).perform(scrollTo(), clickHeader());
        verifyDetailOpen(true);

        // Scroll up/down within the open detail screen (its own ScrollView, id/scrollView -
        // the category list's ScrollView of the same id is GONE underneath it, so isDisplayed()
        // disambiguates)
        myLogD("Scrolling while detail is open...");
        onView(allOf(withId(R.id.scrollView), isDisplayed()))
                .perform(TestNavUtils.scrollScrollViewToBottom());
        TestNavUtils.sleep(WAIT_DELAY_SECTION_AFTER_SCROLL, "WAIT_DELAY_SECTION_AFTER_SCROLL");
        onView(allOf(withId(R.id.scrollView), isDisplayed()))
                .perform(TestNavUtils.scrollScrollViewToTop());
        TestNavUtils.sleep(WAIT_DELAY_SECTION_AFTER_SCROLL, "WAIT_DELAY_SECTION_AFTER_SCROLL");

        // Back to the category list
        Espresso.pressBack();
        verifyDetailOpen(false);
    }

    private void testSectionInteractions(int sectionId) {
        String sectionName = appContext.getResources().getResourceEntryName(sectionId);
        myLogI("Testing interactions for section: " + sectionName);

        // Open the category's detail screen
        onView(withId(sectionId)).perform(scrollTo(), clickHeader());
        verifyDetailOpen(true);
        TestNavUtils.sleep(WAIT_DELAY_SECTION_INTERACTION_START, "WAIT_DELAY_SECTION_INTERACTION_START"); // Wait for
                                                                                                          // fragment to
                                                                                                          // load and
                                                                                                          // layout

        final java.util.List<View> targetViews = new java.util.ArrayList<>();

        // Its fragment now lives in the detail pane's container, not inside the
        // SettingsSectionView row anymore - walk that subtree instead.
        onView(withId(R.id.detailFragmentContainer)).perform(new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return isDisplayed();
            }

            @Override
            public String getDescription() {
                return "collect interactable views";
            }

            @Override
            public void perform(UiController uiController, View view) {
                collectTargets(view, targetViews);
            }

            private void collectTargets(View view, java.util.List<View> targets) {
                if (view.isShown()) {
                    if (view instanceof CheckBox || view instanceof EditText
                            || view instanceof android.widget.Spinner) {
                        targets.add(view);
                    }
                }
                if (view instanceof android.view.ViewGroup) {
                    android.view.ViewGroup group = (android.view.ViewGroup) view;
                    for (int i = 0; i < group.getChildCount(); i++) {
                        collectTargets(group.getChildAt(i), targets);
                    }
                }
            }
        });

        for (final View target : targetViews) {
            Matcher<View> instanceMatcher = new org.hamcrest.TypeSafeMatcher<View>() {
                @Override
                protected boolean matchesSafely(View item) {
                    return item == target;
                }

                @Override
                public void describeTo(org.hamcrest.Description description) {
                    description.appendText("matches specific view instance");
                }
            };

            if (target instanceof CheckBox) {
                myLogD("Toggling checkbox: " + getResourceName(target.getId()));
                try {
                    onView(instanceMatcher).perform(androidx.test.espresso.action.ViewActions.scrollTo());
                    onView(instanceMatcher).perform(androidx.test.espresso.action.ViewActions.click());
                    TestNavUtils.sleep(WAIT_DELAY_AFTER_CHECKBOX_CLICK_MS, "WAIT_DELAY_AFTER_CHECKBOX_CLICK_MS");
                    dismissAnyDialog();
                    onView(instanceMatcher).perform(androidx.test.espresso.action.ViewActions.click());
                    TestNavUtils.sleep(WAIT_DELAY_AFTER_CHECKBOX_CLICK_MS, "WAIT_DELAY_AFTER_CHECKBOX_CLICK_MS");
                    dismissAnyDialog();
                } catch (Exception e) {
                    myLog("Error toggling checkbox " + getResourceName(target.getId()) + ": " + e.getMessage());
                }
            } else if (target instanceof EditText) {
                String val = randomValues[random.nextInt(randomValues.length)];
                myLogD("Setting text in " + getResourceName(target.getId()) + " to: " + val);
                try {
                    onView(instanceMatcher).perform(androidx.test.espresso.action.ViewActions.scrollTo(),
                            androidx.test.espresso.action.ViewActions.replaceText(val));
                } catch (Exception e) {
                    myLog("Error setting text in " + getResourceName(target.getId()) + ": " + e.getMessage());
                }
            } else if (target instanceof android.widget.Spinner) {
                final android.widget.Spinner spinner = (android.widget.Spinner) target;
                myLogD("Interacting with spinner: " + getResourceName(spinner.getId()));
                try {
                    onView(instanceMatcher).perform(androidx.test.espresso.action.ViewActions.scrollTo());
                    int count = spinner.getCount();
                    if (count > 0) {
                        int index = random.nextInt(count);
                        myLogD("Selecting index " + index + " in spinner " + getResourceName(spinner.getId()));
                        InstrumentationRegistry.getInstrumentation()
                                .runOnMainSync(() -> spinner.setSelection(index));
                    }
                } catch (Exception e) {
                    myLog("Error interacting with spinner " + getResourceName(spinner.getId()) + ": "
                            + e.getMessage());
                }
            }
        }

        // Small wait for potential background saves
        TestNavUtils.sleep(WAIT_DELAY_SECTION_INTERACTION_END, "WAIT_DELAY_SECTION_INTERACTION_END");

        // A checkbox toggled above may have left a confirmation open (e.g. chk_delete_source_file
        // -> MsgBoxActivity) that the per-checkbox dismissAnyDialog() calls didn't catch in time -
        // unlike those, this step isn't wrapped in try/catch, so leaving it open here would fail
        // the whole section with NoMatchingViewException instead of just skipping one control.
        dismissAnyDialog();

        // Back to the category list
        Espresso.pressBack();
        verifyDetailOpen(false);
    }

    private final String[] randomValues = { "", "123", "abc", "VeryLongStressTestString1234567890!@#$%^&*()", "0.5",
            "-1" };

    private String getResourceName(int id) {
        try {
            return appContext.getResources().getResourceEntryName(id);
        } catch (Exception e) {
            return String.valueOf(id);
        }
    }

    private void verifyDetailOpen(boolean open) {
        onView(withId(R.id.detailContainer)).check((view, noViewFoundException) -> {
            if (view == null) {
                throw new AssertionError("detailContainer not found");
            }
            boolean actuallyOpen = view.getVisibility() == View.VISIBLE;
            if (actuallyOpen != open) {
                throw new AssertionError("Detail pane open-state mismatch. Expected: " + open
                        + ", Actual: " + actuallyOpen);
            }
        });
    }

    // Custom matcher to find all children of a certain type that are displayed
    // Note: Espresso's onView() with a matcher that matches multiple views will
    // fail by default.
    // However, for the stress test, we can use a custom ViewAction to iterate over
    // children if needed,
    // or just rely on the fact that some fragments might only have one or two.
    // Better: use a helper to find all views of type in the hierarchy and act on
    // them.

    private ViewAction clickHeader() {
        return new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return isDisplayed();
            }

            @Override
            public String getDescription() {
                return "click the header card of SettingsSectionView";
            }

            @Override
            public void perform(UiController uiController, View view) {
                if (view instanceof SettingsSectionView) {
                    SettingsSectionView ssv = (SettingsSectionView) view;
                    ssv.getHeaderView().performClick();
                    uiController.loopMainThreadUntilIdle();
                }
            }
        };
    }

    // Every dangerous permission the app can request from within Settings (see
    // AndroidManifest.xml). Some are gated to a minSdkVersion above or below what a given test
    // device runs, so each grant is attempted independently and failures are swallowed.
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
                myLogD("Could not grant " + permission + " (likely unsupported on this API level): "
                        + e.getMessage());
            }
        }
    }

    private void dismissAnyDialog() {
        try {
            androidx.test.uiautomator.UiDevice device = androidx.test.uiautomator.UiDevice
                    .getInstance(InstrumentationRegistry.getInstrumentation());
            androidx.test.uiautomator.UiObject btn1 = device
                    .findObject(new androidx.test.uiautomator.UiSelector().resourceId("android:id/button1"));
            androidx.test.uiautomator.UiObject btn2 = device
                    .findObject(new androidx.test.uiautomator.UiSelector().resourceId("android:id/button2"));
            // Not every confirmation in this app is a framework AlertDialog living inside the
            // same Activity - e.g. chk_delete_source_file in the Import section pops MsgBoxActivity,
            // a whole separate Activity (activity_msgbox.xml, buttons btnPositive/btnNegative),
            // which STOPS the SettingsActivity underneath. Its button ids are this app's own, so
            // they need the real package name, and matching by id (not text) keeps this working
            // no matter which random language Pass 2's language-section spinner already picked.
            String pkg = appContext.getPackageName();
            androidx.test.uiautomator.UiObject msgBoxPositive = device.findObject(
                    new androidx.test.uiautomator.UiSelector().resourceId(pkg + ":id/btnPositive"));
            androidx.test.uiautomator.UiObject msgBoxNegative = device.findObject(
                    new androidx.test.uiautomator.UiSelector().resourceId(pkg + ":id/btnNegative"));
            androidx.test.uiautomator.UiObject okBtn = device.findObject(
                    new androidx.test.uiautomator.UiSelector().textMatches("(?i)ok|annuler|cancel|oui|yes"));

            if (btn1.exists()) {
                btn1.click();
                myLog("Dismissed a dialog via button1");
                TestNavUtils.sleep(WAIT_DELAY_DIALOG_MS, "WAIT_DELAY_DIALOG_MS");
            } else if (btn2.exists()) {
                btn2.click();
                myLog("Dismissed a dialog via button2");
                TestNavUtils.sleep(WAIT_DELAY_DIALOG_MS, "WAIT_DELAY_DIALOG_MS");
            } else if (msgBoxPositive.exists()) {
                msgBoxPositive.click();
                myLog("Dismissed MsgBoxActivity via btnPositive");
                TestNavUtils.sleep(WAIT_DELAY_DIALOG_MS, "WAIT_DELAY_DIALOG_MS");
            } else if (msgBoxNegative.exists()) {
                msgBoxNegative.click();
                myLog("Dismissed MsgBoxActivity via btnNegative");
                TestNavUtils.sleep(WAIT_DELAY_DIALOG_MS, "WAIT_DELAY_DIALOG_MS");
            } else if (okBtn.exists()) {
                okBtn.click();
                myLog("Dismissed a dialog via text match");
                TestNavUtils.sleep(WAIT_DELAY_DIALOG_MS, "WAIT_DELAY_DIALOG_MS");
            }
        } catch (Exception e) {
            myLog("Exception in dismissAnyDialog: " + e.getMessage());
        }
    }
}
