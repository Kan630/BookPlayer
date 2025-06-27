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
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.LanguageItem;
import com.driot.bookplayer.db.LoadBookTaskState;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.utils.AddResourceService;
import com.driot.bookplayer.utils.DownloadService;
import com.driot.bookplayer.utils.LanguageSpinnerAdapter;
import com.driot.bookplayer.utils.MediaScanner2;
import com.driot.bookplayer.utils.NetworkUtils;
import com.driot.bookplayer.utils.PermissionRequest;
import com.driot.bookplayer.utils.KanLogger;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import static com.driot.bookplayer.global.Pref.setLoadBookTaskState;
import static com.driot.bookplayer.global.Var.AUTOTEST_FILE_01;
import static com.driot.bookplayer.global.Var.AUTOTEST_FILE_02;
import static com.driot.bookplayer.global.Var.AUTOTEST_FILE_03;
import static com.driot.bookplayer.utils.PermissionRequest.isReadAudioPermissionGranted;
import static com.driot.bookplayer.utils.WorkFlow.maybeResumeWorkFlow;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 08/11/20
 */
public class GetResourceActivity extends LifecycleLoggingActivity { //AppCompatActivity
    private Button bOpenFile, bOpenFolder, bOpenZipFile, bOpenM4bFile;
    private Button bAutoTest_b1, bAutoTest_b2, bAutoTest_b3, bDirectDownload;

    private TextView tv_message_import_currently_running;

    private EditText etDirectDownload;

    private PermissionRequest mPermissionRequest;
    private int lopperForLog = 0;
    private Timer timer;
    private boolean isActivityActive = true;
    private ActivityResultLauncher<Intent>
             bOpenFileActivityResultLauncher
            ,bOpenFolderActivityResultLauncher
            ,loadOptionsActivityResultLauncher
            ;

    private ActivityResultLauncher<Intent> addResourceActivityResultLauncher;

    private void launchAddResource(ActivityResult result, String type) {
        myLog("launchAddResource()-----------------------------------------------------------------------------------------------------");
        try {
            if (result.getResultCode() == RESULT_OK) {
                if (isReturnedUriOk(result.getData())) {
                    Uri uri = result.getData().getData();
                    myLog("picked data : " + uri.getPath());

                    Intent intent = new Intent(this, LoadOptionsActivity.class);
                    intent.putExtra(LoadOptionsActivity.EXTRA_URI, uri);
                    intent.putExtra(LoadOptionsActivity.EXTRA_TYPE, type);
                    loadOptionsActivityResultLauncher.launch(intent);
                } else {
                    myLogE("returned Uri not OK");
                }
            } else {
                myLogE("result code not OK");
            }
        } catch (Exception e) {
            myToastE("Error reading picked object : " + e.getMessage());
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_getressource);

        bOpenFile = findViewById(R.id.bOpenFile);
        bOpenFolder = findViewById(R.id.bOpenFolder);
        bOpenZipFile = findViewById(R.id.bOpenZipFile);
        bOpenM4bFile = findViewById(R.id.bOpenM4bFile);
        Button bInternetAudioResource_01 = findViewById(R.id.bInternetAudioResource_01);
        Button bInternetAudioResource_02 = findViewById(R.id.bInternetAudioResource_02);
        bAutoTest_b1 = findViewById(R.id.bAutoTest_b1);
        bAutoTest_b2 = findViewById(R.id.bAutoTest_b2);
        bAutoTest_b3 = findViewById(R.id.bAutoTest_b3);
        bDirectDownload = findViewById(R.id.bDirectDownload);
        etDirectDownload = findViewById(R.id.etDirectDownload);

        tv_message_import_currently_running = findViewById(R.id.message_import_currently_running);

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
            myLogI("Button click : single file");
            if (isReadAudioPermissionGranted(this) || Option.getCopyFile(this)) {
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

// FOLDER
        bOpenFolderActivityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> { launchAddResource(result, "Folder"); });
        bOpenFolder.setOnClickListener(view -> {
            myLog("Button click : FOLDER");
            if (isReadAudioPermissionGranted(this) || Option.getCopyFile(this)) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE); //API 21
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                bOpenFolderActivityResultLauncher.launch(intent);
            } else {
                askForPermission();
            }
        });

