package com.driot.bookplayer.testutil;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anyOf;

import android.content.Context;

import androidx.annotation.IdRes;
import androidx.annotation.StringRes;
import androidx.test.platform.app.InstrumentationRegistry;

import com.driot.bookplayer.R;

import org.hamcrest.Matcher;

///  USAGE
/// int[] MENU_IDS = {
///         R.id.menu_open, R.id.menu_manual, R.id.menu_settings,
///         R.id.menu_seelog, R.id.menu_stats, R.id.menu_sendmail,
///         R.id.menu_cacheFiles, R.id.menu_website
/// };
///
/// int[] MENU_TITLES = {
///         R.string.menu_open, R.string.menu_manual, R.string.settings,
///         R.string.menu_seeLog, R.string.stats, R.string.menu_sendmail,
///         R.string.menu_cacheFiles, R.string.menu_website
/// };
///
/// for (int i = 0; i < MENU_IDS.length; i++) {
///     MenuHelpers.tapMenu(MENU_IDS[i], MENU_TITLES[i]);
///     // Do your screen assertions here…
///     androidx.test.espresso.Espresso.pressBack(); // return to main screen before next item
/// }


public final class MenuHelpers {

    private MenuHelpers() {}

    /** Clicks a menu item by id if shown on the toolbar; otherwise opens overflow and clicks by title. */
    public static void tapMenu(@IdRes int menuItemId, @StringRes int menuTitleRes) {
        // 1) Try direct action button (if it’s in the toolbar “ifRoom”)
        try {
            onView(withId(menuItemId)).perform(click());
            myLog("Tapped visible action: id=" + resName(menuItemId));
            return;
        } catch (Throwable ignored) {
            myLog("Action not visible; opening overflow…");
        }

        // 2) Open overflow safely (prefer Espresso helper, fallback to explicit 3-dots)
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try {
            openActionBarOverflowOrOptionsMenu(ctx);
            myLog("Opened overflow via Espresso helper");
        } catch (Throwable ignored) {
            // Fallback: your custom 3-dots in the toolbar
            Matcher threeDots = allOf(
                    anyOf(withId(R.id.action_menu_three_dot), withContentDescription(R.string.action_menu_three_dot)),
                    isDescendantOfA(withId(R.id.toolbar)),
                    isDisplayed()
            );
            onView(threeDots).perform(click());
            myLog("Opened overflow via toolbar three-dots");
        }

        // 3) Tap the item inside the popup by its title
        onView(withText(menuTitleRes))
                .inRoot(isPlatformPopup())
                .perform(click());
        myLog("Tapped overflow item: titleRes=" + resName(menuTitleRes));
    }

    /** Overload: use only the title (for overflow-only items). */
    public static void tapMenu(@StringRes int menuTitleRes) {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try {
            openActionBarOverflowOrOptionsMenu(ctx);
            myLog("Opened overflow via Espresso helper");
        } catch (Throwable ignored) {
            onView(allOf(
                    anyOf(withId(R.id.action_menu_three_dot), withContentDescription(R.string.action_menu_three_dot)),
                    isDescendantOfA(withId(R.id.toolbar)),
                    isDisplayed()
            )).perform(click());
            myLog("Opened overflow via toolbar three-dots");
        }
        onView(withText(menuTitleRes))
                .inRoot(isPlatformPopup())
                .perform(click());
        myLog("Tapped overflow-only item: titleRes=" + resName(menuTitleRes));
    }

    // --- tiny logging helpers (route to your logger if you want) ---
    private static void myLog(String msg) {
        // Replace with your KanLogger if available:
        android.util.Log.i("MenuHelpers", msg);
    }

    private static String resName(int resId) {
        try {
            Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
            return ctx.getResources().getResourceEntryName(resId);
        } catch (Exception ignored) {
            return String.valueOf(resId);
        }
    }
}
