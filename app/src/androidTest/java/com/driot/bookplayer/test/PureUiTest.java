package com.driot.bookplayer.test;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.matcher.ViewMatchers.Visibility;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.MainActivity;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.testutil.LogSupport;
import com.driot.bookplayer.testutil.LoggingWatcher;
import com.driot.bookplayer.testutil.TestNavUtils;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.KanLogger;
import com.google.android.material.navigation.NavigationBarView;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * What a pure user actually sees. Read-only with respect to the library; the only setting it
 * touches is "show bottom navigation bar", forced on so the tab tests are deterministic.
 */
@RunWith(AndroidJUnit4.class)
public class PureUiTest implements LogSupport {

    @Rule
    public LoggingWatcher logs = new LoggingWatcher();

    private Context ctx;
    private ActivityScenario<MainActivity> scenario;

    @Before
    public void setUp() {
        ctx = ApplicationProvider.getApplicationContext();
        assumeTrue("pure-flavor test", Tonio.isPure(ctx));
        KanLogger.init(ctx);
        Option.setDisplayAppNavBar(true);
    }

    @After
    public void tearDown() {
        if (scenario != null)
            scenario.close();
    }

    private void launchOnTab(int tabId) {
        Intent i = new Intent(ctx, MainActivity.class)
                .putExtra(MainActivity.EXTRA_NAV_TAB_ID, tabId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        scenario = ActivityScenario.launch(i);
        TestNavUtils.assertWaitForActivity(MainActivity.class, 8_000, "MainActivity not shown");
    }

    private void launchDefault() {
        scenario = ActivityScenario.launch(MainActivity.class);
        TestNavUtils.assertWaitForActivity(MainActivity.class, 8_000, "MainActivity not shown");
    }

    private int selectedNavItem() {
        AtomicInteger id = new AtomicInteger(-1);
        scenario.onActivity(a -> id.set(((NavigationBarView) a.findViewById(R.id.bottomNav)).getSelectedItemId()));
        return id.get();
    }

    @Test
    public void bottomNav_hasOnlyLibraryAddAndSettings() {
        launchDefault();
        AtomicReference<android.view.Menu> menu = new AtomicReference<>();
        scenario.onActivity(a -> menu.set(((NavigationBarView) a.findViewById(R.id.bottomNav)).getMenu()));
        assertNotNull(menu.get());
        assertEquals("pure bottom nav must be Library / Add / Settings only", 3, menu.get().size());
        assertNotNull(menu.get().findItem(R.id.nav_library));
        assertNotNull(menu.get().findItem(R.id.nav_add));
        assertNotNull(menu.get().findItem(R.id.nav_settings));
        assertNull("radio tab leaked into pure", menu.get().findItem(R.id.nav_radio));
        assertNull("podcast tab leaked into pure", menu.get().findItem(R.id.nav_podcast));
    }

    @Test
    public void settings_hidesTheFullOnlyCategories() {
        launchOnTab(R.id.nav_settings);
        TestNavUtils.waitForViewVisible(R.id.settings_layout_root, 8_000, "settings list did not appear");
        for (int hidden : new int[] { R.id.section_radio, R.id.section_podcast, R.id.section_librivox,
                R.id.section_network, R.id.section_storage }) {
            onView(withId(hidden)).check(matches(withEffectiveVisibility(Visibility.GONE)));
        }
    }

    @Test
    public void settings_stillShowsTheCategoriesPureKeeps() {
        launchOnTab(R.id.nav_settings);
        TestNavUtils.waitForViewVisible(R.id.settings_layout_root, 8_000, "settings list did not appear");
        for (int kept : new int[] { R.id.section_language, R.id.section_play_behaviour, R.id.section_design,
                R.id.section_import, R.id.section_tts, R.id.section_automotive, R.id.section_utilities }) {
            onView(withId(kept)).check(matches(not(withEffectiveVisibility(Visibility.GONE))));
        }
    }

    @Test
    public void addTab_skipsTheHubAndShowsLocalFileImportOnly() {
        launchOnTab(R.id.nav_add);
        TestNavUtils.waitForViewVisible(R.id.bOpenFile, 8_000, "local file import page did not appear");
        onView(withId(R.id.bOpenFile)).check(matches(isDisplayed()));
        // the hub's LibriVox / Gutenberg / direct-link entries must be unreachable
        onView(withId(R.id.bOpenAudiobooks)).check(doesNotExist());
        onView(withId(R.id.bOpenEbooks)).check(doesNotExist());
        onView(withId(R.id.bDirectLink)).check(doesNotExist());
    }

    @Test
    public void tabs_canBeSwitchedBackAndForthWithoutCrashing() {
        launchDefault();
        int[] cycle = { R.id.nav_add, R.id.nav_settings, R.id.nav_library, R.id.nav_settings, R.id.nav_add,
                R.id.nav_library };
        for (int tab : cycle) {
            onView(withId(tab)).perform(click());
            TestNavUtils.sleep(700);
            assertEquals("tab " + ctx.getResources().getResourceEntryName(tab) + " not selected", tab,
                    selectedNavItem());
            assertTrue(scenario.getState().isAtLeast(Lifecycle.State.STARTED));
        }
    }

    @Test
    public void recreate_onEachTab_doesNotCrash() {
        for (int tab : new int[] { R.id.nav_library, R.id.nav_add, R.id.nav_settings }) {
            launchOnTab(tab);
            scenario.recreate(); // what a rotation / theme change / process restore does
            TestNavUtils.assertWaitForActivity(MainActivity.class, 8_000, "not shown after recreate");
            assertTrue(scenario.getState().isAtLeast(Lifecycle.State.STARTED));
            scenario.close();
            scenario = null;
        }
    }

    @Test
    public void staleRadioDeepLink_isHarmlessInPure() {
        // e.g. a radio share link opened from an old install/history - must not crash pure
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("bookplayerfull://radio/some-uuid"))
                .setClass(ctx, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        scenario = ActivityScenario.launch(i);
        TestNavUtils.assertWaitForActivity(MainActivity.class, 8_000, "MainActivity died on a radio deep link");
        assertTrue(scenario.getState().isAtLeast(Lifecycle.State.STARTED));
    }

    @Test
    public void unknownNavTabExtra_doesNotCrash() {
        // radio/podcast tab ids are valid in full; a stale intent asking for them must not kill pure
        for (int tab : new int[] { R.id.nav_radio, R.id.nav_podcast }) {
            Intent i = new Intent(ctx, MainActivity.class)
                    .putExtra(MainActivity.EXTRA_NAV_TAB_ID, tab)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            scenario = ActivityScenario.launch(i);
            TestNavUtils.assertWaitForActivity(MainActivity.class, 8_000, "MainActivity died on tab " + tab);
            assertTrue(scenario.getState().isAtLeast(Lifecycle.State.STARTED));
            scenario.close();
            scenario = null;
        }
    }
}
