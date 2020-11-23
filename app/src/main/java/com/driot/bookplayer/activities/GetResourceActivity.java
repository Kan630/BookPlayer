package com.driot.bookplayer.activities;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.sqlite.db.SimpleSQLiteQuery;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.FolderAttrib;
import com.driot.bookplayer.db.Resource;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.utils.PermissionRequest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Date;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.CompletableObserver;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Action;
import io.reactivex.rxjava3.schedulers.Schedulers;

import static com.driot.bookplayer.utils.Tonio.fileExists;
import static com.driot.bookplayer.utils.Tonio.getExtension;
import static com.driot.bookplayer.utils.Tonio.getFileNameFromPath;
import static com.driot.bookplayer.utils.Tonio.getMimeType;
import static com.driot.bookplayer.utils.Utils.animateView;
import static com.driot.bookplayer.utils.Utils.copyStream;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 08/11/20
 */
public class GetResourceActivity extends LifecycleLoggingActivity {

    private static final int OPEN_ZIP_FILE_REQUEST_CODE = 24;
    private static final int OPEN_FOLDER_REQUEST_CODE = 25;

    private View progressBarOverlay;
    private ProgressBar progressBar;
    private TextView progressBarText;
    private Button bOpenFolder;
    private Button bOpenZipFile;
    private Button bSearchLibrivox;
    private Button bSearchLitteratureaudio;

    public static final int DELAY_ANIMATION = 500;

    private Handler myHandler = new Handler();;

    private PermissionRequest mPermissionRequest;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_getressource);

        bOpenFolder = findViewById(R.id.bOpenFolder);
        bOpenZipFile = findViewById(R.id.bOpenZipFile);
        bSearchLibrivox = findViewById(R.id.bSearchLibrivox);
        bSearchLitteratureaudio = findViewById(R.id.bSearchLitteratureaudio);
        progressBarOverlay = findViewById(R.id.progressBar_overlay);
        progressBar = findViewById(R.id.progressBar);
        progressBarText = findViewById(R.id.progressBarText);

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
            //startActivityForResult(intent, DOWNLOAD_BOOK_REQUEST_CODE);
        });
        bSearchLitteratureaudio.setOnClickListener(view -> {
            String url = "http://www.litteratureaudio.com/classement-de-nos-livres-audio-gratuits-les-plus-apprecies";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
            //startActivityForResult(intent, DOWNLOAD_BOOK_REQUEST_CODE);
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && requestCode == OPEN_FOLDER_REQUEST_CODE) {

            Uri uri = data.getData();

            Intent intent = new Intent(getApplicationContext(), AddResourceActivity.class);
            intent.putExtra("Uri", uri);
            intent.putExtra("type", "Folder");
            startActivity(intent);

        } else if (resultCode == RESULT_OK && requestCode == OPEN_ZIP_FILE_REQUEST_CODE) {

            Uri uri = data.getData();

            Intent intent = new Intent(getApplicationContext(), AddResourceActivity.class);
            intent.putExtra("Uri", uri);
            intent.putExtra("type", "ZIP");
            startActivity(intent);

        }
    }


    // PERMISSIONS
    private boolean checkIfPermissionsReadStorage() {
        boolean HasPermission = false;
        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE);
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) HasPermission = true;
        return HasPermission;
    }

    private void ShowProgress() {
        //animateView(progressOverlay, View.VISIBLE, 0.4f, DELAY_ANIMATION);
        animateView(progressBarOverlay, View.VISIBLE, 1, DELAY_ANIMATION);
        progressBarOverlay.setVisibility(View.VISIBLE);
        progressBarOverlay.bringToFront();
        bOpenFolder.setVisibility(View.INVISIBLE);
        bOpenZipFile.setVisibility(View.INVISIBLE);
        bSearchLibrivox.setVisibility(View.INVISIBLE);
        bSearchLitteratureaudio.setVisibility(View.INVISIBLE);
        progressBar.setProgress(2);
        progressBarText.setText("init");
    }

    private void HideProgress() {
        animateView(progressBarOverlay, View.GONE, 0, DELAY_ANIMATION);
        bOpenFolder.setVisibility(View.VISIBLE);
        bOpenZipFile.setVisibility(View.VISIBLE);
        bSearchLibrivox.setVisibility(View.VISIBLE);
        bSearchLitteratureaudio.setVisibility(View.VISIBLE);
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
        // Submit a permission request to ensure that this app has the
        // required permissions for writing and reading external storage.
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

    /**
     * API 23 (M) callback received when a permissions request has been
     * completed. Redirect callback to permission helper.
     */
    @TargetApi(Build.VERSION_CODES.M)
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
