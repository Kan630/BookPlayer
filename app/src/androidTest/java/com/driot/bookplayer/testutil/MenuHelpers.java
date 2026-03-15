package com.driot.bookplayer.testutil;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.content.Context;
import android.os.SystemClock;

import androidx.annotation.IdRes;
import androidx.annotation.StringRes;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiSelector;

import com.driot.bookplayer.R;
//import com.driot.bookplayer.utils.log.KanLogger;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

public final class MenuHelpers {

    private MenuHelpers() {
    }

    /**
     * Clicks a menu item by id if shown on the toolbar; otherwise opens overflow
     * and clicks by title.
     */
    public static void tapMenu(@IdRes int menuItemId, @StringRes int menuTitleRes) {
        myLog("tapMenu(id=" + resName(menuItemId) + ") START");
        SystemClock.sleep(1000);

        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();

        // 1) Try click via UiDevice directly (bypasses idleness)
        try {
            UiObject item = device.findObject(new UiSelector().resourceId("com.driot.bookplayerfull.debug:id/" + resName(menuItemId)));
            if (item.exists()) {
                item.click();
                myLog("Tapped visible action via UiDevice: id=" + resName(menuItemId));
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
        myLog("tapMenu(title=" + resName(menuTitleRes) + ") START");
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
        try {
            // Try by description "Menu" (from action_bar.xml title)
            UiObject threeDots = device.findObject(new UiSelector().description(ctx.getString(R.string.Menu)));
            if (threeDots.exists()) {
                threeDots.click();
                myLog("Opened overflow via description");
                return;
            }

            // Try by resource id
            threeDots = device.findObject(new UiSelector().resourceId("com.driot.bookplayerfull.debug:id/action_menu_three_dot"));
            if (threeDots.exists()) {
                threeDots.click();
                myLog("Opened overflow via id");
                return;
            }

            // Try MENU key
            device.pressMenu();
            myLog("Pressed MENU key");
        } catch (Exception e) {
            myLog("Failed to open overflow via UiDevice: " + e.getMessage());
        }
    }

    private static void clickItemViaUiDevice(UiDevice device, String title, @StringRes int menuTitleRes) {
        try {
            UiObject item = device.findObject(new UiSelector().text(title));
            if (item.exists()) {
                item.click();
                myLog("Tapped item via UiDevice: " + title);
                return;
            }
        } catch (Exception e) {
            myLog("UiDevice click item failed: " + e.getMessage());
        }

        // Fallback to Espresso if UiDevice missed it
        try {
            onView(withText(menuTitleRes)).inRoot(isPlatformPopup()).perform(click());
            myLog("Tapped item via Espresso fallback: " + title);
        } catch (Exception e) {
            myLog("Espresso fallback also failed: " + e.getMessage());
            throw e; // Reraise to fail test if item truly not found
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
