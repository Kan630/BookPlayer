package com.driot.bookplayer.test;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.action.ViewActions.swipeUp;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.driot.bookplayer.testutil.TestNavUtils.getRecyclerItemCount;
import static com.driot.bookplayer.testutil.TestNavUtils.sleep;
import static com.driot.bookplayer.testutil.TestNavUtils.waitForViewVisible;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.IdRes;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.Configuration;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.driot.bookplayer.BuildConfig;
import com.driot.bookplayer.R;
import com.driot.bookplayer.adapter.FoldersRVAdapter;
import com.driot.bookplayer.activities.GetActivity;
import com.driot.bookplayer.imports.ImportBookSingleActivity;
import com.driot.bookplayer.activities.MainActivity;
import com.driot.bookplayer.imports.OngoingTaskUiState;
import com.driot.bookplayer.player.PlayActivity;
import com.driot.bookplayer.activities.ZikFileActivity;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.imports.ImportHelper;
import com.driot.bookplayer.player.PlayList;
import com.driot.bookplayer.player.PlaybackUiBus;
import com.driot.bookplayer.player.PlaybackUiState;
import com.driot.bookplayer.testutil.ImportProbe;
import com.driot.bookplayer.testutil.LogSupport;
import com.driot.bookplayer.testutil.LoggingWatcher;
import com.driot.bookplayer.testutil.TestNavUtils;
import com.driot.bookplayer.utils.log.KanLogger;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.imports.ImportJob;
import com.driot.bookplayer.global.Var;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

import androidx.core.content.FileProvider;

public class LoadManyBookTest implements LogSupport {

    private Context appContext;

    private final static boolean DEBUG_MODE_NO_LOOP = false;
    private final static long TIMEOUT_TEST_END = 1 * 60_000;
    private final static long TIMEOUT_BOOK_LOAD = 120_000;
    private final static long TIMEOUT_VISUAL_CHECK = 3_000;
    private final static long DEBUG_VISUAL_CHECK = 3_000;
    private final static long TIMEOUT_SINGLE_IMPORT_READY = 15_000;

    private static final int ID_MAIN_RECYCLER = R.id.recyclerview_folders; // list on MainActivity
    private static final int ID_TRACKS_RECYCLER = R.id.recyclerview_zikfiles; // list on ZikFileActivity
    private static final int ID_PLAY_BUTTON = R.id.ibPlayPause; // play button on PlayActivity
    private final static long PLAY_TIME = 3_000;

    // Some fixture categories (e.g. "ebooks", pulled straight off a real personal collection on
    // the SD card) can hold hundreds of files - importing+playing every single one would turn
    // this test into a multi-hour run without meaningfully improving regression coverage over a
    // smaller representative sample. Cap each category to a random sample instead.
    private static final int MAX_FIXTURES_PER_CATEGORY = 5;

    private static final class TestCase {
        final String uri_type;
        final String subfolderName; // e.g. "m4b" - resolved under the discovered fixtures root

        TestCase(String uri_type, String subfolderName) {
            this.uri_type = uri_type;
            this.subfolderName = subfolderName;
        }
    }

    //Do not change that List layout, I like it like this for easy change
    private static final List<TestCase> TESTS = Arrays.asList(
              new TestCase("File", "zip")
            , new TestCase("File", "ebooks")
            , new TestCase("Folder", "folders")
            , new TestCase("File", "m4b")
            , new TestCase("File", "single_files"));

    private ImportProbe importProbe;

    private String lastPlayedSong = "init no song";

    // Launches MainActivity before each test
    @Rule
    public ActivityScenarioRule<MainActivity> activityRule = new ActivityScenarioRule<>(MainActivity.class);

    StringBuilder logFinalImportMsg;
    StringBuilder logFinalPlayMsg;
    int nbPlayed = 0;
    int nbImported = 0;
    int nbAttempted = 0;
    // "KO"-named fixtures (see [[ko_fixture_convention]]) are deliberately broken - they're
    // expected to fail import gracefully (no crash), so a failure there is counted here rather
    // than treated as a test failure. Any OTHER fixture failing to import is unexpected and gets
    // collected in unexpectedImportFailures instead. Neither bucket is used for a crash-level
    // failure - see crashLikeImportFailures and ImportOutcome below for that distinction.
    int nbKoHandled = 0;
    final List<String> unexpectedImportFailures = new ArrayList<>();
    // A failure that looks like an actual app-level bug rather than a designed, graceful error
    // path: a timeout/hang, a broken DB invariant after a reported success, or the "Unexpected
    // error" message that a worker's outer catch(Throwable) uses specifically when it swallowed
    // an uncaught exception (see ImportOutcome). Always treated as a real problem worth failing
    // the test over - even on a KO fixture, since being deliberately broken means it's expected
    // to fail via a normal designed error, not by tripping an actual bug in the app's own code.
    final List<String> crashLikeImportFailures = new ArrayList<>();
    String lastImport;
    int nb_TESTS;
    int current_TEST;
    int nb_subTESTS;
    int current_subTEST;


