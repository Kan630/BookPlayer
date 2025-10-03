package com.driot.bookplayer.activities;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.objects.OngoingTaskHost;
import com.driot.bookplayer.objects.TaskStateManager;
import com.driot.bookplayer.utils.MediaScanner2;
import com.driot.bookplayer.utils.NetworkUtils;
import com.driot.bookplayer.utils.PermissionRequest;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.util.Arrays;
import java.util.List;

import static com.driot.bookplayer.utils.PermissionRequest.isReadAudioPermissionGranted;

public class GetOtherActivity extends LoggingActivity {

    private Button bOpenFile, bOpenFolder, bOpenZipFile, bOpenM4bFile, bMassImport;
    private Button bAutoTest_b1, bAutoTest_b2, bAutoTest_b3, bAutoTest_b4, bDirectDownload;

    private PermissionRequest mPermissionRequest;

    private OngoingTaskViewModel viewModel;

    private ActivityResultLauncher<Intent> bOpenFileActivityResultLauncher,
            bOpenFolderActivityResultLauncher,
            loadOptionsActivityResultLauncher,
            bMassImportActivityResultLauncher;

    private ActivityResultLauncher<Intent> addResourceActivityResultLauncher;

    private void launchAddResource(ActivityResult result, String type) {
        myLog("launchAddResource()-----------------------------------------------------------------------------------------------------");
        try {
            if (result.getResultCode() == RESULT_OK) {
                if (isReturnedUriOk(result.getData())) {
                    Uri uri = result.getData().getData();
                    myLog("picked data : " + uri.getPath());

                    getContentResolver().takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    );

                    if (type.equals("MassImport")) {
                        WorkManager.getInstance(this).enqueue(
                                new OneTimeWorkRequest.Builder(com.driot.bookplayer.services.ScanAndReimportWorker.class)
                                        .setInputData(new Data.Builder()
                                                .putString(com.driot.bookplayer.services.ScanAndReimportWorker.K_ROOT_TREE_URI, uri.toString())
                                                .putString(com.driot.bookplayer.services.ScanAndReimportWorker.K_SOURCE_LOC, "MassImport")
                                                .build())
                                        .addTag("BulkReimportScan")
                                        .build()
                        );
                        Intent intentActivity = new Intent(this, AddResourceActivity.class);
                        startActivity(intentActivity);
                    } else {
                        Intent intent = new Intent(this, LoadBookActivity.class);
                        intent.putExtra(LoadBookActivity.EXTRA_URI, uri);
                        intent.putExtra(LoadBookActivity.EXTRA_TYPE, type);
                        loadOptionsActivityResultLauncher.launch(intent);
                    }
                } else {
                    myLogE("returned Uri not OK");
                }
            } else {
                myLogE("result code not OK");
            }
        } catch (Exception e) {
            myToastEE(e, "Error reading picked object");
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_get_other);
        InsetHelper.apply(this);

        bOpenFile = findViewById(R.id.bOpenFile);
        bOpenZipFile = findViewById(R.id.bOpenZipFile);
        bOpenM4bFile = findViewById(R.id.bOpenM4bFile);
        bOpenFolder = findViewById(R.id.bOpenFolder);
        bMassImport = findViewById(R.id.bMassImport);
        bAutoTest_b1 = findViewById(R.id.bAutoTest_b1);
        bAutoTest_b2 = findViewById(R.id.bAutoTest_b2);
        bAutoTest_b3 = findViewById(R.id.bAutoTest_b3);
        bAutoTest_b4 = findViewById(R.id.bAutoTest_b4);
        bDirectDownload = findViewById(R.id.bDirectDownload);

        // NEW: attach the ongoing banner fragment once; it will self-show/hide
        OngoingTaskHost.attach(
                this,
                R.id.topOverlayContainer,
                new Intent(this, AddResourceActivity.class) // tap banner -> open details
        );

