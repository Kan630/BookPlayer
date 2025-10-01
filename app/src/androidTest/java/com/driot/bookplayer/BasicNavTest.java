package com.driot.bookplayer;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.allOf;

import android.content.Context;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.GetActivity;
import com.driot.bookplayer.activities.MainActivity;
import com.driot.bookplayer.test.util.TestNavUtils;
import com.driot.bookplayer.utils.KanLogger;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BasicNavTest {

    // Launches MainActivity before each test
    @Rule
    public ActivityScenarioRule<MainActivity> activityRule =
            new ActivityScenarioRule<>(MainActivity.class);

    @Before
    public void setUp() {
        myLog("oooooooooooooooooooooooooooooooooooooooooooooooooooooo");
        myLog("----------------- setUp ----------------- BasicNavTest");
        myLog("oooooooooooooooooooooooooooooooooooooooooooooooooooooo");
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
        myLog("nothing i gue");
/*
        onView(withId(R.id.action_menu_three_dot))
                .check(matches(isDisplayed()))
                .perform(click());
        myLog("step2");

 */

        try {
            onView(withId(R.id.menu_open)).perform(click());
            myLog("menu item pas visible");
        } catch (Exception ignored) {

            onView(allOf(
                    anyOf(withId(R.id.action_menu_three_dot), withContentDescription(R.string.action_menu_three_dot)),
                    isDescendantOfA(withId(R.id.toolbar)),
                    isDisplayed()
            )).perform(click());
            myLog("clicked 3 dots");

            onView(withText(R.string.menu_open))
                    .inRoot(isPlatformPopup())
                    .perform(click());
            myLog("clicked menu item");
}

        myLog("sleep");
        try {Thread.sleep(300);} catch (InterruptedException e) {e.printStackTrace();}

        TestNavUtils.logCurrentActivity();
        myLog("in GET");

        onView(withId(R.id.bOpenOther)).perform(click());   //perform(scrollTo());

        TestNavUtils.logCurrentActivity();
        myLog("in GET OTHER");

        onView(withId(R.id.bOpenFile)).perform(click());   //perform(scrollTo());
        TestNavUtils.logCurrentActivity();


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