// ZIP
        // TODO sadly, honor with android 7 wont let you select a zip file, the file picker will open the content... instead of selecting it
        // TODO => try ACTION_PICK ?
        bOpenZipFile.setOnClickListener(view -> {
            scanThatShit();
            myLog("Button click : ZIP file");
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
            myLog("Button click : M4B file");

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


// DIRECT DOWNLOAD - JUST GET IT
        bDirectDownload.setOnClickListener(view -> {
            myLog("Button click : DIRECT DOWNLOAD");
            String url = etDirectDownload.getText().toString();

            if (url.isEmpty()) {
                myToast("Please enter a URL.");
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

        loadOptionsActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        if (data == null) return;

                        Uri uri = data.getParcelableExtra("uri");
                        String type = data.getStringExtra("type");
                        String title = data.getStringExtra("title");
                        boolean split = data.getBooleanExtra("split", false);
                        boolean copy = data.getBooleanExtra("copy", false);
                        boolean delete = data.getBooleanExtra("delete", false);

                        LoadBookTaskState state = new LoadBookTaskState();
                        state.uri = uri;
                        state.type = type;
                        state.title = title;
                        state.split = split;
                        state.copy = copy;
                        state.delete = delete;

                        setLoadBookTaskState(this, state); // save in SharedPrefs

                        Intent intentService = new Intent(this, AddResourceService.class);
                        intentService.putExtra("LoadBookTaskState", state);
                        startService(intentService);

                        Intent intentActivity = new Intent(this, AddResourceActivity.class);
                        intentActivity.putExtra("LoadBookTaskState", state);
                        startActivity(intentActivity);
                    }
                }
        );


        ////////////////////////////////
        ///// LINKS
        ////////////////////////////////
        TextView tv;
        bInternetAudioResource_01.setText("Internet Archive");
        tv = findViewById(R.id.tvInternetAudioRessource_01);
        tv.setText("Surf the vast Internet Archive for audio files, and download some !");
        bInternetAudioResource_01.setOnClickListener(view -> {
            String url = "https://archive.org/details/audio";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        });

        bInternetAudioResource_02.setText("Litterature Audio");
        tv = findViewById(R.id.tvInternetAudioRessource_02);
        tv.setText(R.string.bSearchlitteratureaudio_desc);
        bInternetAudioResource_02.setOnClickListener(view -> {
            String url = "https://www.litteratureaudio.com";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            //Intent intent = new Intent(getApplicationContext(), DbBackupActivity.class);
            startActivity(intent);
        });
        /*
        Button bInternetAudioResource_03 = findViewById(R.id.bInternetAudioResource_03);
        bInternetAudioResource_03.setText("Open Culture");
        tv = findViewById(R.id.tvInternetAudioRessource_03);
        tv.setText(R.string.bSearchOpenCulture_desc);
        bInternetAudioResource_03.setOnClickListener(view -> {
            String url = "https://www.openculture.com/";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        });

         */

        ////////////////////////////////
        ///// AUTO TEST
        ////////////////////////////////
        bAutoTest_b1.setOnClickListener(view -> {
            myLog("Button click : AUTO TEST 01");
            checkWWW(canReach -> {
                if (canReach) {
                    Intent intent = new Intent(this, LoadOptionsActivity.class);
                    intent.putExtra(LoadOptionsActivity.EXTRA_URI, Uri.parse(AUTOTEST_FILE_01));
                    intent.putExtra(LoadOptionsActivity.EXTRA_TYPE, "File");
                    loadOptionsActivityResultLauncher.launch(intent);
                }
            });
        });
        bAutoTest_b2.setOnClickListener(view -> {
            myLog("Button click : AUTO TEST 02");
            checkWWW(canReach -> {
                if (canReach) {
                    Intent intent = new Intent(this, LoadOptionsActivity.class);
                    intent.putExtra(LoadOptionsActivity.EXTRA_URI, Uri.parse(AUTOTEST_FILE_02));
                    intent.putExtra(LoadOptionsActivity.EXTRA_TYPE, "File");
                    loadOptionsActivityResultLauncher.launch(intent);
                }
            });
        });
        bAutoTest_b3.setOnClickListener(view -> {
            myLog("Button click : AUTO TEST 03");
            checkWWW(canReach -> {
                if (canReach) {
                    Intent intent = new Intent(this, LoadOptionsActivity.class);
                    intent.putExtra(LoadOptionsActivity.EXTRA_URI, Uri.parse(AUTOTEST_FILE_03));
                    intent.putExtra(LoadOptionsActivity.EXTRA_TYPE, "File");
                    loadOptionsActivityResultLauncher.launch(intent);
                }
            });
        });

        tv_message_import_currently_running.setOnClickListener(v -> {
            myLogI("Click on [OnGoing Import] message !");
            Intent intent = new Intent(this, AddResourceActivity.class);
            startActivity(intent);
        });


        /// // LIBRIVOX SEARCH

        Spinner spinnerLanguage = findViewById(R.id.spinnerLanguage);
        List<LanguageItem> languageItems = Arrays.asList(
                 new LanguageItem("eng", "English", R.drawable.flag_uk)
                ,new LanguageItem("fre", "French", R.drawable.flag_fr)
                ,new LanguageItem("ger", "German", R.drawable.flag_de)
                ,new LanguageItem("spa", "Spanish", R.drawable.flag_es)
                ,new LanguageItem("ita", "Italian", R.drawable.flag_it)
        );
        LanguageSpinnerAdapter adapter = new LanguageSpinnerAdapter(this, languageItems);
        spinnerLanguage.setAdapter(adapter);

        EditText editTextQuery;
        Button buttonSearch;
        editTextQuery = findViewById(R.id.etLibrivoxSearch);
        buttonSearch = findViewById(R.id.bLibrivoxSearch);
        buttonSearch.setOnClickListener(v -> {
            String query = editTextQuery.getText().toString();
            String lang = spinnerLanguage.getSelectedItem().toString().toLowerCase();

            if (lang.isEmpty()) {
                myToast("selected language error");
                return;
            }
            if (query.isEmpty()) {
                myToast("Please enter some text to search.");
                return;
            }

            Intent intent = new Intent(this, LibrivoxResultsActivity.class);
            intent.putExtra("query", query);
            intent.putExtra("lang", lang);
            startActivity(intent);
        });

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


    private void startTimer() {
        myLog("startTimer()");
        isActivityActive = true;
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(checkServiceRunningRunnable);
            }
        }, 0, 500);
    }
    final Runnable checkServiceRunningRunnable = new Runnable() { // sinon Error :  Animators may only be run on Looper threads
        public void run() {
            if (isActivityActive) { //needed because if not, the run() continue after activity is destroyed
                checkServiceRunning();
            }
        }
    };
    private void stopTimer() {
        myLog("stopTimer()");
        isActivityActive = false;
        if (timer != null) {
            try { timer.cancel(); timer.purge(); timer=null; }
            catch (Exception e) { myLogE("stopTimer() - " + e.getMessage());}
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        maybeResumeWorkFlow(this);
        startTimer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTimer();
    }
    @Override
    protected void onPause() {
        super.onPause();
        stopTimer();
    }

    private void checkServiceRunning() {
        if (lopperForLog%10==0) myLogD("checkServiceRunning()");
        try {
            List<TextView> textViewToHide = Arrays.asList(
                     findViewById(R.id.TextHeaderOpen)
                    ,findViewById(R.id.bOpenFile_desc)
                    ,findViewById(R.id.bOpenFolder_desc)
                    ,findViewById(R.id.bOpenZipFile_desc)
                    ,findViewById(R.id.bOpenM4bFile_desc)
                    ,findViewById(R.id.txtAutoTest_title)
                    ,findViewById(R.id.txtAutoTest_desc)
                    ,findViewById(R.id.txtDirectDownload_title)
                    ,findViewById(R.id.txtDirectDownload_desc)
            );
            List<Button> buttonsToLock = Arrays.asList(bOpenFile, bOpenFolder, bOpenZipFile, bOpenM4bFile
                    , bAutoTest_b1, bAutoTest_b2, bAutoTest_b3, bDirectDownload);

            if (AddResourceService.isBusy) {
                if (lopperForLog%20==0) {
                     myLog("AddResourceService.isBusy => displaying banner, disabling buttons");
                }
                for (Button b: buttonsToLock) { b.setEnabled(false); }
                for (TextView tv: textViewToHide) { tv.setVisibility(View.GONE); }
                tv_message_import_currently_running.setVisibility(View.VISIBLE);
            } else {
                for (Button b: buttonsToLock) { b.setEnabled(true); }
                for (TextView tv: textViewToHide) { tv.setVisibility(View.VISIBLE); }
                tv_message_import_currently_running.setVisibility(View.GONE);
            }
            lopperForLog = lopperForLog + 1;
        } catch (Exception e) {
            myLogE("Error while checking if service is running : " + e.getMessage());
        }
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
            myLogE("checkDataOk is KO : " + e.getMessage());
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
            myLogE("openAppSettingsOnPhone() => " + e.getMessage());
        }
    }
    public void openOptionActivity() {
        try {
            startActivity(new Intent(this, OptionActivity.class).putExtra("CopyFileSetRed",true));
        } catch (Exception e) {
            myLogE("openOptionActivity() => " + e.getMessage());
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
                .setTitle("Permission Required")
                .setMessage(R.string.permission_read_write_denied)
                .setPositiveButton("App Info", (dialog, which) -> openAppInfo())
                .setNeutralButton("Options", (dialog, which) -> openOptionActivity())
                .setNegativeButton("Cancel", null)
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


    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
    private void myLogD(String str) { KanLogger.myLogD(this.getClass().getName(), str); }
    private void myLogI(String str) { KanLogger.myLogI(this.getClass().getName(), str); }
    private void myToast(String str) { KanLogger.myToast(this.getClass().getName(), str); }
    private void myToastE(String str) { KanLogger.myToastE(this.getClass().getName(), str); }
}
