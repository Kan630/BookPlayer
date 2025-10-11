package com.driot.bookplayer.test;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.swipeUp;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.driot.bookplayer.testutil.TestNavUtils.getRecyclerItemCount;
import static com.driot.bookplayer.testutil.TestNavUtils.isOn;
import static com.driot.bookplayer.testutil.TestNavUtils.sleep;
import static com.driot.bookplayer.testutil.TestNavUtils.waitForTextVisible;
import static com.driot.bookplayer.testutil.TestNavUtils.waitForViewVisible;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.IdRes;
import androidx.lifecycle.Observer;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.Configuration;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.driot.bookplayer.BuildConfig;
import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.AddResourceActivity;
import com.driot.bookplayer.activities.GetActivity;
import com.driot.bookplayer.activities.LoadBookActivity;
import com.driot.bookplayer.activities.MainActivity;
import com.driot.bookplayer.activities.PlayActivity;
import com.driot.bookplayer.activities.ZikFileActivity;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.objects.TaskStateRepository;
import com.driot.bookplayer.objects.TaskUiState;
import com.driot.bookplayer.player.PlayList;
import com.driot.bookplayer.services.BookLoadingWorkLauncher;
import com.driot.bookplayer.testutil.LogSupport;
import com.driot.bookplayer.testutil.LoggingWatcher;
import com.driot.bookplayer.testutil.TestNavUtils;
import com.driot.bookplayer.testutil.TaskStateTestProbe;
import com.driot.bookplayer.utils.KanLogger;
import com.driot.bookplayer.utils.Tonio;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executors;

import androidx.core.content.FileProvider;

public class LoadManyBookTest implements LogSupport {

    private Context appContext;

    private final static boolean DEBUG_MODE_NO_LOOP = false;
    private final static long TIMEOUT_TEST_END = 1*60_000;
    private final static long TIMEOUT_BOOK_LOAD = 120_000;
    private final static long TIMEOUT_VISUAL_CHECK = 3_000;

    private static final int ID_MAIN_RECYCLER   = R.id.recyclerview_folders;   // list on MainActivity
    private static final int ID_TRACKS_RECYCLER = R.id.recyclerview_zikfiles; // list on ZikFileActivity
    private static final int ID_PLAY_BUTTON     = R.id.ibPlayPause;        // play button on PlayActivity
    private static final String PLAY_TEXT_FALLBACK = "PLAY";           // fallback text if no id
    private final static long PLAY_TIME = 3_000;

    private static final class TestCase {
        final String uri_type;
        final String assetFolderPath; // e.g. "fixtures/m4b/"

        TestCase(String uri_type, String assetFolderPath) {
            this.uri_type = uri_type;
            this.assetFolderPath = assetFolderPath.endsWith("/") ? assetFolderPath : (assetFolderPath + "/");
        }
    }
    private static final List<TestCase> TESTS = Arrays.asList(
            new TestCase("File", "fixtures/zip")
            ,new TestCase("File", "fixtures/ebooks")
            ,new TestCase("Folder", "fixtures/folders")
            ,new TestCase("File", "fixtures/m4b")
    );

    private TaskStateTestProbe probe;
    private Observer<TaskUiState> stateObs;

    private String lastPlayedSong = "init no song";

    @Rule
    public ActivityScenarioRule<MainActivity> activityRule = new ActivityScenarioRule<>(MainActivity.class);

    @Rule
    public LoggingWatcher logs = new LoggingWatcher();

    StringBuilder logFinalMessage;

    @Before
    public void setUp() {
        myLog("ooooooooooooooooooooooooooooooooooooooooo");
        myLog("----------------- setUp -----------------");
        myLog("ooooooooooooooooooooooooooooooooooooooooo");

        appContext = ApplicationProvider.getApplicationContext();
        KanLogger.init(appContext);
        Option.setTechLog(true);

        Option.setCopyFile(false);
        Option.setUseSdCard(true);

        Configuration config = new Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .setExecutor(Executors.newSingleThreadExecutor()) //if not worker on main UI => not allowed...
                .setTaskExecutor(new SynchronousExecutor())
                .build();
        WorkManagerTestInitHelper.initializeTestWorkManager(appContext, config);

        TestNavUtils.logCurrentActivity();
        if (TestNavUtils.getCurrentResumedActivity() instanceof GetActivity) {
            myLogW("On GetActivity, pressing back to reach MainActivity…");
            boolean ok = TestNavUtils.pressBackTo(MainActivity.class, 3, 1000);
            if (!ok) throw new AssertionError("Could not navigate back to MainActivity");
            TestNavUtils.logCurrentActivity();
        }
    }


