package com.driot.bookplayer.test;

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
import android.content.pm.ActivityInfo;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.AddResourceActivity;
import com.driot.bookplayer.activities.GetActivity;
import com.driot.bookplayer.activities.GetOtherActivity;
import com.driot.bookplayer.imports.ImportBookSingleActivity;
import com.driot.bookplayer.activities.MainActivity;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.imports.ImportHelper;
import com.driot.bookplayer.testutil.LogSupport;
import com.driot.bookplayer.testutil.LoggingWatcher;
import com.driot.bookplayer.testutil.MenuHelpers;
import com.driot.bookplayer.testutil.TestNavUtils;
import com.driot.bookplayer.utils.log.KanLogger;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public abstract class BasicNavTest implements LogSupport {

    private Context appContext;
    protected abstract int desiredOrientation();

    // Launches MainActivity before each test
    @Rule
    public ActivityScenarioRule<MainActivity> activityRule =
            new ActivityScenarioRule<>(MainActivity.class);

    @Rule public LoggingWatcher logs = new LoggingWatcher();

    @Before
    public void setUp() {
        myLog("ooooooooooooooooooooooooooooooooooooooooo");
        myLog("----------------- setUp -----------------");
        myLog("ooooooooooooooooooooooooooooooooooooooooo");
        appContext = ApplicationProvider.getApplicationContext();

        KanLogger.init(appContext);Option.setTechLog(true);

        if (desiredOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
            myLog("+++++++++++++ Orientation = PORTRAIT -----------------------");
        } else {
            myLog("+++++++++++++ Orientation = LANDSCAPE -----------------------");
        }
    }

    @Test
    public void BasicNavigationTest_01() {
        myLog("BasicNavTest : BasicNavigationTest_01");
        Context context = ApplicationProvider.getApplicationContext();

        TestNavUtils.logCurrentActivity();

            //TODO remove
        ImportHelper.cancelCurrentImport(appContext);

        // If we landed on GetActivity (empty state), press back to reach MainActivity
        if (TestNavUtils.getCurrentResumedActivity() instanceof GetActivity) {
            myLogW("On GetActivity, pressing back to reach MainActivity…");
            boolean ok = TestNavUtils.pressBackTo(MainActivity.class, /*maxPresses*/3, /*perStepWaitMs*/1000);
            if (!ok) throw new AssertionError("Could not navigate back to MainActivity");
            TestNavUtils.logCurrentActivity();
        }

///  MAIN
        myLogD("on Main");
        // TODO change your custom menu top stock menu, so you can use this handy method
        // TODO => openActionBarOverflowOrOptionsMenu(InstrumentationRegistry.getInstrumentation().getTargetContext());onView(withText(R.string.menu_open)).perform(click());
// 1) Make sure toolbar is there
        onView(ViewMatchers.withId(com.driot.bookplayer.R.id.toolbar)).check(matches(isDisplayed()));
        myLogD("toolbar reachable");

        //menu_manual

        MenuHelpers.tapMenu(com.driot.bookplayer.R.string.manual);
        onView(ViewMatchers.withId(com.driot.bookplayer.R.id.tvHelpText)).check(matches(isDisplayed()));
        TestNavUtils.logCurrentActivity();
        onView(withId(android.R.id.content)).perform(swipeUp());
        onView(withId(android.R.id.content)).perform(swipeDown());
        TestNavUtils.assertPressBackTo(MainActivity.class);

        //menu_settings

        MenuHelpers.tapMenu(com.driot.bookplayer.R.string.settings);
        TestNavUtils.logCurrentActivity();
        onView(ViewMatchers.withId(R.id.section_play_behaviour)).perform(scrollTo(), click());
        onView(ViewMatchers.withId(R.id.section_play_behaviour)).perform(scrollTo(), click());
        onView(ViewMatchers.withId(R.id.section_design)).perform(scrollTo(), click());
        onView(ViewMatchers.withId(R.id.section_design)).perform(scrollTo(), click());

/*
        onView(withId(com.driot.bookplayer.R.id.scrollView)).perform(TestNavUtils.scrollScrollViewToBottom());
        TestNavUtils.logCurrentActivity();
        TestNavUtils.assertPressBackTo(SettingsActivity.class);
        onView(withId(com.driot.bookplayer.R.id.scrollView)).perform(TestNavUtils.scrollScrollViewToBottom());
        onView(ViewMatchers.withId(com.driot.bookplayer.R.id.btn_show_advanced)).perform(click());
        onView(withId(com.driot.bookplayer.R.id.scrollView)).perform(TestNavUtils.scrollScrollViewToBottom());
        onView(withId(com.driot.bookplayer.R.id.scrollView)).perform(TestNavUtils.scrollScrollViewToTop());
        TestNavUtils.assertPressBackTo(MainActivity.class);

        //menu_stats

        MenuHelpers.tapMenu(com.driot.bookplayer.R.string.menu_stats);
        TestNavUtils.logCurrentActivity();
        TestNavUtils.sleep(2_000); //let time to populate
        onView(withId(android.R.id.content)).perform(swipeUp());
        onView(withId(android.R.id.content)).perform(swipeDown());
        TestNavUtils.assertPressBackTo(MainActivity.class);

        //menu_clean

        MenuHelpers.tapMenu(com.driot.bookplayer.R.string.menu_cacheFiles);
        TestNavUtils.logCurrentActivity();
        onView(withId(android.R.id.content)).perform(swipeUp());
        onView(withId(android.R.id.content)).perform(swipeDown());
        TestNavUtils.assertPressBackTo(MainActivity.class);
*/
        //menu_open

        MenuHelpers.tapMenu(com.driot.bookplayer.R.string.open);
        TestNavUtils.logCurrentActivity();
        myLog("in GET");

        onView(ViewMatchers.withId(com.driot.bookplayer.R.id.bOpenOther)).perform(click());   //perform(scrollTo());
        TestNavUtils.logCurrentActivity();
        TestNavUtils.assertWaitForActivity(GetOtherActivity.class, 1_000, "not in get others");
        myLog("in GET OTHER");

        for (int i = 0; i < 3; i++) {
            onView(ViewMatchers.withId(com.driot.bookplayer.R.id.viewSecretEntry)).perform(click());
        }
        myLog("in SECRET DEV");

        onView(ViewMatchers.withId(com.driot.bookplayer.R.id.bAutoTest_b1)).perform(click());
        TestNavUtils.sleep(1_000);
        TestNavUtils.logCurrentActivity();
        TestNavUtils.assertWaitForActivity(ImportBookSingleActivity.class, 1_000, "not in load book");
        myLog("in LOAD BOOK");

        onView(withId(android.R.id.content)).perform(swipeUp());
        TestNavUtils.logCurrentActivity();
        onView(ViewMatchers.withId(com.driot.bookplayer.R.id.btnConfirm)).perform(click());
        TestNavUtils.logCurrentActivity();
        TestNavUtils.assertWaitForAnyActivity(2_000, AddResourceActivity.class, MainActivity.class);
        myLog("in ADD RESOURCE");
        //onView(withId(android.R.id.content)).perform(WaitForView.waitFor(withId(R.id.toolbar), 10000));
        TestNavUtils.sleep(10_000);
        TestNavUtils.logCurrentActivity();
        TestNavUtils.maybePressBackTo(MainActivity.class,3, 1_000);

        // play audio

        TestNavUtils.assertWaitForActivity(MainActivity.class, 1_000, "not in main");
        onView(ViewMatchers.withId(com.driot.bookplayer.R.id.recyclerview_folders)).perform(RecyclerViewActions.actionOnItemAtPosition(0, click()));
        TestNavUtils.sleep(5_000);
        TestNavUtils.logCurrentActivity();

        onView(ViewMatchers.withId(R.id.ibPlayPause)).perform(click());
        TestNavUtils.sleep(1_000);




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

}