package com.driot.bookplayer.activities;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.utils.AddResourceService;
import com.driot.bookplayer.utils.CopyFileService;
import com.driot.bookplayer.utils.DownloadService;
import com.driot.bookplayer.utils.MediaScanner2;
import com.driot.bookplayer.utils.PermissionRequest;
import com.driot.tonylib.KanLogger;

import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import static com.driot.bookplayer.global.Var.AUTOTEST_FILE_01;
import static com.driot.bookplayer.global.Var.AUTOTEST_FILE_02;
import static com.driot.bookplayer.global.Var.AUTOTEST_FILE_03;
import static com.driot.bookplayer.utils.PermissionRequest.isReadAudioPermissionGranted;
import static com.driot.tonylib.KanLogger.myToastE;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 08/11/20
 */
public class GetResourceActivity extends LifecycleLoggingActivity { //AppCompatActivity

    private static final int OPEN_ZIP_FILE_REQUEST_CODE = 24;
    private static final int OPEN_FILE_REQUEST_CODE = 2444;
    private static final int OPEN_FOLDER_REQUEST_CODE = 25;
    public static final int ADD_RESOURCE_REQUEST_CODE = 26;
    public static final int AUTOTEST_REQUEST_CODE = 987;

    private Button bOpenFile, bOpenFolder, bOpenZipFile;
    private Button bAutoTest_b1, bAutoTest_b2, bAutoTest_b3;
    List<Button> buttonsToLock;

