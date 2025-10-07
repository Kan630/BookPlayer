package com.driot.bookplayer.test;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.swipeUp;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.net.Uri;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
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
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.services.BookLoadingWorkLauncher;
import com.driot.bookplayer.testutil.LogSupport;
import com.driot.bookplayer.testutil.LoggingWatcher;
import com.driot.bookplayer.testutil.TestNavUtils;
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
    private final static long TIMEOUT_TEST_END = 600_000;
    private final static long TIMEOUT_BOOK_LOAD = 60_000;
    private final static long TIMEOUT_VISUAL_CHECK = 3_000;

    private static final class TestCase {
        final String name;
        final String assetFolderPath; // e.g. "fixtures/m4b/"
        TestCase(String name, String assetFolderPath) {
            this.name = name;
            this.assetFolderPath = assetFolderPath.endsWith("/") ? assetFolderPath : (assetFolderPath + "/");
        }
    }

    private static final List<TestCase> TESTS = Arrays.asList(
            new TestCase("zip", "fixtures/zip"),
            new TestCase("m4b", "fixtures/m4b")
    );

    @Rule
    public ActivityScenarioRule<MainActivity> activityRule = new ActivityScenarioRule<>(MainActivity.class);

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

        Context testContext = InstrumentationRegistry.getInstrumentation().getContext(); // test APK
        StringBuilder logFinalMessage = new StringBuilder("--------------------------\n--------------------------\nFinal Message");

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
            long lastTimestamp;
            List<String> assetFiles = listAssetFilesRecursively(testContext.getAssets(), tc.assetFolderPath); // <-- use testContext
            myLogD("--------------------------------------------------");
            myLog(String.format("TestCase '%s' -> %d files", tc.name, assetFiles.size()));
            myLogD("--------------------------------------------------");
            for (String assetPath : assetFiles) {
                Uri contentUri = stageAssetAsContentUri(appContext, testContext, assetPath);
                myLogD("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
                myLog("loading file : " + contentUri);
                myLogD("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
                lastTimestamp = System.currentTimeMillis();

                // Launch LoadBookActivity with grants
                appContext.startActivity(new Intent(appContext, LoadBookActivity.class)
                        .putExtra(LoadBookActivity.EXTRA_URI, contentUri)
                        .putExtra(LoadBookActivity.EXTRA_TYPE, "File") // your code expects "File"
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION));
                myLog("LoadBookActivity launched");
                TestNavUtils.assertWaitForActivity(LoadBookActivity.class, 1_000);
                myLog("ok, on LoadBookActivity");
                TestNavUtils.logCurrentActivity();

                // Scroll & confirm import
                onView(withId(android.R.id.content)).perform(swipeUp());
                onView(withId(R.id.btnConfirm)).perform(click());

                BookLoadingWorkLauncher.launch(appContext);

                appContext.startActivity(new Intent(appContext,
                        AddResourceActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                myLog("AddResourceActivity launched");
                TestNavUtils.assertWaitForActivity(AddResourceActivity.class, 1_000);
                myLog("ok, on AddResourceActivity");

                TestNavUtils.waitForAnyActivity(
                        TIMEOUT_BOOK_LOAD,
                        GetActivity.class,
                        MainActivity.class
                );
                TestNavUtils.clickButtonIfOnActivity(
                        AddResourceActivity.class,
                        "EXIT"
                );




                //Duration Log
                String duration = Tonio.formatMmSs(System.currentTimeMillis() - lastTimestamp);
                String newLineMsg = "\n" + duration + "  " + Tonio.getFileNameFromPath(contentUri.getPath());
                logFinalMessage.append(newLineMsg);

                TestNavUtils.sleep(TIMEOUT_VISUAL_CHECK);


            if (DEBUG_MODE_NO_LOOP) return;
            }
        }
        myLogI(logFinalMessage.append("\n--------------------------\n--------------------------").toString());
        TestNavUtils.sleep(TIMEOUT_TEST_END);
    }





    // ---------- Helpers ----------

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
}
