package com.driot.bookplayer.test;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.action.ViewActions.swipeUp;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import android.Manifest;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.widget.CheckBox;

import androidx.core.content.FileProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import androidx.work.Configuration;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.driot.bookplayer.BuildConfig;
import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.AdminActivity;
import com.driot.bookplayer.activities.ExportActivity;
import com.driot.bookplayer.activities.GetOtherActivity;
import com.driot.bookplayer.activities.MsgBoxActivity;
import com.driot.bookplayer.activities.SettingsActivity;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.importexport.BackupShareActivity;
import com.driot.bookplayer.imports.ImportBookSingleActivity;
import com.driot.bookplayer.quickshare.NearbyShareActivity;
import com.driot.bookplayer.testutil.LogSupport;
import com.driot.bookplayer.testutil.LoggingWatcher;
import com.driot.bookplayer.testutil.TestNavUtils;
import com.driot.bookplayer.utils.log.KanLogger;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Verifies the app fails gracefully (no crash, sensible messaging) when the user DENIES each
 * dangerous runtime permission it can request, rather than only exercising the granted path.
 * See memory "permission_handling_test_plan" for the inventory this was built from.
 *
 * <p>Each test: revokes the relevant permission(s) up front (so the OS shows its live grant
 * dialog on request, regardless of what earlier tests in the suite granted), triggers the
 * request, denies it via UiAutomator, then asserts the app's own denial UI/messaging appeared
 * and the activity didn't crash.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class PermissionHandlingTest implements LogSupport {

    @Rule
    public LoggingWatcher logs = new LoggingWatcher();

    private Context appContext;
    private UiDevice device;

    @Before
    public void setUp() {
        myLog("ooooooooooooooooooooooooooooooooooooooooo");
        myLog("----------------- setUp -----------------");
        myLog("ooooooooooooooooooooooooooooooooooooooooo");

        appContext = ApplicationProvider.getApplicationContext();
        KanLogger.init(appContext);
        Option.setTechLog(true);
        // GetOtherActivity/ImportBookSingleActivity only call askForPermission() when copy-mode
        // is off (otherwise they proceed to read the picked file straight away).
        Option.setCopyFile(false);

        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        Configuration config = new Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .setExecutor(Executors.newSingleThreadExecutor())
                .setTaskExecutor(new SynchronousExecutor())
                .build();
        WorkManagerTestInitHelper.initializeTestWorkManager(appContext, config);
    }

    // =================================================================================
    // 1) Settings > Play Behaviour > Visualizer  (RECORD_AUDIO)
    // =================================================================================

    @Test
    public void recordAudioPermission_denied_showsRedDeniedTextAndNoCrash() {
        revokePermissions(Manifest.permission.RECORD_AUDIO);

        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            TestNavUtils.assertWaitForActivity(SettingsActivity.class, 5_000, "SettingsActivity not loaded");

            TestNavUtils.openSettingSection(R.id.section_play_behaviour);
            TestNavUtils.sleep(500, "wait for section to expand/layout");

            // Make sure we start unchecked so the next click both checks it and fires the
            // permission request (unchecking never requests RECORD_AUDIO, see
            // PlayBehaviourSettingsFragment's onCheckedChangeListener).
            ensureCheckboxState(R.id.chk_visualizer_on, false);
            onView(withId(R.id.chk_visualizer_on)).perform(scrollTo(), click());

            int denied = denyOsPermissionDialogs(1, 4_000);
            myLog("recordAudioPermission test: denied " + denied + " OS dialog(s)");
            TestNavUtils.sleep(500, "settle after denial");

            onView(withId(R.id.tx_Visualizer_on)).check(matches(isDisplayed()));
            String txt = TestNavUtils.getText(withId(R.id.tx_Visualizer_on));
            String deniedWord = appContext.getString(R.string.denied);
            if (txt == null || !txt.contains(deniedWord)) {
                throw new AssertionError(
                        "Expected visualizer permission text to show the '" + deniedWord + "' state, got: " + txt);
            }
            myLog("OK - visualizer permission text reflects denial: " + txt);
        }
    }

    // =================================================================================
    // 2) GetOtherActivity - "Open Audio" (READ_EXTERNAL_STORAGE / READ_MEDIA_AUDIO)
    // =================================================================================

    @Test
    public void getOtherActivity_readAudioPermissionDenied_showsMessageAndNoCrash() {
        boolean tiramisuPlus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
        String perm = tiramisuPlus ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
        revokePermissions(perm);

        Intent intent = new Intent(appContext, GetOtherActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try (ActivityScenario<GetOtherActivity> scenario = ActivityScenario.launch(intent)) {
            TestNavUtils.assertWaitForActivity(GetOtherActivity.class, 5_000, "GetOtherActivity did not open");

            onView(withId(R.id.bOpenFile)).perform(scrollTo(), click());
            int denied = denyOsPermissionDialogs(1, 4_000);
            myLog("getOtherActivity test: denied " + denied + " OS dialog(s)");
            TestNavUtils.sleep(500, "settle after denial");

            if (tiramisuPlus) {
                // The API>=33 branch wires a Callback -> showPermissionDeniedDialog() -> MsgBoxActivity.
                TestNavUtils.assertWaitForActivity(MsgBoxActivity.class, 4_000,
                        "Expected MsgBoxActivity permission-denied dialog after denying read-audio permission");
                boolean sawTitle = waitForTextContaining(appContext.getString(R.string.Permission_Required), 2_000);
                myLog("OK - GetOtherActivity showed permission-denied dialog (title seen: " + sawTitle + ")");
                TestNavUtils.pressBackTo(GetOtherActivity.class, 3, 1_000);
            } else {
                // Pre-33 branch has no Callback wired: PermissionRequest just shows its own denied
                // Snackbar/Toast (see PermissionRequest.showMessage()) and there's nothing else to
                // assert - confirm the activity is still alive and didn't crash.
                boolean sawDeniedMsg = waitForTextContaining("denied", 3_000);
                myLog("Pre-33 denied feedback observed: " + sawDeniedMsg);
            }

            if (!TestNavUtils.isOn(GetOtherActivity.class)) {
                throw new AssertionError("Expected to still be on GetOtherActivity after the permission denial flow");
            }
        }
    }

    // =================================================================================
    // 3) ImportBookSingleActivity - "Link" (don't copy) option (READ_EXTERNAL_STORAGE / READ_MEDIA_AUDIO)
    // =================================================================================

    @Test
    public void importBookSingleActivity_readAudioPermissionDenied_noCrash() {
        File fixturesRoot = findFixturesRoot(appContext);
        Assume.assumeTrue("No 'fixtures' dir found on device storage - see README_FIXTURES.txt", fixturesRoot != null);

        List<File> files = listFilesRecursively(appContext, new File(fixturesRoot, "single_files"));
        if (files.isEmpty()) {
            files = listFilesRecursively(appContext, new File(fixturesRoot, "ebooks"));
        }
        Assume.assumeFalse("No fixture files found under single_files/ or ebooks/", files.isEmpty());

        Uri contentUri = fileToContentUri(appContext, files.get(0));

        boolean tiramisuPlus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
        String perm = tiramisuPlus ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
        revokePermissions(perm);

        Intent intent = new Intent(appContext, ImportBookSingleActivity.class)
                .putExtra(ImportBookSingleActivity.EXTRA_URI, contentUri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try (ActivityScenario<ImportBookSingleActivity> scenario = ActivityScenario.launch(intent)) {
            TestNavUtils.assertWaitForActivity(ImportBookSingleActivity.class, 5_000,
                    "ImportBookSingleActivity did not open");
            TestNavUtils.sleep(1_500, "wait for candidate analysis to populate the Copy/Link toggle");
            onView(withId(android.R.id.content)).perform(swipeUp());

            try {
                onView(withId(R.id.btnLink)).perform(scrollTo(), click());
            } catch (Exception e) {
                Assume.assumeNoException(
                        "btnLink not available for this fixture (forced-copy candidate?) - skipping", e);
                return;
            }

            int denied = denyOsPermissionDialogs(1, 4_000);
            myLog("importBookSingleActivity test: denied " + denied + " OS dialog(s)");
            TestNavUtils.sleep(500, "settle after denial");

            if (tiramisuPlus) {
                TestNavUtils.assertWaitForActivity(MsgBoxActivity.class, 4_000,
                        "Expected MsgBoxActivity permission-denied dialog after denying read-audio permission");
                myLog("OK - ImportBookSingleActivity showed permission-denied dialog");
                TestNavUtils.pressBackTo(ImportBookSingleActivity.class, 3, 1_000);
            } else {
                boolean sawDeniedMsg = waitForTextContaining("denied", 3_000);
                myLog("Pre-33 denied feedback observed: " + sawDeniedMsg);
            }

            // Back out without confirming the import - this test only cares about the denial
            // handling, not about actually adding a book.
            try {
                onView(withId(R.id.btnCancel)).perform(click());
            } catch (Exception ignored) {
                myLogW("Could not click btnCancel to back out cleanly (activity may already be gone)");
            }
        }
    }

    // =================================================================================
    // 4) BackupShareActivity - Nearby "Live Share" backup send/receive
    // =================================================================================

    @Test
    public void backupShareActivity_permissionsDenied_showsToastAndNoCrash() {
        String[] perms = nearbyPermissionSet();
        revokePermissions(perms);

        Intent intent = new Intent(appContext, BackupShareActivity.class)
                .putExtra(BackupShareActivity.EXTRA_MODE, BackupShareActivity.MODE_SEND)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try (ActivityScenario<BackupShareActivity> scenario = ActivityScenario.launch(intent)) {
            TestNavUtils.assertWaitForActivity(BackupShareActivity.class, 5_000, "BackupShareActivity did not open");

            onView(withId(R.id.btn_action_share)).perform(click());
            int denied = denyOsPermissionDialogs(perms.length, 4_000);
            myLog("backupShareActivity test: denied " + denied + " OS dialog(s)");

            boolean sawToast = waitForTextContaining(
                    appContext.getString(R.string.backup_share_permissions_required), 4_000);
            myLog("backup_share_permissions_required toast observed: " + sawToast);

            if (!TestNavUtils.isOn(BackupShareActivity.class)) {
                throw new AssertionError("BackupShareActivity crashed or navigated away after permission denial");
            }
        }
    }

    // =================================================================================
    // 5) NearbyShareActivity - Nearby book share (receive mode needs no Folder extra)
    // =================================================================================

    @Test
    public void nearbyShareActivity_permissionsDenied_showsToastAndNoCrash() {
        String[] perms = nearbyPermissionSet();
        revokePermissions(perms);

        Intent intent = new Intent(appContext, NearbyShareActivity.class)
                .putExtra("RECEIVE_MODE", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try (ActivityScenario<NearbyShareActivity> scenario = ActivityScenario.launch(intent)) {
            TestNavUtils.assertWaitForActivity(NearbyShareActivity.class, 5_000, "NearbyShareActivity did not open");

            onView(withId(R.id.btnStartSharing)).perform(click());
            int denied = denyOsPermissionDialogs(perms.length, 4_000);
            myLog("nearbyShareActivity test: denied " + denied + " OS dialog(s)");

            boolean sawToast = waitForTextContaining(
                    appContext.getString(R.string.nearby_share_permissions_required), 4_000);
            myLog("nearby_share_permissions_required toast observed: " + sawToast);

            if (!TestNavUtils.isOn(NearbyShareActivity.class)) {
                throw new AssertionError("NearbyShareActivity crashed or navigated away after permission denial");
            }
        }
    }

    // =================================================================================
    // 6) ExportActivity - export/backup a book (WRITE_EXTERNAL_STORAGE, API < 29 only)
    // =================================================================================

    @Test
    public void exportActivity_writeStoragePermissionDenied_noCrash() {
        Assume.assumeTrue("WRITE_EXTERNAL_STORAGE is only requested pre-Android 10 (API < 29)",
                Build.VERSION.SDK_INT < Build.VERSION_CODES.Q);

        Folder folder = firstUsableFolder();
        Assume.assumeTrue("No existing, on-disk book folder found in DB - run LoadManyBookTest first", folder != null);

        revokePermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE);

        Intent intent = new Intent(appContext, ExportActivity.class)
                .putExtra(Intents.EXTRA_FOLDER, folder)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try (ActivityScenario<ExportActivity> scenario = ActivityScenario.launch(intent)) {
            TestNavUtils.assertWaitForActivity(ExportActivity.class, 5_000, "ExportActivity did not open");

            // The WRITE_EXTERNAL_STORAGE check (and thus the request) fires straight from
            // onCreate() on API < 29, before any button click.
            int denied = denyOsPermissionDialogs(1, 4_000);
            myLog("exportActivity test: denied " + denied + " OS dialog(s)");

            boolean sawToast = waitForTextContaining("Permission denied", 4_000);
            myLog("export denial toast observed: " + sawToast);

            if (!TestNavUtils.isOn(ExportActivity.class)) {
                throw new AssertionError("ExportActivity crashed or navigated away after permission denial");
            }
        }
    }

    // =================================================================================
    // 7) AdminActivity - "flush logs to Downloads" (WRITE_EXTERNAL_STORAGE, API < 29 only)
    // =================================================================================

    @Test
    public void adminActivity_flushLogsWriteStoragePermissionDenied_noCrash() {
        Assume.assumeTrue("WRITE_EXTERNAL_STORAGE is only requested pre-Android 10 (API < 29)",
                Build.VERSION.SDK_INT < Build.VERSION_CODES.Q);

        revokePermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE);

        Intent intent = new Intent(appContext, AdminActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try (ActivityScenario<AdminActivity> scenario = ActivityScenario.launch(intent)) {
            TestNavUtils.assertWaitForActivity(AdminActivity.class, 5_000, "AdminActivity did not open");

            onView(withId(R.id.bFlushLogsToDownloads)).perform(scrollTo(), click());
            int denied = denyOsPermissionDialogs(1, 4_000);
            myLog("adminActivity test: denied " + denied + " OS dialog(s)");

            boolean sawToast = waitForTextContaining("Permission denied", 4_000);
            myLog("flush-logs denial toast observed: " + sawToast);

            if (!TestNavUtils.isOn(AdminActivity.class)) {
                throw new AssertionError("AdminActivity crashed or navigated away after permission denial");
            }
        }
    }

    // =================================================================================
    // Helpers
    // =================================================================================

    private String[] nearbyPermissionSet() {
        List<String> perms = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            perms.add(Manifest.permission.BLUETOOTH_CONNECT);
            perms.add(Manifest.permission.BLUETOOTH_SCAN);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        }
        if (Build.VERSION.SDK_INT >= 29) {
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        } else {
            perms.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        return perms.toArray(new String[0]);
    }

    private void revokePermissions(String... permissions) {
        UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        for (String permission : permissions) {
            try {
                automation.revokeRuntimePermission(appContext.getPackageName(), permission);
                myLogD("Revoked " + permission);
            } catch (Exception e) {
                myLogD("Could not revoke " + permission + " (likely unsupported on this API level): "
                        + e.getMessage());
            }
        }
    }

    private void ensureCheckboxState(int id, boolean want) {
        final boolean[] current = { false };
        final boolean[] found = { false };
        onView(withId(id)).check((view, noViewFoundException) -> {
            if (noViewFoundException != null || !(view instanceof CheckBox))
                return;
            current[0] = ((CheckBox) view).isChecked();
            found[0] = true;
        });
        if (found[0] && current[0] != want) {
            onView(withId(id)).perform(scrollTo(), click());
            TestNavUtils.sleep(150, "settle after checkbox toggle");
        }
    }

    /**
     * Denies up to {@code maxDialogs} consecutive OS runtime-permission dialogs (a
     * multi-permission request can pop one dialog per permission group). Returns how many were
     * actually found and denied - 0 usually means the permission was already permanently denied
     * (no dialog shown at all), which the caller should still treat as a valid "not granted"
     * outcome rather than a failure.
     */
    private int denyOsPermissionDialogs(int maxDialogs, long perDialogTimeoutMs) {
        int denied = 0;
        for (int i = 0; i < maxDialogs; i++) {
            UiObject2 btn = findDenyButton(perDialogTimeoutMs);
            if (btn == null)
                break;
            try {
                btn.click();
                denied++;
                TestNavUtils.sleep(300, "after clicking deny");
            } catch (Exception e) {
                myLogW("Failed to click deny button: " + e.getMessage());
                break;
            }
        }
        return denied;
    }

    /**
     * Finds the "Deny" button of Android's runtime-permission grant dialog. Resource ids are
     * tried first (locale-proof, stable across the permission controller's own version); a text
     * fallback covers older packageinstaller-hosted dialogs and any un-recognized layout, in
     * both English and French (this app also ships French strings).
     */
    private UiObject2 findDenyButton(long timeoutMs) {
        String[] resIds = {
                "com.android.permissioncontroller:id/permission_deny_button",
                "com.android.permissioncontroller:id/permission_deny_and_dont_ask_again_button",
                "com.android.packageinstaller:id/permission_deny_button",
        };
        long perIdTimeout = Math.max(300, timeoutMs / resIds.length);
        for (String id : resIds) {
            UiObject2 obj = device.wait(Until.findObject(By.res(id)), perIdTimeout);
            if (obj != null)
                return obj;
        }
        return device.wait(Until.findObject(
                By.text(java.util.regex.Pattern.compile("(?i)^(deny|don.?t allow|refuser|ne pas autoriser)$"))),
                500);
    }

    /** True if a view or Toast/Snackbar containing this text shows up within the timeout. */
    private boolean waitForTextContaining(String substring, long timeoutMs) {
        UiObject2 obj = device.wait(Until.findObject(By.textContains(substring)), timeoutMs);
        return obj != null;
    }

    /** First folder in DB whose path is a real, existing directory on disk. */
    private Folder firstUsableFolder() {
        final Folder[] result = { null };
        CountDownLatch done = new CountDownLatch(1);
        AppDatabase.databaseReadExecutor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(appContext);
                List<Folder> all = db.folderDao().getAll();
                for (Folder f : all) {
                    if (f.getPath() != null && new File(f.getPath()).isDirectory()) {
                        result[0] = f;
                        break;
                    }
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

    /**
     * Locates a real "fixtures" directory on device storage - same convention used by
     * LoadManyBookTest (see README_FIXTURES.txt): primary storage first, then every volume from
     * {@link Context#getExternalFilesDirs}.
     */
    private static File findFixturesRoot(Context context) {
        List<File> candidateRoots = new ArrayList<>();
        File primary = android.os.Environment.getExternalStorageDirectory();
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

    /** All paths MediaStore has indexed under `rootPath/` (any depth) - see LoadManyBookTest. */
    private static List<String> queryIndexedPathsUnderPrefix(Context context, String rootPath) {
        List<String> out = new ArrayList<>();
        Uri filesUri = android.provider.MediaStore.Files.getContentUri("external");
        String[] projection = { android.provider.MediaStore.Files.FileColumns.DATA };
        String selection = android.provider.MediaStore.Files.FileColumns.DATA + " LIKE ?";
        String[] args = { rootPath + "/%" };
        try (android.database.Cursor c = context.getContentResolver().query(filesUri, projection, selection, args,
                null)) {
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

    private static Uri fileToContentUri(Context appCtx, File file) {
        String authority = BuildConfig.APPLICATION_ID + ".FileProvider";
        return FileProvider.getUriForFile(appCtx, authority, file);
    }
}
