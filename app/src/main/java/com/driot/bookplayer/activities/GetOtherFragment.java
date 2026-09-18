package com.driot.bookplayer.activities;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.LayoutInflater;
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
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.helpers.UriHelper;
import com.driot.bookplayer.imports.ImportBookMultipleActivity;
import com.driot.bookplayer.imports.ImportBookSingleActivity;
import com.driot.bookplayer.imports.OngoingTaskViewModel;
import com.driot.bookplayer.settings.ui.ImportSettingsFragment;
import com.driot.bookplayer.settings.ui.MassiveImportSettingsFragment;
import com.driot.bookplayer.utils.MediaScanner2;
import com.driot.bookplayer.utils.MsgBox;
import com.driot.bookplayer.utils.PermissionRequest;
import com.driot.bookplayer.utils.log.LoggingFragment;

import static com.driot.bookplayer.utils.PermissionRequest.isReadAudioPermissionGranted;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class GetOtherFragment extends LoggingFragment {

    private static final int REQ_PERMISSION_DENIED = 2001;

    private View importDimScrim;
    private OngoingTaskViewModel viewModel;
    private TextView importDimMessage;

    private PermissionRequest mPermissionRequest;

    private Folder folderToAddTo;

    private ActivityResultLauncher<Intent> bOpenFileActivityResultLauncher,
            bOpenFolderActivityResultLauncher,
            loadBookActivityResultLauncher,
            bMassImportActivityResultLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // registerForActivityResult() must be called unconditionally during Fragment
        // initialization (onCreate), not onCreateView/onViewCreated.

        // ADD RESOURCE (log)
        registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    myLog("results from ActivityResultContracts.StartActivityForResult");
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        myLog("result OK - closing activity");
                        requireActivity().finish();
                    } else {
                        myLog("no ok result - doing nothing");
                    }
                });

        // SINGLE FILE
        bOpenFileActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> launchAddResource(result, "File"));

        // ZIP/M4B/EPUB (filtered) share the same launcher
        // FOLDER
        bOpenFolderActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> launchAddResource(result, "Folder"));

        // MASS IMPORT (folder)
        bMassImportActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> launchAddResource(result, "MassImport"));

        // RESULT LAUNCHER
        loadBookActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        startActivity(new Intent(requireContext(), MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
                    }
                });
    }

    private void launchAddResource(ActivityResult result, String type) {
        myLog("launchAddResource()");
        try {
            if (result.getResultCode() == Activity.RESULT_OK) {
                if (UriHelper.isReturnedUriOk(result.getData())) {
                    Uri uri = result.getData().getData();
                    myLog("-------------------------------------------------------------------------------------------------");
                    myLog("picked data : " + uri.getPath());
                    myLog("-------------------------------------------------------------------------------------------------");

                    requireContext().getContentResolver().takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                    if (type.equals("MassImport")) {

                        Intent intent = new Intent(requireContext(), ImportBookMultipleActivity.class);
                        intent.putExtra(ImportBookMultipleActivity.EXTRA_URI, uri);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                        startActivity(intent);
                        requireActivity().overridePendingTransition(0, 0);

                    } else {
                        Intent intent = new Intent(requireContext(), ImportBookSingleActivity.class);
                        intent.putExtra(ImportBookSingleActivity.EXTRA_URI, uri);
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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_get_other, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Button bOpenFile = view.findViewById(R.id.bOpenFile);
        Button bOpenZipFile = view.findViewById(R.id.bOpenZipFile);
        Button bOpenM4bFile = view.findViewById(R.id.bOpenM4bFile);
        Button bOpenEpubFile = view.findViewById(R.id.bOpenEpubFile);
        view.findViewById(R.id.ll_open_filtered_file).setVisibility(
                Option.getShowFilteredFileButtons() ? View.VISIBLE : View.GONE);
        Button bOpenFolder = view.findViewById(R.id.bOpenFolder);
        Button bMassImport = view.findViewById(R.id.bMassImport);
        Button bAutoTest_b1 = view.findViewById(R.id.bAutoTest_b1);
        Button bAutoTest_b2 = view.findViewById(R.id.bAutoTest_b2);
        Button bAutoTest_b3 = view.findViewById(R.id.bAutoTest_b3);
        Button bAutoTest_b4 = view.findViewById(R.id.bAutoTest_b4);

        folderToAddTo = null;
        Bundle args = getArguments();
        folderToAddTo = args != null ? args.getParcelable(Intents.EXTRA_ADD_TO_FOLDER) : null;
        if (folderToAddTo != null) {
            myLog("ADD NEW TRACKS MODE ---> to [" + folderToAddTo.getName() + "]");
        }

        view.findViewById(R.id.ibSettings).setOnClickListener(v -> clickSettings());
        view.findViewById(R.id.ibMassImportSettings).setOnClickListener(v -> clickMassImportSettings());

        importDimScrim = view.findViewById(R.id.importDimScrim);
        importDimMessage = view.findViewById(R.id.importDimMessage);

        // Eat all touches explicitly (belt & suspenders)
        importDimScrim.setOnTouchListener((v, ev) -> true);
        importDimScrim.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        importDimScrim.setContentDescription(getString(R.string.Import_in_progress));

        viewModel = new ViewModelProvider(this).get(OngoingTaskViewModel.class);

        // SINGLE FILE
        bOpenFile.setOnClickListener(v -> {
            myLogI("------------ USER CLICKS : button ANY file");
            if (isReadAudioPermissionGranted(requireContext()) || Option.getCopyFile()) {
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
        bOpenZipFile.setOnClickListener(v -> {
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
        bOpenM4bFile.setOnClickListener(v -> {
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
        bOpenEpubFile.setOnClickListener(v -> {
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
        bOpenFolder.setOnClickListener(v -> {
            myLogI("------------ USER CLICKS : button FOLDER");
            if (isReadAudioPermissionGranted(requireContext()) || Option.getCopyFile()) {
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
            view.findViewById(R.id.ll_massive_import).setVisibility(View.VISIBLE);
            view.findViewById(R.id.ll_append_mode).setVisibility(View.GONE);
            // MASS IMPORT (folder)
            bMassImport.setOnClickListener(v -> {
                myLogI("------------ USER CLICKS : button MASS IMPORT");
                if (isReadAudioPermissionGranted(requireContext()) || Option.getCopyFile()) {
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
            view.findViewById(R.id.ll_append_mode).setVisibility(View.VISIBLE);
            view.findViewById(R.id.ll_massive_import).setVisibility(View.GONE);
            view.findViewById(R.id.tv_load_one_book).setVisibility(View.GONE);
        }

        // Secret / auto-tests unchanged...
        View secretEntry = view.findViewById(R.id.viewSecretEntry);
        final long[] taps = new long[3];
        secretEntry.setOnClickListener(v -> {
            System.arraycopy(taps, 1, taps, 0, taps.length - 1);
            taps[taps.length - 1] = System.currentTimeMillis();
            if (taps[0] >= System.currentTimeMillis() - 1000) {
                myLogI("click on secret");
                LinearLayout llsecretDev = view.findViewById(R.id.llsecretDev);
                llsecretDev.setVisibility(View.VISIBLE);
            }
        });

        bAutoTest_b1.setOnClickListener(v -> {
            myLogI("Button click : AUTO TEST 01");
            checkWWW(canReach -> {
                if (canReach) {
                    Intent intent = new Intent(requireContext(), ImportBookSingleActivity.class);
                    intent.putExtra(ImportBookSingleActivity.EXTRA_URI, Uri.parse(Var.AUTOTEST_FILE_01));
                    loadBookActivityResultLauncher.launch(intent);
                }
            });
        });
        bAutoTest_b2.setOnClickListener(v -> {
            myLogI("Button click : AUTO TEST 02");
            checkWWW(canReach -> {
                if (canReach) {
                    Intent intent = new Intent(requireContext(), ImportBookSingleActivity.class);
                    intent.putExtra(ImportBookSingleActivity.EXTRA_URI, Uri.parse(Var.AUTOTEST_FILE_02));
                    loadBookActivityResultLauncher.launch(intent);
                }
            });
        });
        bAutoTest_b3.setOnClickListener(v -> {
            myLogI("Button click : AUTO TEST 03");
            checkWWW(canReach -> {
                if (canReach) {
                    Intent intent = new Intent(requireContext(), ImportBookSingleActivity.class);
                    intent.putExtra(ImportBookSingleActivity.EXTRA_URI, Uri.parse(Var.AUTOTEST_FILE_03));
                    loadBookActivityResultLauncher.launch(intent);
                }
            });
        });
        bAutoTest_b4.setOnClickListener(v -> {
            myLogI("Button click : AUTO TEST 04");
            checkWWW(canReach -> {
                if (canReach) {
                    Intent intent = new Intent(requireContext(), ImportBookSingleActivity.class);
                    intent.putExtra(ImportBookSingleActivity.EXTRA_URI, Uri.parse(Var.AUTOTEST_FILE_04));
                    loadBookActivityResultLauncher.launch(intent);
                }
            });
        });

    }

    public interface WWWCheckCallback {
        void onResult(boolean canReach);
    }

    private void checkWWW(WWWCheckCallback callback) {
        if (!NetworkHelper.isNetworkAvailable(requireContext())) {
            myToast(getString(com.driot.bookplayer.R.string.error_network_not_available));
            callback.onResult(false);
            return;
        }
        new Thread(() -> {
            boolean canReach = NetworkHelper.canReachUrl("https://bookplayer.driot.com");
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
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
        MediaScanner2.scanFileAndNotifyMediaScanner(requireContext(), paths[0], mimeTypes[0]);
    }

    public void openAppInfo() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", requireContext().getPackageName(), null);
            intent.setData(uri);
            startActivity(intent);
        } catch (Exception e) {
            myLogEE(e, "openAppSettingsOnPhone()");
        }
    }

    public void openOptionActivity() {
        try {
            startActivity(new Intent(requireContext(), SettingsHostActivity.class));
        } catch (Exception e) {
            myLogEE(e, "openOptionActivity()");
        }
    }

    private void askForPermission() {
        if (!isReadAudioPermissionGranted(requireContext())) {
            myLog("askForPermission() -- NOT already granted => asking...");
            checkPermissionsReadStorage();
        } else {
            myLog("askForPermission() -- already granted...");
        }
    }

    private void checkPermissionsReadStorage() {
        if (Build.VERSION.SDK_INT < 33) {
            mPermissionRequest = PermissionRequest
                    .with(requireActivity())
                    .permissions(Manifest.permission.READ_EXTERNAL_STORAGE)
                    .rationale(R.string.permission_read_write_rationale)
                    .denied(R.string.permission_read_write_denied)
                    .snackbar((ViewGroup) requireView().findViewById(android.R.id.content))
                    .submit();
        } else {
            mPermissionRequest = PermissionRequest
                    .with(requireActivity())
                    .permissions(Manifest.permission.READ_MEDIA_AUDIO)
                    .rationale(R.string.permission_read_write_rationale)
                    .denied(R.string.permission_read_write_denied)
                    .snackbar((ViewGroup) requireView().findViewById(android.R.id.content))
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
        Intent neutralIntent = new Intent(requireContext(), SettingsHostActivity.class);
        MsgBox.alertWithNeutral(requireContext(),
                getString(R.string.Permission_Required),
                getString(R.string.permission_read_write_denied),
                null,
                getString(R.string.Settings),
                neutralIntent);
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

        View root = getView() != null ? getView().findViewById(R.id.rootContainer) : null;
        if (root != null) {
            root.setImportantForAccessibility(
                    show ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                            : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        }

        importDimMessage.setText(getString(R.string.please_wait_another_book_is_being_imported));
    }

    private void clickSettings() {
        myLogI("--- User clicks SETTINGS ---");
        SettingsHostActivity.start(requireContext(), ImportSettingsFragment.class, true, R.string.import_settings);
    }

    private void clickMassImportSettings() {
        myLogI("--- User clicks MASS IMPORT SETTINGS ---");
        SettingsHostActivity.start(requireContext(), MassiveImportSettingsFragment.class, true, R.string.Mass_Import);
    }

}
