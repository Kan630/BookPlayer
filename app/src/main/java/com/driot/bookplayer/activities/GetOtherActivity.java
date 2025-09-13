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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.objects.AppViewModelStoreOwner;
import com.driot.bookplayer.objects.LanguageItem;
import com.driot.bookplayer.helpers.AnalyticsHelper;
import com.driot.bookplayer.services.BookLoadingWorkLauncher;
import com.driot.bookplayer.services.ScanAndReimportWorker;
import com.driot.bookplayer.views.EditTextWithButtons;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.utils.LanguageHelper;
import com.driot.bookplayer.utils.MediaScanner2;
import com.driot.bookplayer.utils.NetworkUtils;
import com.driot.bookplayer.utils.PermissionRequest;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.util.Arrays;
import java.util.List;

import static com.driot.bookplayer.utils.PermissionRequest.isReadAudioPermissionGranted;
import static com.driot.bookplayer.objects.WorkFlow.maybeResumeWorkFlow;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 08/11/20
 */
public class GetOtherActivity extends LoggingActivity { //AppCompatActivity
    private Button bOpenFile, bOpenFolder, bOpenZipFile, bOpenM4bFile, bMassImport;
    private Button bAutoTest_b1, bAutoTest_b2, bAutoTest_b3, bAutoTest_b4, bDirectDownload;

    private PermissionRequest mPermissionRequest;

    private OngoingTaskViewModel viewModel;

    private ActivityResultLauncher<Intent>
            bOpenFileActivityResultLauncher
            ,bOpenFolderActivityResultLauncher
            ,loadOptionsActivityResultLauncher
            ,bMassImportActivityResultLauncher
            ;

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
                                new OneTimeWorkRequest.Builder(ScanAndReimportWorker.class)
                                        .setInputData(new Data.Builder()
                                                .putString(ScanAndReimportWorker.K_ROOT_TREE_URI, uri.toString())
                                                .putString(ScanAndReimportWorker.K_SOURCE_LOC, "MassImport")
                                                .build())
                                        .addTag("BulkReimportScan")
                                        .build()
                        );
                        Intent intentActivity = new Intent(this, AddResourceActivity.class);
                        startActivity(intentActivity);

                    } else {

                        Intent intent = new Intent(this, LoadOptionsActivity.class);
                        intent.putExtra(LoadOptionsActivity.EXTRA_URI, uri);
                        intent.putExtra(LoadOptionsActivity.EXTRA_TYPE, type);
                        loadOptionsActivityResultLauncher.launch(intent);

                    }

                } else {
                    myLogE("returned Uri not OK");
                }
            } else {
                myLogE("result code not OK");
            }
        } catch (Exception e) {
            myToastEE(e,"Error reading picked object");
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_get_other);

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

// ADD RESOURCE
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
        bOpenFileActivityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> { launchAddResource(result, "File"); });
        bOpenFile.setOnClickListener(view -> {
            myLogI("------------ USER CLICKS : button ANY file");
            if (isReadAudioPermissionGranted(this) || Option.getCopyFile()) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("*/*");
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                //CATEGORY_OPENABLE => able to use : ContentResolver#openFileDescriptor(Uri, String)
                //can be opened as a File object i.e. with read and write permissions and have complete access to the physical location of the data
                bOpenFileActivityResultLauncher.launch(intent);
            } else {
                askForPermission();
            }
        });

// ZIP
        // TODO sadly, honor with android 7 wont let you select a zip file, the file picker will open the content... instead of selecting it
        // TODO => try ACTION_PICK ?
        bOpenZipFile.setOnClickListener(view -> {
            scanThatShit();
            myLogI("------------ USER CLICKS : button ZIP file");
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            // TODO ACTION_GET_CONTENT should be enough since we copy locally...
            // ACTION_PICK could be interesting.... as an option..
            //intent.setType("application/zip");
            intent.setType("*/*");
            String[] mimeTypes = {"application/zip", "application/x-zip-compressed"};
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            bOpenFileActivityResultLauncher.launch(intent);
        });

