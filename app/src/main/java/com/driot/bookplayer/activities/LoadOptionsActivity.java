package com.driot.bookplayer.activities;

import static com.driot.bookplayer.global.Var.FOLDER_UNZIPPED;
import static com.driot.bookplayer.global.Var.ONLY_MIME_AUDIO;
import static com.driot.bookplayer.global.Var.SUPPORTED_AUDIO_EXTENSIONS;
import static com.driot.bookplayer.utils.PermissionRequest.isReadAudioPermissionGranted;
import static com.driot.bookplayer.utils.Tonio.formatNameForDisplay;
import static com.driot.bookplayer.utils.Tonio.getExtension;
import static com.driot.bookplayer.utils.Tonio.getFileNameFromPath;
import static com.driot.bookplayer.utils.Tonio.getFileNameFromUri;
import static com.driot.bookplayer.utils.Tonio.getMimeType;
import static com.driot.bookplayer.utils.Tonio.getSourceLocation;
import static com.driot.bookplayer.utils.Tonio.stripExtension;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.utils.PermissionRequest;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.util.Objects;

public class LoadOptionsActivity extends LoggingActivity {

    public static final String EXTRA_URI = "uri";
    public static final String EXTRA_TYPE = "type";  // File or Folder

    private Uri uri;
    private String type;

    private String audioBookTitle, sourceLocation;

    private TextView errorTextView;
    private CheckBox cbSplit, cbCopy, cbDelete;
    private LinearLayout llSplit, llCopy, llDelete;

    private boolean internalCheckBoxStateCalculationInProgress;

    private PermissionRequest mPermissionRequest;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_load_options);

        uri = getIntent().getParcelableExtra(EXTRA_URI);
        type = Objects.toString(getIntent().getStringExtra(EXTRA_TYPE),"");

        if (!(type.equals("File")) && !(type.equals("Folder"))) {
            myToastE("Error picking audio - unsupported type : [" + type + "]");
            finish();
            return; //if not, on create code continues...
        }
        if (Objects.isNull(uri)) {
            myToastE("Error picking audio : [uri is null]");
            finish();
            return; //if not, on create code continues...
        }

        sourceLocation = getSourceLocation(uri);
        String sourceLocationText = "Source Location = [" + sourceLocation + "]";
        myLog(sourceLocationText);
        TextView tvLocation = findViewById(R.id.tvLocation);
        tvLocation.setText(sourceLocationText);

        TextView tvFileName = findViewById(R.id.tvFileName);
        TextView tvMimeExtension = findViewById(R.id.tvMimeExtension);
        Button btnConfirm = findViewById(R.id.btnConfirm);
        Button btnCancel = findViewById(R.id.btnCancel);

        errorTextView = findViewById(R.id.errorTextView);
        errorTextView.setVisibility(View.GONE);

        String uriPath = uri.getPath();
        myLog("picked data : " + uriPath);

        if (type.equals("File")) {
            String mimeType = Objects.toString(getMimeType(this, uri),"");
            String fileName = getFileNameFromUri(this, uri);
            String fileExtension = getExtension(fileName);

            if (Objects.toString(fileExtension,"").isEmpty()) {
                myToastE("Error : file extension not found");
                finish();
            }

            if (fileExtension.equalsIgnoreCase("zip")) {
                type = "ZIP";
            } else if (fileExtension.equalsIgnoreCase("m4b")) {
                type = "M4B";
            }

            String infoMimeExtension = "Type = [" + type + "] :    [" + mimeType + "] - [." + fileExtension + "]";
            tvMimeExtension.setText(infoMimeExtension);

            if (mimeType.startsWith(ONLY_MIME_AUDIO) || SUPPORTED_AUDIO_EXTENSIONS.contains(fileExtension)) {
                myLog("ok mime - " + infoMimeExtension);
            } else {
                myLogE("mime ko - " + infoMimeExtension);
            }

            audioBookTitle = stripExtension(getFileNameFromUri(this, uri));

        } else if (type.equals("Folder")) {

            findViewById(R.id.llMimeExtension).setVisibility(View.GONE);
            audioBookTitle = formatNameForDisplay(getFileNameFromPath(uriPath));
        }

        tvFileName.setText(audioBookTitle);

