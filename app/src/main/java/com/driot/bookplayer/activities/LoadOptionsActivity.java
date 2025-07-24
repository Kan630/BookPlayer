package com.driot.bookplayer.activities;

import static com.driot.bookplayer.global.Pref.setLoadBookTaskState;
import static com.driot.bookplayer.global.Var.FOLDER_UNZIPPED;
import static com.driot.bookplayer.utils.HashWorker.WORKER_TAG_COMPUTE_HASH;
import static com.driot.bookplayer.utils.PermissionRequest.isReadAudioPermissionGranted;
import static com.driot.bookplayer.utils.Tonio.getCurrentDateTimeString;

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
import androidx.lifecycle.Observer;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.objects.BookToAdd;
import com.driot.bookplayer.objects.LoadBookTaskState;
import com.driot.bookplayer.utils.HashWorker;
import com.driot.bookplayer.utils.PermissionRequest;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.util.Objects;

public class LoadOptionsActivity extends LoggingActivity {

    public static final String EXTRA_URI = "uri";
    public static final String EXTRA_TYPE = "type";  // File or Folder

    private Uri uri;
    private String type;
    private BookToAdd bookToAdd;

    private String audioBookTitle; // name can be changed... so keep as separate var
    private String originalHash;

    private TextView waitTextView, warningTextView, errorTextView;
    private CheckBox cbSplit, cbCopy, cbDelete;
    private LinearLayout llSplit, llCopy, llDelete;
    Button btnConfirm;

    private boolean internalCheckBoxStateCalculationInProgress;
    private boolean isKO = false;

    private PermissionRequest mPermissionRequest;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_load_options);

        uri = getIntent().getParcelableExtra(EXTRA_URI);
        type = Objects.toString(getIntent().getStringExtra(EXTRA_TYPE),"");

        if (
                !(type.equals("File") || type.equals("Folder") || type.equals("Podcast"))
        ) {
            myToastE("Error picking audio - unsupported type : [" + type + "]");
            finish();
            return;
        }

        if (Objects.isNull(uri)) {
            myToastE("Error picking audio : [uri is null]");
            finish();
            return;
        }

        TextView tvFileName = findViewById(R.id.tvFileName);
        TextView tvMimeExtension = findViewById(R.id.tvMimeExtension);
        TextView tvSourceLocation = findViewById(R.id.tvLocation);
        btnConfirm = findViewById(R.id.btnConfirm);
        Button btnCancel = findViewById(R.id.btnCancel);

        waitTextView = findViewById(R.id.waitTextView);
        waitTextView.setText(getString(R.string.init_please_wait));
        warningTextView = findViewById(R.id.warningTextView);
        warningTextView.setVisibility(View.GONE);
        errorTextView = findViewById(R.id.errorTextView);
        errorTextView.setVisibility(View.GONE);

        cbSplit = findViewById(R.id.cbSplitM4B);
        cbCopy = findViewById(R.id.cbCopyInternal);
        cbDelete = findViewById(R.id.cbDeleteSource);
        llSplit = findViewById(R.id.ll_split_m4b);
        llCopy = findViewById(R.id.ll_copy_internal);
        llDelete = findViewById(R.id.ll_delete_source);

        if (type.equals("Podcast") ) { type = "File";}
        bookToAdd = new BookToAdd(uri, type);

        if (bookToAdd.isBroken()) {
            myToastE("Could not read resource");
            finish();
            return;
        }

        audioBookTitle = bookToAdd.getAudioBookName();

        myLogD(bookToAdd.toString());

        // FORCING SPECIAL TYPE
        if (bookToAdd.getType().equalsIgnoreCase("m4b")) {
            type = "M4B";
        } else if (bookToAdd.getType().equalsIgnoreCase("zip")) {
            type = "ZIP";
        }

        tvFileName.setText(audioBookTitle);
        tvSourceLocation.setText(bookToAdd.getInfoSourceLocation());
        tvMimeExtension.setText(bookToAdd.getInfoMimeExtension());

        checkHashDoesNotAlreadyExist();

        btnCancel.setOnClickListener(v -> finish());

//----------------------------------------------------------------------------------------------------------------------------------
/// CHECKBOXES
//----------------------------------------------------------------------------------------------------------------------------------

        llSplit.setOnClickListener(v -> cbSplit.toggle());
        llCopy.setOnClickListener(v -> cbCopy.toggle());
        llDelete.setOnClickListener(v -> cbDelete.toggle());

        cbSplit.setChecked(Option.getSplitM4b());
        cbCopy.setChecked(Option.getCopyFile());
        cbDelete.setChecked(Option.getDeleteSourceFile());

        calculateCheckboxState();

        cbSplit.setOnCheckedChangeListener((buttonView, isChecked) -> {
            myLog("cbSplit CLICK : " + isChecked);
            if (!internalCheckBoxStateCalculationInProgress) calculateCheckboxState();
        });
        cbCopy.setOnCheckedChangeListener((buttonView, isChecked) -> {
            myLog("cbCopy CLICK : " + isChecked);
            if (!isChecked) {
                askForPermission();
                reDo_checkPathDoesNotAlreadyExist();
            } else {
                reDo_checkPathDoesNotAlreadyExist();
            }
            if (!internalCheckBoxStateCalculationInProgress) calculateCheckboxState();
        });
        cbDelete.setOnCheckedChangeListener((buttonView, isChecked) -> {
            myLog("cbDelete CLICK : " + isChecked);
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
                    Option.setDeleteSourceFile(false);
                }
            }
        });