// M4B
        bOpenM4bFile.setOnClickListener(view -> {
            scanThatShit();
            myLogI("------------ USER CLICKS : button AUDIO file");

            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("*/*");

            // Use a broader list of supported MIME types
            String[] mimeTypes = {
                    "audio/*"
                    //,"audio/mp4"       // common for m4b/m4a
                    //,"audio/x-m4a"     // fallback
                    //,"audio/mpeg"      // in case some mislabel it
                    //,"application/octet-stream" // catch-all
            };
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);

            intent.setFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                            | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            );
            intent.addCategory(Intent.CATEGORY_OPENABLE);

            bOpenFileActivityResultLauncher.launch(intent);
        });

// FOLDER
        bOpenFolderActivityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> { launchAddResource(result, "Folder"); });
        bOpenFolder.setOnClickListener(view -> {
            myLogI("------------ USER CLICKS : button FOLDER");
            if (isReadAudioPermissionGranted(this) || Option.getCopyFile()) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE); //API 21
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                bOpenFolderActivityResultLauncher.launch(intent);
            } else {
                askForPermission();
            }
        });

// FOLDER
        bMassImportActivityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> launchAddResource(result, "MassImport"));
        bMassImport.setOnClickListener(view -> {
            myLogI("------------ USER CLICKS : button MASS IMPORT");
            if (isReadAudioPermissionGranted(this) || Option.getCopyFile()) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE); //API 21
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                bMassImportActivityResultLauncher.launch(intent);
            } else {
                askForPermission();
            }
        });






        loadOptionsActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {

                        BookLoadingWorkLauncher.launch(this);
                        /*
                        Intent intentService = new Intent(this, AddResourceService.class);
                        intentService.putExtra("LoadBookTaskState", getLoadBookTaskState(this));
                        startService(intentService);
                        */

                        Intent intentActivity = new Intent(this, AddResourceActivity.class);
                        startActivity(intentActivity);

                    }
                }
        );

        ////////////////////////////////
        //// DIRECT DOWNLOAD - JUST GET IT
        ////////////////////////////////

        bDirectDownload.setOnClickListener(view -> {
            myLog("Button click : DIRECT DOWNLOAD");
            EditTextWithButtons editTextDirectDownload = findViewById(R.id.etDirectDownload);

            String url = editTextDirectDownload.getText();

            if (url.isEmpty()) {
                myToast(getString(R.string.Please_enter_a_URL));
                return;
            }
            myLog("url : [" + url + "]");

            Uri uri = Uri.parse(url);
            myLog("uri : [" + uri.toString() + "]");

            if (uri.getPath() == null) {
                myToastE("Error parsing url.");
                return;
            }
            String type = "File";

            Intent intent = new Intent(this, LoadOptionsActivity.class);
            intent.putExtra(LoadOptionsActivity.EXTRA_URI, uri);
            intent.putExtra(LoadOptionsActivity.EXTRA_TYPE, type);
            loadOptionsActivityResultLauncher.launch(intent);
        });



