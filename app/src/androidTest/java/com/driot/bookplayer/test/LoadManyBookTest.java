package com.driot.bookplayer.test;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.swipeUp;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.driot.bookplayer.testutil.TestNavUtils.getRecyclerItemCount;
import static com.driot.bookplayer.testutil.TestNavUtils.sleep;
import static com.driot.bookplayer.testutil.TestNavUtils.waitForViewVisible;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.IdRes;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.Configuration;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.driot.bookplayer.BuildConfig;
import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.GetActivity;
import com.driot.bookplayer.imports.ImportBookSingleActivity;
import com.driot.bookplayer.activities.MainActivity;
import com.driot.bookplayer.imports.OngoingTaskUiState;
import com.driot.bookplayer.player.PlayActivity;
import com.driot.bookplayer.activities.ZikFileActivity;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.imports.ImportHelper;
import com.driot.bookplayer.player.PlayList;
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
    private final static long TIMEOUT_TEST_END = 1 * 60_000;
    private final static long TIMEOUT_BOOK_LOAD = 120_000;
    private final static long TIMEOUT_VISUAL_CHECK = 3_000;

    private static final int ID_MAIN_RECYCLER = R.id.recyclerview_folders; // list on MainActivity
    private static final int ID_TRACKS_RECYCLER = R.id.recyclerview_zikfiles; // list on ZikFileActivity
    private static final int ID_PLAY_BUTTON = R.id.ibPlayPause; // play button on PlayActivity
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
            new TestCase("File", "fixtures/zip"), new TestCase("File", "fixtures/ebooks"),
            new TestCase("Folder", "fixtures/folders"), new TestCase("File", "fixtures/m4b"),
            new TestCase("File", "fixtures/single_files"));

    private ImportProbe importProbe;

    private String lastPlayedSong = "init no song";

    // Launches MainActivity before each test
    @Rule
    public ActivityScenarioRule<MainActivity> activityRule = new ActivityScenarioRule<>(MainActivity.class);

    StringBuilder logFinalImportMsg;
    StringBuilder logFinalPlayMsg;
    int nbPlayed = 0;
    int nbImported = 0;
    String lastImport;

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
        Option.setUseSdCard(true);

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

        Context testContext = InstrumentationRegistry.getInstrumentation().getContext(); // test APK
        logFinalImportMsg = new StringBuilder(
                "--------------------------\n--------------------------\nFinal Import Message\n--------------------------");
        logFinalPlayMsg = new StringBuilder(
                "--------------------------\n--------------------------\nFinal Play Message\n--------------------------");

        // TODO Should not be used, hide potential user errors, check other test classes
        ImportHelper.cancelCurrentImport(appContext);

        // sanity log to prove assets are visible
        String[] root = testContext.getAssets().list("");
        myLog("test assets root size = " + (root == null ? -1 : root.length));
        assert root != null;
        assert root.length > 0; // throw new AssertionError(...

        // Clean staging dir for a fresh run
        File stagingRoot = new File(appContext.getCacheDir(), "fixtures");
        deleteQuiet(stagingRoot);
        // noinspection ResultOfMethodCallIgnored
        stagingRoot.mkdirs();

        myLogI("--------------------------------------------------------------------------------------------------------------------------------------");
        myLogI("---------------------------------------- ooooooooooooooooooooooo ---------------------------------------------------------------------");
        myLogI("--------------------------------------------------------------------------------------------------------------------------------------");
        for (TestCase tc : TESTS) {
            myLog(tc.uri_type + " - " + tc.assetFolderPath);
        }

        for (TestCase tc : TESTS) {
            List<String> assetFiles = listAssetFilesRecursively(testContext.getAssets(), tc.assetFolderPath); // <-- use
                                                                                                              // testContext
            myLogI("--------------------------------------------------------------------------------------------------------------------------------------");
            myLogI("---------------------------------------- ooooooooooooooooooooooo ---------------------------------------------------------------------");
            myLogI("--------------------------------------------------------------------------------------------------------------------------------------");
            myLogI("         Import => " + String.format("TestCase '%s'-'%s' -> %d files", tc.uri_type,
                    tc.assetFolderPath, assetFiles.size()));
            myLogD("--------------------------------------------------");
            if ("Folder".equals(tc.uri_type)) {
                List<String> subdirs = listAssetSubdirectories(testContext.getAssets(), tc.assetFolderPath);
                myLog("Found " + subdirs.size() + " folders to import under " + tc.assetFolderPath);
                for (String assetDir : subdirs) {
                    Uri dirUri = stageAssetDirectoryAsFileUri(appContext, testContext, assetDir);
                    int idFolder = runImport(dirUri, tc.uri_type);
                    goPlay(idFolder);
                    if (DEBUG_MODE_NO_LOOP)
                        return;
                }
            } else {
                for (String assetPath : assetFiles) {
                    Uri contentUri = stageAssetAsContentUri(appContext, testContext, assetPath);
                    int idFolder = runImport(contentUri, tc.uri_type);
                    goPlay(idFolder);
                    if (DEBUG_MODE_NO_LOOP)
                        return;
                }
            }
            logFinalImportMsg.append("\n--------------------------");
            logFinalPlayMsg.append("\n--------------------------");
        }
        TestNavUtils.maybePressBackTo(MainActivity.class, 3, 1_000);
        waitForViewVisible(ID_MAIN_RECYCLER, 5_000, "MainActivity not visible");
        myLogI(nbImported + " books imported");
        myLogI(logFinalImportMsg.append("\n--------------------------").toString());
        myLogI(nbPlayed + " books played");
        myLogI(logFinalPlayMsg.append("\n--------------------------").toString());
        TestNavUtils.assertRecyclerItemCountEquals(ID_MAIN_RECYCLER, nbImported, 5_000,
                "Mismatch between nb of imported book, and nb of actually present books");
        myLog("nb Books imported =" + nbImported);
        TestNavUtils.sleep(TIMEOUT_TEST_END, "TEST END");
    }

    private void goPlay(int idFolder) {
        TestNavUtils.logCurrentActivity();
        TestNavUtils.maybePressBackTo(MainActivity.class, 3, 1_000);
        TestNavUtils.logCurrentActivity();
        openTargetedItemThenPlay(idFolder, PLAY_TIME);
    }

    /// -----------------------------------------------------------------------------------------------------------------------------------------
    /// -----------------------------------------------------------------------------------------------------------------------------------------
    /// -----------------------------------------------------------------------------------------------------------------------------------------

    // ---------- Helpers ----------

    private int runImport(Uri uri_content, String uri_type) throws InterruptedException {
        long lastTimestamp;
        lastImport = uri_content.getLastPathSegment();
        myLogD("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
        myLog("runImport " + uri_type + " : " + uri_content);
        myLog("runImport " + lastImport);
        myLogD("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");

        lastTimestamp = System.currentTimeMillis();
        nbImported += 1;

        importProbe = new ImportProbe(appContext);
        importProbe.start();

        try {
            appContext.startActivity(new Intent(appContext, ImportBookSingleActivity.class)
                    .putExtra(ImportBookSingleActivity.EXTRA_URI, uri_content)
                    .putExtra(ImportBookSingleActivity.EXTRA_TYPE, uri_type)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                            | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION));
            myLog("ImportBookSingleActivity launched");
            TestNavUtils.assertWaitForActivity(ImportBookSingleActivity.class, 1_000, "arfff");
            myLogD("ok, on ImportBookSingleActivity");

            onView(withId(android.R.id.content)).perform(swipeUp());
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
                throw new AssertionError("Timeout after " + TIMEOUT_BOOK_LOAD / 1000
                        + "s waiting for import. Last progress: " + lastProgress);
            }

            if (!Var.IMPORT_STATUS_SUCCEEDED.equals(terminal.status)) {
                String devErr = terminal.errorText != null ? terminal.errorText : "(no errorText)";
                String warn = terminal.warningText != null ? terminal.warningText : "";
                throw new AssertionError("Import failed according to Probe.\n" +
                        "Status: " + terminal.status + "\n" +
                        "Title: " + terminal.title + "\n" +
                        "Progress: " + terminal.progressPercent + "% - " + terminal.progressText + "\n" +
                        "Error: " + devErr + "\n" +
                        "Warnings: " + warn);
            }

            // SUCCESS path continues below...
            myLog("Success book load (via probe)");

            // --- DB Reality Check ---
            AppDatabase db = AppDatabase.getInstance(appContext);
            ImportJob job = db.importJobDao().getUniqueJob();
            if (job == null)
                throw new AssertionError("ImportJob not found in DB after success");
            myLogI("DB Reality Check: ImportJob status=" + job.status + ", futureFolderPath=" + job.futureFolderPath);

            if (!Var.IMPORT_STATUS_SUCCEEDED.equals(job.status)) {
                throw new AssertionError(
                        "DB Reality Check failed: ImportJob status is " + job.status + " but probe said SUCCEEDED");
            }

            Folder folder = db.folderDao().getFolderByPath(job.futureFolderPath);
            if (folder == null) {
                // Try to find it by name as fallback or if path is absolute vs relative
                folder = db.folderDao().getByName(job.title);
                if (folder == null) {
                    throw new AssertionError(
                            "Folder not found in DB for path: " + job.futureFolderPath + " or title: " + job.title);
                }
            }
            int idFolder = folder.getId();
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
            myLogI("Import n°" + nbImported + ": Duration: " + logDuration);
            String newLineMsg = "\n" + logDuration;

            // log warnings
            String txtWarnings = terminal.warningText;
            if (txtWarnings != null)
                newLineMsg = newLineMsg + "\ndisplayed warnings : \n" + txtWarnings;
            logFinalImportMsg.append(newLineMsg);

            TestNavUtils.sleep(TIMEOUT_VISUAL_CHECK, "Visual Check");

            return idFolder;
        } finally {
            if (importProbe != null)
                importProbe.stop();
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
            if (list == null)
                continue;
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

    /**
     * Copy an asset into cache/fixtures and return a FileProvider content:// Uri.
     */
    private static Uri stageAssetAsContentUri(Context appCtx, Context testCtx, String assetPath) throws IOException {
        File stagingRoot = new File(appCtx.getCacheDir(), "fixtures");
        File outFile = new File(stagingRoot, assetPath);
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists())
            parent.mkdirs();

        if (!outFile.exists()) {
            try (InputStream in = testCtx.getAssets().open(assetPath); // <-- testCtx here
                    FileOutputStream out = new FileOutputStream(outFile)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) >= 0)
                    out.write(buf, 0, n);
            }
        }

        // authority must match your manifest ("${applicationId}.FileProvider")
        String authority = BuildConfig.APPLICATION_ID + ".FileProvider";
        return FileProvider.getUriForFile(appCtx, authority, outFile);
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

    /** Return full asset paths for direct subdirectories of `root` (no files). */
    private static List<String> listAssetSubdirectories(AssetManager am, String root) throws IOException {
        String normalized = root.endsWith("/") ? root.substring(0, root.length() - 1) : root;
        List<String> out = new ArrayList<>();
        String[] children = am.list(normalized);
        if (children == null)
            return out;
        for (String name : children) {
            String child = normalized + "/" + name;
            String[] nested = am.list(child);
            if (nested != null && nested.length > 0) { // directory in assets
                out.add(child);
            }
        }
        return out;
    }

    /**
     * Copy an entire asset directory tree to cache/fixtures and return a file://
     * Uri to the dir.
     */
    private static Uri stageAssetDirectoryAsFileUri(Context appCtx, Context testCtx, String assetDirPath)
            throws IOException {
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
        if (list == null)
            return;
        for (String name : list) {
            String childAssetPath = assetDir + "/" + name;
            String[] nested = am.list(childAssetPath);
            if (nested != null && nested.length > 0) {
                // directory
                copyAssetDirRecursively(am, childAssetPath, new File(destDir, name));
            } else {
                // file
                File outFile = new File(destDir, name);
                if (!outFile.getParentFile().exists())
                    outFile.getParentFile().mkdirs();
                try (InputStream in = am.open(childAssetPath);
                        FileOutputStream out = new FileOutputStream(outFile)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) >= 0)
                        out.write(buf, 0, n);
                }
            }
        }
    }

    /** Call this right after an import when you're back on MainActivity. */
    private void openTargetedItemThenPlay(int idFolder, long playTime) {
        // 1) ensure window focused before Espresso checks
        if (!TestNavUtils.waitForWindowFocus(2_000)) {
            throw new AssertionError("Window never gained focus before click.");
        }

        // 2) get the title of the folder from DB to find it in the list
        Folder folder = AppDatabase.getInstance(appContext).folderDao().getById(idFolder);
        if (folder == null)
            throw new AssertionError("Could not find folder with id " + idFolder + " in DB");
        String title = folder.getName();

        // 3) click the specific item in the main list
        waitForViewVisible(ID_MAIN_RECYCLER, 5_000, "MainActivity not visible");
        onView(withId(ID_MAIN_RECYCLER))
                .perform(RecyclerViewActions.actionOnItem(hasDescendant(withText(title)), click()));
        myLog("Clicked targeted item: " + title);
        TestNavUtils.sleep(300);

        // 4) wait until we land on either PlayActivity or ZikFileActivity
        TestNavUtils.assertWaitForAnyActivity(5_000, PlayActivity.class, ZikFileActivity.class);

        if (TestNavUtils.isOn(PlayActivity.class)) {
            myLog("Landed directly on PlayActivity");
            runPlay(playTime);
            return;
        }

        // 5) intermediate screen: pick a random track, then expect PlayActivity
        if (TestNavUtils.isOn(ZikFileActivity.class)) {
            myLog("On ZikFileActivity → will click a random track");
            clickRandomItemInRecycler(ID_TRACKS_RECYCLER);
            TestNavUtils.assertWaitForActivity(PlayActivity.class, 5_000,
                    "Expected PlayActivity after choosing a track");
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
        pressPlay();
        sleep(1_000, "END PLAY");
        TestNavUtils.pressBackTo(MainActivity.class, 3, 1_000);
    }

    /** Clicks a random item in the given RecyclerView (by id). */
    private void clickRandomItemInRecycler(@IdRes int recyclerId) {
        waitForViewVisible(recyclerId, 5_000, "Recycler view not visible: " + recyclerId);
        int count = getRecyclerItemCount(recyclerId);
        if (count <= 0)
            throw new AssertionError("Recycler has no items to click (id=" + recyclerId + ")");
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
            nbPlayed += 1;
            myLog("Pressed Play n°" + nbPlayed);
            return;
        } catch (Exception ignored) {
        }
        throw new AssertionError("Could not find a Play control (id nor text).");
    }

}
