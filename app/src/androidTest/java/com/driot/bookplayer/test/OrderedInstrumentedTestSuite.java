package com.driot.bookplayer.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        PingTest.class,
        TestApplication.class,
        JustOpenAndWait.class,
        //UnzipWorkerTest.class,
        //M4bSplitWorkerTest.class,
        BasicNavPortraitTest.class,
        BasicNavLandscapeTest.class,
        LoadManyBookTest.class,
        DeepSettingsTest.class,
})
public class OrderedInstrumentedTestSuite {
}