//----------------------------------------------------------------------------------------------------------------------------------
/// OPTIONS CHECKBOXES
//----------------------------------------------------------------------------------------------------------------------------------
        cbSplit = findViewById(R.id.cbSplitM4B);
        cbCopy = findViewById(R.id.cbCopyInternal);
        cbDelete = findViewById(R.id.cbDeleteSource);
        llSplit = findViewById(R.id.ll_split_m4b);
        llCopy = findViewById(R.id.ll_copy_internal);
        llDelete = findViewById(R.id.ll_delete_source);

        llSplit.setOnClickListener(v -> cbSplit.toggle());
        llCopy.setOnClickListener(v -> cbCopy.toggle());
        llDelete.setOnClickListener(v -> cbDelete.toggle());

        cbSplit.setChecked(Option.getSplitM4b(this));
        cbCopy.setChecked(Option.getCopyFile(this));
        cbDelete.setChecked(Option.getDeleteSourceFile(this));
        calculateCheckboxState();

        cbSplit.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!internalCheckBoxStateCalculationInProgress) calculateCheckboxState();
        });
        cbCopy.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                checkPathDoesNotAlreadyExist(isChecked);
            } else {
                askForPermission();
            }
            if (!internalCheckBoxStateCalculationInProgress) calculateCheckboxState();
        });
        cbDelete.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!internalCheckBoxStateCalculationInProgress) {
                if (isChecked) {
                    new AlertDialog.Builder(this)
                            .setTitle(getString(R.string.option_alert_delete_picked_source_file_title))
                            .setMessage(getString(R.string.option_alert_delete_picked_source_file_message))
                            .setCancelable(false)
                            .setPositiveButton("ok", (dialog, which) -> calculateCheckboxState())
                            .setNegativeButton("cancel", (dialog, which) -> cbDelete.setChecked(false))
                            .show();
                } else {
                    Option.setDeleteSourceFile(this, false);
                }
            }
        });

//----------------------------------------------------------------------------------------------------------------------------------
// check Not Already Imported
//*****************************

        checkPathDoesNotAlreadyExist(cbCopy.isChecked());
        checkNameDoesNotAlreadyExist();

//-------------------------------------------------------------------------------------------------------------------------------------------------
// ACTION BUTTONS
//-------------------------------------------------------------------------------------------------------------------------------------------------
        btnCancel.setOnClickListener(v -> finish());

        btnConfirm.setOnClickListener(v -> {
            Intent data = new Intent();
            data.putExtra("uri", uri);
            data.putExtra("type", type);
            data.putExtra("title", audioBookTitle);
            data.putExtra("split", cbSplit.isChecked());
            data.putExtra("copy", cbCopy.isChecked());
            data.putExtra("delete", cbDelete.isChecked());
            setResult(RESULT_OK, data);
            finish();
        });
    }

