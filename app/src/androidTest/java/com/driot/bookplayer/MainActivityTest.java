package com.driot.bookplayer;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.intent.Intents;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.espresso.action.ViewActions;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.intent.Intents.intended;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;

import com.driot.bookplayer.activities.GetResourceActivity;
import com.driot.bookplayer.activities.MainActivity;

@RunWith(AndroidJUnit4.class)
public class MainActivityTest {

    @Rule
    public ActivityScenarioRule<MainActivity> activityRule = new ActivityScenarioRule<>(MainActivity.class);

    @Test
    public void useAppContext() {
        // Context of the app under test.
        Context appContext = ApplicationProvider.getApplicationContext();
        assertEquals("com.driot.bookplayer", appContext.getPackageName());
    }

    @Test
    public void testButtonClick() {
        // Initialize Espresso Intents
        Intents.init();

        try {
            // Click on the FAB button
            onView(withId(R.id.FAB_Add)).perform(click());

            // Verify that the intent to launch GetResourceActivity was fired
            intended(hasComponent(GetResourceActivity.class.getName()));
        } finally {
            // Release Espresso Intents
            Intents.release();
        }
    }
}