    private PermissionRequest mPermissionRequest;
    private int lopperForLog = 0;
    private Timer timer;
    private boolean isActivityActive = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_getressource);

        bOpenFile = findViewById(R.id.bOpenFile);
        bOpenFolder = findViewById(R.id.bOpenFolder);
        bOpenZipFile = findViewById(R.id.bOpenZipFile);
        Button bSearchLibrivox = findViewById(R.id.bSearchLibrivox);
        Button bSearchLitteratureaudio = findViewById(R.id.bSearchLitteratureaudio);
        Button bSearchGutenberg = findViewById(R.id.bSearchGutenberg);
        bAutoTest_b1 = findViewById(R.id.bAutoTest_b1);
        bAutoTest_b2 = findViewById(R.id.bAutoTest_b2);
        bAutoTest_b3 = findViewById(R.id.bAutoTest_b3);
        buttonsToLock = Arrays.asList(bOpenFile, bOpenFolder, bOpenZipFile, bAutoTest_b1, bAutoTest_b2, bAutoTest_b3);

            // SINGLE FILE
        bOpenFile.setOnClickListener(view -> {
            myLog("Button click : single file");
            if (isReadAudioPermissionGranted(this) || Option.getCopyFile(this)) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("audio/*");
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                //CATEGORY_OPENABLE => able to use : ContentResolver#openFileDescriptor(Uri, String)
                //can be opened as a File object i.e. with read and write permissions and have complete access to the physical location of the data
                startActivityForResult(intent, OPEN_FILE_REQUEST_CODE);
            } else {
                askForPermission();
            }
        });

        // FOLDER
        bOpenFolder.setOnClickListener(view -> {
            myLog("Button click : FOLDER");
            if (isReadAudioPermissionGranted(this) || Option.getCopyFile(this)) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE); //API 21
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                startActivityForResult(intent, OPEN_FOLDER_REQUEST_CODE);
            } else {
                askForPermission();
            }
        });

        // ZIP
        bOpenZipFile.setOnClickListener(view -> {
            scanThatShit();
            myLog("Button click : ZIP file");
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                // TODO ACTION_GET_CONTENT should be enough since we copy locally...
                // ACTION_PICK could be interesting.... as an option..
                intent.setType("application/zip");
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(intent, OPEN_ZIP_FILE_REQUEST_CODE);
        });

        ////////////////////////////////
        ///// LINKS
        ////////////////////////////////

        bSearchLibrivox.setOnClickListener(view -> {
            String url = "https://librivox.org";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        });
        bSearchLitteratureaudio.setOnClickListener(view -> {
            String url = "https://www.litteratureaudio.com";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            //Intent intent = new Intent(getApplicationContext(), DbBackupActivity.class);
            startActivity(intent);
        });
        bSearchGutenberg.setOnClickListener(view -> {
            String url = "https://marhamilresearch4.blob.core.windows.net/gutenberg-public/Website/index.html";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        });

        ////////////////////////////////
        ///// AUTO TEST
        ////////////////////////////////

        bAutoTest_b1.setOnClickListener(view -> {
            myLog("Button click : AUTOTEST 1"); // maybe not need write permission since writing inside app folders....
            Intent intent = new Intent(getApplicationContext(), DownloadActivity.class);
            intent.putExtra("filePathToDownload", AUTOTEST_FILE_01);
            startActivityForResult(intent, AUTOTEST_REQUEST_CODE);
        });
        bAutoTest_b2.setOnClickListener(view -> {
            myLog("Button click : AUTOTEST 2");
            Intent intent = new Intent(getApplicationContext(), DownloadActivity.class);
            intent.putExtra("filePathToDownload", AUTOTEST_FILE_02);
            startActivityForResult(intent, AUTOTEST_REQUEST_CODE);
        });
        bAutoTest_b3.setOnClickListener(view -> {
            myLog("Button click : AUTOTEST 3");
            Intent intent = new Intent(getApplicationContext(), DownloadActivity.class);
            intent.putExtra("filePathToDownload", AUTOTEST_FILE_03);
            startActivityForResult(intent, AUTOTEST_REQUEST_CODE);
        });

        startTimer(); // used to lock buttons if service running
    }

    private void startTimer() {
        isActivityActive = true;
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
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
        isActivityActive = false;
        if (timer != null) {
            try { timer.cancel(); timer.purge(); timer=null; }
            catch (Exception e) { myLogE("stopTimer() - " + e.getMessage());}
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
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
        try {
            lopperForLog = lopperForLog + 1;
            TextView tv1 = findViewById(R.id.bOpenFolder_desc);
            TextView tv2 = findViewById(R.id.message_import_currently_running);

            if (AddResourceService.isBusy || DownloadService.isBusy) {
                if (lopperForLog%10==0) myLog("AddResourceService.isBusy => displaying banner, disabling buttons");
                for (Button b: buttonsToLock) { b.setEnabled(false); }
                tv1.setVisibility(View.INVISIBLE);
                tv2.setVisibility(View.VISIBLE);
            } else {
                for (Button b: buttonsToLock) { b.setEnabled(true); }
                tv1.setVisibility(View.VISIBLE);
                tv2.setVisibility(View.INVISIBLE);
            }
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
            e.printStackTrace();
            return false;
        }
    }
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case OPEN_FILE_REQUEST_CODE:  // return of intent ACTION_OPEN_DOCUMENT
                if (resultCode == RESULT_OK) {
                    if (!isReturnedUriOk(data)) break;
                    Uri uri = data.getData();
                    myLog("picked data : " + uri.getPath() );
                    Intent intent = new Intent(getApplicationContext(), AddResourceActivity.class);
                    intent.putExtra("Uri", uri);
                    intent.putExtra("type", "File");
                    startActivityForResult(intent,ADD_RESOURCE_REQUEST_CODE);
                }
                break;
            case OPEN_FOLDER_REQUEST_CODE: // return of ACTION_OPEN_DOCUMENT_TREE
                if (resultCode == RESULT_OK) {
                    if (!isReturnedUriOk(data)) break;
                    Uri uri = data.getData();
                    myLog("picked data : " + uri.getPath() );
                    Intent intent = new Intent(getApplicationContext(), AddResourceActivity.class);
                    intent.putExtra("Uri", uri);
                    intent.putExtra("type", "Folder");
                    startActivityForResult(intent,ADD_RESOURCE_REQUEST_CODE);
                }
                break;
            case OPEN_ZIP_FILE_REQUEST_CODE: // return of ACTION_OPEN_DOCUMENT
                if (resultCode == RESULT_OK) {
                    if (!isReturnedUriOk(data)) break;
                    Uri uri = data.getData();
                    myLog("picked data : " + uri.getPath() );
                    Intent intent = new Intent(getApplicationContext(), AddResourceActivity.class);
                    intent.putExtra("Uri", uri);
                    intent.putExtra("type", "ZIP");
                    startActivityForResult(intent, ADD_RESOURCE_REQUEST_CODE);
                }
                break;
            case ADD_RESOURCE_REQUEST_CODE:
                myLog("return from Add_Resource_Activity");
                if (resultCode == Activity.RESULT_OK) {
                    myLog("result ok - closing activity");
                    finish();
                }
                break;
                // TODO problem if Activity is closed... like in case of 'back' action from user....
            case AUTOTEST_REQUEST_CODE:
                myLog("return from Download_Activity : ResultCode=" + resultCode);
                if (resultCode == RESULT_OK) {
                    myLog("return from Download_Activity : Result Code OK");
                } else {
                    myLogE("return from Download_Activity : Result NOT ok");
                }
                break;

                default:
                myLogE("Bad Activity Request Result Code");
        }
        myLog("end return from Activity");
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


    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(getApplicationContext().ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
}
