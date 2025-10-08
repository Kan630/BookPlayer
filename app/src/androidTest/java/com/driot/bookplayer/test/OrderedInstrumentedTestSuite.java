package com.driot.bookplayer.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        PingTest.class,
        TestApplication.class,
        JustOpenAndWait.class,
        BasicNavPortraitTest.class,
        BasicNavLandscapeTest.class,
        LoadManyBookTest.class,
})
public class OrderedInstrumentedTestSuite {
}
