// test/util/WaitForView.java
package com.driot.bookplayer.testutil;



import android.view.View;

import androidx.test.espresso.PerformException;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.util.HumanReadables;
import androidx.test.espresso.util.TreeIterables;

import org.hamcrest.Matcher;

import java.util.concurrent.TimeoutException;

import static androidx.test.espresso.matcher.ViewMatchers.isRoot;

public class WaitForView implements ViewAction {
    private final Matcher<View> viewMatcher;
    private final long timeoutMs;

    public static ViewAction waitFor(Matcher<View> viewMatcher, long timeoutMs) {
        return new WaitForView(viewMatcher, timeoutMs);
    }

    private WaitForView(Matcher<View> viewMatcher, long timeoutMs) {
        this.viewMatcher = viewMatcher;
        this.timeoutMs = timeoutMs;
    }

    @Override public Matcher<View> getConstraints() { return isRoot(); }
    @Override public String getDescription() {
        return "wait up to " + timeoutMs + "ms for view " + viewMatcher.toString();
    }
    @Override public void perform(UiController uiController, View root) {
        uiController.loopMainThreadUntilIdle();
        final long start = System.currentTimeMillis();
        final long end = start + timeoutMs;

        do {
            for (View child : TreeIterables.breadthFirstViewTraversal(root)) {
                if (viewMatcher.matches(child)) return;
            }
            uiController.loopMainThreadForAtLeast(50);
        } while (System.currentTimeMillis() < end);

        throw new PerformException.Builder()
                .withActionDescription(getDescription())
                .withViewDescription(HumanReadables.describe(root))
                .withCause(new TimeoutException())
                .build();
    }
}
