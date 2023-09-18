package com.driot.bookplayer.activities;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.driot.bookplayer.R;
import com.driot.bookplayer.utils.AddResourceService;
import com.driot.bookplayer.utils.PermissionRequest;

import java.security.Permission;
import java.util.Timer;
import java.util.TimerTask;

//import com.nbsp.materialfilepicker.MaterialFilePicker;
//import com.nbsp.materialfilepicker.ui.FilePickerActivity;

import static com.driot.tonylib.KanLogger.myLog;
import static com.driot.tonylib.KanLogger.myLogE;
import static com.driot.tonylib.KanLogger.myToast;
import static com.driot.tonylib.KanLogger.myToastE;
import static com.driot.tonylib.TonioCommonStuff.MD5;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 08/11/20
 */
public class GetResourceActivity extends LifecycleLoggingActivity {

    private static final int OPEN_ZIP_FILE_REQUEST_CODE = 24;
    private static final int OPEN_FILE_REQUEST_CODE = 2444;
    private static final int OPEN_FOLDER_REQUEST_CODE = 25;
    public static final int ADD_RESOURCE_REQUEST_CODE = 26;

    private Button bOpenFile;
    private Button bOpenFolder;
    private Button bOpenZipFile;
    private Button bSearchLibrivox;
    private Button bSearchLitteratureaudio;
    private Button bSearchGutenberg;

