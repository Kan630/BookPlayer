package com.driot.bookplayer.test;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.action.ViewActions.swipeUp;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import static com.driot.bookplayer.testutil.ListTestParser.TestCase;
import static com.driot.bookplayer.testutil.ListTestParser.formatHeader;
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
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.imports.ImportJobDao;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.imports.ImportHelper;
import com.driot.bookplayer.imports.LoadBookActivity;
import com.driot.bookplayer.imports.TaskUiState;
import com.driot.bookplayer.player.PlayActivity;
import com.driot.bookplayer.player.PlayList;
import com.driot.bookplayer.testutil.ImportProbe;
import com.driot.bookplayer.testutil.ListTestParser;
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
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Instrumented test: opens app, imports books from fixtures, optionally plays
 * them.
 *
 * <p>
 * Two modes via instrumentation arg: -e MODE build | test
 * <ul>
 * <li>build: discover fixtures, import each, write LIST_TEST to filesDir (and
 * log). Copy output to app/src/androidTest/assets/LIST_TEST</li>
 * <li>test: read assets/LIST_TEST, run each case (import, assert nb tracks +
 * img, play one track)</li>
 * </ul>
 * If MODE=test and no LIST_TEST in assets, runs a simple flow: first file +
 * first folder, import+play each.
 */
public class ImportBookTest implements LogSupport {

    private static final String ASSET_LIST_TEST = "LIST_TEST";
    private static final long TIMEOUT_IMPORT = 120_000;
    private static final long PLAY_TIME_MS = 10_000;
    private static final int ID_MAIN_RECYCLER = R.id.recyclerview_folders;
    private static final int ID_TRACKS_RECYCLER = R.id.recyclerview_zikfiles;
    private static final int ID_PLAY_BUTTON = R.id.ibPlayPause;

    private static final String[] FILE_CANDIDATES = { "fixtures/ebooks", "fixtures/single_files", "fixtures/m4b",
            "fixtures/zip" };
    private static final String FOLDER_CANDIDATE = "fixtures/folders";

    private Context appContext;
    private Context testContext;
    private ImportProbe importProbe;

    @Rule
    public ActivityScenarioRule<MainActivity> activityRule = new ActivityScenarioRule<>(MainActivity.class);

