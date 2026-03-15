package com.driot.bookplayer.testutil;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.content.Context;
import android.os.SystemClock;

import androidx.annotation.IdRes;
import androidx.annotation.StringRes;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;

import com.driot.bookplayer.R;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

public final class MenuHelpers {

    private MenuHelpers() {
    }

    /**
     * Clicks a menu item by id if shown on the toolbar; otherwise opens overflow
     * and clicks by title.
     */
    public static void tapMenu(@IdRes int menuItemId, @StringRes int menuTitleRes) {
        myLogI("tapMenu(id=" + resName(menuItemId) + ") START");
        SystemClock.sleep(1000);

        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();

        // 1) Try click via UiDevice directly (bypasses idleness and visibility constraints)
        try {
            UiObject item = device.findObject(new UiSelector().resourceId("com.driot.bookplayerfull.debug:id/" + resName(menuItemId)));
            if (item.waitForExists(500)) {
                item.click();
                myLogI("Tapped visible action via UiDevice: id=" + resName(menuItemId));
                return;
            }
        } catch (Exception ignored) {
        }

        // 2) Open overflow
        openOverflowViaUiDevice(device, ctx);
        SystemClock.sleep(1000);

        // 3) Tap item by title
        clickItemViaUiDevice(device, ctx.getString(menuTitleRes), menuTitleRes);
    }

    /** Overload: use only the title (for overflow-only items). */
    public static void tapMenu(@StringRes int menuTitleRes) {
        myLogI("tapMenu(titleRes=" + resName(menuTitleRes) + ") START");
        SystemClock.sleep(1000);

        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();

        // 1) Open overflow
        openOverflowViaUiDevice(device, ctx);
        SystemClock.sleep(1000);

        // 2) Tap item by title
        clickItemViaUiDevice(device, ctx.getString(menuTitleRes), menuTitleRes);
    }

    private static void openOverflowViaUiDevice(UiDevice device, Context ctx) {
        myLogD("Attempting to open overflow...");
        try {
            // Try by description "Menu" (from action_bar.xml title)
            UiObject threeDots = device.findObject(new UiSelector().description(ctx.getString(R.string.Menu)));
            if (threeDots.waitForExists(1000)) {
                threeDots.click();
                myLogI("Opened overflow via description");
                return;
            }

            // Try by resource id
            threeDots = device.findObject(new UiSelector().resourceId("com.driot.bookplayerfull.debug:id/action_menu_three_dot"));
            if (threeDots.waitForExists(500)) {
                threeDots.click();
                myLogI("Opened overflow via id");
                return;
            }

            // Try MENU key
            device.pressMenu();
            myLogI("Pressed MENU key");
        } catch (Exception e) {
            myLogE("Failed to open overflow via UiDevice: " + e.getMessage());
        }
    }

    private static void clickItemViaUiDevice(UiDevice device, String title, @StringRes int menuTitleRes) {
        myLogD("Looking for menu item: " + title);
        try {
            // First try direct find (if it's already on screen)
            UiObject item = device.findObject(new UiSelector().text(title));
            if (item.waitForExists(1000)) {
                item.click();
                myLogI("Tapped item via UiDevice: " + title);
                return;
            }

            // If not found, it might need scrolling (e.g., J5 in landscape)
            myLogD("Item not found immediately, trying UiScrollable...");
            UiScrollable menuList = new UiScrollable(new UiSelector().scrollable(true));
            // Specifically look for ListView or ScrollView if possible, but generic scrollable is safer
            if (menuList.exists()) {
                if (menuList.scrollIntoView(new UiSelector().text(title))) {
                    item.click();
                    myLogI("Tapped item after scroll via UiDevice: " + title);
                    return;
                }
            }
        } catch (Exception e) {
            myLogW("UiDevice click item failed or item not found: " + e.getMessage());
        }

        // Fallback to Espresso if UiDevice missed it (likely on A16 if root is tricky)
        myLogD("Attempting Espresso fallback for: " + title);
        try {
            // Try without inRoot(isPlatformPopup()) if A16 is failing to match it
            onView(withText(menuTitleRes))
                    .perform(click());
            myLogI("Tapped item via Espresso fallback (no root check): " + title);
        } catch (Exception e1) {
            myLogD("Espresso no-root-check failed, trying with isDisplayed()...");
            try {
                // Last ditch effort: find anything displayed with that text
                onView(withText(menuTitleRes))
                        .check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()))
                        .perform(click());
                myLogI("Tapped item via Espresso fallback (isDisplayed): " + title);
            } catch (Exception e2) {
                myLogE("CRITICAL: All click methods failed for [" + title + "]");
                throw e2;
            }
        }
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