//-------------------------------------------------------------------------------------------------------------------------------------------------
// CONFIRM BUTTON
//-------------------------------------------------------------------------------------------------------------------------------------------------

        btnConfirm.setOnClickListener(v -> {

            LoadBookTaskState state = new LoadBookTaskState();
            state.uri = uri;
            state.type = type;
            state.title = audioBookTitle;
            state.optionSplit = cbSplit.isChecked();
            state.optionCopy = cbCopy.isChecked();
            state.optionDelete = cbDelete.isChecked();
            state.originalType = bookToAdd.getOriginalType();
            state.originalFile = bookToAdd.getOriginalFile();
            state.originalHash = originalHash;
            state.sourceLocation = bookToAdd.getSourceLocation();
            state.fileExtension = bookToAdd.getFileExtension();
            state.mimeType = bookToAdd.getMimeType();

            setLoadBookTaskState(this, state); // save in SharedPrefs
            myLog("CLICK btnConfirm....   LoadBookTaskState saved - Sending ok Result");

            setResult(RESULT_OK);
            finish();
        });

        desactivateInteractive();


    }


    private void desactivateInteractive() {
        myLog("desactivate Interactive()");
        waitTextView.setVisibility(View.VISIBLE);
        warningTextView.setText("");
        errorTextView.setText("");
        cbSplit.setEnabled(false);
        llSplit.setEnabled(false);
        cbCopy.setEnabled(false);
        llCopy.setEnabled(false);
        cbDelete.setEnabled(false);
        llDelete.setEnabled(false);
        btnConfirm.setEnabled(false);
    }
    private void activateInteractive() {
        myLog("Activate Interactive()");
        waitTextView.setVisibility(View.GONE);
        cbSplit.setEnabled(true);
        llSplit.setEnabled(true);
        cbCopy.setEnabled(true);
        llCopy.setEnabled(true);
        cbDelete.setEnabled(true);
        llDelete.setEnabled(true);
        btnConfirm.setEnabled(true);
        calculateCheckboxState();
}

