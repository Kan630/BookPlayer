package com.driot.bookplayer.imports;

import static com.driot.bookplayer.utils.HashWorker.HASH_NOT_COMPUTED;
import static com.driot.bookplayer.utils.HashWorker.WORKER_TAG_COMPUTE_HASH;
import static com.driot.bookplayer.utils.PermissionRequest.isReadAudioPermissionGranted;
import static com.driot.bookplayer.helpers.StorageHelper.getUnzipFolder;
import static com.driot.bookplayer.utils.Tonio.getCurrentDateTimeString;

import android.Manifest;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.driot.bookplayer.R;
import com.driot.bookplayer.nav.BaseBottomNavActivity;
import com.driot.bookplayer.activities.SupportedExtensionsActivity;
import com.driot.bookplayer.adapter.FolderSpinnerAdapter;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.CountCallback;
import com.driot.bookplayer.helpers.FileCounterHelper;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.utils.HashWorker;
import com.driot.bookplayer.utils.MsgBox;
import com.driot.bookplayer.utils.PermissionRequest;
import com.driot.bookplayer.helpers.StorageHelper;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executors;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ImportBookSingleActivity extends BaseBottomNavActivity {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean cancelScan = false;
    private boolean warningTooManyFilesHasBeenShown = false;
    private boolean initialHashCheckTriggered = false;

    private boolean hashJobRunning = false;
    private boolean countJobRunning = false;

    public static final String EXTRA_URI = "EXTRA_URI";
    public static final String EXTRA_FORCE_COPY = "EXTRA_FORCE_COPY"; // from OpenWithProxy...
    public static final String EXTRA_BOOK_CANDIDATE = "EXTRA_BOOK_CANDIDATE";

    private ImportBookSingleViewModel viewModel;

    private Uri uri;
    boolean forceCopy;

    private String audioBookTitle; // name can be changed... so keep as separate var

    private String originalHash;

    private TextView waitTextView, warningTextView, errorTextView;
    private TextView tvAppendMode;
    private Spinner destinationFolderSpinner;
    private CheckBox cbSplit, cbCopy, cbDelete, cbUseSdCard;
    private LinearLayout llSplit, llCopy, llDelete, llUseSdCard;
    private Button btnConfirm, btnCancel;
    private ProgressBar progressBarStep1, progressBarStep2;
    private TextView tvProgressStatusStep1, tvProgressStatusStep2;

    private boolean internalCheckBoxStateCalculationInProgress;
    private boolean boolAlso = false;

    Folder folderToAddTo = null;

    private PermissionRequest mPermissionRequest;

    private static final int REQ_DELETE_SOURCE = 2001;

    @Override
    protected int getNavId() {
        return R.id.nav_add;
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_import_book_single;
    }

    @Override
    protected boolean enableOngoingTaskOverlay() {
        return false;
    }

    protected boolean displayBottomNavBar() {
        return false;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        viewModel = new ViewModelProvider(this).get(ImportBookSingleViewModel.class);

        BookCandidate passedCandidate = getIntent().getParcelableExtra(EXTRA_BOOK_CANDIDATE);
        boolean detailMode = (passedCandidate != null);

        if (detailMode) {
            myLog("detail mode. candidate: " + passedCandidate.name);
        } else {
            uri = getIntent().getParcelableExtra(EXTRA_URI);
            forceCopy = getIntent().getBooleanExtra(EXTRA_FORCE_COPY, false);
            folderToAddTo = getIntent().getParcelableExtra(Intents.EXTRA_ADD_TO_FOLDER);

            if (Objects.isNull(uri)) {
                myToastEE(null, "Error picking audio : [uri is null]");
                finish();
                return;
            }
        }

        TextView tvFileName = findViewById(R.id.tvFileName);
        ImageView ivCover = findViewById(R.id.ivCover);
        TextView tvMimeExtension = findViewById(R.id.tvMimeExtension);
        TextView tvInfoLine1 = findViewById(R.id.tvInfoLine1);
        btnConfirm = findViewById(R.id.btnConfirm);
        btnCancel = findViewById(R.id.btnCancel);

        waitTextView = findViewById(R.id.waitTextView);
        waitTextView.setText(getString(R.string.init_please_wait));
        warningTextView = findViewById(R.id.warningTextView);
        warningTextView.setVisibility(View.GONE);
        errorTextView = findViewById(R.id.errorTextView);
        errorTextView.setVisibility(View.GONE);

        tvAppendMode = findViewById(R.id.tvAppendMode);
        destinationFolderSpinner = findViewById(R.id.spinner_destination_folder);

        progressBarStep1 = findViewById(R.id.loadingProgressBarStep1);
        tvProgressStatusStep1 = findViewById(R.id.tvProgressStatusStep1);
        progressBarStep2 = findViewById(R.id.loadingProgressBarStep2);
        tvProgressStatusStep2 = findViewById(R.id.tvProgressStatusStep2);

        cbSplit = findViewById(R.id.cbSplitM4B);
        cbCopy = findViewById(R.id.cbCopyInternal);
        cbUseSdCard = findViewById(R.id.cbUseSdCard);
        cbDelete = findViewById(R.id.cbDeleteSource);
        llSplit = findViewById(R.id.ll_split_m4b);
        llCopy = findViewById(R.id.ll_copy_internal);
        llUseSdCard = findViewById(R.id.ll_use_sdcard);
        llDelete = findViewById(R.id.ll_delete_source);

        tvFileName.setText("...");

        // init checkbox by loading default from general settings
        // then, it will be controlled and maybe changed by dynamic checks
        cbSplit.setChecked(Option.getSplitM4b());
        cbUseSdCard.setChecked(Option.getUseSdCard());
        cbDelete.setChecked(Option.getDeleteSourceFile());
        cbCopy.setChecked(Option.getCopyFile());

        // Observe BookCandidate from ViewModel
        viewModel.getBookCandidate().observe(this, bookCandidate -> {
            if (bookCandidate == null)
                return;

            if (!bookCandidate.isMimeSupported) {
                startActivity(SupportedExtensionsActivity.newIntent(this, bookCandidate.infoMimeExtensionSmall));
                finish();
                return;
            }
            if (bookCandidate.isBroken) {
                myToastEE(null, getString(R.string.could_not_read_resource));
                finish();
                return;
            }

            audioBookTitle = bookCandidate.audioBookName;
            myLogD(bookCandidate.toString());

            tvFileName.setText(audioBookTitle);
            if (bookCandidate.coverImagePath != null) {
                ivCover.setImageURI(Uri.parse(bookCandidate.coverImagePath));
            } else {
                ivCover.setImageResource(R.drawable.no_image_icon);
            }
            tvInfoLine1.setText(bookCandidate.infoLine1);
            tvMimeExtension.setText(bookCandidate.infoMimeExtension);

            if (!detailMode) {
                // Now that candidate is ready, activate UI
                if (!initialHashCheckTriggered) {
                    checkHashDoesNotAlreadyExist();
                    initialHashCheckTriggered = true;
                }
                calculateCheckboxState(bookCandidate);
                setupCheckboxListeners(bookCandidate);

            }

            // Check for ebook warning
            if (Var.SUPPORTED_EBOOK_EXTENSIONS
                    .contains(bookCandidate.sourceType.replace(".", "").toLowerCase(Locale.ROOT))) {
                showWarning(getString(R.string.text_to_speech) + " " + getString(R.string.still_in_development) + "\n"
                        + getString(R.string.beta_test) + "\n" + getString(R.string.weird_behavior_could_happen));
            }

        });
        // Observe real-time tracks
        LinearLayout llTrackListContainer = findViewById(R.id.llTrackListContainer);
        LinearLayout llTrackList = findViewById(R.id.llTrackList);
        TextView tvTrackListTitle = findViewById(R.id.tvTrackListTitle);
        llTrackListContainer.setVisibility(View.GONE);

        viewModel.getRealTimeTracks().observe(this, tracks -> {
            if (tracks == null || tracks.isEmpty()) {
                llTrackListContainer.setVisibility(View.GONE);
            } else {
                llTrackListContainer.setVisibility(View.VISIBLE);

                String txtTitle = getResources().getQuantityString(R.plurals.tracks_found_count, tracks.size(),
                        tracks.size());
                tvTrackListTitle.setText(txtTitle);

                llTrackList.removeAllViews();
                for (String track : tracks) {
                    TextView tv = new TextView(this);
                    tv.setText(track);
                    tv.setTextSize(12);
                    tv.setMaxLines(1);
                    tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    tv.setPadding(0, 4, 0, 4);
                    llTrackList.addView(tv);
                }
            }
        });

        if (detailMode) {
            // DETAIL MODE

            myLogI("Using passed BookCandidate: " + passedCandidate.name);
            viewModel.setBookCandidate(passedCandidate);
            hideAndDisableEverything();

        } else {
            // SINGLE IMPORT MODE

            if (viewModel.getBookCandidate().getValue() == null) {
                viewModel.initializeBookCandidate(uri);
            }

            displayAppendWarning();
            buildDestinationFolderSpinner();

            // Observe originalHash from ViewModel
            viewModel.getOriginalHash().observe(this, hash -> {
                originalHash = hash;
            });

            // Observe loading state
            viewModel.getIsLoading().observe(this, isLoading -> {
                if (isLoading) {
                    waitTextView.setVisibility(View.VISIBLE);
                } else {
                    waitTextView.setVisibility(View.GONE);
                }
            });

            // Observe errors
            viewModel.getErrorMessage().observe(this, errorMsg -> {
                if (errorMsg != null && !errorMsg.isEmpty()) {
                    myToastEE(null, errorMsg);
                    finish();
                }
            });

            // Observe loading status for ProgressBar

            viewModel.getLoadingStatus().observe(this, status -> {
                if (status == 0) {
                    progressBarStep2.setVisibility(View.GONE);
                    tvProgressStatusStep2.setVisibility(View.GONE);

                    // Fast Init - Yellow
                    progressBarStep1.setVisibility(View.VISIBLE);
                    progressBarStep1.setIndeterminate(true);
                    progressBarStep1.getIndeterminateDrawable().setColorFilter(
                            getResources().getColor(R.color.yellow, null),
                            android.graphics.PorterDuff.Mode.SRC_IN);

                    tvProgressStatusStep1.setVisibility(View.VISIBLE);
                    tvProgressStatusStep1.setText(R.string.loading_step_1);
                    tvProgressStatusStep1.setTextColor(getResources().getColor(R.color.yellow, null));

                } else if (status == 1) {
                    // Heavy Init - Light Green
                    progressBarStep1.setVisibility(View.GONE);
                    tvProgressStatusStep1.setVisibility(View.GONE);

                    progressBarStep2.setVisibility(View.VISIBLE);
                    progressBarStep2.setIndeterminate(true);
                    progressBarStep2.getIndeterminateDrawable().setColorFilter(
                            getResources().getColor(R.color.green_300, null),
                            android.graphics.PorterDuff.Mode.SRC_IN);

                    tvProgressStatusStep2.setVisibility(View.VISIBLE);
                    tvProgressStatusStep2.setText(R.string.loading_step_2);
                    tvProgressStatusStep2.setTextColor(getResources().getColor(R.color.green_300, null));

                } else {
                    // Done
                    progressBarStep1.setVisibility(View.GONE);
                    tvProgressStatusStep1.setVisibility(View.GONE);
                    progressBarStep2.setVisibility(View.GONE);
                    tvProgressStatusStep2.setVisibility(View.GONE);

                }
            });

            btnCancel.setOnClickListener(v -> {
                myLogI("------ USER CLICKS btn CANCEL....   ");
                cancelScan = true;
                finish();
            });

            cbSplit.setOnCheckedChangeListener((buttonView, isChecked) -> {
                myLogI("USER CHECKS -SPLIT- : " + isChecked);
                if (!internalCheckBoxStateCalculationInProgress) {
                    BookCandidate bc = viewModel.getBookCandidate().getValue();
                    if (bc != null)
                        calculateCheckboxState(bc);
                }
            });
            cbCopy.setOnCheckedChangeListener((buttonView, isChecked) -> {
                myLog("USER CHECKS -COPY- : " + isChecked);
                if (!isChecked) {
                    askForPermission();
                    reDo_checkPathDoesNotAlreadyExist();
                } else {
                    reDo_checkPathDoesNotAlreadyExist();
                }
                if (!internalCheckBoxStateCalculationInProgress) {
                    BookCandidate bc = viewModel.getBookCandidate().getValue();
                    if (bc != null)
                        calculateCheckboxState(bc);
                }
            });
            cbUseSdCard.setOnCheckedChangeListener((buttonView, isChecked) -> {
                myLog("USER CHECKS -SD CARD- : " + isChecked);
                if (!internalCheckBoxStateCalculationInProgress) {
                    BookCandidate bc = viewModel.getBookCandidate().getValue();
                    if (bc != null)
                        calculateCheckboxState(bc);
                }
            });
            cbDelete.setOnCheckedChangeListener((buttonView, isChecked) -> {
                myLog("USER CHECKS -DELETE- " + isChecked);
                if (!internalCheckBoxStateCalculationInProgress) {
                    if (isChecked) {
                        MsgBox.ask(this,
                                getString(R.string.option_alert_delete_picked_source_file_title),
                                getString(R.string.option_alert_delete_picked_source_file_message),
                                null,
                                getString(android.R.string.ok),
                                getString(android.R.string.cancel),
                                REQ_DELETE_SOURCE);
                    } else {
                        Option.setDeleteSourceFile(false);
                    }
                }
            });

            // -------------------------------------------------------------------------------------------------------------------------------------------------
            // CONFIRM BUTTON
            // -------------------------------------------------------------------------------------------------------------------------------------------------

            btnConfirm.setOnClickListener(v -> {
                myLogI("------ USER CLICKS btnConfirm....   ");

                // Disable immediately to prevent double taps
                btnConfirm.setEnabled(false);

                // Cancel any ongoing heavy initialization

                // Observe Real-time tracks
                viewModel.cancelInitialization();

                // Get bookCandidate from ViewModel
                BookCandidate bookCandidate = viewModel.getBookCandidate().getValue();
                if (bookCandidate == null) {
                    myLogEE(null, "bookCandidate is null when confirm clicked");
                    btnConfirm.setEnabled(true);
                    return;
                }

                AppDatabase.databaseReadExecutor.execute(() -> {
                    String futureFolderName;
                    String finalFutureFolderPath;

                    if (folderToAddTo == null) {
                        String futureFolderPath;
                        long lCheck;
                        if (!cbCopy.isChecked()) {
                            lCheck = 0;
                            futureFolderPath = uri.toString();
                        } else {
                            futureFolderPath = getUnzipFolder(this, cbUseSdCard.isChecked()).getAbsolutePath() + "/"
                                    + audioBookTitle;
                            myLogD("Checking Folder Path doesn't already exist in DB (internal copy case) : ["
                                    + futureFolderPath + "]");
                            String existingPath = ImportValidator.checkPathExists(ImportBookSingleActivity.this,
                                    futureFolderPath);
                            lCheck = existingPath != null ? 1 : 0;
                        }
                        finalFutureFolderPath = futureFolderPath;
                        // btnConfirm.setEnabled(true);
                        if (lCheck > 0) {
                            futureFolderName = audioBookTitle + " " + getCurrentDateTimeString();
                            myLogW("folder path does already exist in DB (internal copy case) : ["
                                    + finalFutureFolderPath
                                    + "]");
                            myLog("filesystem folder name changed to [" + futureFolderName + "]");
                        } else {
                            futureFolderName = audioBookTitle;
                            myLogD("ok, filesystem folder name = [" + futureFolderName + "]");
                        }
                    } else {
                        myLogD("adding to existing book => overwriting folder values");
                        audioBookTitle = folderToAddTo.getName();
                        futureFolderName = folderToAddTo.getName();
                        finalFutureFolderPath = folderToAddTo.getPath();
                    }

                    final boolean anotherRunning = ImportHelper.isAnyImportActiveSync(this.getApplicationContext());

                    ImportBookTaskState state = new ImportBookTaskState();
                    state.originalUri = uri;
                    state.sourceType = bookCandidate.sourceType;
                    state.dynamicUri = uri;
                    state.dynamicType = "Folder".equals(bookCandidate.sourceType) ? "Folder" : "File";
                    state.title = audioBookTitle;
                    state.futureFolderName = futureFolderName;
                    state.futureFolderPath = finalFutureFolderPath;
                    state.optionSplit = cbSplit.isChecked();
                    state.optionCopy = cbCopy.isChecked();
                    state.optionDelete = cbDelete.isChecked();
                    state.originalFile = bookCandidate.originalFile;
                    state.originalHash = originalHash;
                    state.sourceLocation = bookCandidate.sourceLocation;
                    state.fileExtension = bookCandidate.fileExtension;
                    state.mimeType = bookCandidate.mimeType;
                    state.playType = bookCandidate.playType;
                    state.addToExistingFolderId = (folderToAddTo == null ? -1 : folderToAddTo.getId());

                    runOnUiThread(() -> {
                        if (anotherRunning) {
                            // Re-enable so user can try again later
                            btnConfirm.setEnabled(true);
                            showWarning(getString(R.string.please_wait_another_book));
                            return;
                        }

                        // No active import -> enqueue and finish
                        setResult(RESULT_OK);

                        // Enqueue on background (or main—WorkManager is fine either way)
                        AppDatabase.databaseWriteExecutor.execute(() -> BookLoadingWorkLauncher
                                .launch(this.getApplicationContext(), state, /* sequential = */ false));
                        finish();
                    });

                });
            });

            desactivateInteractive();

        }

    }

    /**
     * Setup checkbox listeners that depend on bookCandidate being initialized.
     * This is called from the async callback after bookCandidate is ready.
     */
    private void setupCheckboxListeners(BookCandidate bookCandidate) {
        // Firebase analytics tracking when delete checkbox changes
        FirebaseAnalyticsHelper.tellAnalyticsManualLoad(
                bookCandidate.sourceType,
                bookCandidate.fileExtension,
                bookCandidate.sourceLocation,
                bookCandidate.originalFile);
    }

    private void desactivateInteractive() {
        myLog("desactivate Interactive()");
        // waitTextView.setVisibility(View.VISIBLE);
        warningTextView.setText("");
        errorTextView.setText("");
        cbSplit.setEnabled(false);
        llSplit.setEnabled(false);
        cbCopy.setEnabled(false);
        llCopy.setEnabled(false);
        cbDelete.setEnabled(false);
        llDelete.setEnabled(false);
        btnConfirm.setEnabled(false);
        cbUseSdCard.setEnabled(false);
        llUseSdCard.setEnabled(false);
        llUseSdCard.setAlpha(0.4f);
        llDelete.setAlpha(0.4f);
        llCopy.setAlpha(0.4f);
        llSplit.setAlpha(0.4f);
    }

    private void activateInteractive() {
        myLog("Activate Interactive()");
        // waitTextView.setVisibility(View.GONE);
        cbSplit.setEnabled(true);
        llSplit.setEnabled(true);
        cbCopy.setEnabled(true);
        llCopy.setEnabled(true);
        cbDelete.setEnabled(true);
        llDelete.setEnabled(true);
        btnConfirm.setEnabled(true);
        cbUseSdCard.setEnabled(true);
        llUseSdCard.setEnabled(true);
        llUseSdCard.setAlpha(1.0f);
        llDelete.setAlpha(1.0f);
        llCopy.setAlpha(1.0f);
        llSplit.setAlpha(1.0f);
        calculateCheckboxState(viewModel.getBookCandidate().getValue());
    }

    // -------------------------------------------------------------------------------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------------------------------------------------------

    private void calculateCheckboxState(BookCandidate bookCandidate) {
        internalCheckBoxStateCalculationInProgress = true;
        myLogD("calculateCheckboxState");

        if (bookCandidate.supportsSplit()) {
            llSplit.setVisibility(View.VISIBLE);
            if (bookCandidate.requiresForcedSplitCopy(cbSplit.isChecked())) {
                cbCopy.setChecked(true);
                cbCopy.setEnabled(false);
                llCopy.setEnabled(false);
                llCopy.setAlpha(0.4f);
            } else {
                cbCopy.setEnabled(true);
                llCopy.setEnabled(true);
                llCopy.setAlpha(1.0f);
            }
        } else {
            llSplit.setVisibility(View.GONE);
        }

        if (bookCandidate.requiresForcedCopy() || forceCopy) {
            cbCopy.setChecked(true);
            cbCopy.setEnabled(false);
            llCopy.setEnabled(false);
            llCopy.setAlpha(0.4f);
        }

        if (!StorageHelper.isExternalSDCardAvailable(this)) {
            llUseSdCard.setVisibility(View.GONE);
        } else {
            llUseSdCard.setVisibility(View.VISIBLE);
            if (cbCopy.isChecked()) {
                cbUseSdCard.setEnabled(true);
                llUseSdCard.setEnabled(true);
                llUseSdCard.setAlpha(1.0f);

            } else {
                cbUseSdCard.setChecked(false);
                cbUseSdCard.setEnabled(false);
                llUseSdCard.setEnabled(false);
                llUseSdCard.setAlpha(0.4f);
            }
        }

        // delete
        if (cbCopy.isChecked() && !bookCandidate.sourceLocation.equals("cloud")
                && !bookCandidate.sourceLocation.equals("web")) {
            cbDelete.setEnabled(true);
            llDelete.setEnabled(true);
            llDelete.setAlpha(1.0f);
        } else {
            cbDelete.setChecked(false);
            cbDelete.setEnabled(false);
            llDelete.setEnabled(false);
            llDelete.setAlpha(0.4f);
        }

        internalCheckBoxStateCalculationInProgress = false;
    }

    // -------------------------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------
    // -- PERMISSIONS
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
        if (Build.VERSION.SDK_INT < 33) {
            myLog("checkPermissionsReadStorage() < 33");
            mPermissionRequest = PermissionRequest
                    .with(this)
                    .permissions(Manifest.permission.READ_EXTERNAL_STORAGE) // Manifest.permission.READ_EXTERNAL_STORAGE,
                    .rationale(R.string.permission_read_write_rationale_short_text_on_load)
                    // .granted(R.string.permission_read_write_granted) // Tonio no need to display
                    // message if granted OK
                    .denied(R.string.permission_read_write_denied)
                    .snackbar((ViewGroup) findViewById(android.R.id.content))
                    .submit();
        } else {
            myLog("checkPermissionsReadStorage() >= 33");
            mPermissionRequest = PermissionRequest
                    .with(this)
                    .permissions(Manifest.permission.READ_MEDIA_AUDIO) // Manifest.permission.READ_EXTERNAL_STORAGE,
                    .rationale(R.string.permission_read_write_rationale_short_text_on_load)
                    // .granted(R.string.permission_read_write_granted) // Tonio no need to display
                    // message if granted OK
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
        MsgBox.alertWithNeutral(this,
                getString(R.string.Permission),
                getString(R.string.permission_read_denied_short_text_on_load),
                null,
                "App Info",
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", getPackageName(), null)));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {

        for (int i = 0; i < grantResults.length; i++) {
            myLog(permissions[i] + " => " + grantResults[i] + "   -requestCode=" + requestCode);
        }
        myLog("onRequestPermissionsResult() : " + permissions[0] + " - " + requestCode + " - " + grantResults[0]);
        // Redirect hook call to permission helper method.
        if (mPermissionRequest != null) {
            mPermissionRequest.onRequestPermissionsResult(requestCode, permissions, grantResults);
            mPermissionRequest = null; // request no longer needed
        } else {
            myLogEE(null, "onRequestPermissionsResult() - mPermissionRequest is null ! bad hook");
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
            myLogEE(e, "openAppSettingsOnPhone()");
        }
    }

    private void showWarning(String warningTxt) {
        String previousTxt = warningTextView.getText().toString();
        String newTxt = previousTxt.isEmpty() ? warningTxt : previousTxt + "\n" + warningTxt;
        warningTextView.setText(newTxt);
        warningTextView.setVisibility(View.VISIBLE);
    }

    private void showError(String txt) {
        String previousTxt = errorTextView.getText().toString();
        String newTxt = previousTxt.isEmpty() ? txt : previousTxt + "\n" + txt;
        errorTextView.setText(newTxt);
        errorTextView.setVisibility(View.VISIBLE);
    }

    private void checkHashDoesNotAlreadyExist() {
        myLog("Checking if hash already exists in DB for [" + uri + "]");

        hashJobRunning = true;
        waitTextView.setText(getString(R.string.init_check_already_imported_please_wait));
        updateLoadingUi();

        final String TAG = WORKER_TAG_COMPUTE_HASH;

        // Cancel any ongoing hash computation to avoid overlap
        WorkManager.getInstance(this).cancelAllWorkByTag(TAG);

        OneTimeWorkRequest hashRequest = new OneTimeWorkRequest.Builder(HashWorker.class)
                .setInputData(new Data.Builder().putString("uri", uri.toString()).build())
                .addTag(TAG)
                .build();

        WorkManager.getInstance(this).enqueue(hashRequest);

        waitTextView.setText(getString(R.string.init_check_already_imported_please_wait));

        // Observe result
        Observer<WorkInfo> observer = new Observer<>() {
            @Override
            public void onChanged(WorkInfo workInfo) {
                myLogD("WorkInfo changed: " + workInfo);
                if (workInfo == null || !workInfo.getState().isFinished())
                    return;

                // Remove observer after first result
                WorkManager.getInstance(getApplicationContext())
                        .getWorkInfoByIdLiveData(hashRequest.getId())
                        .removeObserver(this);

                if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                    String hash = workInfo.getOutputData().getString(WORKER_TAG_COMPUTE_HASH);
                    originalHash = hash;
                    myLogD("Computed hash: [" + hash + "]");

                    if (hash == null || hash.isEmpty() || hash.equals(HASH_NOT_COMPUTED)) {
                        myLogEE(null, "bad returned Hash for uri " + uri);
                        showWarning(getString(R.string.could_not_check_already_imported));
                        okContinue(viewModel.getBookCandidate().getValue());
                    } else {
                        btnConfirm.setEnabled(false);
                        new Thread(() -> {
                            String existingBook = ImportValidator.checkHashExists(ImportBookSingleActivity.this, hash);
                            runOnUiThread(() -> {
                                if (existingBook != null) {
                                    if (uri.toString().startsWith("http")) {
                                        // TODO, ideally, a second hash column should be computed "realHashOfTheContent"
                                        myLogW("same Hash [" + hash + "] for URL " + uri + " for book = "
                                                + existingBook);
                                        showWarning(getString(R.string.warning_url_already_loaded_under_the_name) + " ["
                                                + existingBook + "]\n");
                                        boolAlso = true;
                                        okContinue(viewModel.getBookCandidate().getValue());
                                    } else {
                                        myLog("-----------------------------------------------------------------------------------");
                                        myLogW("Duplicate hash detected: already imported as [" + existingBook + "]");
                                        myLog("-----------------------------------------------------------------------------------");
                                        showError(getString(R.string.error_media_already_loaded_samePath_under_the_name)
                                                + "\n" + existingBook);
                                        stopAndDisableEverything();
                                        return;
                                    }
                                } else {
                                    myLogD("Hash OK: not found in DB.");
                                    waitTextView
                                            .setText(getString(R.string.init_check_complementary_checks_please_wait));
                                    okContinue(viewModel.getBookCandidate().getValue());
                                }
                            });
                        }).start();
                    }
                } else if (workInfo.getState() == WorkInfo.State.FAILED) {
                    myLogEE(null, "Hash computation failed for uri: " + uri);
                    showWarning(getString(R.string.could_not_check_already_imported));
                    okContinue(viewModel.getBookCandidate().getValue());
                }
            }
        };

        WorkManager.getInstance(this)
                .getWorkInfoByIdLiveData(hashRequest.getId())
                .observe(this, observer);
    }

    private void okContinue(BookCandidate bookCandidate) {
        if ("Folder".equals(bookCandidate.sourceType) && bookCandidate.hasMultipleBooksInFolder()) {
            showError(getString(R.string.error_folder_multiple_books));
            stopAndDisableEverything();
            return;
        }
        checkPathDoesNotAlreadyExist();
    }

    private void reDo_checkPathDoesNotAlreadyExist() {
        desactivateInteractive();
        checkPathDoesNotAlreadyExist();
    }

    private void checkPathDoesNotAlreadyExist() {
        if (!cbCopy.isChecked()) { // only for direct link (if file copied, the app must deal it self with
                                   // duplicates paths)
            String strPath = uri.toString();
            myLog("Checking Folder Path doesn't already exist in DB (direct link case, no copy) : [" + strPath + "]");
            new Thread(() -> {
                String audioBookAlreadyThere = AppDatabase.getDatabase(this).folderDao()
                        .folderAlreadyExist_checkFolderPath_getBookName(strPath);
                runOnUiThread(() -> {
                    if (audioBookAlreadyThere != null) {
                        myLogW("KO, folder path does already exist in DB : [" + strPath + "]");
                        showError(getString(R.string.error_media_already_loaded_samePath) + audioBookAlreadyThere);
                        stopAndDisableEverything();
                    } else {
                        myLogD("OK, folder path doesn't already exist in DB");
                        checkNameDoesNotAlreadyExist();
                    }
                });
            }).start();
        } else {
            checkNameDoesNotAlreadyExist();
        }
    }

    private void checkNameDoesNotAlreadyExist() {
        myLog("Checking Folder Name doesn't already exist in DB : [" + audioBookTitle + "]");
        new Thread(() -> {
            boolean nameExists = ImportValidator.checkNameExists(ImportBookSingleActivity.this, audioBookTitle);
            runOnUiThread(() -> {
                hashJobRunning = false;
                updateLoadingUi();
                if (nameExists) {
                    myLogW("KO, folder name does already exist in DB : [" + audioBookTitle + "]");
                    audioBookTitle = audioBookTitle + " " + getCurrentDateTimeString();
                    String strText;
                    if (boolAlso) {
                        strText = getString(R.string.Also);
                    } else {
                        strText = getString(R.string.A_different_media_with_the_same_name_had_already_been_loaded);
                    }
                    showWarning(
                            strText + getString(R.string.the_name_will_be_changed_to) + "\n[" + audioBookTitle + "]");
                    myLogW("book name changed to [" + audioBookTitle + "]");
                } else {
                    myLogD("OK, folder name doesn't already exist in DB");
                }
            });
        }).start();
    }

    private void displayAppendWarning() {
        if (folderToAddTo != null) {
            myLog("ADD NEW TRACKS MODE ---> to [" + folderToAddTo.getName() + "]");
        }
        tvAppendMode.setVisibility(folderToAddTo != null ? View.VISIBLE : View.GONE);
    }

    private void buildDestinationFolderSpinner() {

        AppDatabase.databaseReadExecutor.execute(() -> {
            List<Folder> items = AppDatabase.getDatabase(this).folderDao().getAll();
            // Create the neutral first item
            Folder neutral = new Folder();
            neutral.setId(-1); // special fake ID
            neutral.setName("New book"); // the label
            // Insert at index 0
            items.add(0, neutral);
            // Compute preselection
            int selectedPosition = 0;
            if (folderToAddTo != null) {
                for (int i = 1; i < items.size(); i++) { // start at 1 because 0 = neutral
                    if (Objects.equals(folderToAddTo.getId(), items.get(i).getId())) {
                        selectedPosition = i;
                        break;
                    }
                }
            }
            // Switch to UI thread
            final int finalSelectedPosition = selectedPosition;
            runOnUiThread(() -> {
                FolderSpinnerAdapter adapter = new FolderSpinnerAdapter(this, items);
                destinationFolderSpinner.setAdapter(adapter);
                destinationFolderSpinner.setSelection(finalSelectedPosition);

                destinationFolderSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        Folder selected = (Folder) parent.getItemAtPosition(position);

                        boolean isNeutral = selected.getId() == -1; // check fake item
                        if (isNeutral) {
                            tvAppendMode.setVisibility(View.GONE);
                            folderToAddTo = null;
                        } else {
                            tvAppendMode.setVisibility(View.VISIBLE);
                            folderToAddTo = selected;
                        }
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                });
            });
        });

    }

    private void startCounting(Uri folderTreeUri, int depth, TextView tvCountFolder, String prefix, String playType) {
        cancelScan = false;
        countJobRunning = true;
        updateLoadingUi();

        boolean countTextFiles = Var.PLAY_TYPE_TEXT.equals(playType);
        int filesPluralResId = countTextFiles ? R.plurals.text_files_count : R.plurals.audio_files_count;

        Executors.newSingleThreadExecutor().execute(() -> {
            FileCounterHelper.countFilesFromTreeUriRealtime(
                    this,
                    folderTreeUri,
                    depth,
                    countTextFiles,
                    new CountCallback() {
                        @Override
                        public void onCountUpdated(int fileCount, String currentPath, int subFolderCount) {
                            mainHandler.post(() -> {
                                Resources res = getResources();
                                String filesPart = res.getQuantityString(filesPluralResId, fileCount, fileCount);
                                String txt;
                                if (subFolderCount > 0) {
                                    String foldersPart = res.getQuantityString(R.plurals.subfolders_count,
                                            subFolderCount, subFolderCount);
                                    txt = prefix + " : " + getString(
                                            R.string.count_status,
                                            filesPart,
                                            foldersPart);
                                } else {
                                    txt = prefix + " " + filesPart;
                                }
                                tvCountFolder.setText(txt);
                                waitTextView.setText(getString(R.string.scanning_tracks) + " " + currentPath);

                                if (subFolderCount > 10 || fileCount > 100) {
                                    if (!warningTooManyFilesHasBeenShown) {
                                        showWarning(getString(R.string.import_warning_lot_of_file_and_subfolders));
                                    }
                                    warningTooManyFilesHasBeenShown = true;
                                }
                            });
                        }

                        @Override
                        public boolean isCancelled() {
                            return cancelScan;
                        }

                        @Override
                        public void onFinished(int fileCount, int folderCount) {
                            mainHandler.post(() -> {
                                myLog("FINISHED COUNT: files=" + fileCount + " folders=" + folderCount);
                                // tvCountFolder.setText(getString(R.string.done) + ": " +
                                // tvCountFolder.getText());

                                countJobRunning = false;
                                updateLoadingUi();
                            });
                        }
                    });
        });
    }

    private void updateLoadingUi() {
        boolean anyRunning = hashJobRunning || countJobRunning;

        if (anyRunning) {
            // "init" mode
            waitTextView.setVisibility(View.VISIBLE);
            // you can also keep desactivateInteractive() here if you want
            // but then remove the call from onCreate to avoid double-clearing texts
            // desactivateInteractive();
        } else {
            waitTextView.setVisibility(View.GONE);
            activateInteractive();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_DELETE_SOURCE) {
            if (resultCode == RESULT_OK) {
                BookCandidate bc = viewModel.getBookCandidate().getValue();
                if (bc != null) {
                    calculateCheckboxState(bc);
                }
            } else {
                cbDelete.setChecked(false);
            }
        }
    }

    private void stopAndDisableEverything() {
        myLog("stop And Disable Everything()");
        disableEveryThing();
        btnConfirm.setVisibility(View.GONE);
        btnCancel.setVisibility(View.VISIBLE); // Change Cancel to Close
        btnCancel.setText(R.string.Close); // Change Cancel to Close
        warningTextView.setVisibility(View.GONE);
    }

    private void hideAndDisableEverything() {
        disableEveryThing();
        findViewById(R.id.llButtons).setVisibility(View.GONE);
    }

    private void disableEveryThing() {
        findViewById(R.id.llAppendAndDest).setVisibility(View.GONE);
        findViewById(R.id.vSeparator1).setVisibility(View.GONE);
        findViewById(R.id.llOptions).setVisibility(View.GONE);
        findViewById(R.id.vSeparator2).setVisibility(View.GONE);
        btnConfirm.setVisibility(View.GONE);
        btnCancel.setVisibility(View.VISIBLE); // Change Cancel to Close
        btnCancel.setText(R.string.Close); // Change Cancel to Close
        // Hide other editing/importing options if needed
        llSplit.setVisibility(View.GONE);
        llCopy.setVisibility(View.GONE);
        llUseSdCard.setVisibility(View.GONE);
        llDelete.setVisibility(View.GONE);
        tvAppendMode.setVisibility(View.GONE);
        destinationFolderSpinner.setVisibility(View.GONE);
        waitTextView.setVisibility(View.GONE);
        progressBarStep1.setVisibility(View.GONE);
        tvProgressStatusStep1.setVisibility(View.GONE);
        progressBarStep2.setVisibility(View.GONE);
        tvProgressStatusStep2.setVisibility(View.GONE);
    }

}
