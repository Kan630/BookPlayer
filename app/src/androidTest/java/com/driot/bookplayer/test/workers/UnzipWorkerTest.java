// app/src/androidTest/java/com/driot/bookplayer/test/workers/UnzipWorkerTest.java
package com.driot.bookplayer.test.workers;

import static com.driot.bookplayer.testutil.FixtureTestUtils.*;
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
import com.driot.bookplayer.services.UnzipWorker;
import com.driot.bookplayer.testutil.LogSupport;
import com.driot.bookplayer.testutil.LoggingWatcher;
import com.driot.bookplayer.utils.log.KanLogger;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class UnzipWorkerTest implements LogSupport {

    private Context context;

    /** One case = one asset zip + expected folder-level hash (name+size over all files). */
    private static final class TestCase {
        final String name;
        final String assetPath;
        final String expectedFolderHash; // SHA-256 of canonical listing
        TestCase(String name, String assetPath, String expectedFolderHash) {
            this.name = name;
            this.assetPath = assetPath;
            this.expectedFolderHash = expectedFolderHash;
        }
    }

    // ===== Fill these AFTER running once with -e INIT true (hash will be printed) =====
    private static final List<TestCase> TESTS = Arrays.asList(
            new TestCase(
                    "file02",
                    "fixtures/zip/file02.zip",
                    /* paste from INIT logs */ "ea70824a0cc866c4910d0ac5219ec38a71ddbfbccc4a6754406842d241cce3b9"
            )
            ,new TestCase(
                    "funny_names_01",
                    "fixtures/zip/funny_names_01.zip",
                    "91aa6ee905e72572f380aab19c15fefdd88f50d6a6a1ec44c40d445ceb233c3f"
            )
            ,new TestCase(
                    "test_collisions_cp437.zip",
                    "fixtures/zip/test_collisions_cp437.zip",
                    "f666960db7dc25d66d9eb616b7d1d075a69ed5b2a193fbf9f1652b146329a3eb"
            )
            ,new TestCase(
                    "test_cp437.zip",
                    "fixtures/zip/test_cp437.zip",
                    "b5a23576e5a9205257885ced1476c414463600c406ecf8fc35adaec5443568ef"
            )
            ,new TestCase(
                    "test_utf8",
                    "fixtures/zip/test_utf8.zip",
                    "bdd41b80a2042f962cf4e31957db37a12edb62556b4fbf23997da69bff31a7c3"
            )
            ,new TestCase(
                    "മലയാളം+عربى+Русский+हिन्दी",
                    "fixtures/zip/മലയാളം+عربى+Русский+हिन्दी.zip",
                    "fe2c873a8a72b9a5325839e89f39aec26ce9bee1ee4da3a00f28a494967038dc"
            )
    );

    @Rule public LoggingWatcher logs = new LoggingWatcher();

    @Before
    public void setup() {
        context = ApplicationProvider.getApplicationContext();
        Configuration config = new Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.DEBUG)
                .setExecutor(new SynchronousExecutor())
                .build();
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config);
        KanLogger.init(context);
        myLogI("WorkManager test environment initialized");
    }

    @Test
    public void unzip_multiple_zips_and_check_folder_hash_or_init() throws Exception {
        boolean init = isInitMode();
        myLog("INIT mode = " + init + "  | cases=" + TESTS.size());
        for (TestCase tc : TESTS) runCase(tc, init);
    }

    // -------------------- helpers --------------------

    private void runCase(TestCase tc, boolean init) throws Exception {
        myLog("---------------------------------------------------------------------------------");
        myLogI("▶ Running case: " + tc.name + "  (asset=" + tc.assetPath + ")");
        myLog("---------------------------------------------------------------------------------");

        // 1) Prepare sandbox (unique per case)
        File tempRoot = new File(context.getFilesDir(), "unzip_" + tc.name);
        deleteRecursively(tempRoot);
        //noinspection ResultOfMethodCallIgnored
        tempRoot.mkdirs();

        File zipFile = new File(tempRoot, "input.zip");
        copyAssetToFile(context, tc.assetPath, zipFile);

        File destDir = new File(tempRoot, "out");
        //noinspection ResultOfMethodCallIgnored
        destDir.mkdirs();

        /*
        TODO DELETE or change

        // 2) Inject state consumed by the Worker
        LoadBookTaskState s = new LoadBookTaskState();
        s.dynamicSourceFilePath = zipFile.getAbsolutePath();
        s.futureFolderPath = destDir.getAbsolutePath();
        Pref.setLoadBookTaskState(s);

         */

        // 3) Run Worker
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(UnzipWorker.class).build();
        WorkManager wm = WorkManager.getInstance(context);
        wm.enqueue(req).getResult().get();

        WorkInfo wi = wm.getWorkInfoById(req.getId()).get(15, TimeUnit.SECONDS);
        org.junit.Assert.assertEquals("Worker did not succeed for case " + tc.name,
                WorkInfo.State.SUCCEEDED, wi.getState());
        myLogI("Worker SUCCEEDED (" + tc.name + "): " + req.getId());

        // 4) INIT-mode: log canonical listing & final hash; else assert folder hash
        myLogI("Checking folder hash for case: " + tc.name);
        assertOrInitFolderHash(destDir, tc.expectedFolderHash, init);
    }
}
