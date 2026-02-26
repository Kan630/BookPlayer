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

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.helpers.UriHelper;
import com.driot.bookplayer.imports.ImportBookMultipleActivity;
import com.driot.bookplayer.imports.ImportBookSingleActivity;
import com.driot.bookplayer.imports.OngoingTaskViewModel;
import com.driot.bookplayer.nav.BaseBottomNavActivity;
import com.driot.bookplayer.settings.ui.ImportSettingsFragment;
import com.driot.bookplayer.settings.ui.MassiveImportSettingsFragment;
import com.driot.bookplayer.utils.MediaScanner2;
import com.driot.bookplayer.utils.PermissionRequest;

import static com.driot.bookplayer.utils.PermissionRequest.isReadAudioPermissionGranted;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
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
                if (UriHelper.isReturnedUriOk(result.getData())) {
                    Uri uri = result.getData().getData();
                    myLog("picked data : " + uri.getPath());

                    getContentResolver().takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                    if (type.equals("MassImport")) {

                        Intent intent = new Intent(this, ImportBookMultipleActivity.class);
                        intent.putExtra(ImportBookMultipleActivity.EXTRA_URI, uri);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                        startActivity(intent);
                        overridePendingTransition(0, 0);

                    } else {
                        Intent intent = new Intent(this, ImportBookSingleActivity.class);
                        intent.putExtra(ImportBookSingleActivity.EXTRA_URI, uri);
                        intent.putExtra(ImportBookSingleActivity.EXTRA_TYPE, type);
                        if (folderToAddTo != null)
                            intent.putExtra(Intents.EXTRA_ADD_TO_FOLDER, folderToAddTo);
                        loadBookActivityResultLauncher.launch(intent);
                    }
                } else {
                    myLogE("returned Uri not OK");
                }
            } else {
                myLog("result code not OK -- no item picked?");
            }
        } catch (Exception e) {
            myToastEE(e, "Error reading picked object");
        }
    }

    @Override
    protected int getNavId() {
        return R.id.nav_add;
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_get_other;
    }

    @Override
    protected boolean enableOngoingTaskOverlay() {
        return true;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        Button bOpenFile = findViewById(R.id.bOpenFile);
        Button bOpenZipFile = findViewById(R.id.bOpenZipFile);
        Button bOpenM4bFile = findViewById(R.id.bOpenM4bFile);
        Button bOpenEpubFile = findViewById(R.id.bOpenEpubFile);
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
        findViewById(R.id.ibMassImportSettings).setOnClickListener(v -> clickMassImportSettings());

        importDimScrim = findViewById(R.id.importDimScrim);
        importDimMessage = findViewById(R.id.importDimMessage);

        // Eat all touches explicitly (belt & suspenders)
        importDimScrim.setOnTouchListener((v, ev) -> true);
        importDimScrim.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        importDimScrim.setContentDescription(getString(R.string.Import_in_progress));

        viewModel = new ViewModelProvider(this).get(OngoingTaskViewModel.class);
        // myLogD("ViewModel instance: " + System.identityHashCode(viewModel));

        // viewModel.getUi().observe(this, ui -> {
        // setImportOverlayVisible(ui.isRunningLike());
        // });

        // ADD RESOURCE (log)
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
                result -> {
                    launchAddResource(result, "File");
                });
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
            String[] mimeTypes = { "application/zip", "application/x-zip-compressed" };
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            bOpenFileActivityResultLauncher.launch(intent);
        });

        // M4B (filter like ZIP: only M4B)
        bOpenM4bFile.setOnClickListener(view -> {
            scanThatShit();
            myLogI("------------ USER CLICKS : button M4B file");
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("*/*");
            String[] mimeTypes = { "audio/mp4", "audio/x-m4a" };
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            bOpenFileActivityResultLauncher.launch(intent);
        });

        // EPUB (filter like ZIP: only EPUB)
        bOpenEpubFile.setOnClickListener(view -> {
            scanThatShit();
            myLogI("------------ USER CLICKS : button EPUB file");
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("*/*");
            String[] mimeTypes = { "application/epub+zip" };
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
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
                result -> {
                    launchAddResource(result, "Folder");
                });
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

        if (folderToAddTo == null) {
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
            findViewById(R.id.tv_load_one_book).setVisibility(View.GONE);
        }

        // RESULT LAUNCHER

        loadBookActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        startActivity(new Intent(this, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
                        // startActivity(new Intent(this, AddResourceActivity.class));
                    }
                });

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
                    Intent intent = new Intent(this, ImportBookSingleActivity.class);
                    intent.putExtra(ImportBookSingleActivity.EXTRA_URI, Uri.parse(Var.AUTOTEST_FILE_01));
                    intent.putExtra(ImportBookSingleActivity.EXTRA_TYPE, "File");
                    loadBookActivityResultLauncher.launch(intent);
                }
            });
        });
        bAutoTest_b2.setOnClickListener(view -> {
            myLogI("Button click : AUTO TEST 02");
            checkWWW(canReach -> {
                if (canReach) {
                    Intent intent = new Intent(this, ImportBookSingleActivity.class);
                    intent.putExtra(ImportBookSingleActivity.EXTRA_URI, Uri.parse(Var.AUTOTEST_FILE_02));
                    intent.putExtra(ImportBookSingleActivity.EXTRA_TYPE, "File");
                    loadBookActivityResultLauncher.launch(intent);
                }
            });
        });
        bAutoTest_b3.setOnClickListener(view -> {
            myLogI("Button click : AUTO TEST 03");
            checkWWW(canReach -> {
                if (canReach) {
                    Intent intent = new Intent(this, ImportBookSingleActivity.class);
                    intent.putExtra(ImportBookSingleActivity.EXTRA_URI, Uri.parse(Var.AUTOTEST_FILE_03));
                    intent.putExtra(ImportBookSingleActivity.EXTRA_TYPE, "File");
                    loadBookActivityResultLauncher.launch(intent);
                }
            });
        });
        bAutoTest_b4.setOnClickListener(view -> {
            myLogI("Button click : AUTO TEST 04");
            checkWWW(canReach -> {
                if (canReach) {
                    Intent intent = new Intent(this, ImportBookSingleActivity.class);
                    intent.putExtra(ImportBookSingleActivity.EXTRA_URI, Uri.parse(Var.AUTOTEST_FILE_04));
                    intent.putExtra(ImportBookSingleActivity.EXTRA_TYPE, "File");
                    loadBookActivityResultLauncher.launch(intent);
                }
            });
        });

    }

    public interface WWWCheckCallback {
        void onResult(boolean canReach);
    }

    private void checkWWW(WWWCheckCallback callback) {
        if (!NetworkHelper.isNetworkAvailable(this)) {
            myToast(getString(com.driot.bookplayer.R.string.error_network_not_available));
            callback.onResult(false);
            return;
        }
        new Thread(() -> {
            boolean canReach = NetworkHelper.canReachUrl("https://bookplayer.driot.com");
            runOnUiThread(() -> {
                if (canReach)
                    callback.onResult(true);
                else {
                    myToast(getString(com.driot.bookplayer.R.string.error_server_not_reachable));
                    callback.onResult(false);
                }
            });
        }).start();
    }

    private void scanThatShit() {
        String[] paths = {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() };
        String[] mimeTypes = { "*/*" };
        MediaScanner2.scanFileAndNotifyMediaScanner(this, paths[0], mimeTypes[0]);
    }

    public void openAppInfo() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", getPackageName(), null);
            intent.setData(uri);
            startActivity(intent);
        } catch (Exception e) {
            myLogEE(e, "openAppSettingsOnPhone()");
        }
    }

    public void openOptionActivity() {
        try {
            startActivity(new Intent(this, SettingsActivity.class).putExtra("CopyFileSetRed", true));
        } catch (Exception e) {
            myLogEE(e, "openOptionActivity()");
        }
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
                        @Override
                        public void onPermissionsGranted() {
                            myLog("Granted");
                        }

                        @Override
                        public void onPermissionsDenied() {
                            myLog("Denied");
                            showPermissionDeniedDialog();
                        }
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
                .setNegativeButton(android.R.string.cancel, null)
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
        if (importDimScrim == null)
            return;

        final float target = show ? 1f : 0f;
        if (show && importDimScrim.getVisibility() != View.VISIBLE) {
            importDimScrim.setAlpha(0f);
            importDimScrim.setVisibility(View.VISIBLE);
        }
        importDimScrim.animate()
                .alpha(target)
                .setDuration(180)
                .withEndAction(() -> {
                    if (!show)
                        importDimScrim.setVisibility(View.GONE);
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

    private void clickMassImportSettings() {
        myLogI("--- User clicks MASS IMPORT SETTINGS ---");
        SettingsHostActivity.start(this, MassiveImportSettingsFragment.class, true, R.string.Mass_Import);
    }

}
