package com.driot.bookplayer.test;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.SettingsActivity;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.testutil.LogSupport;
import com.driot.bookplayer.testutil.TestNavUtils;
import com.driot.bookplayer.utils.log.KanLogger;
import com.driot.bookplayer.testutil.LoggingWatcher;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import androidx.work.Configuration;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class SettingsActivitySmokeTest implements LogSupport {

    // ---- Preset rule & setup (as you requested) ----
    @Rule public LoggingWatcher logs = new LoggingWatcher();
    @Rule public ActivityScenarioRule<SettingsActivity> scenarioRule =
            new ActivityScenarioRule<>(SettingsActivity.class);
    @Before
    public void setUp() {
        com.driot.bookplayer.utils.log.LoggerStaticHelper.myLog("ooooooooooooooooooooooooooooooooooooooooo");
        com.driot.bookplayer.utils.log.LoggerStaticHelper.myLog("----------------- setUp -----------------");
        com.driot.bookplayer.utils.log.LoggerStaticHelper.myLog("ooooooooooooooooooooooooooooooooooooooooo");

        KanLogger.init(ApplicationProvider.getApplicationContext());
        Option.setTechLog(true);

        Option.setCopyFile(false);
        Option.setUseSdCard(true);

        Configuration config = new Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .setExecutor(Executors.newSingleThreadExecutor())
                .setTaskExecutor(new SynchronousExecutor())
                .build();
        WorkManagerTestInitHelper.initializeTestWorkManager(
                ApplicationProvider.getApplicationContext(), config);

        TestNavUtils.logCurrentActivity();
    }

    // ---- Helper to “assert at least one control is visible” for a section ----
    private void assertAnyControlVisible(int sectionId, int[] expectedControlIds) {
        int found = 0;
        for (int vid : expectedControlIds) {
            try {
                // Make sure we scrolled near that section before checking its children.
                onView(withId(vid)).check(matches(isDisplayed()));
                found++;
            } catch (Throwable ignored) {
                // keep scanning the rest; we just need at least one
            }
        }
        if (found == 0) {
            throw new AssertionError(
                    "No expected controls were visible for section id="
                            + sectionId + " expected one of "
                            + Arrays.toString(expectedControlIds));
        }
    }

    @Test
    public void expandsEachSection_scrolls_topAndBottom_andSeesControls() {
        // Wait for SettingsActivity to be RESUMED
        TestNavUtils.assertWaitForActivity(SettingsActivity.class, 5_000,
                "SettingsActivity did not come to foreground");

        // Map each section view id -> an array of expected child control ids to probe
        // TODO: Replace placeholder ids (R.id.any_view_in_xxx) with real, stable ids present in each fragment.
        final Map<Integer, int[]> plan = new LinkedHashMap<>();
        plan.put(R.id.section_play_behaviour, new int[] {
                R.id.etTimeBeforeSleep    // <-- replace
                //,R.id.option_open_play_activity      // <-- replace if you have it
        });
        plan.put(R.id.section_design, new int[] {
                R.id.chk_theme_mode_force
        });
        plan.put(R.id.section_import, new int[] {
                R.id.chk_copy_file             // <-- replace
        });
        plan.put(R.id.section_librivox, new int[] {
                R.id.et_librivox_api_nb_results           // <-- replace
        });
        plan.put(R.id.section_podcast, new int[] {
                R.id.et_librivox_api_nb_results            // <-- replace
        });
        plan.put(R.id.section_tts, new int[] {
                R.id.et_tts_chunk_size,               // <-- replace
                R.id.spinnerTtsVoice                // if exposed when expanded
        });
        plan.put(R.id.section_automotive, new int[] {
                R.id.chk_automotive_on         // <-- replace
        });
        plan.put(R.id.section_network, new int[] {
                R.id.spinner_download_user            // <-- replace
        });
        plan.put(R.id.section_utilities, new int[] {
                R.id.chk_tech_log_file          // <-- replace
        });

        // Run the loop: click -> bottom -> top -> check controls -> (optionally click again to collapse)
        for (Map.Entry<Integer, int[]> e : plan.entrySet()) {
            final int sectionId = e.getKey();
            final int[] expectedIds = e.getValue();

            // Bring the section into view then click its header (SettingsSectionView itself is clickable)
            onView(withId(sectionId)).perform(scrollTo(), click());

            // Scroll the whole page to bottom and back to top
            onView(withId(R.id.scrollView)).perform(TestNavUtils.scrollScrollViewToBottom());
            onView(withId(R.id.scrollView)).perform(TestNavUtils.scrollScrollViewToTop());

            // Assert at least one of the expected controls for that section is visible
            assertAnyControlVisible(sectionId, expectedIds);

            // Move on to the next section. If you want to collapse, uncomment the next line:
            // onView(withId(sectionId)).perform(click());
        }
    }

    private void assertAnyControlVisibleByIdsOrStrings(int[] viewIds, int[] stringResIds) {
        int found = 0;

        // Try view ids
        for (int vid : viewIds) {
            try {
                onView(withId(vid)).check(matches(isDisplayed()));
                found++;
            } catch (Throwable ignored) {}
        }

        // Try string ids
        for (int sid : stringResIds) {
            try {
                String text = ApplicationProvider.getApplicationContext().getString(sid);
                onView(withText(text)).check(matches(isDisplayed()));
                found++;
            } catch (Throwable ignored) {}
        }

        if (found == 0) {
            throw new AssertionError("No expected controls/texts were visible for this section.");
        }
    }
}
