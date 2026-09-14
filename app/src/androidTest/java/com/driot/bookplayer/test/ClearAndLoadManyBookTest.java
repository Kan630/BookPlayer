package com.driot.bookplayer.test;

import androidx.test.platform.app.InstrumentationRegistry;

import com.driot.bookplayer.helpers.DeleteHelper;
import com.driot.bookplayer.testutil.TestNavUtils;
import org.junit.Before;

/**
 * Runs the same scenario as LoadManyBookTest, but first clears only books imported in the
 * last {@link #CLEAR_WINDOW_MINUTES} - i.e. leftovers from a previous test run on this same
 * device - rather than the real, possibly-long-lived library. Instrumented tests run in-process
 * against whatever app instance is under test (no sandbox), so a wide deletion window here would
 * risk wiping real user data on a device that also holds a real library. Keep this window just
 * wide enough to cover this suite's own runtime.
 */
public class ClearAndLoadManyBookTest extends LoadManyBookTest {

    private static final int CLEAR_WINDOW_MINUTES = 120;

    @Before
    @Override
    public void setUp() {
        myLog("ooooooooooooooooooooooooooooooooooooooooo");
        myLog("------- ClearAndLoadManyBookTest -------");
        myLog("ooooooooooooooooooooooooooooooooooooooooo");

        // 1) Clear only this test suite's own recently-added books (last CLEAR_WINDOW_MINUTES)
        myLog("Clearing books via AdminActivity.deleteBooksByTimedelta...");
        DeleteHelper.deleteBooksByTimeDelta(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                CLEAR_WINDOW_MINUTES
        );

        // 2) Wait for workers to at least start/finish as we use SynchronousExecutor in super.setUp()
        TestNavUtils.sleep(2000, "Waiting for deletion workers");

        // 3) Proceed with standard setup from parent (launches MainActivity etc.)
        super.setUp();
    }
}
