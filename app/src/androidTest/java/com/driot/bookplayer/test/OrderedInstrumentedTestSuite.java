package com.driot.bookplayer.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        PingTest.class,
        // TestApplication is a plain Application subclass, not a JUnit test - it has no @Test
        // methods, so JUnit's Suite runner rejects it with "Invalid test class: No test methods
        // found". It isn't referenced anywhere else in the project either; it never belonged in
        // this list.
        JustOpenAndWait.class,
        //UnzipWorkerTest.class,
        //M4bSplitWorkerTest.class,
        BasicNavPortraitTest.class,
        BasicNavLandscapeTest.class,
        LoadManyBookTest.class,
        ClearAndLoadManyBookTest.class,
        DeepSettingsTest.class,
})
public class OrderedInstrumentedTestSuite {
}