    @Rule
    public LoggingWatcher logs = new LoggingWatcher();

    @Before
    public void setUp() {
        myLog("ooooooooooooooooooooooooooooooooooooooooo");
        myLog("----------------- setUp -----------------");
        myLog("ooooooooooooooooooooooooooooooooooooooooo");

        appContext = ApplicationProvider.getApplicationContext();
        KanLogger.init(appContext);
        Option.setTechLog(true);

        Option.setCopyFile(false);
        Option.setUseSdCard(false);

        Configuration config = new Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .setExecutor(Executors.newSingleThreadExecutor()) // if not worker on main UI => not allowed...
                .setTaskExecutor(new SynchronousExecutor())
                .build();
        WorkManagerTestInitHelper.initializeTestWorkManager(appContext, config);

        TestNavUtils.logCurrentActivity();
        if (TestNavUtils.getCurrentResumedActivity() instanceof GetActivity) {
            myLogW("On GetActivity, pressing back to reach MainActivity…");
            boolean ok = TestNavUtils.pressBackTo(MainActivity.class, 3, 1000);
            if (!ok)
                throw new AssertionError("Could not navigate back to MainActivity");
            TestNavUtils.logCurrentActivity();
        }
    }

    @Test
    public void loadManyBooks() throws Exception {
        myLog("loadManyBooks");

        logFinalImportMsg = new StringBuilder(
                "--------------------------\n--------------------------\nFinal Import Message\n--------------------------");
        logFinalPlayMsg = new StringBuilder(
                "--------------------------\n--------------------------\nFinal Play Message\n--------------------------");

        // TODO Should not be used, hide potential user errors, check other test classes
        ImportHelper.cancelCurrentImport(appContext);

        // Fixtures live as real files directly on device storage (SD card or internal) under a
        // "fixtures" folder - never bundled into the androidTest APK's assets. That would mean
        // re-copying a large, ever-growing set of real book files into the app/src/androidTest
        // source tree and repackaging/reinstalling on every single test run just to pick up a
        // fixture change, which is exactly what this is deliberately avoiding: fixtures are
        // populated once, directly on the device, and read from there in place.
        File fixturesRoot = findFixturesRoot(appContext);
        if (fixturesRoot == null) {
            throw new AssertionError("No 'fixtures' directory found on any mounted storage volume "
                    + "(checked primary storage and every volume from getExternalFilesDirs()). "
                    + "Put real book/ebook/zip/m4b files under <storage-root>/fixtures/{zip,ebooks,folders,m4b,single_files}/ "
                    + "on this device - see README_FIXTURES.txt.");
        }
        myLog("Using fixtures root: " + fixturesRoot.getAbsolutePath());

        myLogI("--------------------------------------------------------------------------------------------------------------------------------------");
        myLogI("---------------------------------------- ooooooooooooooooooooooo ---------------------------------------------------------------------");
        myLogI("--------------------------------------------------------------------------------------------------------------------------------------");
        nb_TESTS = TESTS.size();
        current_TEST = 0;
        for (TestCase tc : TESTS) {
            myLog(tc.uri_type + " - " + tc.subfolderName);
        }

        for (TestCase tc : TESTS) {
            current_TEST += 1;
            File caseRoot = new File(fixturesRoot, tc.subfolderName);
            List<File> files = randomSample(listFilesRecursively(appContext, caseRoot), MAX_FIXTURES_PER_CATEGORY);
            myLogI("--------------------------------------------------------------------------------------------------------------------------------------");
            myLogI("---------------------------------------- ooooooooooooooooooooooo ---------------------------------------------------------------------");
            myLogI("--------------------------------------------------------------------------------------------------------------------------------------");
            myLogI("         Import => " + String.format("TestCase '%s'-'%s' -> %d files", tc.uri_type, caseRoot, files.size()));
            myLogD("--------------------------------------------------");
            if ("Folder".equals(tc.uri_type)) {
                List<File> subdirs = randomSample(listSubdirectories(appContext, caseRoot), MAX_FIXTURES_PER_CATEGORY);
                nb_subTESTS = subdirs.size();
                current_subTEST = 0;
                myLog("Found " + nb_subTESTS + " folders to import under " + caseRoot);
                for (File dir : subdirs) {
                    current_subTEST += 1;
                    // Unlike a single file, importing "a folder" means the app's own import
                    // logic recursively lists that folder itself (a real File.listFiles() scan,
                    // not just a read of already-known paths) to discover its tracks - which
                    // fails the same way our own discovery would have, since it's a directory
                    // outside this app's sandbox. Mirror just this one book's files into the
                    // app's own cache (discovered via MediaStore, same as above) so that scan has
                    // something it can freely enumerate.
                    File cacheDir = mirrorFolderToCache(appContext, dir, listFilesRecursively(appContext, dir));
                    Uri dirUri = Uri.fromFile(cacheDir); // same-app -> file:// OK
                    long idFolder = runImport(dirUri, tc.uri_type);
                    if (idFolder != -1) {
                        goPlay(idFolder);
                    } else {
                        myLogW("Skipping playback for skipped/duplicate import: " + dirUri);
                    }
                    if (DEBUG_MODE_NO_LOOP)
                        return;
                }
            } else {
                nb_subTESTS = files.size();
                current_subTEST = 0;
                for (File file : files) {
                    current_subTEST += 1;
                    Uri contentUri = fileToContentUri(appContext, file);
                    long idFolder = runImport(contentUri, tc.uri_type);
                    if (idFolder != -1) {
                        goPlay(idFolder);
                    } else {
                        myLogW("Skipping playback for skipped/duplicate import: " + contentUri);
                    }
                    if (DEBUG_MODE_NO_LOOP)
                        return;
                }
            }
            logFinalImportMsg.append("\n--------------------------");
            logFinalPlayMsg.append("\n--------------------------");
        }
        TestNavUtils.maybePressBackTo(MainActivity.class, 3, 1_000);
        waitForViewVisible(ID_MAIN_RECYCLER, 5_000, "MainActivity not visible");
        myLogI(nbAttempted + " fixture(s) attempted");
        myLogI(nbImported + " books imported");
        myLogI(logFinalImportMsg.append("\n--------------------------").toString());
        myLogI(nbPlayed + " books played");
        myLogI(logFinalPlayMsg.append("\n--------------------------").toString());
        myLogI(nbKoHandled + " KO (deliberately-broken) fixture(s) failed import gracefully, as expected");
        if (!unexpectedImportFailures.isEmpty()) {
            myLogE(unexpectedImportFailures.size()
                    + " graceful-but-unexpected import failure(s) (not KO-named, but still a clean, designed error - not a crash):");
            for (String f : unexpectedImportFailures) {
                myLogE("  - " + f);
            }
        }
        if (!crashLikeImportFailures.isEmpty()) {
            myLogE(crashLikeImportFailures.size()
                    + " CRASH-LEVEL import failure(s) (timeout/hang, broken DB invariant, or a swallowed uncaught "
                    + "exception - see ImportOutcome) - counts even on a KO fixture, since that's not the "
                    + "graceful failure a KO fixture is supposed to produce:");
            for (String f : crashLikeImportFailures) {
                myLogE("  - " + f);
            }
        }
        TestNavUtils.assertRecyclerItemCountEquals(ID_MAIN_RECYCLER, nbImported, 5_000,
                "Mismatch between nb of imported book, and nb of actually present books");
        myLog("nb Books imported =" + nbImported);
        TestNavUtils.sleep(TIMEOUT_TEST_END, "TEST END");

        // Only raised now, after every fixture has been attempted and the full report above is
        // logged - a KO fixture failing gracefully is expected (see [[ko_fixture_convention]] and
        // nbKoHandled above) and never reaches here on its own.
        if (!unexpectedImportFailures.isEmpty() || !crashLikeImportFailures.isEmpty()) {
            StringBuilder msg = new StringBuilder();
            if (!crashLikeImportFailures.isEmpty()) {
                msg.append(crashLikeImportFailures.size()).append(" CRASH-LEVEL fixture(s) (timeout/hang, broken DB ")
                        .append("invariant, or a swallowed uncaught exception):\n - ")
                        .append(String.join("\n - ", crashLikeImportFailures));
            }
            if (!unexpectedImportFailures.isEmpty()) {
                if (msg.length() > 0)
                    msg.append("\n");
                msg.append(unexpectedImportFailures.size())
                        .append(" fixture(s) failed to import unexpectedly (not KO-named, so not an expected failure, "
                                + "though still a clean/graceful error, not a crash):\n - ")
                        .append(String.join("\n - ", unexpectedImportFailures));
            }
            throw new AssertionError(msg.toString());
        }
    }