    @Test
    public void loadManyBooks() throws Exception {
        myLog("loadManyBooks");
        int nbBooks = 0;

        Context testContext = InstrumentationRegistry.getInstrumentation().getContext(); // test APK
        logFinalMessage = new StringBuilder("--------------------------\n--------------------------\nFinal Message\n--------------------------");

        // sanity log to prove assets are visible
        String[] root = testContext.getAssets().list("");
        myLog("test assets root size = " + (root == null ? -1 : root.length));
        assert root != null;
        assert root.length>0;  //throw new AssertionError(...

        // Clean staging dir for a fresh run
        File stagingRoot = new File(appContext.getCacheDir(), "fixtures");
        deleteQuiet(stagingRoot);
        //noinspection ResultOfMethodCallIgnored
        stagingRoot.mkdirs();


        for (TestCase tc : TESTS) {
            List<String> assetFiles = listAssetFilesRecursively(testContext.getAssets(), tc.assetFolderPath); // <-- use testContext
            myLogD("--------------------------------------------------");
            myLog(String.format("TestCase '%s'-'%s' -> %d files", tc.uri_type, tc.assetFolderPath, assetFiles.size()));
            myLogD("--------------------------------------------------");
            if ("Folder".equals(tc.uri_type)) {
                List<String> subdirs = listAssetSubdirectories(testContext.getAssets(), tc.assetFolderPath);
                myLog("Found " + subdirs.size() + " folders to import under " + tc.assetFolderPath);
                for (String assetDir : subdirs) {
                    Uri dirUri = stageAssetDirectoryAsFileUri(appContext, testContext, assetDir);
                    nbBooks += 1;
                    runImport(dirUri, tc.uri_type);
                    goPlay();
                    if (DEBUG_MODE_NO_LOOP) return;
                }
            } else {
                for (String assetPath : assetFiles) {
                    Uri contentUri = stageAssetAsContentUri(appContext, testContext, assetPath);
                    nbBooks += 1;
                    runImport(contentUri, tc.uri_type);
                    goPlay();
                    if (DEBUG_MODE_NO_LOOP) return;
                }
            }
            logFinalMessage.append("\n--------------------------");
        }
        if (!isOn(MainActivity.class)) {
            myLogW("going back to MainActivity");
            TestNavUtils.pressBackTo(MainActivity.class,3, 1_000);
        }
        waitForViewVisible(ID_MAIN_RECYCLER, 5_000, "MainActivity not visible");
        myLogI(logFinalMessage.append("\n--------------------------").toString());
        TestNavUtils.assertRecyclerItemCountEquals(ID_MAIN_RECYCLER, nbBooks, 5_000, "Mismatch between nb of imported book, and nb of actually present books");
        myLog("nb Books imported =" + nbBooks);
        TestNavUtils.sleep(TIMEOUT_TEST_END, "TEST END");
    }

    private void goPlay() {
        TestNavUtils.logCurrentActivity();
        if (!isOn(MainActivity.class)) {
            myLogW("going back to MainActivity");
            TestNavUtils.pressBackTo(MainActivity.class,3, 1_000);
        }
        TestNavUtils.logCurrentActivity();
        openFirstItemThenPlay(PLAY_TIME);
    }




    // ---------- Helpers ----------