//-------------------------------------------------------------------------------------------------------------------------------------------------
//-------------------------------------------------------------------------------------------------------------------------------------------------
//-------------------------------------------------------------------------------------------------------------------------------------------------

    private void calculateCheckboxState() {
        internalCheckBoxStateCalculationInProgress = true;

        if ("M4B".equals(type)) {
            llSplit.setVisibility(View.VISIBLE);
            if (cbSplit.isChecked()) {
                cbCopy.setEnabled(false);
                llCopy.setEnabled(false);
                cbCopy.setChecked(true);
            } else {
                cbCopy.setEnabled(true);
                llCopy.setEnabled(true);
            }
        } else {
            llSplit.setVisibility(View.GONE);
        }

        if ("ZIP".equals(type)) {
            cbCopy.setEnabled(false);
            cbCopy.setChecked(true);
        }

        if (sourceLocation.equals("cloud") || sourceLocation.equals("web")) {
            cbCopy.setEnabled(false);
            llCopy.setEnabled(false);
            cbCopy.setChecked(true);
        }

        // delete
        if (cbCopy.isChecked() && !sourceLocation.equals("cloud") && !sourceLocation.equals("web")) {
            cbDelete.setEnabled(true);
            llDelete.setEnabled(true);
        } else {
            cbDelete.setChecked(false);
            cbDelete.setEnabled(false);
            llDelete.setEnabled(false);
        }

        internalCheckBoxStateCalculationInProgress = false;
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
                    .rationale(R.string.permission_read_write_rationale_short_text_on_load)
                    //.granted(R.string.permission_read_write_granted)  // Tonio no need to display message if granted OK
                    .denied(R.string.permission_read_write_denied)
                    .snackbar((ViewGroup) findViewById(android.R.id.content))
                    .submit();
        } else {
            myLog("checkPermissionsReadStorage() >= 33");
            mPermissionRequest = PermissionRequest
                    .with(this)
                    .permissions(Manifest.permission.READ_MEDIA_AUDIO) //Manifest.permission.READ_EXTERNAL_STORAGE,
                    .rationale(R.string.permission_read_write_rationale_short_text_on_load)
                    //.granted(R.string.permission_read_write_granted)  // Tonio no need to display message if granted OK
                    .denied(R.string.permission_read_write_denied)
                    .snackbar((ViewGroup) findViewById(android.R.id.content))
                    .callback(new PermissionRequest.Callback() {
                        @Override
                        public void onPermissionsGranted() {
                            cbCopy.setChecked(false);
                            myLog("Granted");
                        }

                        @Override
                        public void onPermissionsDenied() {
                            cbCopy.setChecked(true);
                            myLog("Denied");
                            showPermissionDeniedDialog();
                        }
                    })
                    .submit();
        }
    }
    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.Permission))
                .setMessage(getString(R.string.permission_read_denied_short_text_on_load))
                .setPositiveButton("App Info", (dialog, which) -> openAppInfo())
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

    private void ShowWarning(String warningTxt) {
        String previousTxt = errorTextView.getText().toString();
        String newTxt = previousTxt.isEmpty() ? warningTxt : previousTxt + "\n" + warningTxt;
        errorTextView.setText(newTxt);
        errorTextView.setVisibility(View.VISIBLE);
    }

    private void checkPathDoesNotAlreadyExist(boolean copy) {
        String strPath;
        if (copy) {
            strPath = getFilesDir().getAbsolutePath() + "/" + FOLDER_UNZIPPED + "/" + audioBookTitle;
        } else {
            strPath = uri.getPath();
        }
        myLog("Checking Folder Path doesn't already exist in DB : [" + strPath + "]");
        new Thread(() -> {
            long lCheck = AppDatabase.getDatabase(this).FolderDao().folderAlreadyExist_checkFolderPath(strPath);
            if (lCheck>0) {
                myLogE("KO, folder path does already exist in DB : [" + strPath + "]");
                runOnUiThread(() -> {
                    ShowWarning (getString(R.string.error_media_already_loaded_samePath));
                });
            } else {
                myLog("OK, folder path doesn't already exist in DB");
            }
        }).start();
    }

    private void checkNameDoesNotAlreadyExist() {
        myLog("Checking Folder Name doesn't already exist in DB : [" + audioBookTitle + "]");
        new Thread(() -> {
            long lCheck = AppDatabase.getDatabase(this).FolderDao().folderAlreadyExist_checkFolderName(audioBookTitle);
            if (lCheck>0) {
                myLogE("KO, folder name does already exist in DB : [" + audioBookTitle + "]");
                runOnUiThread(() -> {
                    ShowWarning (getString(R.string.error_media_already_loaded_sameName));
                });
            } else {
                myLog("OK, folder name doesn't already exist in DB");
            }
        }).start();
    }

}
