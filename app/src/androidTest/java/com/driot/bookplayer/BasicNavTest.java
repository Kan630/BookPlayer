package com.driot.bookplayer;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.action.ViewActions.swipeDown;
import static androidx.test.espresso.action.ViewActions.swipeUp;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import androidx.test.espresso.contrib.RecyclerViewActions;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.allOf;

import android.content.Context;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.driot.bookplayer.activities.GetActivity;
import com.driot.bookplayer.activities.MainActivity;
import com.driot.bookplayer.activities.SettingsActivity;
import com.driot.bookplayer.test.util.MenuHelpers;
import com.driot.bookplayer.test.util.TestNavUtils;
import com.driot.bookplayer.test.util.WaitForView;
import com.driot.bookplayer.utils.KanLogger;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public abstract class BasicNavTest {

    protected abstract int desiredOrientation();

    // Launches MainActivity before each test
    @Rule
    public ActivityScenarioRule<MainActivity> activityRule =
            new ActivityScenarioRule<>(MainActivity.class);

    @Before
    public void setUp() {
        myLog("ooooooooooooooooooooooooooooooooooooooooo");
        myLog("----------------- setUp -----------------");
        myLog("ooooooooooooooooooooooooooooooooooooooooo");
        activityRule.getScenario().onActivity(a ->
                a.setRequestedOrientation(desiredOrientation()));
        KanLogger.init(ApplicationProvider.getApplicationContext());
    }

    @Test
    public void testNavigationFlow() {
        myLog("testNavigationFlow");
        Context context = ApplicationProvider.getApplicationContext();

        TestNavUtils.logCurrentActivity();

        // If we landed on GetActivity (empty state), press back to reach MainActivity
        if (TestNavUtils.getCurrentResumedActivity() instanceof GetActivity) {
            myLogW("On GetActivity, pressing back to reach MainActivity…");
            boolean ok = TestNavUtils.pressBackTo(MainActivity.class, /*maxPresses*/3, /*perStepWaitMs*/1000);
            if (!ok) throw new AssertionError("Could not navigate back to MainActivity");
            TestNavUtils.logCurrentActivity();
        }

///  MAIN
        myLog("on Main");
        // TODO change your custom menu top stock menu, so you can use this handy method
        // TODO => openActionBarOverflowOrOptionsMenu(InstrumentationRegistry.getInstrumentation().getTargetContext());onView(withText(R.string.menu_open)).perform(click());
// 1) Make sure toolbar is there
        onView(withId(R.id.toolbar)).check(matches(isDisplayed()));
        myLog("toolbar reachable");

        MenuHelpers.tapMenu(R.string.menu_manual);
        onView(withId(R.id.tvHelpText)).check(matches(isDisplayed()));
        TestNavUtils.logCurrentActivity();
        onView(withId(android.R.id.content)).perform(swipeUp());
        onView(withId(android.R.id.content)).perform(swipeDown());
        TestNavUtils.assertPressBackTo(MainActivity.class);

        MenuHelpers.tapMenu(R.string.menu_settings);
        TestNavUtils.logCurrentActivity();
        onView(withId(R.id.scrollView)).perform(TestNavUtils.scrollScrollViewToBottom());
        onView(withId(R.id.btnPodcastSettings)).perform(scrollTo(), click());
        TestNavUtils.logCurrentActivity();
        TestNavUtils.assertPressBackTo(SettingsActivity.class);
        onView(withId(R.id.scrollView)).perform(TestNavUtils.scrollScrollViewToBottom());
        onView(withId(R.id.btn_show_advanced)).perform(click());
        onView(withId(R.id.scrollView)).perform(TestNavUtils.scrollScrollViewToBottom());
        onView(withId(R.id.scrollView)).perform(TestNavUtils.scrollScrollViewToTop());
        TestNavUtils.assertPressBackTo(MainActivity.class);

        //menu_seelog

        MenuHelpers.tapMenu(R.string.menu_stats);
        TestNavUtils.logCurrentActivity();
        onView(withId(android.R.id.content)).perform(swipeUp());
        onView(withId(android.R.id.content)).perform(swipeDown());
        TestNavUtils.assertPressBackTo(MainActivity.class);

        //menu_sendmail

        MenuHelpers.tapMenu(R.string.menu_cacheFiles);
        TestNavUtils.logCurrentActivity();
        onView(withId(android.R.id.content)).perform(swipeUp());
        onView(withId(android.R.id.content)).perform(swipeDown());
        TestNavUtils.assertPressBackTo(MainActivity.class);

        //menu_website

        MenuHelpers.tapMenu(R.string.menu_open);
        TestNavUtils.logCurrentActivity();
        myLog("in GET");

        onView(withId(R.id.bOpenOther)).perform(click());   //perform(scrollTo());
        TestNavUtils.logCurrentActivity();
        myLog("in GET OTHER");

        for (int i = 0; i < 3; i++) {
            onView(withId(R.id.viewSecretEntry)).perform(click());
        }
        TestNavUtils.logCurrentActivity();
        myLog("in SECRET DEV");

        onView(withId(R.id.bAutoTest_b1)).perform(click());
        TestNavUtils.sleep(10000);
        TestNavUtils.logCurrentActivity();
        myLog("in LOAD BOOK");

        onView(withId(android.R.id.content)).perform(swipeUp());
        onView(withId(R.id.btnConfirm)).perform(click());
        TestNavUtils.logCurrentActivity();
        myLog("in ADD RESOURCE");
        //onView(withId(android.R.id.content)).perform(WaitForView.waitFor(withId(R.id.toolbar), 10000));
        TestNavUtils.sleep(10000);
        TestNavUtils.logCurrentActivity();


        onView(withId(R.id.recyclerview_folders)).perform(RecyclerViewActions.actionOnItemAtPosition(0, click()));
        TestNavUtils.sleep(5000);
        TestNavUtils.logCurrentActivity();

        onView(withId(R.id.ibPlayPause)).perform(click());
        TestNavUtils.sleep(1000);




/*
        onView(withId(R.id.bOpenFile)).perform(click());   //perform(scrollTo());
        TestNavUtils.logCurrentActivity();

 */


        // Example: scroll to a view (useful inside ScrollView or RecyclerView)
        //onView(withId(R.id.bOpenOther))perform(scrollTo());

        // Example: type text into an EditText
        //onView(withId(R.id.editText_username)).perform(typeText("TonyMontana"));

        // Example: click a button with text
        //onView(withText("Continue")).perform(click());
    }


    // ----------------------- LOG -----------------------
    private static final String TAG = "BasicNavTest";
    // ----------------------- LOG -----------------------
    private static void myLog(String str) { KanLogger.myLog(TAG, str); }
    private static void myLogD(String str) { Log.d(TAG, str); }
    private static void myLogI(String str) { KanLogger.myLogI(TAG, str); }
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }


}