    private void runImport(Uri uri_content, String uri_type) throws InterruptedException {
        long lastTimestamp;

        myLogD("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
        myLog("loading " + uri_type + " : " + uri_content);
        myLogD("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
        lastTimestamp = System.currentTimeMillis();
        String txtWarnings = null;

        // --- Start a fresh probe + attach a verbose state logger
        probe = new TaskStateTestProbe();
        probe.start();

        stateObs = s -> {
            if (s == null) return;
            /*
            myLogD("[TaskState] running=" + s.running +
                    " paused=" + s.paused +
                    " finished=" + s.finished +
                    " pauseAvail=" + s.pauseAvailable +
                    " title='" + s.title + "'" +
                    " progress=" + s.progressPercent +
                    " text='" + s.progressText + "'" +
                    (s.warningText != null ? " warn='" + s.warningText + "'" : "") +
                    (s.errorText != null ? " error='" + s.errorText + "'" : ""));

             */
        };

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            TaskStateRepository.get().hydrateFromPrefs();
            TaskStateRepository.get().resetToIdle();
            TaskStateRepository.get().state().observeForever(stateObs);
        });

        try {
            appContext.startActivity(new Intent(appContext, LoadBookActivity.class)
                    .putExtra(LoadBookActivity.EXTRA_URI, uri_content)
                    .putExtra(LoadBookActivity.EXTRA_TYPE, uri_type)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION));
            myLog("LoadBookActivity launched");
            TestNavUtils.assertWaitForActivity(LoadBookActivity.class, 1_000, "arfff");
            myLogD("ok, on LoadBookActivity");

            onView(withId(android.R.id.content)).perform(swipeUp());
            onView(withId(R.id.btnConfirm)).perform(click());

            BookLoadingWorkLauncher.launch(appContext);

            appContext.startActivity(new Intent(appContext, AddResourceActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            myLog("AddResourceActivity launched");
            TestNavUtils.assertWaitForActivity(AddResourceActivity.class, 1_000, "gizmo");
            myLogD("ok, on AddResourceActivity");

            // --- Wait for the task to finish (success OR failure) ---
            probe.await(TIMEOUT_BOOK_LOAD);

            long deadline = System.currentTimeMillis() + TIMEOUT_BOOK_LOAD;
            boolean done = false;

            while (System.currentTimeMillis() < deadline) {
                // 1) fail fast if app signaled a failure
                if (probe.isFailed()) {
                    TaskStateTestProbe.Outcome out = probe.await(10); // quick drain to capture final error
                    throw new AssertionError("Import failed: " + out.errorText + " | progress='" + out.progressText + "'");
                }

                // 2) success if app signaled finished success
                if (probe.isSuccess()) {
                    done = true;
                    myLog("success book load (via probe)");
                    break;
                }

                // 3) legacy success heuristics (like before you added the probe)
                if (TestNavUtils.isOn(GetActivity.class) || TestNavUtils.isOn(MainActivity.class)) {
                    done = true;
                    myLog("success book load (via navigation)");
                    TestNavUtils.logCurrentActivity();
                    break;
                }

                if (TestNavUtils.isOn(AddResourceActivity.class) && TestNavUtils.isTextVisible("EXIT")) {
                    TestNavUtils.logCurrentActivity();
                    onView(withText("EXIT")).perform(click());
                    myLogW("EXIT button clicked (...some warnings displayed ?)");
                    done = true;
                    break;
                }

                Thread.sleep(100);
            }

            if (!done) {
                TaskUiState s = probe.lastState();
                String lastProgress = (s == null || s.progressText == null) ? "" : s.progressText;
                Activity a = TestNavUtils.getCurrentResumedActivity();
                String where = (a == null) ? "none" : a.getClass().getSimpleName();
                throw new AssertionError(
                        "Timeout " + TIMEOUT_BOOK_LOAD / 1000 + " sec. waiting for finish/navigation.\nCurrent activity: " + where +
                                " | last progress='" + lastProgress + "'"
                );
            }
            TestNavUtils.logCurrentActivity();

            // --- Duration log (robust name from URI) ---
            String duration = Tonio.formatMmSs(System.currentTimeMillis() - lastTimestamp);
            String baseFromPath = (uri_content.getPath() != null) ? Tonio.getFileNameFromPath(uri_content.getPath()) : null;
            String baseFromSeg = (uri_content.getLastPathSegment() != null) ? uri_content.getLastPathSegment() : null;
            String targetName = (baseFromPath != null && !baseFromPath.isEmpty()) ? baseFromPath :
                    (baseFromSeg != null ? baseFromSeg : uri_content.toString());

            String logDuration = duration + "  " + targetName;
            myLogI("Import Duration: " + logDuration);
            String newLineMsg = "\n" + logDuration;

            try {
                txtWarnings = TaskStateRepository.get().state().getValue().warningText;
            } catch (Throwable ignored) {}
            if (txtWarnings != null) newLineMsg = newLineMsg + "\ndisplayed warnings : \n" + txtWarnings;

            logFinalMessage.append(newLineMsg);

            TestNavUtils.sleep(TIMEOUT_VISUAL_CHECK, "Visual Check");

        } catch (Exception e) {
            throw new AssertionError("Import failed: " + e.getMessage());
        } finally {
            // --- Always detach the observer & stop the probe to avoid leaks / cross-test noise ---
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                if (stateObs != null) {
                    TaskStateRepository.get().state().removeObserver(stateObs);
                }
            });
            if (probe != null) probe.stop();
        }
    }




    private static List<String> listAssetFilesRecursively(AssetManager am, String root) throws IOException {
        List<String> out = new ArrayList<>();
        Deque<String> stack = new ArrayDeque<>();
        String normalizedRoot = root.endsWith("/") ? root.substring(0, root.length() - 1) : root;
        stack.push(normalizedRoot);

        while (!stack.isEmpty()) {
            String dir = stack.pop();
            String[] list = am.list(dir);
            if (list == null) continue;
            for (String name : list) {
                String child = dir + "/" + name;
                String[] nested = am.list(child);
                if (nested != null && nested.length > 0) {
                    stack.push(child);
                } else {
                    out.add(child); // file
                }
            }
        }
        return out;
    }

    /** Copy an asset into cache/fixtures and return a FileProvider content:// Uri. */
    private static Uri stageAssetAsContentUri(Context appCtx, Context testCtx, String assetPath) throws IOException {
        File stagingRoot = new File(appCtx.getCacheDir(), "fixtures");
        File outFile = new File(stagingRoot, assetPath);
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        if (!outFile.exists()) {
            try (InputStream in = testCtx.getAssets().open(assetPath);  // <-- testCtx here
                 FileOutputStream out = new FileOutputStream(outFile)) {
                byte[] buf = new byte[8192];
                int n; while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            }
        }

        // authority must match your manifest ("${applicationId}.FileProvider")
        String authority = BuildConfig.APPLICATION_ID + ".FileProvider";
        return FileProvider.getUriForFile(appCtx, authority, outFile);
    }


    private static void deleteQuiet(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteQuiet(k);
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    /** Return full asset paths for direct subdirectories of `root` (no files). */
    private static List<String> listAssetSubdirectories(AssetManager am, String root) throws IOException {
        String normalized = root.endsWith("/") ? root.substring(0, root.length() - 1) : root;
        List<String> out = new ArrayList<>();
        String[] children = am.list(normalized);
        if (children == null) return out;
        for (String name : children) {
            String child = normalized + "/" + name;
            String[] nested = am.list(child);
            if (nested != null && nested.length > 0) { // directory in assets
                out.add(child);
            }
        }
        return out;
    }

    /** Copy an entire asset directory tree to cache/fixtures and return a file:// Uri to the dir. */
    private static Uri stageAssetDirectoryAsFileUri(Context appCtx, Context testCtx, String assetDirPath) throws IOException {
        File stagingRoot = new File(appCtx.getCacheDir(), "fixtures");
        File outDir = new File(stagingRoot, assetDirPath);
        copyAssetDirRecursively(testCtx.getAssets(), assetDirPath, outDir);
        return Uri.fromFile(outDir); // same-app -> file:// OK
    }

    /** Recursive copy of an assets directory to a real filesystem directory. */
    private static void copyAssetDirRecursively(AssetManager am, String assetDir, File destDir) throws IOException {
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw new IOException("Failed to create dir: " + destDir);
        }
        String[] list = am.list(assetDir);
        if (list == null) return;
        for (String name : list) {
            String childAssetPath = assetDir + "/" + name;
            String[] nested = am.list(childAssetPath);
            if (nested != null && nested.length > 0) {
                // directory
                copyAssetDirRecursively(am, childAssetPath, new File(destDir, name));
            } else {
                // file
                File outFile = new File(destDir, name);
                if (!outFile.getParentFile().exists()) outFile.getParentFile().mkdirs();
                try (InputStream in = am.open(childAssetPath);
                     FileOutputStream out = new FileOutputStream(outFile)) {
                    byte[] buf = new byte[8192];
                    int n; while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
                }
            }
        }
    }
    /** Call this right after an import when you're back on MainActivity. */
    private void openFirstItemThenPlay(long playTime) {
        // 1) ensure window focused before Espresso checks
        if (!TestNavUtils.waitForWindowFocus(2_000)) {
            throw new AssertionError("Window never gained focus before click.");
        }

        // 2) click first item in the main list
        waitForViewVisible(ID_MAIN_RECYCLER, 5_000, "MainActivity not visible");
        onView(withId(ID_MAIN_RECYCLER))
                .perform(RecyclerViewActions.actionOnItemAtPosition(0, click()));
        myLog("Clicked first item in main list");
        TestNavUtils.sleep(300);

        // 3) wait until we land on either PlayActivity or ZikFileActivity
        TestNavUtils.assertWaitForAnyActivity(5_000, PlayActivity.class, ZikFileActivity.class);

        if (TestNavUtils.isOn(PlayActivity.class)) {
            myLog("Landed directly on PlayActivity");
            runPlay(playTime);
            return;
        }

        // 4) intermediate screen: pick a random track, then expect PlayActivity
        if (TestNavUtils.isOn(ZikFileActivity.class)) {
            myLog("On ZikFileActivity → will click a random track");
            clickRandomItemInRecycler(ID_TRACKS_RECYCLER);
            TestNavUtils.assertWaitForActivity(PlayActivity.class, 5_000, "Expected PlayActivity after choosing a track");
            runPlay(playTime);
            return;
        }

        throw new AssertionError("Unexpected navigation: neither PlayActivity nor ZikFileActivity is RESUMED.");
    }

    private void runPlay(long playTime) {
        sleep(playTime, "PLAY TIME");
        PlayList pl = PlayList.getInstance();
        if (pl != null && pl.getZikFile() != null) {
            String newPlayedSong = pl.getZikFile().getFolderName() + " / " + pl.getZikFile().getDisplayName();
            if (lastPlayedSong.equals(newPlayedSong)) {
                throw new AssertionError("Tried to play the same song... So import did not work");
            }
            myLogI("played track :" + newPlayedSong);
            lastPlayedSong = newPlayedSong;
        } else {
            throw new AssertionError("Playlist not properly instantiated");
        }
        pressPlay();
        sleep(1_000, "END PLAY");
        TestNavUtils.pressBackTo(MainActivity.class,3, 1_000);
    }

    /** Clicks a random item in the given RecyclerView (by id). */
    private void clickRandomItemInRecycler(@IdRes int recyclerId) {
        waitForViewVisible(recyclerId, 5_000, "Recycler view not visible: " + recyclerId);
        int count = getRecyclerItemCount(recyclerId);
        if (count <= 0) throw new AssertionError("Recycler has no items to click (id=" + recyclerId + ")");
        int index = (int) (Math.random() * count);
        myLog("Clicking item index " + index + " / " + count);
        onView(withId(recyclerId))
                .perform(RecyclerViewActions.actionOnItemAtPosition(index, click()));
        TestNavUtils.sleep(200);
    }

    private void pressPlay() {
        // settle a moment for the button to appear
        TestNavUtils.sleep(200);

        try {
            waitForViewVisible(ID_PLAY_BUTTON, 2_000, "Play button not visible by id");
            onView(withId(ID_PLAY_BUTTON)).perform(click());
            myLog("Pressed Play via id");
            return;
        } catch (Exception ignored) {
            // fall back to a text-based control
        }

        //fallback
        try {
            myLogW("Using fallback for button PLAY");
            waitForTextVisible(PLAY_TEXT_FALLBACK, 2_000, "Play text control not visible");
            onView(withText(PLAY_TEXT_FALLBACK)).perform(click());
            myLog("Pressed Play via text");
            return;
        } catch (Exception ignored) {}

        throw new AssertionError("Could not find a Play control (id nor text).");
    }

}