    /**
     * Thrown by {@link #runImport} to tag WHY an import didn't succeed, so its catch block can
     * tell a designed, graceful failure apart from a crash-level one (timeout/hang, a broken DB
     * invariant, or an uncaught exception a worker's own outer catch(Throwable) swallowed instead
     * of letting it crash the app - see the "Unexpected error" check at its throw site). Plain
     * {@code AssertionError} (e.g. from Espresso/TestNavUtils elsewhere in this file) is still
     * caught the same way but treated as non-crash-level, since it carries no such tag.
     */
    private static final class ImportOutcome extends AssertionError {
        final boolean crashLevel;

        ImportOutcome(String message, boolean crashLevel) {
            super(message);
            this.crashLevel = crashLevel;
        }
    }

    private void goPlay(long idFolder) throws InterruptedException {
        TestNavUtils.logCurrentActivity();
        TestNavUtils.maybePressBackTo(MainActivity.class, 3, 1_000);
        TestNavUtils.logCurrentActivity();
        openTargetedItemThenPlay(idFolder, PLAY_TIME);
    }

    /// -----------------------------------------------------------------------------------------------------------------------------------------
    /// -----------------------------------------------------------------------------------------------------------------------------------------
    /// -----------------------------------------------------------------------------------------------------------------------------------------