    @Before
    public void setUp() {
        appContext = ApplicationProvider.getApplicationContext();
        testContext = InstrumentationRegistry.getInstrumentation().getContext();
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

    private static String getMode() {
        String m = InstrumentationRegistry.getArguments().getString("MODE", "test");
        return "build".equalsIgnoreCase(m) ? "build" : "test";
    }

    @Test
    public void openAppAndImportBook() throws Exception {
        String mode = getMode();

        if ("build".equals(mode)) {
            runBuildMode();
        } else {
            runTestMode();
        }
    }

    private void runBuildMode() throws Exception {
        List<TestCase> cases = new ArrayList<>();
        ensureStagingRoot();

        for (String dir : FILE_CANDIDATES) {
            List<String> files = listAssetFilesRecursively(testContext.getAssets(), dir);
            for (String assetPath : files) {
                importAndRecord(cases, "File", assetPath, null);
            }
        }

        List<String> folders = listAssetSubdirectories(testContext.getAssets(), FOLDER_CANDIDATE);
        for (String assetDir : folders) {
            importAndRecord(cases, "Folder", null, assetDir);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(formatHeader()).append("\n");
        for (TestCase tc : cases) {
            sb.append(tc.toString()).append("\n");
        }
        String content = sb.toString();

        File outFile = new File(appContext.getFilesDir(), "LIST_TEST");
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            fos.write(content.getBytes());
        }
        myLogI("LIST_TEST written to: " + outFile.getAbsolutePath());
        myLogI("=== LIST_TEST_CONTENT ===");
        myLogI(content);
        myLogI("=== END_LIST_TEST ===");
    }

    private void importAndRecord(List<TestCase> cases, String loadWay, String assetFilePath, String assetFolderPath)
            throws Exception {
        Uri uri;
        String filepath;
        if ("File".equals(loadWay)) {
            uri = stageAssetAsContentUri(appContext, testContext, assetFilePath);
            filepath = assetFilePath;
        } else {
            uri = stageAssetDirectoryAsContentUri(appContext, testContext, assetFolderPath);
            filepath = assetFolderPath;
        }

        boolean ok = runImport(uri, loadWay);
        if (!ok) {
            myLogW("Import failed for " + filepath + ", skipping");
            return;
        }

        Folder folder = getLastImportedFolder();
        if (folder == null) {
            myLogW("Could not get folder for " + filepath + ", skipping");
            return;
        }

        int nbTracks = (int) folder.nbZikFile;
        boolean hasImg = folder.image != null && !folder.image.isEmpty();
        cases.add(new TestCase(loadWay, filepath, nbTracks, hasImg));
        myLog("Recorded: " + loadWay + " " + filepath + " -> " + nbTracks + " tracks, img=" + hasImg);

        TestNavUtils.maybePressBackTo(MainActivity.class, 3, 2_000);
    }

    private void runTestMode() throws Exception {
        String listTestContent = readAssetAsString(ASSET_LIST_TEST);
        List<TestCase> cases;
        if (listTestContent != null && !listTestContent.isEmpty()) {
            cases = ListTestParser.parse(new StringReader(listTestContent));
        } else {
            cases = new ArrayList<>();
        }
        if (cases.isEmpty()) {
            cases = buildSimpleCases();
            myLog("No LIST_TEST cases (or empty), running simple flow: " + cases.size() + " cases");
        } else {
            myLog("Running " + cases.size() + " cases from LIST_TEST");
        }

        ensureStagingRoot();

        for (int i = 0; i < cases.size(); i++) {
            TestCase tc = cases.get(i);
            myLog("--- Case " + (i + 1) + "/" + cases.size() + ": " + tc.loadWay + " " + tc.filepath + " ---");

            Uri uri;
            if ("File".equals(tc.loadWay)) {
                uri = stageAssetAsContentUri(appContext, testContext, tc.filepath);
            } else {
                uri = stageAssetDirectoryAsContentUri(appContext, testContext, tc.filepath);
            }

            boolean ok = runImport(uri, tc.loadWay);
            if (!ok) {
                throw new AssertionError("Import failed for " + tc.filepath);
            }

            Folder folder = getLastImportedFolder();
            if (folder == null) {
                throw new AssertionError("Could not get folder after import: " + tc.filepath);
            }

            int nbTracks = (int) folder.nbZikFile;
            boolean hasImg = folder.image != null && !folder.image.isEmpty();

            if (tc.expectedNbTracks >= 0 && nbTracks != tc.expectedNbTracks) {
                throw new AssertionError("Nb tracks mismatch for " + tc.filepath + ": expected " + tc.expectedNbTracks
                        + ", got " + nbTracks);
            }
            if (tc.expectedNbTracks >= 0 && hasImg != tc.expectedImg) {
                throw new AssertionError(
                        "Img mismatch for " + tc.filepath + ": expected " + tc.expectedImg + ", got " + hasImg);
            }

            TestNavUtils.maybePressBackTo(MainActivity.class, 3, 2_000);
            waitForViewVisible(ID_MAIN_RECYCLER, 5_000, "MainActivity not visible");

            openItemThenPlayAndAssertProgress(i);
        }

        myLog("Test passed: all " + cases.size() + " cases OK");
        sleep(500, "Final");
    }

    private List<TestCase> buildSimpleCases() throws IOException {
        List<TestCase> cases = new ArrayList<>();
        String firstFile = findFirstFixtureFile(testContext.getAssets());
        if (firstFile != null) {
            cases.add(new TestCase("File", firstFile, -1, false)); // -1 = don't assert nb tracks in simple mode
        }
        List<String> folders = listAssetSubdirectories(testContext.getAssets(), FOLDER_CANDIDATE);
        if (!folders.isEmpty()) {
            cases.add(new TestCase("Folder", folders.get(0), -1, false));
        }
        if (cases.isEmpty()) {
            throw new AssertionError(
                    "No fixture found. Add files to fixtures/ebooks, fixtures/single_files, etc. or folders to fixtures/folders/");
        }
        return cases;
    }

    private void openItemThenPlayAndAssertProgress(int recyclerIndex) {
        if (!TestNavUtils.waitForWindowFocus(2_000)) {
            throw new AssertionError("Window never gained focus");
        }
        waitForViewVisible(ID_MAIN_RECYCLER, 5_000, "MainActivity not visible");
        int count = getRecyclerItemCount(ID_MAIN_RECYCLER);
        if (count <= recyclerIndex) {
            throw new AssertionError("Recycler has only " + count + " items, cannot open index " + recyclerIndex);
        }
        onView(withId(ID_MAIN_RECYCLER))
                .perform(RecyclerViewActions.actionOnItemAtPosition(recyclerIndex, click()));
        sleep(300);

        TestNavUtils.assertWaitForAnyActivity(5_000, PlayActivity.class, ZikFileActivity.class);

        if (TestNavUtils.isOn(PlayActivity.class)) {
            runPlayAndAssertProgressSaved();
            return;
        }
        if (TestNavUtils.isOn(ZikFileActivity.class)) {
            clickFirstTrack();
            TestNavUtils.assertWaitForActivity(PlayActivity.class, 5_000, "Expected PlayActivity after choosing track");
            runPlayAndAssertProgressSaved();
            return;
        }
        throw new AssertionError("Unexpected navigation");
    }

    private void clickFirstTrack() {
        waitForViewVisible(ID_TRACKS_RECYCLER, 5_000, "Tracks RecyclerView not visible");
        int count = getRecyclerItemCount(ID_TRACKS_RECYCLER);
        if (count <= 0)
            throw new AssertionError("No tracks to click");
        onView(withId(ID_TRACKS_RECYCLER))
                .perform(RecyclerViewActions.actionOnItemAtPosition(0, click()));
        sleep(200);
    }

    private void runPlayAndAssertProgressSaved() {
        pressPlay();

        // Wait for playback to actually start and duration to be available
        // The progress updater requires duration > 0 to save progress
        long startTime = System.currentTimeMillis();
        long timeout = 10_000; // 10 seconds max wait
        boolean durationAvailable = false;
        while (System.currentTimeMillis() - startTime < timeout) {
            PlayList pl = PlayList.getInstance();
            if (pl != null && pl.getZikFile() != null) {
                double duration = pl.getZikFile().getDuration();
                if (duration > 0) {
                    durationAvailable = true;
                    myLog("Duration available: " + duration + " ms");
                    break;
                }
            }
            sleep(200, "Waiting for duration");
        }
        if (!durationAvailable) {
            throw new AssertionError("Duration never became available after playback start");
        }

        // Now wait for playback time and progress save
        sleep(PLAY_TIME_MS, "Playback + progress save");

        // Wait a bit more to ensure async progress update completes
        sleep(1_500, "Wait for async progress update");

        PlayList pl = PlayList.getInstance();
        if (pl == null || pl.getZikFile() == null) {
            throw new AssertionError("PlayList not properly instantiated");
        }
        double position = pl.getZikFile().getPosition();
        if (position <= 0) {
            // Try refreshing from database as a fallback
            double dbPosition = refreshZikFileFromDb(pl.getZikFile().getId());
            if (dbPosition > 0) {
                myLog("Position from DB: " + dbPosition + " ms (in-memory was " + position + ")");
                position = dbPosition;
            } else {
                throw new AssertionError(
                        "Progress not saved: position=" + position + " (checked both in-memory and DB)");
            }
        }
        myLog("Progress saved: position=" + position + " ms");

        sleep(500, "Before back");
        TestNavUtils.pressBackTo(MainActivity.class, 3, 1_000);
    }

    private double refreshZikFileFromDb(int zikFileId) {
        final double[] result = { 0.0 };
        CountDownLatch done = new CountDownLatch(1);
        AppDatabase.databaseReadExecutor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(appContext);
                ZikFile zf = db.zikFileDao().getById(zikFileId);
                if (zf != null) {
                    result[0] = zf.getPosition();
                }
            } finally {
                done.countDown();
            }
        });
        try {
            done.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result[0];
    }

    private void pressPlay() {
        sleep(200);
        waitForViewVisible(ID_PLAY_BUTTON, 2_000, "Play button not visible");
        onView(withId(ID_PLAY_BUTTON)).perform(click());
        myLog("Pressed Play");
    }

    private boolean runImport(Uri uri, String loadWay) throws InterruptedException {
        importProbe = new ImportProbe(appContext);
        importProbe.start();
        try {
            appContext.startActivity(new Intent(appContext, LoadBookActivity.class)
                    .putExtra(LoadBookActivity.EXTRA_URI, uri)
                    .putExtra(LoadBookActivity.EXTRA_TYPE, loadWay)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                            | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION));

            TestNavUtils.assertWaitForActivity(LoadBookActivity.class, 5_000, "LoadBookActivity did not open");
            onView(withId(android.R.id.content)).perform(swipeUp());
            onView(withId(R.id.btnConfirm)).perform(scrollTo(), click());

            TaskUiState terminal = importProbe.await(TIMEOUT_IMPORT);
            if (terminal == null) {
                TaskUiState last = importProbe.lastState();
                String lastProgress = (last == null || last.progressText == null) ? "" : last.progressText;
                throw new AssertionError("Import timeout. Last progress: " + lastProgress);
            }
            if (Var.IMPORT_STATUS_FAILED.equals(terminal.status)) {
                String err = terminal.errorText != null ? terminal.errorText : "(no error text)";
                throw new AssertionError("Import failed: " + err);
            }
            if (Var.IMPORT_STATUS_CANCELLED.equals(terminal.status)) {
                throw new AssertionError("Import cancelled unexpectedly");
            }
            return true;
        } finally {
            if (importProbe != null)
                importProbe.stop();
        }
    }

    private Folder getLastImportedFolder() {
        final Folder[] result = { null };
        CountDownLatch done = new CountDownLatch(1);
        AppDatabase.databaseReadExecutor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(appContext);
                ImportJobDao dao = db.importJobDao();
                com.driot.bookplayer.imports.ImportJob job = dao.getUniqueJob();
                if (job != null && job.futureFolderPath != null && !job.futureFolderPath.isEmpty()) {
                    result[0] = db.folderDao().getFolderByPath(job.futureFolderPath);
                }
            } finally {
                done.countDown();
            }
        });
        try {
            done.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result[0];
    }

    private void ensureStagingRoot() {
        File stagingRoot = new File(appContext.getCacheDir(), "fixtures");
        deleteQuiet(stagingRoot);
        stagingRoot.mkdirs();
    }

    private static String readAssetAsString(String assetPath) {
        try {
            InputStream in = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(assetPath);
            byte[] buf = new byte[4096];
            StringBuilder sb = new StringBuilder();
            int n;
            while ((n = in.read(buf)) >= 0)
                sb.append(new String(buf, 0, n));
            in.close();
            return sb.toString();
        } catch (IOException e) {
            return null;
        }
    }

    private static String findFirstFixtureFile(AssetManager am) throws IOException {
        for (String dir : FILE_CANDIDATES) {
            String[] files = am.list(dir);
            if (files == null || files.length == 0)
                continue;
            for (String name : files) {
                String child = dir + "/" + name;
                String[] nested = am.list(child);
                if (nested == null || nested.length == 0)
                    return child;
            }
        }
        return null;
    }

    private static List<String> listAssetFilesRecursively(AssetManager am, String root) throws IOException {
        List<String> out = new ArrayList<>();
        Deque<String> stack = new java.util.ArrayDeque<>();
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
                if (nested != null && nested.length > 0)
                    stack.push(child);
                else
                    out.add(child);
            }
        }
        return out;
    }

    private static List<String> listAssetSubdirectories(AssetManager am, String root) throws IOException {
        String normalized = root.endsWith("/") ? root.substring(0, root.length() - 1) : root;
        List<String> out = new ArrayList<>();
        String[] children = am.list(normalized);
        if (children == null)
            return out;
        for (String name : children) {
            String child = normalized + "/" + name;
            String[] nested = am.list(child);
            if (nested != null && nested.length > 0)
                out.add(child);
        }
        return out;
    }

    private static Uri stageAssetAsContentUri(Context appCtx, Context testCtx, String assetPath) throws IOException {
        File stagingRoot = new File(appCtx.getCacheDir(), "fixtures");
        File outFile = new File(stagingRoot, assetPath);
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists())
            parent.mkdirs();
        try (InputStream in = testCtx.getAssets().open(assetPath);
                FileOutputStream out = new FileOutputStream(outFile)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0)
                out.write(buf, 0, n);
        }
        return FileProvider.getUriForFile(appCtx, BuildConfig.APPLICATION_ID + ".FileProvider", outFile);
    }

    private static Uri stageAssetDirectoryAsContentUri(Context appCtx, Context testCtx, String assetDirPath)
            throws IOException {
        File stagingRoot = new File(appCtx.getCacheDir(), "fixtures");
        File outDir = new File(stagingRoot, assetDirPath);
        copyAssetDirRecursively(testCtx.getAssets(), assetDirPath, outDir);

        // Instead of returning file:// URI, return a content:// Tree URI via our
        // StubDocumentProvider
        // The provider serves files from appCtx.getCacheDir()/fixtures
        // We need to construct the document ID relative to that root.
        // outDir is e.g. .../cache/fixtures/fixtures/folders/MyBook
        // root of provider is .../cache/fixtures
        // so docId is fixtures/folders/MyBook

        String fullPath = outDir.getAbsolutePath();
        String rootPath = stagingRoot.getAbsolutePath();
        String docId;
        if (fullPath.equals(rootPath)) {
            docId = "stub_root";
        } else if (fullPath.startsWith(rootPath)) {
            docId = fullPath.substring(rootPath.length() + 1); // +1 for slash
        } else {
            throw new IOException("Staged path " + fullPath + " is not under root " + rootPath);
        }

        return android.provider.DocumentsContract.buildTreeDocumentUri(
                "com.driot.bookplayer.test.documents", docId);
    }

    private static void copyAssetDirRecursively(AssetManager am, String assetDir, File destDir) throws IOException {
        if (!destDir.exists() && !destDir.mkdirs())
            throw new IOException("Failed to create dir: " + destDir);
        String[] list = am.list(assetDir);
        if (list == null)
            return;
        for (String name : list) {
            String childAssetPath = assetDir + "/" + name;
            String[] nested = am.list(childAssetPath);
            if (nested != null && nested.length > 0) {
                copyAssetDirRecursively(am, childAssetPath, new File(destDir, name));
            } else {
                File outFile = new File(destDir, name);
                if (!outFile.getParentFile().exists())
                    outFile.getParentFile().mkdirs();
                try (InputStream in = am.open(childAssetPath); FileOutputStream out = new FileOutputStream(outFile)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) >= 0)
                        out.write(buf, 0, n);
                }
            }
        }
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
        f.delete();
    }
}
