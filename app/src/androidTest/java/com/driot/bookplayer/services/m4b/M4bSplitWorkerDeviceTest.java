package com.driot.bookplayer.services.m4b;

import static com.driot.bookplayer.imports.BookLoadingWorkLauncher.BOOK_LOADING_WORKERS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.os.ParcelFileDescriptor;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.Configuration;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.driot.bookplayer.imports.ImportJob;
import com.driot.bookplayer.imports.ImportJobRepository;
import com.driot.bookplayer.imports.ImportWorker;
import com.driot.bookplayer.services.M4bSplitWorker;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * M4bSplitWorker end to end (as an import runs it), on the inputs pushed to /data/local/tmp/bp_m4b/ (see
 * M4bSplitterDeviceTest): split, rescued split, and refused split (imported as the single M4B, with a warning).
 */
@RunWith(AndroidJUnit4.class)
public class M4bSplitWorkerDeviceTest {

    private Context ctx;
    private File root;
    private ImportJobRepository repo;

    @Before
    public void setUp() {
        ctx = ApplicationProvider.getApplicationContext();
        WorkManagerTestInitHelper.initializeTestWorkManager(ctx, new Configuration.Builder()
                .setExecutor(new SynchronousExecutor()).setTaskExecutor(new SynchronousExecutor()).build());
        root = new File(ctx.getFilesDir(), "m4bsplitworker_test");
        deleteRecursively(root);
        assertTrue(root.mkdirs());
        repo = new ImportJobRepository(ctx);
    }

    @After
    public void tearDown() {
        deleteRecursively(root);
        com.driot.bookplayer.db.AppDatabase.getDatabase(ctx).getOpenHelper().getWritableDatabase()
                .execSQL("DELETE FROM ImportJob WHERE importId LIKE 'm4bworker_test_%'");
    }

    private static final class Run {
        File m4b, dest;
        ImportJob job;
        WorkInfo.State state;
    }

    private Run run(String name) throws Exception {
        Run r = new Run();
        File dir = new File(root, name);
        assertTrue(dir.mkdirs());
        // like a real (non-downloaded) import: the copied M4B sits in the book folder (futureFolderPath/originalFile)
        r.dest = new File(dir, "Book " + name);
        assertTrue(r.dest.mkdirs());
        r.m4b = new File(r.dest, name + ".m4b");
        assumeTrue("input missing: " + name, copyFromShell("/data/local/tmp/bp_m4b/" + name + ".m4b", r.m4b));

        String id = "m4bworker_test_" + UUID.randomUUID();
        ImportJob j = new ImportJob();
        j.importId = id;
        j.dynamicType = "INSTRUMENTED_TESTS";
        j.dynamicSourceFilePath = r.m4b.getAbsolutePath();
        j.futureFolderPath = r.dest.getAbsolutePath();
        j.createdAt = 1; // never the "latest" job the app UI observes
        j.updatedAt = 1;
        repo.upsert(j);

        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(M4bSplitWorker.class)
                .setInputData(new Data.Builder().putString(ImportWorker.KEY_IMPORT_ID, id).build())
                .addTag(BOOK_LOADING_WORKERS).addTag("import:" + id).build();
        WorkManager wm = WorkManager.getInstance(ctx);
        wm.enqueue(req).getResult().get();
        long deadline = System.currentTimeMillis() + 180_000;
        WorkInfo wi = null;
        while (System.currentTimeMillis() < deadline) {
            wi = wm.getWorkInfoById(req.getId()).get(2, TimeUnit.SECONDS);
            if (wi != null && wi.getState().isFinished())
                break;
            Thread.sleep(200);
        }
        assertNotNull(wi);
        r.state = wi.getState();
        r.job = repo.get(id);
        return r;
    }

    private static int count(File dir, String ext) {
        File[] kids = dir.listFiles();
        int n = 0;
        if (kids != null)
            for (File k : kids)
                if (k.getName().endsWith(ext))
                    n++;
        return n;
    }

    @Test
    public void split_savesTrackTitles() throws Exception {
        Run r = run("frost");
        assertEquals(WorkInfo.State.SUCCEEDED, r.state);
        assertEquals(12, count(r.dest, ".aac"));
        assertFalse(r.m4b.exists());
        JSONObject meta = new JSONObject(r.job.metadataJson);
        assertEquals(12, meta.getJSONObject("track_titles").length());
    }

    @Test
    public void rescuedSplit() throws Exception {
        Run r = run("elements_broken_vide");
        assertEquals(WorkInfo.State.SUCCEEDED, r.state);
        assertEquals(11, count(r.dest, ".aac"));
        assertFalse(r.m4b.exists());
    }

    @Test
    public void refusedSplit_importsTheSingleM4b() throws Exception {
        Run r = run("frost_bad_chapters");
        assertEquals(WorkInfo.State.SUCCEEDED, r.state); // the import goes on, unsplit
        assertEquals(0, count(r.dest, ".aac"));
        assertTrue("the M4B stays in the book folder", r.m4b.exists());
        assertEquals(1, count(r.dest, ".m4b"));
        assertTrue("user told why: " + r.job.warningText, r.job.warningText != null && !r.job.warningText.isEmpty());
    }

    private static boolean copyFromShell(String path, File to) throws Exception {
        ParcelFileDescriptor pfd = InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommand("cat " + path);
        long n = 0;
        try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
                OutputStream out = new FileOutputStream(to)) {
            byte[] b = new byte[256 * 1024];
            int r;
            while ((r = in.read(b)) > 0) {
                out.write(b, 0, r);
                n += r;
            }
        }
        return n > 0;
    }

    private static void deleteRecursively(File f) {
        File[] kids = f.listFiles();
        if (kids != null)
            for (File k : kids)
                deleteRecursively(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