    // ---------- Helpers ----------

    private long runImport(Uri uri_content, String uri_type) throws InterruptedException {
        long lastTimestamp;
        lastImport = uri_content.getLastPathSegment();
        // See [[ko_fixture_convention]]: any file/folder with "KO" in its name is a deliberately
        // broken fixture, expected to fail import gracefully - not a real regression.
        boolean isKoFixture = lastImport != null && lastImport.toLowerCase(Locale.ROOT).contains("ko");
        myLogD("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
        myLog("runImport " + uri_type + " : " + uri_content);
        myLog("runImport " + lastImport + (isKoFixture ? " [KO fixture]" : ""));
        myLogD("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");

        lastTimestamp = System.currentTimeMillis();
        nbAttempted += 1;

        importProbe = new ImportProbe(appContext);
        importProbe.start();

        try {
            appContext.startActivity(new Intent(appContext, ImportBookSingleActivity.class)
                    .putExtra(ImportBookSingleActivity.EXTRA_URI, uri_content)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                            | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION));
            myLog("ImportBookSingleActivity launched");
            TestNavUtils.assertWaitForActivity(ImportBookSingleActivity.class, 1_000, "arfff");
            myLogD("ok, on ImportBookSingleActivity");

            onView(withId(android.R.id.content)).perform(swipeUp());

            myLog("DEBUG_VISUAL_CHECK - Waiting " + DEBUG_VISUAL_CHECK + " ms...");
            Thread.sleep(DEBUG_VISUAL_CHECK);

            myLog("Waiting for btnConfirm to be enabled or errorTextView to show error...");
            long startWait = System.currentTimeMillis();
            boolean isDuplicate = false;
            while (System.currentTimeMillis() - startWait < TIMEOUT_SINGLE_IMPORT_READY) {
                try {
                    // Check button
                    onView(withId(R.id.btnConfirm))
                            .perform(scrollTo())
                            .check(matches(isDisplayed()))
                            .check(matches(isEnabled()));
                    break;
                } catch (AssertionError | Exception e) {
                    // Check error text
                    try {
                        String errorText = TestNavUtils.getText(withId(R.id.errorTextView));
                        if (errorText != null && !errorText.trim().isEmpty()) {
                            myLogW("Duplicate/Error detected: " + errorText);
                            isDuplicate = true;
                            break;
                        }
                    } catch (Exception ignored) {
                        // View might not be visible yet or not a TextView
                    }
                    Thread.sleep(500);
                }
            }

            if (isDuplicate) {
                myLog("Proceeding to next import as requested (duplicate detected)");
                TestNavUtils.sleep(DEBUG_VISUAL_CHECK, "Visual check of error message");
                androidx.test.espresso.Espresso.pressBack();
                return -1;
            }

            onView(withId(R.id.btnConfirm)).perform(click());
            /*
             * appContext.startActivity(new Intent(appContext, AddResourceActivity.class)
             * .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
             * myLog("AddResourceActivity launched");
             * TestNavUtils.assertWaitForActivity(AddResourceActivity.class, 1_000,
             * "gizmo");
             * myLogD("ok, on AddResourceActivity");
             * 
             */

            // --- Wait for terminal state from Room ---
            OngoingTaskUiState terminal = importProbe.await(TIMEOUT_BOOK_LOAD);
            if (terminal == null) {
                OngoingTaskUiState last = importProbe.lastState();
                String lastProgress = (last == null || last.progressText == null) ? "" : last.progressText;
                // A timeout means the app never reached ANY terminal state (not even a clean
                // FAILED) within the allotted time - always crash-level (possible hang/ANR), never
                // the graceful failure a KO fixture is supposed to produce.
                throw new ImportOutcome("Timeout after " + TIMEOUT_BOOK_LOAD / 1000
                        + "s waiting for import. Last progress: " + lastProgress, true);
            }

            if (!Var.IMPORT_STATUS_SUCCEEDED.equals(terminal.status)) {
                String devErr = terminal.errorText != null ? terminal.errorText : "(no errorText)";
                String warn = terminal.warningText != null ? terminal.warningText : "";
                // FinalParseFolderWorker's (and DownloadWorker's) outer catch(Throwable) - the one
                // that swallows an actual uncaught exception instead of letting it crash the app -
                // always reports this exact user-facing string (see ImportWorker.failResult() /
                // R.string.unexpected_error). Every other designed error path uses its own
                // specific message, so this string is a reliable signal that the fixture tripped a
                // real bug, not a normal "can't read this file" kind of failure.
                boolean looksLikeSwallowedCrash = appContext.getString(R.string.unexpected_error).equals(devErr);
                throw new ImportOutcome("Import failed according to Probe.\n" +
                        "Status: " + terminal.status + "\n" +
                        "Title: " + terminal.title + "\n" +
                        "Progress: " + terminal.progressPercent + "% - " + terminal.progressText + "\n" +
                        "Error: " + devErr + "\n" +
                        "Warnings: " + warn, looksLikeSwallowedCrash);
            }

            // SUCCESS path continues below...
            myLog("Success book load (via probe)");
            nbImported += 1;

            // --- DB Reality Check --- (a broken invariant here means something is actually wrong
            // in the app/worker/test harness, not a designed error path - always crash-level)
            AppDatabase db = AppDatabase.getInstance(appContext);
            ImportJob job = db.importJobDao().getUniqueJob();
            if (job == null)
                throw new ImportOutcome("ImportJob not found in DB after success", true);
            myLogD("DB Reality Check: ImportJob status=" + job.status + ", futureFolderPath=" + job.futureFolderPath);

            if (!Var.IMPORT_STATUS_SUCCEEDED.equals(job.status)) {
                throw new ImportOutcome(
                        "DB Reality Check failed: ImportJob status is " + job.status + " but probe said SUCCEEDED",
                        true);
            }

            Folder folder = db.folderDao().getFolderByPath(job.futureFolderPath);
            if (folder == null) {
                // Try to find it by name as fallback or if path is absolute vs relative
                folder = db.folderDao().getByName(job.title);
                if (folder == null) {
                    throw new ImportOutcome(
                            "Folder not found in DB for path: " + job.futureFolderPath + " or title: " + job.title,
                            true);
                }
            }
            long idFolder = folder.getId();
            myLogI("DB Reality Check: Folder found with id=" + idFolder + ", name=" + folder.getName());

            // --- Duration log (robust name from URI) ---
            String duration = Tonio.formatMmSs(System.currentTimeMillis() - lastTimestamp);
            String baseFromPath = (uri_content.getPath() != null) ? Tonio.getFileNameFromPath(uri_content.getPath())
                    : null;
            String baseFromSeg = (uri_content.getLastPathSegment() != null) ? uri_content.getLastPathSegment() : null;
            String targetName = (baseFromPath != null && !baseFromPath.isEmpty()) ? baseFromPath
                    : (baseFromSeg != null ? baseFromSeg : uri_content.toString());

            String logDuration = duration + "  " + targetName;
            myLogI("-----------------------------------------------------------------------------------------------------------------------------");
            myLogI("Import n°" + nbImported + " [" + current_subTEST + "/" + nb_TESTS + "] [" + current_TEST + "/" + nb_TESTS  + "]: Duration: " + logDuration);
            String newLineMsg = "\n" + logDuration;

            // log warnings
            String txtWarnings = terminal.warningText;
            if (txtWarnings != null)
                newLineMsg = newLineMsg + "\ndisplayed warnings : \n" + txtWarnings;
            logFinalImportMsg.append(newLineMsg);

            TestNavUtils.sleep(TIMEOUT_VISUAL_CHECK, "Visual Check");

            return idFolder;
        } catch (AssertionError importFailure) {
            // An import error should never stop the whole test run, whether the fixture is a KO
            // (deliberately-broken) one or not - see the user's request that led here: the app is
            // expected to fail gracefully either way, and this test's job is to keep going and
            // report on it, not to die on the first bad fixture. But a graceful, designed failure
            // and a crash-level one (timeout/hang, broken DB invariant, or an uncaught exception
            // a worker swallowed - see ImportOutcome) are NOT the same severity: only a crash-level
            // failure always fails the test, KO fixture or not; a graceful failure is only a
            // problem when it hits a fixture that wasn't supposed to fail at all.
            String label = uri_type + " - " + lastImport;
            boolean crashLevel = (importFailure instanceof ImportOutcome) && ((ImportOutcome) importFailure).crashLevel;
            if (crashLevel) {
                String detail = label + " : " + importFailure.getMessage();
                crashLikeImportFailures.add(detail);
                myLogE("CRASH-LEVEL import failure" + (isKoFixture ? " (on a KO fixture - still not the graceful "
                        + "failure a KO fixture is supposed to produce)" : "") + ": " + detail);
                logFinalImportMsg.append("\n[CRASH-LEVEL FAILURE] ").append(label);
            } else if (isKoFixture) {
                nbKoHandled += 1;
                myLogI("KO fixture failed import gracefully, as expected (" + nbKoHandled + " so far): "
                        + label + " - " + importFailure.getMessage());
                logFinalImportMsg.append("\n[KO - expected failure] ").append(label);
            } else {
                String detail = label + " : " + importFailure.getMessage();
                unexpectedImportFailures.add(detail);
                myLogE("UNEXPECTED (but graceful, non-crash) import failure (not a KO fixture): " + detail);
                logFinalImportMsg.append("\n[UNEXPECTED FAILURE] ").append(label);
            }
            // The failed import may have left us on an error screen (ImportBookSingleActivity or
            // AddResourceActivity) rather than back on MainActivity - get back there so the next
            // fixture in the loop starts from a clean state.
            TestNavUtils.maybePressBackTo(MainActivity.class, 4, 1_000);
            return -1;
        } finally {
            if (importProbe != null)
                importProbe.stop();
        }

    }

