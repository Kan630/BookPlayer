// app/src/androidTest/java/com/driot/bookplayer/test/workers/UnzipWorkerTest.java
package com.driot.bookplayer.test;

import static com.driot.bookplayer.imports.BookLoadingWorkLauncher.BOOK_LOADING_WORKERS;
import static com.driot.bookplayer.testutil.FixtureTestUtils.*;
import static com.driot.bookplayer.testutil.HashAssert.assertOrInitFolderHash;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Configuration;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.imports.ImportJob;
import com.driot.bookplayer.imports.ImportJobRepository;
import com.driot.bookplayer.imports.ImportWorker;
import com.driot.bookplayer.services.UncompressWorker;
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
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class UnzipWorkerTest implements LogSupport {

    private Context appContext;

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
                    "fixtures/worker_test_zip/file02.zip",
                    /* paste from INIT logs */ "ea70824a0cc866c4910d0ac5219ec38a71ddbfbccc4a6754406842d241cce3b9"
            )
            ,new TestCase(
                    "funny_names_01",
                    "fixtures/worker_test_zip/funny_names_01.zip",
                    "91aa6ee905e72572f380aab19c15fefdd88f50d6a6a1ec44c40d445ceb233c3f"
            )
            ,new TestCase(
                    "test_collisions_cp437.zip",
                    "fixtures/worker_test_zip/test_collisions_cp437.zip",
                    "f666960db7dc25d66d9eb616b7d1d075a69ed5b2a193fbf9f1652b146329a3eb"
            )
            ,new TestCase(
                    "test_cp437.zip",
                    "fixtures/worker_test_zip/test_cp437.zip",
                    "b5a23576e5a9205257885ced1476c414463600c406ecf8fc35adaec5443568ef"
            )
            ,new TestCase(
                    "test_utf8",
                    "fixtures/worker_test_zip/test_utf8.zip",
                    "bdd41b80a2042f962cf4e31957db37a12edb62556b4fbf23997da69bff31a7c3"
            )
            ,new TestCase(
                    "മലയാളം+عربى+Русский+हिन्दी",
                    "fixtures/worker_test_zip/മലയാളം+عربى+Русский+हिन्दी.zip",
                    "fe2c873a8a72b9a5325839e89f39aec26ce9bee1ee4da3a00f28a494967038dc"
            )
    );

    @Rule public LoggingWatcher logs = new LoggingWatcher();

    @Before
    public void setup() {
        myLog("ooooooooooooooooooooooooooooooooooooooooo");
        myLog("----------------- setUp -----------------");
        myLog("ooooooooooooooooooooooooooooooooooooooooo");
        appContext = ApplicationProvider.getApplicationContext();
        KanLogger.init(appContext);
        Option.setTechLog(true);
        Configuration config = new Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.DEBUG)
                .setExecutor(new SynchronousExecutor())
                .setTaskExecutor(new SynchronousExecutor()) // keeps callbacks synchronous too
                .build();
        WorkManagerTestInitHelper.initializeTestWorkManager(appContext, config);
        myLogI("WorkManager test environment initialized");
    }

    @Test
    public void unzip_multiple_zips_and_check_folder_hash_or_init() throws Exception {
        boolean init = isInitMode();
        myLog("INIT mode = " + init + "  | cases=" + TESTS.size());
        int i = 0;
        for (TestCase tc : TESTS) {
            i = i+1;
            myLogI("---------------------------------------------------------------------------------");
            myLogI("▶ Running case n°" + i + "/" + TESTS.size() + ": " + tc.name + "  (asset=" + tc.assetPath + ")");
            myLogI("---------------------------------------------------------------------------------");
            runCase(tc, init);
        }
    }

    // -------------------- helpers --------------------

    private void runCase(TestCase tc, boolean init) throws Exception {

        // 1) Prepare sandbox (unique per case)
        File tempRoot = new File(appContext.getFilesDir(), "unzip_" + tc.name);
        deleteRecursively(tempRoot);
        //noinspection ResultOfMethodCallIgnored
        tempRoot.mkdirs();

        File inputFile = new File(tempRoot, "input.zip");
        copyAssetToFile(appContext, tc.assetPath, inputFile);

        File destDir = new File(tempRoot, "out");
        //noinspection ResultOfMethodCallIgnored
        destDir.mkdirs();

        // 2) Prepare Job
        String importId = "test_" + UUID.randomUUID();
        ImportJob j = new ImportJob();
        j.importId = importId;
        j.dynamicType = "INSTRUMENTED_TESTS";
        j.dynamicSourceFilePath = inputFile.getAbsolutePath();
        j.futureFolderPath = destDir.getAbsolutePath();
        ImportJobRepository repo = new ImportJobRepository(appContext);
        repo.upsert(j);
        myLogD("ImportJobRepository populated, about to launch worker");

        // 3) Run Worker
        OneTimeWorkRequest req = new OneTimeWorkRequest
                .Builder(UncompressWorker.class)
                .setInputData(new Data.Builder().putString(ImportWorker.KEY_IMPORT_ID, importId).build())
                .addTag(BOOK_LOADING_WORKERS).addTag("import:" + importId)
                .build();
        WorkManager wm = WorkManager.getInstance(appContext);
        wm.enqueue(req).getResult().get();

        WorkInfo wi = wm.getWorkInfoById(req.getId()).get(15, TimeUnit.SECONDS);
        assertEquals("Worker did not succeed for case " + tc.name, WorkInfo.State.SUCCEEDED, wi.getState());
        myLogI("Worker SUCCEEDED (" + tc.name + "): " + req.getId());

        // 4) INIT-mode: log canonical listing & final hash; else assert folder hash
        myLogI("Checking folder hash for case: " + tc.name);
        assertOrInitFolderHash(destDir, tc.expectedFolderHash, init);
    }
}
