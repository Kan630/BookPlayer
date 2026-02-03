package com.driot.bookplayer.test;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.swipeUp;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import static com.driot.bookplayer.testutil.TestNavUtils.getRecyclerItemCount;
import static com.driot.bookplayer.testutil.TestNavUtils.sleep;
import static com.driot.bookplayer.testutil.TestNavUtils.waitForViewVisible;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.net.Uri;
import android.util.Log;

import androidx.core.content.FileProvider;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.Configuration;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.driot.bookplayer.BuildConfig;
import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.MainActivity;
import com.driot.bookplayer.activities.ZikFileActivity;
import com.driot.bookplayer.player.PlayActivity;
import com.driot.bookplayer.player.PlayList;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.imports.ImportHelper;
import com.driot.bookplayer.imports.LoadBookActivity;
import com.driot.bookplayer.imports.TaskUiState;
import com.driot.bookplayer.testutil.ImportProbe;
import com.driot.bookplayer.testutil.LogSupport;
import com.driot.bookplayer.testutil.TestNavUtils;
import com.driot.bookplayer.utils.log.KanLogger;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executors;

/**
 * First instrumented test: opens the app and imports a single book from fixtures.
 *
 * <p>Fixture setup: Add at least one book file under:
 * <ul>
 *   <li>app/src/androidTest/assets/fixtures/ebooks/ (e.g. sample.epub)</li>
 *   <li>app/src/androidTest/assets/fixtures/single_files/ (e.g. sample.mp3)</li>
 * </ul>
 * The fixtures folder is gitignored; add your own test files.
 */
public class ImportBookTest implements LogSupport {

    private static final long TIMEOUT_IMPORT = 120_000;
    private static final long PLAY_TIME_MS = 4_000;  // play long enough for progress to save (updater runs every 1s)
    private static final int ID_MAIN_RECYCLER = R.id.recyclerview_folders;
    private static final int ID_TRACKS_RECYCLER = R.id.recyclerview_zikfiles;
    private static final int ID_PLAY_BUTTON = R.id.ibPlayPause;

    private Context appContext;
    private ImportProbe importProbe;

    @Rule
    public ActivityScenarioRule<MainActivity> activityRule = new ActivityScenarioRule<>(MainActivity.class);

    @Before
    public void setUp() {
        appContext = ApplicationProvider.getApplicationContext();
        KanLogger.init(appContext);
        Option.setTechLog(true);
        Option.setCopyFile(false);
        Option.setUseSdCard(true);

        Configuration config = new Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .setExecutor(Executors.newSingleThreadExecutor())
                .setTaskExecutor(new SynchronousExecutor())
                .build();
        WorkManagerTestInitHelper.initializeTestWorkManager(appContext, config);

        ImportHelper.cancelCurrentImport(appContext);
    }