    /** Returns at most `max` elements of `list`, chosen at random (whole list if it's smaller). */
    private static <T> List<T> randomSample(List<T> list, int max) {
        if (list.size() <= max)
            return list;
        List<T> shuffled = new ArrayList<>(list);
        Collections.shuffle(shuffled);
        return shuffled.subList(0, max);
    }

    /**
     * Locates a real "fixtures" directory on device storage - primary storage first, then every
     * volume reported by {@link Context#getExternalFilesDirs}, which is the portable way to
     * enumerate mounted volumes (SD card included) without StorageManager reflection. Each
     * volume's app-private "…/Android/data/<pkg>/files" dir is walked back up to that volume's
     * real root (the parent of "Android"), then checked for a "fixtures" subfolder. A stat on an
     * already-known path like this works fine without any special permission on API 30+ - see
     * queryIndexedPathsUnderPrefix() below for why bulk directory *listing* is a different story.
     */
    private static File findFixturesRoot(Context context) {
        List<File> candidateRoots = new ArrayList<>();
        File primary = Environment.getExternalStorageDirectory();
        if (primary != null)
            candidateRoots.add(primary);

        File[] externalFilesDirs = context.getExternalFilesDirs(null);
        if (externalFilesDirs != null) {
            for (File dir : externalFilesDirs) {
                if (dir == null)
                    continue;
                File node = dir;
                while (node != null && !"Android".equals(node.getName())) {
                    node = node.getParentFile();
                }
                File volumeRoot = (node != null) ? node.getParentFile() : null;
                if (volumeRoot != null)
                    candidateRoots.add(volumeRoot);
            }
        }

        for (File root : candidateRoots) {
            File fixtures = new File(root, "fixtures");
            if (fixtures.isDirectory())
                return fixtures;
        }
        return null;
    }

