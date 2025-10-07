// app/src/androidTest/java/com/driot/bookplayer/test/workers/M4bSplitWorkerTest.java
package com.driot.bookplayer.test.workers;

import static com.driot.bookplayer.testutil.FixtureTestUtils.copyAssetToFile;
import static com.driot.bookplayer.testutil.FixtureTestUtils.deleteRecursively;
import static com.driot.bookplayer.testutil.FixtureTestUtils.isInitMode;
import static com.driot.bookplayer.testutil.HashAssert.assertOrInitFolderHash;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Configuration;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.objects.LoadBookTaskState;
import com.driot.bookplayer.services.M4bSplitWorker;   // <-- your worker
import com.driot.bookplayer.testutil.LogSupport;
import com.driot.bookplayer.testutil.LoggingWatcher;
import com.driot.bookplayer.utils.KanLogger;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class M4bSplitWorkerTest implements LogSupport {

    private Context context;

    /** One case = input .m4b asset + expected folder-level hash (name+size). */
    private static final class TestCase {
        final String name;
        final String assetPath;          // e.g. "fixtures/m4b/chapters_small.m4b"
        final String expectedFolderHash; // SHA-256 of canonical listing (computed in INIT)
        TestCase(String name, String assetPath, String expectedFolderHash) {
            this.name = name;
            this.assetPath = assetPath;
            this.expectedFolderHash = expectedFolderHash;
        }
    }

    // ===== Fill these AFTER a first run with -e INIT true (hash printed in logs) =====
    private static final List<TestCase> TESTS = Arrays.asList(
            new TestCase(
                    "FrostTonight_librivox",
                    "fixtures/m4b/FrostTonight_librivox.m4b",
                    /* paste from INIT */ "5bb8569eeec03eba21f790172cc7e6f12df081ada3eae5f82ac53fc3134d1bc8"
            )
            ,new TestCase(
                    "Mythos (Unabridged)",
                    "fixtures/m4b/Mythos (Unabridged).m4b",
                    /* paste from INIT */ "d6a3ca56ed8905de05664d661a097078e1336d570cfddb2fb63fd703e3b9150a"
            )
            ,new TestCase(
                    "Yukio Mishima - Sun and Steel",
                    "fixtures/m4b/Yukio Mishima - Sun and Steel.m4b",
                    /* paste from INIT */ "ba289a83b6e040fb3877d6238e76bc63980727e0d2b7027d64d764ca41fefce3"
            )
            ,new TestCase(
                    "Can't Hurt Me by David Goggins",
                    "fixtures/m4b/Can't Hurt Me by David Goggins.m4b",
                    /* paste from INIT */ "7aa8e71c8f7215be5913942cefb0c4f134da93c2eb6be11d56d2700ead8af523"
            )
    );

    @Rule public LoggingWatcher logs = new LoggingWatcher();

    @Before
    public void setup() {
        context = ApplicationProvider.getApplicationContext();
        Configuration config = new Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.DEBUG)
                .setExecutor(new SynchronousExecutor())
                .setTaskExecutor(new SynchronousExecutor()) // keeps callbacks synchronous too
                .build();
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config);
        KanLogger.init(context);
        myLogI("WorkManager test environment initialized");
    }

    @Test
    public void split_multiple_m4b_and_check_folder_hash_or_init() throws Exception {
        boolean init = isInitMode();
        myLog("INIT mode = " + init + "  | cases=" + TESTS.size());
        for (TestCase tc : TESTS) {
            runCase(tc, init);
        }
    }

    // -------------------- helpers --------------------

    private void runCase(TestCase tc, boolean init) throws Exception {
        myLog("---------------------------------------------------------------------------------");
        myLogI("▶ Running case: " + tc.name + "  (asset=" + tc.assetPath + ")");
        myLog("---------------------------------------------------------------------------------");

        // 1) Sandbox (unique per case)
        File tempRoot = new File(context.getFilesDir(), "m4bsplit_" + tc.name);
        deleteRecursively(tempRoot);
        //noinspection ResultOfMethodCallIgnored
        tempRoot.mkdirs();

        File inputFile = new File(tempRoot, "input.m4b");
        copyAssetToFile(context, tc.assetPath, inputFile);

        File destDir = new File(tempRoot, "out");
        //noinspection ResultOfMethodCallIgnored
        destDir.mkdirs();

        // 2) Inject state for the Worker (same contract as UnzipWorker)
        LoadBookTaskState s = new LoadBookTaskState();
        s.dynamicSourceFilePath = inputFile.getAbsolutePath();
        s.futureFolderPath = destDir.getAbsolutePath();
        Pref.setLoadBookTaskState(s);

        // 3) Run Worker
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(M4bSplitWorker.class).build();
        WorkManager wm = WorkManager.getInstance(context);
        wm.enqueue(req).getResult().get();

        WorkInfo wi = waitForTerminalState(wm, req.getId(), /*maxMillis*/ 180_000);
        org.junit.Assert.assertEquals("Worker did not succeed for case " + tc.name,
                WorkInfo.State.SUCCEEDED, wi.getState());
        myLogI("Worker SUCCEEDED (" + tc.name + "): " + req.getId());

        // 4) Some splitters create a single subfolder. If so, hash that; else hash destDir itself.
        File hashRoot = pickHashRoot(destDir);
        myLogI("Hash root: " + hashRoot.getAbsolutePath());

        // 5) INIT vs ASSERT (uses name+size canonical folder fingerprint)
        assertOrInitFolderHash(hashRoot, tc.expectedFolderHash, init);
    }

    /** If destDir contains exactly one directory and no files, return that directory; else destDir. */
    private File pickHashRoot(File destDir) {
        File[] kids = destDir.listFiles();
        if (kids == null) return destDir;
        int fileCount = 0, dirCount = 0; File onlyDir = null;
        for (File k : kids) {
            if (k.isDirectory()) { dirCount++; onlyDir = k; }
            else fileCount++;
        }
        return (fileCount == 0 && dirCount == 1 && onlyDir != null) ? onlyDir : destDir;
    }

    /** Robust wait that polls until terminal state or times out. */
    private WorkInfo waitForTerminalState(WorkManager wm, UUID id, long maxMillis) throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + maxMillis;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            WorkInfo wi = wm.getWorkInfoById(id).get(2, TimeUnit.SECONDS);
            if (wi != null && wi.getState().isFinished()) {
                myLogI("Worker finished with state=" + wi.getState() + " id=" + id);
                return wi;
            }
            Thread.sleep(200);
        }
        WorkInfo wi = wm.getWorkInfoById(id).get();
        throw new AssertionError("Timed out waiting for work " + id + " state=" + (wi != null ? wi.getState() : "null"));
    }
}