    private PermissionRequest mPermissionRequest;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_getressource);

        bOpenFile = findViewById(R.id.bOpenFile);
        bOpenFolder = findViewById(R.id.bOpenFolder);
        bOpenZipFile = findViewById(R.id.bOpenZipFile);
        bSearchLibrivox = findViewById(R.id.bSearchLibrivox);
        bSearchLitteratureaudio = findViewById(R.id.bSearchLitteratureaudio);
        bSearchGutenberg = findViewById(R.id.bSearchGutenberg);

        // SINGLE FILE
        bOpenFile.setOnClickListener(view -> {
            myLog("Button click : single file");
            if (!checkIfPermissionsReadStorage()) {
                askPermissionsReadStorage();
            } else {
                //myToastE(getString(R.string.permissions_denied_sorry_cannot));

                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("audio/*");
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(intent, OPEN_FILE_REQUEST_CODE);
            }
        });

        // ZIP
        bOpenZipFile.setOnClickListener(view -> {
            myLog("Button click : ZIP file");
            if (checkIfPermissionsReadStorage()) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("application/zip");
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(intent, OPEN_ZIP_FILE_REQUEST_CODE);
                /*
                new MaterialFilePicker()
                        .withActivity(this)
                        .withRequestCode(PICK_FILE_REQUEST_CODE)
                        .withHiddenFiles(true)
                        .start();

                 */
            } else {
                myToastE(getString(R.string.permissions_denied_sorry_cannot));
            }
        });

        // FOLDER
        bOpenFolder.setOnClickListener(view -> {
            myLog("Button click : FOLDER");
            if (checkIfPermissionsReadStorage()) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                startActivityForResult(intent, OPEN_FOLDER_REQUEST_CODE);
            } else {
                myToastE(getString(R.string.permissions_denied_sorry_cannot));
            }
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

        //create timer to check progress
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(checkServiceRunningRunnable);
            }
        }, 0, 500);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkServiceRunning();
    }

    final Runnable checkServiceRunningRunnable = new Runnable() { // sinon Error :  Animators may only be run on Looper threads
        public void run() {
            checkServiceRunning();
        }
    };


    // TODO : Better display of waiting message when loading books
    private void checkServiceRunning() {
        try {
            TextView tv1 = findViewById(R.id.bOpenFolder_desc);
            TextView tv2 = findViewById(R.id.message_import_currently_running);

            //if (isMyServiceRunning(AddResourceService.class)) {
            if (AddResourceService.isBusy) {
                bOpenFile.setEnabled(false);
                bOpenZipFile.setEnabled(false);
                bOpenFolder.setEnabled(false);
                tv1.setVisibility(View.INVISIBLE);
                tv2.setVisibility(View.VISIBLE);
            } else {
                bOpenFile.setEnabled(true);
                bOpenZipFile.setEnabled(true);
                bOpenFolder.setEnabled(true);
                tv1.setVisibility(View.VISIBLE);
                tv2.setVisibility(View.INVISIBLE);
            }
        } catch (Exception e) {
            myLogE("Error while checking if service is running : " + e.getMessage());
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case OPEN_FILE_REQUEST_CODE:  // return of intent ACTION_OPEN_DOCUMENT
                if (resultCode == RESULT_OK) {
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
                    Uri uri = data.getData();
                    myLog("picked data : " + uri.getPath() );
                    Intent intent = new Intent(getApplicationContext(), AddResourceActivity.class);
                    intent.putExtra("Uri", uri);
                    intent.putExtra("type", "ZIP");
                    startActivityForResult(intent, ADD_RESOURCE_REQUEST_CODE);
                }
                break;
            case ADD_RESOURCE_REQUEST_CODE:
                myLog("retour activity");
                if (resultCode == Activity.RESULT_OK) {
                    myLog("result ok");
                    finish();
                }
                break;
            default:
                myLogE("Bad Activity Request Result Code");
        }
    }




    // -------------------------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------
    // --     PERMISSIONS
    // -------------------------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------

    private boolean checkIfPermissionsReadStorage() {
        boolean HasPermission = false;
        int permissionCheck1 = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE);
        int permissionCheck2 = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_MEDIA_AUDIO);
        if (permissionCheck1 == PackageManager.PERMISSION_GRANTED || permissionCheck2 == PackageManager.PERMISSION_GRANTED) HasPermission = true;
        myLog("Checking Permissions 1 - GetRessourceActivity.checkIfPermissionsReadStorage() : [" + HasPermission + "]");
        return HasPermission;
    }

    private boolean askPermissionsReadStorage() { //new Permission starting Android 33
        myLog("Permissions - askPermissionsReadStorage");
        //ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_MEDIA_AUDIO}, 1357);
        checkPermissionsReadStorage2();
        return true;
    }


    /**
     * Handle the onPostCreate() hook to call permission helper to handle all
     * permission requests using the API 23 permission model framework.
     * <p>
     * The framework will callback to request this application to provide a
     * descriptive reason for the permission request that is then displayed to
     * the user. The user has the opportunity to grant or deny the permission
     * request. The callback is also handled automatically by the permission
     * helper class.
     *
     * @param savedInstanceState A saved state or null.
     */
    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        myLog("Checking Permissions 2 - GetRessourceActivity.OnPostCreate()");
        checkPermissionsReadStorage2();
        super.onPostCreate(savedInstanceState);
    }

    private void checkPermissionsReadStorage2() {
        if(Build.VERSION.SDK_INT < 33) {
            mPermissionRequest = PermissionRequest
                    .with(this)
                    .permissions(Manifest.permission.READ_EXTERNAL_STORAGE) //Manifest.permission.READ_EXTERNAL_STORAGE,
                    .rationale(R.string.permission_read_write_rationale)
                    //.granted(R.string.permission_read_write_granted)  // Tonio no need to display message if granted OK
                    .denied(R.string.permission_read_write_denied)
                    .snackbar((ViewGroup) findViewById(android.R.id.content))
                    .submit();
        } else {
            mPermissionRequest = PermissionRequest
                    .with(this)
                    .permissions(Manifest.permission.READ_MEDIA_AUDIO) //Manifest.permission.READ_EXTERNAL_STORAGE,
                    .rationale(R.string.permission_read_write_rationale)
                    //.granted(R.string.permission_read_write_granted)  // Tonio no need to display message if granted OK
                    .denied(R.string.permission_read_write_denied)
                    .snackbar((ViewGroup) findViewById(android.R.id.content))
                    .submit();
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        myLog("Checking Permissions 2 - GetRessourceActivity.onRequestPermissionsResult()");
        myLog("Checking Permissions 2 : " + permissions[0] + " - " + requestCode + " - " + grantResults[0]);
        // Redirect hook call to permission helper method.
        if (mPermissionRequest != null) {
            mPermissionRequest.onRequestPermissionsResult(requestCode, permissions, grantResults);
            mPermissionRequest = null; // request no longer needed
        } else {
            myLogE("Checking Permissions 2 - mPermissionRequest is null ! bad hook");
        }
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

}