    /**
     * All paths MediaStore has indexed under `rootPath/` (any depth). Directory enumeration
     * (File.listFiles()) on a path outside this app's own sandbox is blocked/returns empty under
     * scoped storage without MANAGE_EXTERNAL_STORAGE - confirmed on the Samsung A16 test device,
     * where Knox disables the "All files access" toggle for this app entirely (greyed out in
     * Settings, and `adb shell appops set MANAGE_EXTERNAL_STORAGE allow` silently doesn't stick
     * either). A *stat* on an already-known exact path (File.exists()/isFile()/isDirectory(), or
     * opening it for reading) still works fine though - it's specifically bulk listing that's
     * blocked. MediaStore.Files is the sanctioned, scoped-storage-compliant way to discover which
     * paths exist under a folder (it indexes every file type, not just media); this app's SD card
     * fixtures were already indexed by the OS's own media scan. Each returned path is then
     * File.isFile()/isDirectory() checked (a stat, not a listing) to classify it.
     */
    private static List<String> queryIndexedPathsUnderPrefix(Context context, String rootPath) {
        List<String> out = new ArrayList<>();
        Uri filesUri = android.provider.MediaStore.Files.getContentUri("external");
        String[] projection = { android.provider.MediaStore.Files.FileColumns.DATA };
        String selection = android.provider.MediaStore.Files.FileColumns.DATA + " LIKE ?";
        String[] args = { rootPath + "/%" };
        try (android.database.Cursor c = context.getContentResolver().query(filesUri, projection, selection, args, null)) {
            if (c != null) {
                int idx = c.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns.DATA);
                while (c.moveToNext()) {
                    String p = c.getString(idx);
                    if (p != null)
                        out.add(p);
                }
            }
        }
        return out;
    }

    /** Recursively lists every regular file under `root` (files only, no directories). */
    private static List<File> listFilesRecursively(Context context, File root) {
        List<File> out = new ArrayList<>();
        if (root == null)
            return out;
        for (String path : queryIndexedPathsUnderPrefix(context, root.getAbsolutePath())) {
            File f = new File(path);
            if (f.isFile())
                out.add(f);
        }
        return out;
    }

    /** Direct subdirectories of `root` (no files, not recursive). */
    private static List<File> listSubdirectories(Context context, File root) {
        List<File> out = new ArrayList<>();
        if (root == null)
            return out;
        String rootPath = root.getAbsolutePath();
        for (String path : queryIndexedPathsUnderPrefix(context, rootPath)) {
            String rel = path.substring(Math.min(rootPath.length() + 1, path.length()));
            if (!rel.isEmpty() && !rel.contains("/")) { // direct child only
                File f = new File(path);
                if (f.isDirectory())
                    out.add(f);
            }
        }
        return out;
    }

    /**
     * content:// Uri for a real file already sitting on device storage - no copy needed, since
     * unlike an asset packed inside the APK this is already a plain file on disk. Requires the
     * FileProvider's paths config to cover the file's location - see the root-path entry in
     * res/xml/file_provider.xml (covers any mounted volume, not just primary storage).
     */
    private static Uri fileToContentUri(Context appCtx, File file) {
        String authority = BuildConfig.APPLICATION_ID + ".FileProvider";
        return FileProvider.getUriForFile(appCtx, authority, file);
    }

    /**
     * Copies `sourceFiles` (already discovered via {@link #listFilesRecursively}) into a mirror
     * of `sourceDir` under this app's own cache dir, preserving their paths relative to
     * `sourceDir`. Only needed for the Folder test case - see the comment at its call site.
     */
    private static File mirrorFolderToCache(Context appCtx, File sourceDir, List<File> sourceFiles)
            throws IOException {
        File destDir = new File(new File(appCtx.getCacheDir(), "fixtures_staging"), sourceDir.getName());
        deleteQuiet(destDir);
        String sourceRootPath = sourceDir.getAbsolutePath();
        for (File srcFile : sourceFiles) {
            String rel = srcFile.getAbsolutePath().substring(sourceRootPath.length() + 1);
            File destFile = new File(destDir, rel);
            File parent = destFile.getParentFile();
            if (parent != null && !parent.exists())
                parent.mkdirs();
            try (InputStream in = new FileInputStream(srcFile);
                    FileOutputStream out = new FileOutputStream(destFile)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) >= 0)
                    out.write(buf, 0, n);
            }
        }
        return destDir;
    }

    private static void deleteQuiet(File f) {
        if (f == null || !f.exists())
            return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null)
                for (File k : kids)
                    deleteQuiet(k);
        }
        // noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    /** Call this right after an import when you're back on MainActivity. */
    private void openTargetedItemThenPlay(long idFolder, long playTime) throws InterruptedException {
        // 1) ensure window focused before Espresso checks
        if (!TestNavUtils.waitForWindowFocus(2_000)) {
            throw new AssertionError("Window never gained focus before click.");
        }

        // 2) get the title of the folder from DB, for logging only - see findFolderPosition()
        // for how the actual click target is resolved.
        Folder folder = AppDatabase.getInstance(appContext).folderDao().getById(idFolder);
        if (folder == null)
            throw new AssertionError("Could not find folder with id " + idFolder + " in DB");
        String title = folder.getName();

        // 3) click the specific item in the main list
        waitForViewVisible(ID_MAIN_RECYCLER, 5_000, "MainActivity not visible");

        myLog("DEBUG_VISUAL_CHECK - Waiting " + DEBUG_VISUAL_CHECK + " before clicking folder...");
        Thread.sleep(DEBUG_VISUAL_CHECK);

        // Resolve the just-imported folder's actual position by its stable DB id (via the
        // adapter's own findPositionByFolderId()), instead of assuming it's always at position 0.
        // Position 0 used to assume the default "last played" sort always puts the just-imported
        // book on top - but importing sets lLastAccess once at import time, while *playing* the
        // PREVIOUS book in this same loop (including its background progress-save updates) can
        // keep bumping that older book's lLastAccess past this import's timestamp, leaving the
        // previous book pinned at position 0 and silently re-testing it instead. Matching by
        // title text instead would be ambiguous whenever a same-named fixture gets imported more
        // than once (each run creates a new row with its own id and date_added) - the id is the
        // only unambiguous handle to the exact row this import just created, regardless of
        // whatever sort mode/direction/timing is currently in effect.
        int position = findFolderPosition(idFolder);
        if (position < 0) {
            throw new AssertionError("Folder id=" + idFolder + " (\"" + title
                    + "\") not found in MainActivity's adapter - it may not have finished binding yet.");
        }

        onView(withId(ID_MAIN_RECYCLER))
                .perform(RecyclerViewActions.actionOnItemAtPosition(position, click()));
        myLog("Clicked targeted item: " + title + " (position " + position + ")");
        TestNavUtils.sleep(300);

        // 4) wait until we land on either PlayActivity or ZikFileActivity
        TestNavUtils.assertWaitForAnyActivity(5_000, PlayActivity.class, ZikFileActivity.class);

        if (TestNavUtils.isOn(PlayActivity.class)) {
            myLog("Landed directly on PlayActivity");
            runPlay(playTime);
            return;
        }

        // 5) intermediate screen: pick a random track, then confirm playback actually started
        if (TestNavUtils.isOn(ZikFileActivity.class)) {
            myLog("On ZikFileActivity → will click a random track");
            clickRandomItemInRecycler(ID_TRACKS_RECYCLER);
            // Whether this navigates to PlayActivity depends on the user's own
            // Option.getOpenPlayActivity() preference (see StartPlayHelper.onZikFileClick(),
            // which unconditionally starts MediaService playback but only opens PlayActivity
            // when that option, sameTrack, or TTS applies) - with it off, clicking a track starts
            // real playback while deliberately staying on ZikFileActivity. So don't assert on
            // which Activity ends up resumed here; runPlay() below verifies actual playback state
            // instead, which is correct either way.
            runPlay(playTime);
            return;
        }

        throw new AssertionError("Unexpected navigation: neither PlayActivity nor ZikFileActivity is RESUMED.");
    }

    /**
     * Resolves idFolder's current position in MainActivity's folder list via the adapter's own
     * stable-id lookup ({@link FoldersRVAdapter#findPositionByFolderId}) - see the comment at its
     * call site in {@link #openTargetedItemThenPlay} for why position/title matching is unsafe
     * here. Returns -1 if MainActivity isn't the current activity, or the id isn't in the list.
     */
    private int findFolderPosition(long idFolder) {
        final int[] result = { -1 };
        Activity a = TestNavUtils.getCurrentResumedActivity();
        if (!(a instanceof MainActivity))
            return -1;
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            RecyclerView rv = a.findViewById(ID_MAIN_RECYCLER);
            if (rv != null && rv.getAdapter() instanceof FoldersRVAdapter) {
                result[0] = ((FoldersRVAdapter) rv.getAdapter()).findPositionByFolderId(idFolder);
            }
        });
        return result[0];
    }

    private void runPlay(long playTime) throws InterruptedException {
        waitForPlaybackToStart(TIMEOUT_VISUAL_CHECK + 5_000);
        sleep(playTime, "PLAY TIME");
        PlayList pl = PlayList.getInstance();
        if (pl != null && pl.getZikFile() != null) {
            String newPlayedSong = pl.getZikFile().getFolderName() + " / " + pl.getZikFile().getDisplayName();
            logFinalPlayMsg.append("\nPlay: [").append(pl.getZikFile().getDisplayName()).append("] from [")
                    .append(pl.getZikFile().getFolderName()).append("]");
            if (lastPlayedSong.equals(newPlayedSong)) {
                throw new AssertionError("Tried to play the same song : [" + newPlayedSong
                        + "]\nSo import did not work : [" + lastImport + "]");
            }
            myLog("played track :" + newPlayedSong);
            lastPlayedSong = newPlayedSong;
        } else {
            throw new AssertionError("Playlist not properly instantiated");
        }
        nbPlayed += 1;
        myLog("Confirmed playback n°" + nbPlayed);
        if (TestNavUtils.isOn(PlayActivity.class)) {
            // Also exercise the on-screen play/pause control when it's actually there - real
            // playback is already confirmed above via PlayList regardless of this.
            pressPlay();
        } else {
            myLog("Not on PlayActivity (Option.getOpenPlayActivity() is off) - real playback "
                    + "already confirmed via PlaybackUiState/PlayList, skipping the on-screen "
                    + "play/pause button check since that control isn't on screen here.");
        }
        sleep(1_000, "END PLAY");
        TestNavUtils.pressBackTo(MainActivity.class, 3, 1_000);
    }

    /**
     * Waits for MediaService to actually start playing (PlaybackUiState.playing == true), rather
     * than assuming any particular Activity is on screen - see the comment at this method's call
     * site in openTargetedItemThenPlay() for why a screen transition isn't a reliable signal here.
     */
    private void waitForPlaybackToStart(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            PlaybackUiState state = PlaybackUiBus.get().state().getValue();
            if (state != null && state.playing) {
                myLog("Playback confirmed started: " + state);
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Playback never started (PlaybackUiState.playing never became true) within "
                + timeoutMs + "ms");
    }

    /** Clicks a random item in the given RecyclerView (by id). */
    private void clickRandomItemInRecycler(@IdRes int recyclerId) throws InterruptedException {
        waitForViewVisible(recyclerId, 5_000, "Recycler view not visible: " + recyclerId);
        int count = getRecyclerItemCount(recyclerId);
        if (count <= 0)
            throw new AssertionError("Recycler has no items to click (id=" + recyclerId + ")");
        int index = (int) (Math.random() * count);
        myLog("Clicking item index " + (index + 1) + " / " + count);

        myLog("DEBUG_VISUAL_CHECK - Waiting " + DEBUG_VISUAL_CHECK + " before clicking track...");
        Thread.sleep(DEBUG_VISUAL_CHECK);

        onView(withId(recyclerId))
                .perform(RecyclerViewActions.actionOnItemAtPosition(index, click()));
        TestNavUtils.sleep(200);
    }

    private void pressPlay() {
        // settle a moment for the button to appear
        TestNavUtils.sleep(200);
        try {
            waitForViewVisible(ID_PLAY_BUTTON, 2_000, "Play button not visible by id");

            myLog("DEBUG_VISUAL_CHECK - Waiting " + DEBUG_VISUAL_CHECK + " before clicking Play...");
            Thread.sleep(DEBUG_VISUAL_CHECK);

            onView(withId(ID_PLAY_BUTTON)).perform(click());
            myLog("Pressed on-screen Play button");
            return;
        } catch (Exception ignored) {
        }
        throw new AssertionError("Could not find a Play control (id nor text).");
    }

}