//-------------------------------------------------------------------------------------------------------------------------------------------------
//-------------------------------------------------------------------------------------------------------------------------------------------------
//-------------------------------------------------------------------------------------------------------------------------------------------------

    private void calculateCheckboxState() {
        internalCheckBoxStateCalculationInProgress = true;
        myLogD("calculateCheckboxState");

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
            llCopy.setEnabled(false);
            cbCopy.setChecked(true);
        }

        if (bookToAdd.getSourceLocation().equals("cloud") || bookToAdd.getSourceLocation().equals("web")) {
            cbCopy.setEnabled(false);
            llCopy.setEnabled(false);
            cbCopy.setChecked(true);
        }

        // delete
        if (cbCopy.isChecked() && !bookToAdd.getSourceLocation().equals("cloud") && !bookToAdd.getSourceLocation().equals("web")) {
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
            myLogEE(null,"onRequestPermissionsResult() - mPermissionRequest is null ! bad hook");
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
            myLogEE(e,"openAppSettingsOnPhone()");
        }
    }

    private void ShowWarning(String warningTxt) {
        String previousTxt = warningTextView.getText().toString();
        String newTxt = previousTxt.isEmpty() ? warningTxt : previousTxt + "\n" + warningTxt;
        warningTextView.setText(newTxt);
        warningTextView.setVisibility(View.VISIBLE);
    }

    private void ShowError(String txt) {
        String previousTxt = errorTextView.getText().toString();
        String newTxt = previousTxt.isEmpty() ? txt : previousTxt + "\n" + txt;
        errorTextView.setText(newTxt);
        errorTextView.setVisibility(View.VISIBLE);
    }


    private void checkHashDoesNotAlreadyExist() {
        myLog("Checking if hash already exists in DB for [" + uri + "]");

        final String TAG = WORKER_TAG_COMPUTE_HASH;

        // Cancel any ongoing hash computation to avoid overlap
        WorkManager.getInstance(this).cancelAllWorkByTag(TAG);

        // Prepare new input
        Data inputData = new Data.Builder()
                .putString("uri", uri.toString())
                .build();

        OneTimeWorkRequest hashRequest = new OneTimeWorkRequest.Builder(HashWorker.class)
                .setInputData(inputData)
                .addTag(TAG)
                .build();

        // Enqueue the worker
        WorkManager.getInstance(this).enqueue(hashRequest);

        waitTextView.setText(getString(R.string.init_check_already_imported_please_wait));

        // Observe result
        Observer<WorkInfo> observer = new Observer<WorkInfo>() {
            @Override
            public void onChanged(WorkInfo workInfo) {
                if (workInfo == null || !workInfo.getState().isFinished()) return;

                // Remove observer after first result
                WorkManager.getInstance(getApplicationContext())
                        .getWorkInfoByIdLiveData(hashRequest.getId())
                        .removeObserver(this);

                if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                    String hash = workInfo.getOutputData().getString(WORKER_TAG_COMPUTE_HASH);
                    originalHash = hash;
                    myLogD("Computed hash: " + hash);

                    btnConfirm.setEnabled(false);

                    new Thread(() -> {
                        String existingBook = AppDatabase.getDatabase(getApplicationContext()).FolderDao().originalHashAlreadyExist_getBookName(hash);
                        runOnUiThread(() -> {
                            if (existingBook != null) {
                                myLogW("Duplicate hash detected: already imported as [" + existingBook + "]");
                                ShowError(getString(R.string.error_media_already_loaded_samePath_under_the_name) + "\n" + existingBook);
                                waitTextView.setVisibility(View.GONE);
                                isKO = true;
                            } else {
                                myLogD("Hash OK: not found in DB.");
                                waitTextView.setText(getString(R.string.init_check_complementary_checks_please_wait));
                                okContinue();
                            }
                        });
                    }).start();

                } else if (workInfo.getState() == WorkInfo.State.FAILED) {
                    myLogEE(null, "Hash computation failed for uri: " + uri);
                    runOnUiThread(() -> {
                        ShowError(getString(R.string.error_could_not_check_hash));
                        waitTextView.setVisibility(View.GONE);
                        isKO = true;
                    });
                }
            }
        };

        WorkManager.getInstance(this)
                .getWorkInfoByIdLiveData(hashRequest.getId())
                .observe(this, observer);
    }

    private void okContinue() {
        checkPathDoesNotAlreadyExist();
    }

    private void reDo_checkPathDoesNotAlreadyExist() {
        desactivateInteractive();
        checkPathDoesNotAlreadyExist();
    }

    private void checkPathDoesNotAlreadyExist() {
        String strPath;
        if (cbCopy.isChecked()) {
            strPath = getFilesDir().getAbsolutePath() + "/" + FOLDER_UNZIPPED + "/" + audioBookTitle;
            myLog("Checking Folder Path doesn't already exist in DB (copy case) : [" + strPath + "]");
            new Thread(() -> {
                long lCheck = AppDatabase.getDatabase(this).FolderDao().folderAlreadyExist_checkFolderPath(strPath);
                runOnUiThread(() -> {
                    activateInteractive();
                    btnConfirm.setEnabled(true);
                    if (lCheck > 0) {
                        myLogW("KO, folder path does already exist in DB (copy case) : [" + strPath + "]");
                        audioBookTitle = audioBookTitle + " " + getCurrentDateTimeString();
                        ShowWarning(getString(R.string.error_media_already_loaded_samePath_so_changeName) + "\n[" + audioBookTitle + "]");
                    } else {
                        myLogD("OK, folder path doesn't already exist in DB");
                        checkNameDoesNotAlreadyExist();
                    }
                });
            }).start();
        } else {
            //strPath = uri.getPath();
            strPath = uri.toString();
            myLog("Checking Folder Path doesn't already exist in DB (direct link case, no copy) : [" + strPath + "]");
            new Thread(() -> {
                String audioBookAlreadyThere = AppDatabase.getDatabase(this).FolderDao().folderAlreadyExist_checkFolderPath_getBookName(strPath);
                runOnUiThread(() -> {
                    if (audioBookAlreadyThere != null) {
                        myLogW("KO, folder path does already exist in DB : [" + strPath + "]");
                        ShowError(getString(R.string.error_media_already_loaded_samePath) + audioBookAlreadyThere);
                    } else {
                        myLogD("OK, folder path doesn't already exist in DB");
                        checkNameDoesNotAlreadyExist();
                    }
                });
            }).start();
        }
    }


    private void checkNameDoesNotAlreadyExist() {
        myLog("Checking Folder Name doesn't already exist in DB : [" + audioBookTitle + "]");
        new Thread(() -> {
            long lCheck = AppDatabase.getDatabase(this).FolderDao().folderAlreadyExist_checkFolderName(audioBookTitle);
            runOnUiThread(() -> {
                activateInteractive();
                if (lCheck>0) {
                    myLogW("KO, folder name does already exist in DB : [" + audioBookTitle + "]");
                        ShowWarning(getString(R.string.error_media_already_loaded_sameName));
                } else {
                    myLogD("OK, folder name doesn't already exist in DB");
                }
            });
        }).start();
    }


}
