package com.driot.bookplayer.test;

import static androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import android.content.Context;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.driot.bookplayer.activities.GetActivity;
import com.driot.bookplayer.activities.MainActivity;
import com.driot.bookplayer.test.util.TestNavUtils;
import com.driot.bookplayer.utils.KanLogger;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

@RunWith(AndroidJUnit4.class)
public class MenuNavigationSmokeTest {

    @Before
    public void setUp() {
        myLogW("----------------- setUp -----------------");
        KanLogger.init(ApplicationProvider.getApplicationContext());
        myLogD("log D");
        myLog("log V");
        myLogI("log I");
        myLogW("log W");
        myLogE("log E");
    }

    @Rule
    public ActivityScenarioRule<MainActivity> rule =
            new ActivityScenarioRule<>(MainActivity.class);

    private final UiDevice device = UiDevice.getInstance(getInstrumentation());
    private static final long SHORT = 1_000;
    private static final long MEDIUM = 3_000;
    private static final String TAG = "MenuNavSmokeTest";

    private static String PKG() {
        return ApplicationProvider.getApplicationContext().getPackageName();
    }

    private void openOverflow(Context ctx) {
        myLogD("Opening overflow menu...");
        try {
            openActionBarOverflowOrOptionsMenu(ctx);
        } catch (Throwable ignored) { }

        if (hasAnyMenuTitle(SHORT)) return;

        UiObject2 more = device.wait(Until.findObject(By.descContains("More options")), SHORT);
        if (more == null) {
            more = device.wait(Until.findObject(By.descContains("Plus d’options")), SHORT);
            if (more == null) more = device.wait(Until.findObject(By.descContains("Plus d'options")), SHORT);
            if (more == null) more = device.wait(Until.findObject(By.descContains("options")), SHORT);
        }
        if (more != null) {
            myLogD("Clicking 'More options' button");
            more.click();
        } else {
            UiObject2 customDot = device.wait(
                    Until.findObject(By.res(PKG(), "action_menu_three_dot")), SHORT);
            if (customDot != null) {
                myLogD("Clicking custom 3-dot menu button");
                customDot.click();
            }
        }

        device.waitForIdle(SHORT);
        hasAnyMenuTitle(MEDIUM);
    }

    private boolean hasAnyMenuTitle(long timeout) {
        if (device.wait(Until.hasObject(By.res("android:id/title")), timeout)) return true;
        if (device.wait(Until.hasObject(By.res("androidx.appcompat:id/title")), 100)) return true;
        return device.wait(Until.hasObject(By.res(PKG() + ":id/title")), 100);
    }

    private List<UiObject2> findAllMenuTitleNodes() {
        List<UiObject2> out = new ArrayList<>();
        out.addAll(device.findObjects(By.res("android:id/title")));
        out.addAll(device.findObjects(By.res("androidx.appcompat:id/title")));
        out.addAll(device.findObjects(By.res(PKG() + ":id/title")));
        return out;
    }

    private UiObject2 findMenuEntryByText(String label, long timeout) {
        UiObject2 o = device.wait(Until.findObject(By.res("android:id/title").text(label)), timeout);
        if (o != null) return o;
        o = device.wait(Until.findObject(By.res("androidx.appcompat:id/title").text(label)), 200);
        if (o != null) return o;
        return device.wait(Until.findObject(By.res(PKG() + ":id/title").text(label)), 200);
    }

    private void scrollToEnd() throws Exception {
        myLogD("Scrolling to end of screen...");
        UiScrollable scroll = new UiScrollable(new UiSelector().scrollable(true));
        scroll.setAsVerticalList();
        try {
            scroll.scrollToEnd(10);
        } catch (Exception ignored) {
            UiObject2 root = device.findObject(By.pkg(device.getCurrentPackageName()));
            if (root != null) {
                for (int i = 0; i < 5; i++) {
                    root.swipe(Direction.UP, 0.8f, 500);
                    Thread.sleep(150);
                }
            }
        }
    }

    @Test
    public void walkAllOverflowItems_andScrollEachScreen() throws Exception {
        Context ctx = getInstrumentation().getTargetContext();
        myLogD("Starting menu navigation smoke test...");

        TestNavUtils.logCurrentActivity(TAG);

        // If we landed on GetActivity (empty state), press back to reach MainActivity
        if (TestNavUtils.getCurrentResumedActivity() instanceof GetActivity) {
            Log.i("SmokeTest", "On GetActivity, pressing back to reach MainActivity…");
            boolean ok = TestNavUtils.pressBackTo(MainActivity.class, /*maxPresses*/3, /*perStepWaitMs*/1000);
            if (!ok) throw new AssertionError("Could not navigate back to MainActivity");
        }

        TestNavUtils.logCurrentActivity(TAG);

        openOverflow(ctx);

        List<String> labels = new ArrayList<>();
        for (UiObject2 n : findAllMenuTitleNodes()) {
            if (n == null) continue;
            String t = n.getText();
            if (t != null) {
                t = t.trim();
                if (!t.isEmpty()) labels.add(t);
            }
        }
        myLogD("Found menu items: " + labels);

        for (String label : labels) {
            myLogD("Opening menu item: " + label);
            openOverflow(ctx);
            UiObject2 entry = findMenuEntryByText(label, MEDIUM);
            if (entry == null) {
                myLogW("Menu item disappeared: " + label);
                device.pressBack();
                Thread.sleep(150);
                continue;
            }
            entry.click();

            device.waitForIdle(SHORT);
            myLogD("Entered screen for: " + label);

            scrollToEnd();
            myLogD("Scrolled screen for: " + label);

            device.pressBack();
            device.waitForIdle(SHORT);
            myLogD("Returned from screen: " + label);
        }

        myLogD("Finished menu navigation smoke test.");
    }


    // ----------------------- LOG -----------------------
    private static void myLog(String str) { KanLogger.myLog(TAG, str); }
    private static void myLogD(String str) { Log.d(TAG, str); }
    private static void myLogI(String str) { KanLogger.myLogI(TAG, str); }
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }


}
