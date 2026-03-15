package com.driot.bookplayer.test;

import androidx.test.platform.app.InstrumentationRegistry;
import com.driot.bookplayer.activities.AdminActivity;
import com.driot.bookplayer.testutil.TestNavUtils;
import org.junit.Before;

/**
 * Runs the same scenario as LoadManyBookTest, but clears all books
 * imported in the last year before starting.
 */
public class ClearAndLoadManyBookTest extends LoadManyBookTest {

    @Before
    @Override
    public void setUp() {
        myLog("ooooooooooooooooooooooooooooooooooooooooo");
        myLog("------- ClearAndLoadManyBookTest -------");
        myLog("ooooooooooooooooooooooooooooooooooooooooo");

        // 1) Clear all books (last 365 days)
        myLog("Clearing books via AdminActivity.deleteBooksByTimedelta...");
        AdminActivity.deleteBooksByTimedelta(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                60 * 24 * 365
        );

        // 2) Wait for workers to at least start/finish as we use SynchronousExecutor in super.setUp()
        TestNavUtils.sleep(2000, "Waiting for deletion workers");

        // 3) Proceed with standard setup from parent (launches MainActivity etc.)
        super.setUp();
    }
}