// ---------------------------------------------------------------------------------------------------------------------------------------------------------------------
        View secretEntry = findViewById(R.id.viewSecretEntry);
        final long[] taps = new long[3];
        secretEntry.setOnClickListener(v -> {
            System.arraycopy(taps, 1, taps, 0, taps.length - 1);
            taps[taps.length - 1] = System.currentTimeMillis();

            if (taps[0] >= System.currentTimeMillis() - 1000) {
                LinearLayout llsecretDev = findViewById(R.id.llsecretDev);
                llsecretDev.setVisibility(View.VISIBLE);
                //startActivity(new Intent(this, DebugDatabaseActivity.class));
            }
        });
        ////////////////////////////////
        ///// AUTO TEST
        ////////////////////////////////
        bAutoTest_b1.setOnClickListener(view -> {
            myLogI("Button click : AUTO TEST 01");
            checkWWW(canReach -> {
                if (canReach) {
                    Intent intent = new Intent(this, LoadOptionsActivity.class);
                    intent.putExtra(LoadOptionsActivity.EXTRA_URI, Uri.parse(Var.AUTOTEST_FILE_01));
                    intent.putExtra(LoadOptionsActivity.EXTRA_TYPE, "File");
                    loadOptionsActivityResultLauncher.launch(intent);
                }
            });
        });
        bAutoTest_b2.setOnClickListener(view -> {
            myLogI("Button click : AUTO TEST 02");
            checkWWW(canReach -> {
                if (canReach) {
                    Intent intent = new Intent(this, LoadOptionsActivity.class);
                    intent.putExtra(LoadOptionsActivity.EXTRA_URI, Uri.parse(Var.AUTOTEST_FILE_02));
                    intent.putExtra(LoadOptionsActivity.EXTRA_TYPE, "File");
                    loadOptionsActivityResultLauncher.launch(intent);
                }
            });
        });
        bAutoTest_b3.setOnClickListener(view -> {
            myLogI("Button click : AUTO TEST 03");
            checkWWW(canReach -> {
                if (canReach) {
                    Intent intent = new Intent(this, LoadOptionsActivity.class);
                    intent.putExtra(LoadOptionsActivity.EXTRA_URI, Uri.parse(Var.AUTOTEST_FILE_03));
                    intent.putExtra(LoadOptionsActivity.EXTRA_TYPE, "File");
                    loadOptionsActivityResultLauncher.launch(intent);
                }
            });
        });
        bAutoTest_b4.setOnClickListener(view -> {
            myLogI("Button click : AUTO TEST 04");
            checkWWW(canReach -> {
                if (canReach) {
                    Intent intent = new Intent(this, LoadOptionsActivity.class);
                    intent.putExtra(LoadOptionsActivity.EXTRA_URI, Uri.parse(Var.AUTOTEST_FILE_04));
                    intent.putExtra(LoadOptionsActivity.EXTRA_TYPE, "File");
                    loadOptionsActivityResultLauncher.launch(intent);
                }
            });
        });

    }
    // ---------------------------------------------------------------------------------------------------------------------------------------------------------------------
    // --------------- END OnCreate()     ----------------------------------------------------------------------------------------------------------------------------------
    // ---------------------------------------------------------------------------------------------------------------------------------------------------------------------


    private void lockButtons(boolean doLock) {
        myLogD("LockButtons : " + doLock);
        try {
            FragmentManager fm = getSupportFragmentManager();
            Fragment current = fm.findFragmentById(R.id.topOverlayContainer);
            List<TextView> textViewToHide = Arrays.asList(
                    findViewById(R.id.TextHeaderOpen)
                    ,findViewById(R.id.bOpenFile_desc)
                    ,findViewById(R.id.bOpenFolder_desc)
                    ,findViewById(R.id.bOpenZipFile_desc)
                    ,findViewById(R.id.bOpenM4bFile_desc)
                    ,findViewById(R.id.bMassImport_desc)
                    ,findViewById(R.id.txtAutoTest_title)
                    ,findViewById(R.id.txtAutoTest_desc)
                    ,findViewById(R.id.txtDirectDownload_title)
                    ,findViewById(R.id.txtDirectDownload_desc)
            );
            List<Button> buttonsToLock = Arrays.asList(bOpenFile, bOpenFolder, bOpenZipFile, bOpenM4bFile, bMassImport
                    , bAutoTest_b1, bAutoTest_b2, bAutoTest_b3, bAutoTest_b4, bDirectDownload);
            if (doLock) {
                myLog("SomeWorkFlowRunning => displaying banner, disabling buttons");
                if (!(current instanceof OngoingTaskFragment)) {
                    myLogD("display OngoingTaskFragment");
                    fm.beginTransaction()
                            .replace(R.id.topOverlayContainer, new OngoingTaskFragment())
                            .commit();
                }
                for (Button b: buttonsToLock) { b.setEnabled(false); }
                for (TextView tv: textViewToHide) { tv.setVisibility(View.GONE); }
            } else {
                myLogD("No WorkFlowRunning");
                if (current instanceof OngoingTaskFragment) {
                    myLogD("remove OngoingTaskFragment");
                    fm.beginTransaction()
                            .remove(current)
                            .commit();
                }
                for (Button b: buttonsToLock) { b.setEnabled(true); }
                for (TextView tv: textViewToHide) { tv.setVisibility(View.VISIBLE); }
            }
        } catch (Exception e) {
            myLogEE(e,"Error while checking if service is running");
        }
    }



    public interface WWWCheckCallback {
        void onResult(boolean canReach);
    }
    private void checkWWW(WWWCheckCallback callback) {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            myToast("Aie. Network not available.");
            callback.onResult(false); // Notify failure
            return;
        }

        new Thread(() -> {
            boolean canReach = NetworkUtils.canReachUrl("https://bookplayer.driot.com");
            runOnUiThread(() -> {
                if (canReach) {
                    callback.onResult(true); // Notify success
                } else {
                    myToast("Aie. bookplayer.driot.com not reachable.");
                    callback.onResult(false); // Notify failure
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
            myLogEE(e,"checkDataOk is KO");
            return false;
        }
    }

    private void scanThatShit() {
        String[] paths = {Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath()}; // Replace with the actual file path
        String[] mimeTypes = {"*/*"}; // Replace with the appropriate MIME type
        MediaScanner2.scanFileAndNotifyMediaScanner(this, paths[0], mimeTypes[0]);
    }

    public void openAppInfo() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", getPackageName(), null);
            intent.setData(uri);
            startActivity(intent);
        } catch (Exception e) {
            myLogEE(e,"openAppSettingsOnPhone()");
        }
    }
    public void openOptionActivity() {
        try {
            startActivity(new Intent(this, OptionActivity.class).putExtra("CopyFileSetRed",true));
        } catch (Exception e) {
            myLogEE(e,"openOptionActivity()");
        }
    }








    // -------------------------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------
    // --     PERMISSIONS
    // -------------------------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------

    private void askForPermission() {
        if (!isReadAudioPermissionGranted(this)) {
            myLog("askForPermission() -- NOT already granted => asking...");
            checkPermissionsReadStorage();
        } else {
            myLog("askForPermission() -- already granted...");
        }
    }

    private void checkPermissionsReadStorage() {
        if(Build.VERSION.SDK_INT < 33) {
            myLog("checkPermissionsReadStorage() < 33");
            mPermissionRequest = PermissionRequest
                    .with(this)
                    .permissions(Manifest.permission.READ_EXTERNAL_STORAGE) //Manifest.permission.READ_EXTERNAL_STORAGE,
                    .rationale(R.string.permission_read_write_rationale)
                    //.granted(R.string.permission_read_write_granted)  // Tonio no need to display message if granted OK
                    .denied(R.string.permission_read_write_denied)
                    .snackbar((ViewGroup) findViewById(android.R.id.content))
                    .submit();
        } else {
            myLog("checkPermissionsReadStorage() >= 33");
            mPermissionRequest = PermissionRequest
                    .with(this)
                    .permissions(Manifest.permission.READ_MEDIA_AUDIO) //Manifest.permission.READ_EXTERNAL_STORAGE,
                    .rationale(R.string.permission_read_write_rationale)
                    //.granted(R.string.permission_read_write_granted)  // Tonio no need to display message if granted OK
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
                            showPermissionDeniedDialog(); //ask user again... //not working yet...
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
                .setNeutralButton(getString(R.string.Options), (dialog, which) -> openOptionActivity())
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        for (int i=0;i<grantResults.length;i++) {
            myLog(permissions[i] + " => " + grantResults[i] + "   -requestCode=" + requestCode);
        }
        myLog("onRequestPermissionsResult() : " + permissions[0] + " - " + requestCode + " - " + grantResults[0]);
        // Redirect hook call to permission helper method.
        if (mPermissionRequest != null) {
            mPermissionRequest.onRequestPermissionsResult(requestCode, permissions, grantResults);
            mPermissionRequest = null; // request no longer needed
        } else {
            myLogE("onRequestPermissionsResult() - mPermissionRequest is null ! bad hook");
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onResume() {
        super.onResume();

        viewModel = new ViewModelProvider(
                AppViewModelStoreOwner.getInstance(),
                ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication())
        ).get(OngoingTaskViewModel.class);
        myLogD("ViewModel instance: " + System.identityHashCode(viewModel));
        viewModel.isTaskRunning().observe(this, isRunning -> {
            lockButtons(Boolean.TRUE.equals(isRunning));
        });
/*
        //TODO check why view model is not working properly
        if (viewModel != null) {
            viewModel.reinit();
            lockButtons(Boolean.TRUE.equals(viewModel.isTaskRunning().getValue()));
        } else {
            myLogD("onResume => viewModel == null");
            lockButtons(false);
        }

 */
        //ersatz :
        /*
        LoadBookTaskState state = Pref.getLoadBookTaskState();
        if (state != null) {
            lockButtons(state.onGoingLoading);
        } else {
            myLogD("onResume => state == null");
        }

         */
        maybeResumeWorkFlow(this);
    }

}
