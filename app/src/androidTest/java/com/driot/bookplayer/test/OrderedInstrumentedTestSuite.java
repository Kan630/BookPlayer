package com.driot.bookplayer.test;

import com.driot.bookplayer.db.DatabaseUpgradeTest;
import com.driot.bookplayer.db.PureDatabaseTest;
import com.driot.bookplayer.imports.SiblingBookDetectorTest;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        PingTest.class,
        // ---- no-UI / fast: flavor shape, database, upgrades, backup (pure-only ones self-skip on full) ----
        PureFlavorTest.class,
        PureDatabaseTest.class,
        DatabaseUpgradeTest.class,
        PureBackupTest.class,
        SiblingBookDetectorTest.class,
        // Needs the dangerous permissions NOT granted (run-ordered-suite.sh revokes them from the
        // host first), so it runs before DeepSettingsTest / InputScreensCrashSurfaceTest grant them.
        PermissionHandlingTest.class,
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
        InputScreensCrashSurfaceTest.class,
        PureUiTest.class,
})
public class OrderedInstrumentedTestSuite {
}
