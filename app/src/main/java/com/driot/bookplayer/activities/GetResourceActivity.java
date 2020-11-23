package com.driot.bookplayer.activities;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.driot.bookplayer.R;
import com.driot.bookplayer.utils.PermissionRequest;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 08/11/20
 */
public class GetResourceActivity extends LifecycleLoggingActivity {

    private static final int OPEN_ZIP_FILE_REQUEST_CODE = 24;
    private static final int OPEN_FOLDER_REQUEST_CODE = 25;
    public static final int ADD_RESOURCE_REQUEST_CODE = 26;

    private Button bOpenFolder;
    private Button bOpenZipFile;
    private Button bSearchLibrivox;
    private Button bSearchLitteratureaudio;

    private PermissionRequest mPermissionRequest;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_getressource);

        bOpenFolder = findViewById(R.id.bOpenFolder);
        bOpenZipFile = findViewById(R.id.bOpenZipFile);
        bSearchLibrivox = findViewById(R.id.bSearchLibrivox);
        bSearchLitteratureaudio = findViewById(R.id.bSearchLitteratureaudio);

        // ZIP
        bOpenZipFile.setOnClickListener(view -> {
            if (checkIfPermissionsReadStorage()) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("application/zip");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(intent, OPEN_ZIP_FILE_REQUEST_CODE);
            } else {
                myToast(getString(R.string.permissions_denied_sorry_cannot));
            }
        });

        // FOLDER
        bOpenFolder.setOnClickListener(view -> {
            if (checkIfPermissionsReadStorage()) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                startActivityForResult(intent, OPEN_FOLDER_REQUEST_CODE);
            } else {
                myToast(getString(R.string.permissions_denied_sorry_cannot));
            }
        });
        bSearchLibrivox.setOnClickListener(view -> {
            String url = "https://librivox.org/search";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        });
        bSearchLitteratureaudio.setOnClickListener(view -> {
            String url = "http://www.litteratureaudio.com/classement-de-nos-livres-audio-gratuits-les-plus-apprecies";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case OPEN_FOLDER_REQUEST_CODE:
                if (resultCode == RESULT_OK) {
                    Uri uri = data.getData();
                    Intent intent = new Intent(getApplicationContext(), AddResourceActivity.class);
                    intent.putExtra("Uri", uri);
                    intent.putExtra("type", "Folder");
                    startActivityForResult(intent,ADD_RESOURCE_REQUEST_CODE);
                }
                break;
            case OPEN_ZIP_FILE_REQUEST_CODE:
                if (resultCode == RESULT_OK) {
                    Uri uri = data.getData();
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


    // PERMISSIONS
    private boolean checkIfPermissionsReadStorage() {
        boolean HasPermission = false;
        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE);
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) HasPermission = true;
        return HasPermission;
    }

    private void myToast(String str) {
        myLog(str);
        Toast.makeText(getApplicationContext(),str,Toast.LENGTH_SHORT).show();
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
        checkPermissionsReadStorage2();
        super.onPostCreate(savedInstanceState);
    }

    private void checkPermissionsReadStorage2() {
        mPermissionRequest = PermissionRequest
                .with(this)
                .permissions(Manifest.permission.READ_EXTERNAL_STORAGE)
                //Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .rationale(R.string.permission_read_write_rationale)
                //.granted(R.string.permission_read_write_granted)  // Tonio no need to display message if granted OK
                .denied(R.string.permission_read_write_denied)
                .snackbar((ViewGroup)findViewById(android.R.id.content))
                .submit();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        // Redirect hook call to permission helper method.
        if (mPermissionRequest != null) {
            mPermissionRequest.onRequestPermissionsResult(requestCode,
                    permissions,
                    grantResults);
            mPermissionRequest = null; // request no longer needed
        }
    }

}