        // ADD RESOURCE RESULT
        addResourceActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    myLog("return from Add_Resource_Activity");
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        myLog("result ok - closing activity");
                        finish();
                    }
                });

        // SINGLE FILE
        bOpenFileActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> { launchAddResource(result, "File"); });
        bOpenFile.setOnClickListener(view -> {
            myLogI("------------ USER CLICKS : button ANY file");
            if (isReadAudioPermissionGranted(this) || Option.getCopyFile()) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("*/*");
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                bOpenFileActivityResultLauncher.launch(intent);
            } else {
                askForPermission();
            }
        });

        // ZIP
        bOpenZipFile.setOnClickListener(view -> {
            scanThatShit();
            myLogI("------------ USER CLICKS : button ZIP file");
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("*/*");
            String[] mimeTypes = {"application/zip", "application/x-zip-compressed"};
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            bOpenFileActivityResultLauncher.launch(intent);
        });

        // M4B
        bOpenM4bFile.setOnClickListener(view -> {
            scanThatShit();
            myLogI("------------ USER CLICKS : button AUDIO file");

            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"audio/*"});
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            bOpenFileActivityResultLauncher.launch(intent);
        });

        // FOLDER
        bOpenFolderActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> { launchAddResource(result, "Folder"); });
        bOpenFolder.setOnClickListener(view -> {
            myLogI("------------ USER CLICKS : button FOLDER");
            if (isReadAudioPermissionGranted(this) || Option.getCopyFile()) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                try {
                    bOpenFolderActivityResultLauncher.launch(intent);
                } catch (Exception e) {
                    myToastEE(e, "could not open android folder explorer");
                }
            } else {
                askForPermission();
            }
        });

        // MASS IMPORT (folder)
        bMassImportActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> launchAddResource(result, "MassImport"));
        bMassImport.setOnClickListener(view -> {
            myLogI("------------ USER CLICKS : button MASS IMPORT");
            if (isReadAudioPermissionGranted(this) || Option.getCopyFile()) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                try {
                    bMassImportActivityResultLauncher.launch(intent);
                } catch (Exception e) {
                    myToastEE(e, "could not open android folder explorer");
                }
            } else {
                askForPermission();
            }
        });

        loadOptionsActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        TaskStateManager.tellStart();
                        com.driot.bookplayer.services.BookLoadingWorkLauncher.launch(this);
                        startActivity(new Intent(this, AddResourceActivity.class));
                    }
                }
        );

        // Secret / auto-tests unchanged...
        View secretEntry = findViewById(R.id.viewSecretEntry);
        final long[] taps = new long[3];
        secretEntry.setOnClickListener(v -> {
            System.arraycopy(taps, 1, taps, 0, taps.length - 1);
            taps[taps.length - 1] = System.currentTimeMillis();
            if (taps[0] >= System.currentTimeMillis() - 1000) {
                LinearLayout llsecretDev = findViewById(R.id.llsecretDev);
                llsecretDev.setVisibility(View.VISIBLE);
            }
        });

        bAutoTest_b1.setOnClickListener(view -> {
            myLogI("Button click : AUTO TEST 01");
            checkWWW(canReach -> {
                if (canReach) {
                    Intent intent = new Intent(this, LoadBookActivity.class);
                    intent.putExtra(LoadBookActivity.EXTRA_URI, Uri.parse(Var.AUTOTEST_FILE_01));
                    intent.putExtra(LoadBookActivity.EXTRA_TYPE, "File");
                    loadOptionsActivityResultLauncher.launch(intent);
                }
            });
        });
        bAutoTest_b2.setOnClickListener(view -> {
            myLogI("Button click : AUTO TEST 02");
            checkWWW(canReach -> {
                if (canReach) {
                    Intent intent = new Intent(this, LoadBookActivity.class);
                    intent.putExtra(LoadBookActivity.EXTRA_URI, Uri.parse(Var.AUTOTEST_FILE_02));
                    intent.putExtra(LoadBookActivity.EXTRA_TYPE, "File");
                    loadOptionsActivityResultLauncher.launch(intent);
                }
            });
        });
        bAutoTest_b3.setOnClickListener(view -> {
            myLogI("Button click : AUTO TEST 03");
            checkWWW(canReach -> {
                if (canReach) {
                    Intent intent = new Intent(this, LoadBookActivity.class);
                    intent.putExtra(LoadBookActivity.EXTRA_URI, Uri.parse(Var.AUTOTEST_FILE_03));
                    intent.putExtra(LoadBookActivity.EXTRA_TYPE, "File");
                    loadOptionsActivityResultLauncher.launch(intent);
                }
            });
        });
        bAutoTest_b4.setOnClickListener(view -> {
            myLogI("Button click : AUTO TEST 04");
            checkWWW(canReach -> {
                if (canReach) {
                    Intent intent = new Intent(this, LoadBookActivity.class);
                    intent.putExtra(LoadBookActivity.EXTRA_URI, Uri.parse(Var.AUTOTEST_FILE_04));
                    intent.putExtra(LoadBookActivity.EXTRA_TYPE, "File");
                    loadOptionsActivityResultLauncher.launch(intent);
                }
            });
        });
    }

    private void lockButtons(boolean doLock) {
        //myLogD("LockButtons : " + doLock);
        try {
            // NOTE: fragment attach/remove is gone — the banner fragment self-hides.
            List<TextView> textViewToHide = Arrays.asList(
                    findViewById(R.id.TextHeaderOpen),
                    findViewById(R.id.bOpenFile_desc),
                    findViewById(R.id.bOpenFolder_desc),
                    findViewById(R.id.bOpenZipFile_desc),
                    findViewById(R.id.bOpenM4bFile_desc),
                    findViewById(R.id.bMassImport_desc),
                    findViewById(R.id.txtAutoTest_title),
                    findViewById(R.id.txtAutoTest_desc),
                    findViewById(R.id.txtDirectDownload_title),
                    findViewById(R.id.txtDirectDownload_desc)
            );
            List<Button> buttonsToLock = Arrays.asList(
                    bOpenFile, bOpenFolder, bOpenZipFile, bOpenM4bFile, bMassImport,
                    bAutoTest_b1, bAutoTest_b2, bAutoTest_b3, bAutoTest_b4, bDirectDownload
            );

            for (Button b : buttonsToLock) b.setEnabled(!doLock);
            for (TextView tv : textViewToHide) tv.setVisibility(doLock ? View.GONE : View.VISIBLE);
        } catch (Exception e) {
            myLogEE(e, "lockButtons(" + doLock + ")");
        }
    }

    public interface WWWCheckCallback { void onResult(boolean canReach); }
    private void checkWWW(WWWCheckCallback callback) {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            myToast("Aie. Network not available.");
            callback.onResult(false);
            return;
        }
        new Thread(() -> {
            boolean canReach = NetworkUtils.canReachUrl("https://bookplayer.driot.com");
            runOnUiThread(() -> {
                if (canReach) callback.onResult(true);
                else {
                    myToast("Aie. bookplayer.driot.com not reachable.");
                    callback.onResult(false);
                }
            });
        }).start();
    }

    private boolean isReturnedUriOk(Intent data) {
        try {
            Uri uri = data.getData();
            if (uri == null || uri.getPath() == null) {
                myToastE("checkDataOk : Error getting URI of selected item.");
                return false;
            }
            return true;
        } catch (Exception e) {
            myLogEE(e, "checkDataOk is KO");
            return false;
        }
    }

    private void scanThatShit() {
        String[] paths = { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() };
        String[] mimeTypes = {"*/*"};
        MediaScanner2.scanFileAndNotifyMediaScanner(this, paths[0], mimeTypes[0]);
    }

    public void openAppInfo() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", getPackageName(), null);
            intent.setData(uri);
            startActivity(intent);
        } catch (Exception e) { myLogEE(e, "openAppSettingsOnPhone()"); }
    }

    public void openOptionActivity() {
        try {
            startActivity(new Intent(this, SettingsActivity.class).putExtra("CopyFileSetRed", true));
        } catch (Exception e) { myLogEE(e, "openOptionActivity()"); }
    }

    private void askForPermission() {
        if (!isReadAudioPermissionGranted(this)) {
            myLog("askForPermission() -- NOT already granted => asking...");
            checkPermissionsReadStorage();
        } else {
            myLog("askForPermission() -- already granted...");
        }
    }

    private void checkPermissionsReadStorage() {
        if (Build.VERSION.SDK_INT < 33) {
            mPermissionRequest = PermissionRequest
                    .with(this)
                    .permissions(Manifest.permission.READ_EXTERNAL_STORAGE)
                    .rationale(R.string.permission_read_write_rationale)
                    .denied(R.string.permission_read_write_denied)
                    .snackbar((ViewGroup) findViewById(android.R.id.content))
                    .submit();
        } else {
            mPermissionRequest = PermissionRequest
                    .with(this)
                    .permissions(Manifest.permission.READ_MEDIA_AUDIO)
                    .rationale(R.string.permission_read_write_rationale)
                    .denied(R.string.permission_read_write_denied)
                    .snackbar((ViewGroup) findViewById(android.R.id.content))
                    .callback(new PermissionRequest.Callback() {
                        @Override public void onPermissionsGranted() { myLog("Granted"); }
                        @Override public void onPermissionsDenied() { myLog("Denied"); showPermissionDeniedDialog(); }
                    })
                    .submit();
        }
    }

    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.Permission_Required))
                .setMessage(R.string.permission_read_write_denied)
                .setPositiveButton(getString(R.string.App_Info), (dialog, which) -> openAppInfo())
                .setNeutralButton(getString(R.string.Settings), (dialog, which) -> openOptionActivity())
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (mPermissionRequest != null) {
            mPermissionRequest.onRequestPermissionsResult(requestCode, permissions, grantResults);
            mPermissionRequest = null;
        } else {
            myLogE("onRequestPermissionsResult() - mPermissionRequest is null ! bad hook");
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onResume() {
        super.onResume();

        viewModel = new ViewModelProvider(
                com.driot.bookplayer.objects.AppViewModelStoreOwner.getInstance(),
                ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication())
        ).get(OngoingTaskViewModel.class);

        myLogD("ViewModel instance: " + System.identityHashCode(viewModel));

        // Observe running flag and only lock/unlock buttons + labels.
        viewModel.isTaskRunning().observe(this, isRunning -> lockButtons(Boolean.TRUE.equals(isRunning)));
    }
}
