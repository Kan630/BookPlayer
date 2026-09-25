package com.driot.bookplayer.test;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withParent;
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

import android.content.Intent;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.MainActivity;
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
 * DeepSettingsTest: Stress test for SettingsHostActivity (settings_nav_graph.xml).
 * Pass 1: Open/close every category's screen, scroll while open.
 * Pass 2: Interact with every CheckBox and EditText with random values.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class DeepSettingsTest implements LogSupport {

    private final int WAIT_DELAY_AFTER_CHECKBOX_CLICK_MS = 50;
    // Long enough for a focus-triggered postDelayed callback (like the one that actually shipped
    // a ClassCastException crash in prod - see InputScreensCrashSurfaceTest's javadoc) to fire
    // before we move on and possibly mask it.
    private final int WAIT_DELAY_AFTER_EDITTEXT_FOCUS_MS = 500;
    private final int WAIT_DELAY_DIALOG_MS = 100;
    private final int WAIT_DELAY_SECTION_INTERACTION_START = 500;
    private final int WAIT_DELAY_SECTION_INTERACTION_END = 500;
    private final int WAIT_DELAY_SECTION_AFTER_SCROLL = 500;

    private Context appContext;
    private final Random random = new Random();

    @Rule
    public LoggingWatcher logs = new LoggingWatcher();

    // MainActivity is now the app's sole Activity - launch it straight onto the Settings tab
    // instead of the old standalone SettingsHostActivity.
    @Rule
    public ActivityScenarioRule<MainActivity> activityRule = new ActivityScenarioRule<>(
            new Intent(ApplicationProvider.getApplicationContext(), MainActivity.class)
                    .putExtra(MainActivity.EXTRA_NAV_TAB_ID, R.id.nav_settings)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

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

    // Categories SettingsCategoryListFragment hides (View.GONE) on the pure flavor
    private static final List<Integer> FULL_ONLY_SECTION_IDS = Arrays.asList(
            R.id.section_librivox,
            R.id.section_radio,
            R.id.section_podcast,
            R.id.section_network);

    /** SECTION_IDS minus the ones that do not exist in the flavor under test. */
    private static List<Integer> sectionsForCurrentFlavor() {
        List<Integer> ids = new java.util.ArrayList<>(SECTION_IDS);
        if (com.driot.bookplayer.utils.Tonio.isPure(ApplicationProvider.getApplicationContext())) {
            ids.removeAll(FULL_ONLY_SECTION_IDS);
        }
        return ids;
    }

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
        TestNavUtils.assertWaitForActivity(MainActivity.class, 5_000, "MainActivity not loaded");
        TestNavUtils.waitForViewVisible(R.id.settings_layout_root, 5_000, "Settings category list did not appear");
    }

    @Test
    public void deepSettingsTest() throws Exception {
        myLogI("Starting DeepSettingsTest");

        // PASS 1: Stability check (Expand/Collapse/Scroll)
        myLogI("--- Starting PASS 1: Section Stability ---");
        for (int sectionId : sectionsForCurrentFlavor()) {
            testSectionStability(sectionId);
        }

        // PASS 2: Stress check (CheckBoxes, EditTexts)
        myLogI("--- Starting PASS 2: Control Stress Test ---");
        for (int sectionId : sectionsForCurrentFlavor()) {
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
        // the category list's ScrollView of the same id was replaced (not just hidden) by the
        // NavController transaction, so isDisplayed() disambiguates)
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

        // Its fragment now lives inside the shared NavHostFragment container (one per bottom-nav
        // tab, MainActivity attaches/detaches into R.id.nav_host_container - see MainActivity.
        // attachTab()), not inside the SettingsSectionView row anymore - walk that subtree instead.
        // NavHostFragment gives its own view the id of the container it sits in, so
        // nav_host_container matches twice: take the inner one (the current tab's host).
        onView(allOf(withId(R.id.nav_host_container), withParent(withId(R.id.nav_host_container))))
                .perform(new ViewAction() {
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
                    // click() (not replaceText() alone) so the field is actually given real
                    // focus first - replaceText() sets the text programmatically and does not
                    // reliably fire OnFocusChangeListener, which is exactly the gap that let a
                    // focus-driven ClassCastException reach production (see
                    // InputScreensCrashSurfaceTest's javadoc).
                    onView(instanceMatcher).perform(androidx.test.espresso.action.ViewActions.scrollTo(),
                            androidx.test.espresso.action.ViewActions.click());
                    onView(instanceMatcher).perform(androidx.test.espresso.action.ViewActions.clearText(),
                            androidx.test.espresso.action.ViewActions.typeText(val),
                            androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
                    onView(instanceMatcher).perform(clearFocusAction());
                    TestNavUtils.sleep(WAIT_DELAY_AFTER_EDITTEXT_FOCUS_MS, "settle after EditText focus/unfocus");
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

    /**
     * With the NavController-based graph, "detail open" means the category list's own root
     * view (settings_layout_root, from fragment_settings_category_list.xml) has been replaced
     * by the destination fragment - it no longer just gets hidden behind a separate detail
     * pane, so its presence/absence in the view hierarchy IS the open/closed signal.
     */
    private void verifyDetailOpen(boolean open) {
        if (open) {
            onView(withId(R.id.settings_layout_root)).check(doesNotExist());
        } else {
            onView(withId(R.id.settings_layout_root)).check(matches(isDisplayed()));
        }
    }

    // Custom matcher to find all children of a certain type that are displayed
    // Note: Espresso's onView() with a matcher that matches multiple views will
    // fail by default.
    // However, for the stress test, we can use a custom ViewAction to iterate over
    // children if needed,
    // or just rely on the fact that some fragments might only have one or two.
    // Better: use a helper to find all views of type in the hierarchy and act on
    // them.

    /**
     * Defocuses whatever EditText currently has focus by calling View.clearFocus() directly on
     * it, which fires OnFocusChangeListener(false) the same way a real focus loss would. Settings
     * sections don't share a common "tap outside" target the way the Add-book/Radio/Podcast
     * screens do (see InputScreensCrashSurfaceTest's focusGuard usage), so this targets the field
     * itself instead of hunting for a per-section dismiss target.
     */
    private ViewAction clearFocusAction() {
        return new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return isDisplayed();
            }

            @Override
            public String getDescription() {
                return "clearFocus() to defocus the field";
            }

            @Override
            public void perform(UiController uiController, View view) {
                view.clearFocus();
                uiController.loopMainThreadUntilIdle();
            }
        };
    }

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
            // which STOPS the SettingsHostActivity underneath. Its button ids are this app's own, so
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
