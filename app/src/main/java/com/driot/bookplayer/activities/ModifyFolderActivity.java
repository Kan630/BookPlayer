package com.driot.bookplayer.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.FileHelper;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.services.DeleteFolderWorker;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.io.File;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 15/11/20
 */
public class ModifyFolderActivity extends LoggingActivity {

    private Folder folder;
    private View blockingOverlay;
    private Button bDelete, bReset, bExport;
    private Button bChangeCover, bDeleteCover, bGenerateCover;

    EditText etIntroCut;
    EditText etRename;

    private ImageView ivCoverPreview;

    private volatile boolean isDeleting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_modify_folder);
        InsetHelper.apply(this);

        bDelete = findViewById(R.id.bDelete);
        bReset = findViewById(R.id.bReset);
        bExport = findViewById(R.id.bExport);
        blockingOverlay = findViewById(R.id.blockingOverlay);
        bDeleteCover = findViewById(R.id.bDeleteCover);
        bGenerateCover = findViewById(R.id.bGenerateCover);
        bChangeCover = findViewById(R.id.bChangeCover);

        TextView tvTitle = findViewById(R.id.title);
        TextView tvInfo = findViewById(R.id.tvInfo);
        ImageView ivStorageIcon = findViewById(R.id.imageViewStorageIcon);
        TextView tvStorageIcon = findViewById(R.id.textViewStorageIcon);

        folder = getIntent().getParcelableExtra("folder");
        if (folder == null) {
            myLogEE(null, "could_not_identify_folder_to_modify");
            myToastE(getString(R.string.could_not_identify_folder_to_modify));
            finish();
            return;
        }

        tvTitle.setText(folder.getName());

        etRename = findViewById(R.id.etRename);
        etRename.setText(folder.getName());

        String memoryLocationText = getString(R.string.AudioLocation) + " : " + folder.getMemoryLocationText(this);
        int memoryLocationIcon = folder.getMemoryLocationIcon(this);
        ivStorageIcon.setImageResource(memoryLocationIcon);
        tvStorageIcon.setText(memoryLocationText);
        ivStorageIcon.setOnClickListener(view -> {
            myLogI("user clicks - storage icon");
            openFolderInFileExplorer(folder.getUri());
        });

        String percentDone = folder.getPercentdone()>0 ? "  .  " + Tonio.FormatPercentString(folder.getPercentdone()) + " " + getString(R.string.listened) : "";
        String info = "";
        info = info + getString(R.string.Added) + " : " + Tonio.formatLastAccessAsDate(folder.date_added);
        info = info + "\n" + getString(R.string.LastAccess) + " : " + Tonio.formatLastAccessInDays(folder.lLastAccess) + " (" + Tonio.formatLastAccess(folder.lLastAccess,this) + ")";
        info = info + "\n" + Tonio.formatTime(folder.getDuration()) + "  .  " + folder.nbZikFile + " " + getString(R.string.audio_tracks) + percentDone;
        tvInfo.setText(info);

        restoreDeletionIfActive();

        bDelete.setOnClickListener(view -> bDeleteClick());

        bReset.setOnClickListener(view -> bResetClick());

        bExport.setOnClickListener(view -> bExportClick());

        etIntroCut = findViewById(R.id.etIntroCut);
        etIntroCut.setText(String.valueOf(Pref.getIntroCutFromPref(this, folder.getId())));

        ivCoverPreview = findViewById(R.id.ivCoverPreview);

        if (folder.image != null && !folder.image.isEmpty()) {
            ivCoverPreview.setImageURI(Uri.parse(folder.image));
        } else {
            ivCoverPreview.setImageResource(R.drawable.no_image_icon);
        }

        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                checkBeforeLeave();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (isDeleting || (blockingOverlay != null && blockingOverlay.getVisibility() == View.VISIBLE)) {
                    // Don’t cancel the Work; just finish this Activity
                    setResult(RESULT_OK, new Intent().putExtra("deleteInProgressFolderId", folder.getId()));
                    finish();
                } else {
                    // your existing confirmation logic
                    checkBeforeLeave();
                }
            }
        });


        bChangeCover.setOnClickListener(view -> clickChangeCover());
        bDeleteCover.setOnClickListener(view -> clickDeleteCover());
        bGenerateCover.setOnClickListener(view -> clickGenerateCover());

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN); // Avoid keyboard on opening

    }

    private void bDeleteClick() {
        WorkManager wm = WorkManager.getInstance(getApplicationContext());
        wm.getWorkInfosByTag(deleteTag(folder.getId()))
                .addListener(() -> {}, Runnable::run); // no-op; example if you wanted async

        // simpler: use getWorkInfosByTag (blocking) from a background thread, or just rely on UI lock:
        // If overlay is visible, do nothing:
        if (blockingOverlay.getVisibility() == View.VISIBLE) {
            // already deleting → ignore tap
            return;
        }

        new AlertDialog.Builder(ModifyFolderActivity.this)
                .setTitle(getString(R.string.AskDelete_popupTitle))
                .setMessage(getString(R.string.ModifyFolder_AskDelete))
                .setCancelable(false)
                .setPositiveButton("ok", (dialog, which) -> startDeleteWorker())
                .setNegativeButton("cancel", (dialogInterface, i) -> {})
                .show();
    }


    private static String deleteTag(long folderId) {
        return "delete_folder_" + folderId;
    }
    private static String deleteUniqueName(long folderId) {
        return "delete_folder_unique_" + folderId;
    }

    private void startDeleteWorker() {
        setUiDeleting(true);

        Data input = new Data.Builder()
                .putLong(DeleteFolderWorker.KEY_FOLDER_ID, folder.getId())
                .putString(DeleteFolderWorker.KEY_FOLDER_NAME, folder.getName())
                .build();

        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(DeleteFolderWorker.class)
                .addTag(deleteTag(folder.getId()))
                // optional: progress backoff/retry policy here
                .setInputData(input)
                .build();

        WorkManager wm = WorkManager.getInstance(getApplicationContext());

        // This prevents a second Delete from being enqueued for the same folder
        wm.enqueueUniqueWork(
                deleteUniqueName(folder.getId()),
                ExistingWorkPolicy.KEEP,
                req
        );

        // Observe by TAG so we can reattach later, even if we lose the request id
        attachDeletionObserverByTag(deleteTag(folder.getId()));
    }


    private void setUiDeleting(boolean deleting) {
        if (deleting) {
            // block taps visually
            if (blockingOverlay != null) blockingOverlay.setVisibility(View.VISIBLE);
        } else {
            if (blockingOverlay != null) blockingOverlay.setVisibility(View.GONE);
        }

        // Disable all action buttons to prevent multiple clicks
        if (bDelete != null) bDelete.setEnabled(!deleting);
        if (bReset != null)  bReset.setEnabled(!deleting);
        if (bExport != null) bExport.setEnabled(!deleting);
        if (etRename != null) etRename.setEnabled(!deleting);
        if (bChangeCover != null) bChangeCover.setEnabled(!deleting);
        if (bDeleteCover != null) bDeleteCover.setEnabled(!deleting);
        if (bGenerateCover != null) bGenerateCover.setEnabled(!deleting);
    }

    private void renameBook(String newName) {
        if (newName.length() < 2) {
            myToast(getString(R.string.Error_FolderNameTooShort));
        } else {
            new Thread(() -> {
                AppDatabase.getDatabase(this).FolderDao().changeName(folder.getId(), newName);
                AppDatabase.getDatabase(this).FolderDao().updateFolderNameInZikFile(folder.getId(), newName);
                runOnUiThread(() -> {
                    myToast(getString(R.string.Folder_Renamed));
                    myLogInFile(getString(R.string.Folder_Renamed) + " : [" + folder.getName() + "] - > [" + newName + "]");
                    finish();
                });
            }).start();
        }
    }

    private void bResetClick() {
        myLogI("user clicks - reset");
        new AlertDialog.Builder(ModifyFolderActivity.this)
                .setTitle(getString((R.string.AskReset_popupTitle)))
                .setMessage(getString((R.string.ModifyFolder_AskReset)))
                .setCancelable(true)
                .setPositiveButton("ok", (dialog, i) -> resetFolder())
                .setNegativeButton("cancel", (dialog, i) -> {})
                .show();
    }

    private void bExportClick() {
        myLogI("user clicks - export");
        Intent intent = new Intent(this, ExportActivity.class);
        intent.putExtra(ExportActivity.EXTRA_FOLDER_ID, folder.getId());
        this.startActivity(intent);
    }


    private void resetFolder() {
        myLog("resetFolder()");
        new Thread(() -> {
            AppDatabase.getDatabase(this).FolderDao().resetProgression(folder.getId());
            AppDatabase.getDatabase(this).ZikFileDao().resetFolderProgression(folder.getId());
            runOnUiThread(() -> {
                myLogInFile(getString(R.string.Folder_Reset) + " : " + folder.getName());
                myToast(getString(R.string.Folder_Reset));
                finish();
            });
        }).start();
    }

    @Override
    protected void onDestroy() {
        int introCut = 0;
        try {
            introCut = Integer.parseInt(etIntroCut.getText().toString());
        } catch (Exception e) {
            myLogE("Bad introCut value");
        }
        Pref.saveIntroCutToPref(this, folder.getId(), introCut);
        super.onDestroy();
    }

    public void checkBeforeLeave() {
        String newName = etRename.getText().toString().trim();
        if (!newName.equals(folder.getName().trim())) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.AskRename_popupTitle)
                    .setMessage(getString(R.string.AskRename_Book) + "\n[ " + newName + " ]")
                    .setPositiveButton(R.string.Yes, (dialog, which) -> renameBook(newName))
                    .setNegativeButton(R.string.No, (dialog, which) -> finish())
                    .show();
        } else {
            finish(); // No changes, just leave
        }
    }

    private void clickChangeCover() {
        myLogI("user clicks - CHANGE cover IMAGE");
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        selectImageLauncher.launch(Intent.createChooser(intent, "Select Cover Image"));
    }
    private final ActivityResultLauncher<Intent> selectImageLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri selectedImageUri = result.getData().getData();
                    if (selectedImageUri != null) {
                        new Thread(() -> {
                            try {
                                String fileName = "UserPic_" + ImageHelper.IMAGE_PREFIX_FOR_SAVED_BOOK + folder.getId() + ".jpg";
                                String newImagePath = ImageHelper.copyContentUriToImageFile(this, selectedImageUri.toString(), fileName, false);
                                if (newImagePath == null) throw new RuntimeException("Image copy/compression failed");

                                // Delete previous image if different
                                if (folder.image != null && !folder.image.equals(newImagePath)) {
                                    ImageHelper.deleteImage(this, folder);
                                }

                                folder.image = newImagePath;
                                AppDatabase.getDatabase(this).FolderDao().updateImage(folder.getId(), folder.image);

                                runOnUiThread(() -> ivCoverPreview.setImageURI(Uri.fromFile(new File(newImagePath))));

                            } catch (Exception e) {
                                myLogEE(e, "Error processing selected image");
                                runOnUiThread(() -> myToastE("Failed to change image"));
                            }
                        }).start();
                    }
                }
            });
    private void clickDeleteCover() {
        myLogI("user clicks - DELETE cover IMAGE");
        new AlertDialog.Builder(ModifyFolderActivity.this)
                .setTitle(getString(R.string.AskDelete_popupTitle))
                .setMessage(getString(R.string.DeleteCoverImage_AskDelete))
                .setCancelable(false)
                .setPositiveButton("ok", (dialog, which) -> deleteCover())
                .setNegativeButton("cancel", (dialogInterface, i) -> {})
                .show();
    }
    private void deleteCover() {
        new Thread(() -> {
            try {
                FileHelper.deleteFile(this, folder.image);
                folder.image = null;
                AppDatabase.getDatabase(this).FolderDao().updateImage(folder.getId(), folder.image);
                runOnUiThread(() -> ivCoverPreview.setImageResource(R.drawable.no_image_icon));
            } catch (Exception e) {
                myLogEE(e, "delete cover");
            }
        }).start();
    }
    private final ActivityResultLauncher<Intent> coverGenLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                myLogW("result from CoverGenerationActivity " + result.getResultCode() + " " + result.getData());
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                String savedPath = result.getData().getStringExtra(CoverGenerationActivity.RESULT_SAVED_PATH);
                myLogW("savedPath = " + savedPath);
                if (savedPath == null || savedPath.isEmpty()) return;

                new Thread(() -> {
                    try {
                        AppDatabase.getDatabase(this).FolderDao().updateImage(folder.getId(), savedPath);
                        folder.image = savedPath;
                        runOnUiThread(() -> ivCoverPreview.setImageURI(Uri.parse(savedPath)));
                    } catch (Exception e) {
                        myLogEE(e, "save generated cover (CoverGenerationActivity result)");
                    }
                }).start();
            });

    private void clickGenerateCover() {
        myLogI("user clicks - GENERATE cover IMAGE");

        final long fId = folder.getId();
        final String title = folder.getName();
        final int sizePx = Var.FALL_BACK_COVER_IMAGE_SIZE_IN_PIXELS;

        // Load saved prefs (if any), else fall back to defaults
        String savedInitials = Pref.getBookCoverInitials(this, fId);
        Integer savedColor   = Pref.getBookCoverColorOrNull(this, fId);
        Boolean savedRounded = Pref.getBookCoverRoundedOrNull(this, fId);

        final boolean rounded = (savedRounded != null) ? savedRounded : true;
        final int defaultColor = (savedColor != null) ? savedColor : ImageHelper.getColorFromTitle(title);

        Intent i = new Intent(this, CoverGenerationActivity.class);
        i.putExtra(CoverGenerationActivity.EXTRA_FOLDER_ID, fId);
        i.putExtra(CoverGenerationActivity.EXTRA_TITLE, title);
        i.putExtra(CoverGenerationActivity.EXTRA_DEFAULT_COLOR, defaultColor);
        i.putExtra(CoverGenerationActivity.EXTRA_SIZE_PX, sizePx);
        i.putExtra(CoverGenerationActivity.EXTRA_ROUNDED, rounded);
        if (savedInitials != null) {
            i.putExtra(CoverGenerationActivity.EXTRA_INITIALS, savedInitials);
        }

        coverGenLauncher.launch(i);
    }


    private void openFolderInFileExplorer(String pathOrUri) {
        myLog(pathOrUri);
        /*
        Intent intent = new Intent(Intent.ACTION_VIEW);

        Uri uri;
        if (pathOrUri.startsWith("content://")) {
            uri = Uri.parse(pathOrUri);
        } else {
            File file = new File(pathOrUri).getParentFile();
            if (!file.exists()) {
                myToastE("Folder does not exist");
                return;
            }
            uri = FileProvider.getUriForFile(
                    this,
                    getApplicationContext().getPackageName() + ".FileProvider",
                    file
            );
        }
*/
        //intent.setDataAndType(uri, "*/*");
        /*
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            myToastE("No file explorer found to open this folder");
        }
        */

    }
    private void restoreDeletionIfActive() {
        attachDeletionObserverByTag(deleteTag(folder.getId()));
    }

    private void attachDeletionObserverByTag(String tag) {
        WorkManager wm = WorkManager.getInstance(getApplicationContext());
        wm.getWorkInfosByTagLiveData(tag).observe(this, infos -> {
            if (infos == null || infos.isEmpty()) {
                isDeleting = false;
                setUiDeleting(false);
                return;
            }

            // If any already SUCCEEDED → finish immediately (covers the “came back later” case)
            for (WorkInfo wi : infos) {
                if (wi.getState() == WorkInfo.State.SUCCEEDED) {
                    isDeleting = false;
                    setUiDeleting(false);
                    setResult(RESULT_OK, new Intent().putExtra("deletedFolderId", folder.getId()));
                    finish();
                    return;
                }
            }

            // Otherwise, look for an active one
            WorkInfo active = null;
            for (WorkInfo wi : infos) {
                if (!wi.getState().isFinished()) { active = wi; break; }
            }

            if (active == null) {
                isDeleting = false;
                setUiDeleting(false);
                return;
            }

            switch (active.getState()) {
                case ENQUEUED:
                case RUNNING:
                    isDeleting = true;
                    setUiDeleting(true);
                    // (optional) update progress text from active.getProgress()
                    break;

                case SUCCEEDED:
                    isDeleting = false;
                    setUiDeleting(false);
                    myToast(getString(R.string.Folder_Deleted_DB));
                    myLog(getString(R.string.Folder_Deleted_DB) + " : " + folder.getName());
                    setResult(RESULT_OK, new Intent().putExtra("deletedFolderId", folder.getId()));
                    finish();
                    break;

                case FAILED:
                case CANCELLED:
                    isDeleting = false;
                    setUiDeleting(false);
                    String err = active.getOutputData().getString("error");
                    myToastE(err != null ? err :
                            (active.getState() == WorkInfo.State.CANCELLED ? "Delete cancelled" : "Delete failed"));
                    myLogEE(null, "Worker Delete folder : " + err);
                    break;
            }
        });
    }

}