    @Test
    public void openAppAndImportBook() throws Exception {
        Context testContext = InstrumentationRegistry.getInstrumentation().getContext();

        String assetPath = findFirstFixtureFile(testContext.getAssets());
        if (assetPath == null) {
            throw new AssertionError(
                    "No fixture file found. Add a book to app/src/androidTest/assets/fixtures/\n"
                            + "  e.g. fixtures/ebooks/sample.epub or fixtures/single_files/sample.mp3");
        }

        myLog("Importing fixture: " + assetPath);

        File stagingRoot = new File(appContext.getCacheDir(), "fixtures");
        deleteQuiet(stagingRoot);
        stagingRoot.mkdirs();

        Uri contentUri = stageAssetAsContentUri(appContext, testContext, assetPath);
        importProbe = new ImportProbe(appContext);
        importProbe.start();

        try {
            appContext.startActivity(new Intent(appContext, LoadBookActivity.class)
                    .putExtra(LoadBookActivity.EXTRA_URI, contentUri)
                    .putExtra(LoadBookActivity.EXTRA_TYPE, "File")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION));

            TestNavUtils.assertWaitForActivity(LoadBookActivity.class, 5_000, "LoadBookActivity did not open");

            onView(withId(android.R.id.content)).perform(swipeUp());
            onView(withId(R.id.btnConfirm)).perform(click());

            TaskUiState terminal = importProbe.await(TIMEOUT_IMPORT);
            if (terminal == null) {
                TaskUiState last = importProbe.lastState();
                String lastProgress = (last == null || last.progressText == null) ? "" : last.progressText;
                throw new AssertionError("Import timeout after " + (TIMEOUT_IMPORT / 1000) + "s. Last progress: " + lastProgress);
            }

            if (Var.IMPORT_STATUS_FAILED.equals(terminal.status)) {
                String err = terminal.errorText != null ? terminal.errorText : "(no error text)";
                throw new AssertionError("Import failed: " + err);
            }

            if (Var.IMPORT_STATUS_CANCELLED.equals(terminal.status)) {
                throw new AssertionError("Import was cancelled unexpectedly");
            }

            myLog("Import succeeded: " + terminal.title);

            TestNavUtils.maybePressBackTo(MainActivity.class, 3, 2_000);
            waitForViewVisible(ID_MAIN_RECYCLER, 5_000, "MainActivity not visible");

            boolean hasBooks = TestNavUtils.waitForRecyclerItemCountAtLeast(ID_MAIN_RECYCLER, 1, 5_000) >= 1;
            if (!hasBooks) {
                throw new AssertionError("Import reported success but no book appears in the library");
            }

            // Open first book and play it to verify correct import + progress saving
            openFirstItemThenPlay();

            myLog("Test passed: book imported, played, and progress saved");
            sleep(500, "Final");
        } finally {
            if (importProbe != null) importProbe.stop();
        }
    }

    private void openFirstItemThenPlay() {
        if (!TestNavUtils.waitForWindowFocus(2_000)) {
            throw new AssertionError("Window never gained focus before click.");
        }
        waitForViewVisible(ID_MAIN_RECYCLER, 5_000, "MainActivity not visible");
        onView(withId(ID_MAIN_RECYCLER))
                .perform(RecyclerViewActions.actionOnItemAtPosition(0, click()));
        myLog("Clicked first item in main list");
        sleep(300);

        TestNavUtils.assertWaitForAnyActivity(5_000, PlayActivity.class, ZikFileActivity.class);

        if (TestNavUtils.isOn(PlayActivity.class)) {
            myLog("Landed directly on PlayActivity");
            runPlayAndAssertProgressSaved();
            return;
        }

        if (TestNavUtils.isOn(ZikFileActivity.class)) {
            myLog("On ZikFileActivity → clicking first track");
            clickFirstTrack();
            TestNavUtils.assertWaitForActivity(PlayActivity.class, 5_000, "Expected PlayActivity after choosing track");
            runPlayAndAssertProgressSaved();
            return;
        }

        throw new AssertionError("Unexpected navigation: neither PlayActivity nor ZikFileActivity is RESUMED.");
    }

    private void clickFirstTrack() {
        waitForViewVisible(ID_TRACKS_RECYCLER, 5_000, "Tracks RecyclerView not visible");
        int count = getRecyclerItemCount(ID_TRACKS_RECYCLER);
        if (count <= 0) throw new AssertionError("No tracks to click");
        onView(withId(ID_TRACKS_RECYCLER))
                .perform(RecyclerViewActions.actionOnItemAtPosition(0, click()));
        sleep(200);
    }

    private void runPlayAndAssertProgressSaved() {
        pressPlay();
        sleep(PLAY_TIME_MS, "Playback + progress save");

        PlayList pl = PlayList.getInstance();
        if (pl == null || pl.getZikFile() == null) {
            throw new AssertionError("PlayList not properly instantiated");
        }

        double position = pl.getZikFile().getPosition();
        if (position <= 0) {
            throw new AssertionError("Progress not saved: position=" + position + " (expected > 0 after playing). Import or playback may be broken.");
        }
        myLog("Progress saved: position=" + position + " ms");

        sleep(500, "Before back");
        TestNavUtils.pressBackTo(MainActivity.class, 3, 1_000);
    }

    private void pressPlay() {
        sleep(200);
        waitForViewVisible(ID_PLAY_BUTTON, 2_000, "Play button not visible");
        onView(withId(ID_PLAY_BUTTON)).perform(click());
        myLog("Pressed Play");
    }

    private static String findFirstFixtureFile(AssetManager am) throws IOException {
        String[] candidates = {"fixtures/ebooks", "fixtures/single_files", "fixtures/m4b", "fixtures/zip"};
        for (String dir : candidates) {
            String[] files = am.list(dir);
            if (files == null || files.length == 0) continue;
            for (String name : files) {
                String child = dir + "/" + name;
                String[] nested = am.list(child);
                if (nested == null || nested.length == 0) {
                    return child; // it's a file
                }
            }
        }
        return null;
    }

    private static Uri stageAssetAsContentUri(Context appCtx, Context testCtx, String assetPath) throws IOException {
        File stagingRoot = new File(appCtx.getCacheDir(), "fixtures");
        File outFile = new File(stagingRoot, assetPath);
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        try (InputStream in = testCtx.getAssets().open(assetPath);
             FileOutputStream out = new FileOutputStream(outFile)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        }

        String authority = BuildConfig.APPLICATION_ID + ".FileProvider";
        return FileProvider.getUriForFile(appCtx, authority, outFile);
    }

    private static void deleteQuiet(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteQuiet(k);
        }
        f.delete();
    }
}
