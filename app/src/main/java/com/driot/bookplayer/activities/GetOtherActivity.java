package com.driot.bookplayer.activities;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
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
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.imports.ImportHelper;
import com.driot.bookplayer.imports.ImportJob;
import com.driot.bookplayer.imports.ImportJobRepository;
import com.driot.bookplayer.imports.ImportWorker;
import com.driot.bookplayer.imports.LoadBookActivity;
import com.driot.bookplayer.imports.OngoingTaskViewModel;
import com.driot.bookplayer.settings.ui.ImportSettingsFragment;
import com.driot.bookplayer.utils.MediaScanner2;
import com.driot.bookplayer.utils.PermissionRequest;

import static com.driot.bookplayer.utils.PermissionRequest.isReadAudioPermissionGranted;

import java.util.UUID;
import java.util.concurrent.Executors;

public class GetOtherActivity extends BaseBottomNavActivity {

    private View importDimScrim;
    private OngoingTaskViewModel viewModel;
    private TextView importDimMessage;

    private PermissionRequest mPermissionRequest;

    private Folder folderToAddTo;

    private ActivityResultLauncher<Intent> bOpenFileActivityResultLauncher,
            bOpenFolderActivityResultLauncher,
            loadBookActivityResultLauncher,
            bMassImportActivityResultLauncher;

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

                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            Executors.newSingleThreadExecutor().execute(() -> {
                                String importId = "mass_import_" + UUID.randomUUID();
                                ImportJob j = new ImportJob();
                                j.importId = importId;
                                j.status = ImportJob.S_RUNNING;
                                j.createdAt = j.updatedAt = System.currentTimeMillis();
                                j.sourceLocation = Var.WORKER_MASS_IMPORT;
                                j.showToUser = true;
                                j.title = getString(R.string.Mass_Import);
                                ImportJobRepository repo = new ImportJobRepository(this.getApplicationContext());
                                repo.upsert(j);
                                ImportHelper.setShowToUser(this.getApplicationContext(), true);
                                myLogD("ImportJobRepository populated (for proper UI display), now worker code");
                                WorkManager.getInstance(this).enqueue(
                                        new OneTimeWorkRequest.Builder(com.driot.bookplayer.services.ScanAndReimportWorker.class)
                                                .setInputData(new Data.Builder()
                                                        .putString(ImportWorker.KEY_IMPORT_ID, importId)
                                                        .putString(com.driot.bookplayer.services.ScanAndReimportWorker.K_ROOT_TREE_URI, uri.toString())
                                                        .putString(com.driot.bookplayer.services.ScanAndReimportWorker.K_SOURCE_LOC, "MassImport")
                                                        .build())
                                                .addTag("BulkReimportScan")
                                                .build()
                                );
                                myLogD("and open activity");
                                startActivity(new Intent(this, AddResourceActivity.class));
                            });
                        }, 0);

                    } else {
                        Intent intent = new Intent(this, LoadBookActivity.class);
                        intent.putExtra(LoadBookActivity.EXTRA_URI, uri);
                        intent.putExtra(LoadBookActivity.EXTRA_TYPE, type);
                        if (folderToAddTo!=null) intent.putExtra(Intents.EXTRA_ADD_TO_FOLDER, folderToAddTo);
                        loadBookActivityResultLauncher.launch(intent);
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

    @Override protected int getNavId() { return R.id.nav_add; }
    @Override protected int getLayoutResId() { return R.layout.activity_get_other; }
    @Override protected boolean enableOngoingTaskOverlay() { return true; }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        Button bOpenFile = findViewById(R.id.bOpenFile);
        Button bOpenZipFile = findViewById(R.id.bOpenZipFile);
        Button bOpenM4bFile = findViewById(R.id.bOpenM4bFile);
        Button bOpenFolder = findViewById(R.id.bOpenFolder);
        Button bMassImport = findViewById(R.id.bMassImport);
        Button bAutoTest_b1 = findViewById(R.id.bAutoTest_b1);
        Button bAutoTest_b2 = findViewById(R.id.bAutoTest_b2);
        Button bAutoTest_b3 = findViewById(R.id.bAutoTest_b3);
        Button bAutoTest_b4 = findViewById(R.id.bAutoTest_b4);

        folderToAddTo = null;
        folderToAddTo = getIntent().getParcelableExtra(Intents.EXTRA_ADD_TO_FOLDER);
        if (folderToAddTo != null) {
            myLog("ADD NEW TRACKS MODE ---> to [" + folderToAddTo.getName() + "]");
        }

        findViewById(R.id.ibSettings).setOnClickListener(v -> clickSettings());

        importDimScrim = findViewById(R.id.importDimScrim);
        importDimMessage = findViewById(R.id.importDimMessage);

        // Eat all touches explicitly (belt & suspenders)
        importDimScrim.setOnTouchListener((v, ev) -> true);
        importDimScrim.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        importDimScrim.setContentDescription(getString(R.string.Import_in_progress));

        viewModel = new ViewModelProvider(
                com.driot.bookplayer.objects.AppViewModelStoreOwner.getInstance(),
                ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication())
        ).get(OngoingTaskViewModel.class);
        //myLogD("ViewModel instance: " + System.identityHashCode(viewModel));

        viewModel.getUi().observe(this, ui -> {
            setImportOverlayVisible(ui.isRunningLike());
        });

        // ADD RESOURCE  (log)
        registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    myLog("results from ActivityResultContracts.StartActivityForResult");
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        myLog("result OK - closing activity");
                        finish();
                    } else {
                        myLog("no ok result - doing nothing");
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

        if (folderToAddTo==null) {
            findViewById(R.id.ll_massive_import).setVisibility(View.VISIBLE);
            findViewById(R.id.ll_append_mode).setVisibility(View.GONE);
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
        } else {
            findViewById(R.id.ll_append_mode).setVisibility(View.VISIBLE);
            findViewById(R.id.ll_massive_import).setVisibility(View.GONE);
        }


// RESULT LAUNCHER

        loadBookActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        startActivity(new Intent(this, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
                        //startActivity(new Intent(this, AddResourceActivity.class));
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
                myLogI("click on secret");
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
                    loadBookActivityResultLauncher.launch(intent);
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
                    loadBookActivityResultLauncher.launch(intent);
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
                    loadBookActivityResultLauncher.launch(intent);
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
                    loadBookActivityResultLauncher.launch(intent);
                }
            });
        });

    }

    public interface WWWCheckCallback { void onResult(boolean canReach); }
    private void checkWWW(WWWCheckCallback callback) {
        if (!NetworkHelper.isNetworkAvailable(this)) {
            myToast("Aie. Network not available.");
            callback.onResult(false);
            return;
        }
        new Thread(() -> {
            boolean canReach = NetworkHelper.canReachUrl("https://bookplayer.driot.com");
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

    private void setImportOverlayVisible(boolean show) {
        if (importDimScrim == null) return;

        final float target = show ? 1f : 0f;
        if (show && importDimScrim.getVisibility() != View.VISIBLE) {
            importDimScrim.setAlpha(0f);
            importDimScrim.setVisibility(View.VISIBLE);
        }
        importDimScrim.animate()
                .alpha(target)
                .setDuration(180)
                .withEndAction(() -> {
                    if (!show) importDimScrim.setVisibility(View.GONE);
                })
                .start();

        View root = findViewById(R.id.rootContainer);
        if (root != null) {
            root.setImportantForAccessibility(
                    show ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                            : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        }

        importDimMessage.setText(getString(R.string.please_wait_another_book_is_being_imported));
    }


    private void clickSettings() {
        myLogI("--- User clicks SETTINGS ---");
        SettingsHostActivity.start(this, ImportSettingsFragment.class, true, R.string.import_settings);
    }

}
