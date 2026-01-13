package com.driot.bookplayer.activities;

import static com.driot.bookplayer.utils.PermissionRequest.isReadAudioPermissionGranted;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.FileHelper;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.UriHelper;
import com.driot.bookplayer.player.ErrorUi;
import com.driot.bookplayer.player.PlaybackUiBus;
import com.driot.bookplayer.services.DeleteFolderWorker;
import com.driot.bookplayer.utils.MsgBox;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingActivity;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.util.List;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 15/11/20
 */
public class ModifyFolderActivity extends LoggingActivity {

    private Folder folder;
    private View blockingOverlay;
    private Button bDelete, bReset, bExport;
    private Button bChangeCover, bDeleteCover, bGenerateCover, bWebSearch;
    private LinearLayout ll_zikfile_resolve_error;

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
        bWebSearch = findViewById(R.id.bWebSearch);
        ll_zikfile_resolve_error = findViewById(R.id.ll_zikfile_resolve_error);

        ll_zikfile_resolve_error.setVisibility(View.GONE);

        TextView tvTitle = findViewById(R.id.title);
        TextView tvInfo = findViewById(R.id.tvInfo);
        ImageView ivStorageIcon = findViewById(R.id.imageViewStorageIcon);
        TextView tvStorageIcon = findViewById(R.id.textViewStorageIcon);

        folder = getIntent().getParcelableExtra(Intents.EXTRA_FOLDER);
        if (folder == null) {
            myLogEE(null, "could_not_identify_folder_to_modify");
            myToastE(getString(R.string.could_not_identify_folder_to_modify));
            finish();
            return;
        }

        tvTitle.setText(folder.getName());

        etRename = findViewById(R.id.etRename);
        etRename.setText(folder.getName());

        findViewById(R.id.bAddNewTracks).setOnClickListener(view -> {
            Intent i = new Intent(this, GetOtherActivity.class);
            i.putExtra(Intents.EXTRA_ADD_TO_FOLDER, folder);
            startActivity(i);
        });

        findViewById(R.id.bResetTracksOrder).setOnClickListener(view -> { clickResetTracksOrder(); });

        findViewById(R.id.bChangeTracksOrder).setOnClickListener(view -> {
            startActivity(new Intent(this, ZikFileActivity.class)
                    .putExtra(Intents.EXTRA_FOLDER, folder)
                    .putExtra(Intents.EXTRA_ACTIVATE_CHANGE_TRACK_ORDER, true)
            );
            String warning = null;
            if ( PlaybackUiBus.get().state().getValue() != null) {
                warning = getString(R.string.Quit_the_player_to_move_playing_tracks);
            }
            MsgBox.info(this, getString(R.string.ChangeTrackOrder_Title), getString(R.string.ChangeTrackOrder_Text), warning);
        });

        String memoryLocationText = getString(R.string.AudioLocation) + " : " + folder.getMemoryLocationText(this);
        int memoryLocationIcon = folder.getMemoryLocationIcon(this);
        ivStorageIcon.setImageResource(memoryLocationIcon);
        tvStorageIcon.setText(memoryLocationText);
        ivStorageIcon.setOnClickListener(view -> {
            myLogI("user clicks - storage icon");
            openFolderInFileExplorer(folder.getUri());
        });

        checkZikFilesReadable();

        String percentDone = folder.getPercentdone()>0 ? "  .  " + Tonio.formatPercentString(folder.getPercentdone()) + " " + getString(R.string.listened) : "";
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
        ivCoverPreview.setImageResource(R.drawable.no_image_icon);

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
        bWebSearch.setOnClickListener(view -> clickWebSearch());

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN); // Avoid keyboard on opening

        AppDatabase.getDatabase(this).folderDao()
                .observeById(folder.getId())
                .observe(this, fresh -> {
                    if (fresh == null) return;
                    if (fresh.image != null) {
                        myLogD("observe folderDAO => display new fresh image : " + fresh.image);
                        folder.image = fresh.image;
                        String img = fresh.image;

                        if (img == null || img.isEmpty()) {
                            Glide.with(this)
                                    .load(R.drawable.no_image_icon)
                                    .into(ivCoverPreview);
                            return;
                        }

                        Glide.with(this)
                                .load(img) // can be content://, file://, or plain path string
                                .error(R.drawable.no_image_icon)
                                .placeholder(R.drawable.no_image_icon)
                                .into(ivCoverPreview);
                        //ivCoverPreview.setImageURI(Uri.parse(fresh.image));
                    }
                });
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
                AppDatabase.getDatabase(this).folderDao().changeName(folder.getId(), newName);
                AppDatabase.getDatabase(this).folderDao().updateFolderNameInZikFile(folder.getId(), newName);
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
        intent.putExtra(Intents.EXTRA_FOLDER, folder);
        this.startActivity(intent);
    }


    private void resetFolder() {
        myLog("resetFolder()");
        new Thread(() -> {
            AppDatabase.getDatabase(this).folderDao().resetProgression(folder.getId());
            AppDatabase.getDatabase(this).zikFileDao().resetFolderProgression(folder.getId());
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
            Pref.saveIntroCutToPref(this, folder.getId(), introCut);
        } catch (Exception e) {
            myLogE("Bad introCut value");
        }
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

    private final ActivityResultLauncher<Intent> activityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> { checkResults(result); });


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
                                String newImagePath = ImageHelper.saveUserSelectedImageToBookCoverVersioned(this, folder.getId(), selectedImageUri.toString());
                                if (newImagePath == null) throw new RuntimeException("Image copy/compression failed");
                                folder.image = newImagePath;
                                AppDatabase.getDatabase(this).folderDao().updateImage(folder.getId(), folder.image);
                                runOnUiThread(() -> {
                                    myLog("reset ivCoverPreview after activity result : " + newImagePath);
                                    ivCoverPreview.setImageURI(Uri.fromFile(new File(newImagePath)));
                                });
                            } catch (Exception e) {
                                myLogEE(e, "Error processing selected image");
                                runOnUiThread(() -> myToastE(getString(R.string.failed_to_change_image)));
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
                AppDatabase.getDatabase(this).folderDao().updateImage(folder.getId(), folder.image);
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
                        AppDatabase.getDatabase(this).folderDao().updateImage(folder.getId(), savedPath);
                        folder.image = savedPath;
                        runOnUiThread(() -> {
                            myLog("reset ivCoverPreview after activity result coverGenLauncher : " + savedPath);
                            ivCoverPreview.setImageURI(Uri.parse(savedPath));
                        });
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

    private void clickWebSearch() {
        myLogI("--- user click WEB SEARCH COVER ---");
        Intent i = new Intent(this, CoverWebSearchActivity.class);
        i.putExtra(CoverWebSearchActivity.EXTRA_FOLDER_ID, (long) folder.getId());
        i.putExtra(CoverWebSearchActivity.EXTRA_DEFAULT_TITLE, folder.getName());
        this.startActivity(i);
        }

    private void openFolderInFileExplorer(String pathOrUri) {
        myLog("openFolderInFileExplorer: " + pathOrUri);

        Uri dirTreeOrDocUri = null;

        if (pathOrUri.startsWith("content://")) {
            // SAF URI (tree or document)
            Uri input = Uri.parse(pathOrUri);
            try {
                // If it's a tree URI, convert to a document URI pointing at the same folder.
                String docId = DocumentsContract.getTreeDocumentId(input);
                dirTreeOrDocUri = DocumentsContract.buildDocumentUriUsingTree(input, docId);
            } catch (Exception ignore) {
                // Not a tree? Could already be a document uri; use as-is
                dirTreeOrDocUri = input;
            }
        } else {
            // It's a file system path. FileProvider can't "open a folder".
            // Best UX: if you previously stored a treeUri for this folder, use that here.
            // Otherwise, just fall back to the picker at that location (see fallback below).
            File file = new File(pathOrUri);
            File folder = file.isDirectory() ? file : file.getParentFile();
            if (folder == null || !folder.exists()) {
                myToastE("Folder does not exist");
                return;
            }
            // We cannot build a valid SAF uri from a raw path here without prior SAF access.
            // We'll use the picker with EXTRA_INITIAL_URI as a fallback below.
        }

        // 1) Try the system Files app (DocumentsUI) with directory MIME type.
        Intent viewDir = new Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_ACTIVITY_NEW_TASK);

        if (dirTreeOrDocUri != null) {
            viewDir.setDataAndType(dirTreeOrDocUri, DocumentsContract.Document.MIME_TYPE_DIR);
            // Prefer the standard Files app when present (AOSP/Pixel); safe to try and ignore if missing
            viewDir.setPackage("com.android.documentsui");
            try {
                myLog("Opening with DocumentsUI: " + dirTreeOrDocUri);
                startActivity(viewDir);
                return;
            } catch (ActivityNotFoundException e) {
                // Some OEMs don’t ship com.android.documentsui, we’ll try a generic VIEW next.
                viewDir.setPackage(null);
                try {
                    startActivity(viewDir);
                    return;
                } catch (ActivityNotFoundException ignored) {
                    // continue to fallback
                }
            }
        }

        // 2) Fallback: open the system folder picker AT that location (user sees the directory).
        // Works reliably across Android versions/vendors.
        Intent openTree = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                        | Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            if (dirTreeOrDocUri != null) {
                openTree.putExtra(DocumentsContract.EXTRA_INITIAL_URI, dirTreeOrDocUri);
                myLog("Opening picker at EXTRA_INITIAL_URI: " + dirTreeOrDocUri);
            } else {
                // If we only had a raw path, try to hint the picker with the last used tree
                // (optional: store/retrieve a matching treeUri in your DB when the user selects a folder).
            }
            startActivity(openTree);
        } catch (ActivityNotFoundException e) {
            myToastE("No file explorer found to show this folder");
        }
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

    private void clickResetTracksOrder() {
        myLogI("user clicks - RESET tracks ORDER");
        new AlertDialog.Builder(ModifyFolderActivity.this)
                .setTitle(getString(R.string.AskReset_popupTitle))
                .setMessage(getString(R.string.AskReset_popupText_order))
                .setCancelable(false)
                .setPositiveButton("ok", (dialog, which) -> resetTrackOrder())
                .setNegativeButton("cancel", (dialogInterface, i) -> {})
                .show();
    }
    private void resetTrackOrder() {
        AppDatabase db = AppDatabase.getInstance(getApplicationContext());
        AppDatabase.databaseWriteExecutor.execute(() -> {
            db.zikFileDao().resetSmartChapterOrderForFolder(folder.getId());
            runOnUiThread(() -> myToast(getString(R.string.tracks_order_has_been_reset)));
        });
    }

    private void checkResults(ActivityResult result) {
        if (result.getResultCode() == RESULT_OK) {
            if (UriHelper.isReturnedUriOk(result.getData())) {
                final Uri pickedFolderUri = result.getData().getData();
                myLog("pickeddata : " + pickedFolderUri.getPath());
                myLog("pickeddata : " + pickedFolderUri.toString());

//check if better now :
                AppDatabase.databaseReadExecutor.execute(() -> {
                    Context context = getApplicationContext();
                    String oldPath = "", newPath;
                    Uri oldUri, newUri;
                    int i = 0;
                    int nbBetter = 0;
                    int nbWorse = 0;
                    List<ZikFile> list = AppDatabase.getDatabase(context).zikFileDao().getZikFiles(folder.getId());
                    final int nbZikFiles = list.size();
                    myLogW("nb zik files : " + nbZikFiles);
                    for (ZikFile zikFile : list) {
                        i = i + 1;
                        oldPath = zikFile.getPath();
                        newPath = new File(pickedFolderUri.getPath(), zikFile.getName()).getPath();
                        oldUri = UriHelper.resolveUriFromPath(context, oldPath);
                        newUri = UriHelper.resolveUriFromPath(context, newPath);
                        myLog(i + " oldPath : [" + oldPath + "], oldUri : [" + oldUri + "]\n"
                                + i + " newPath : [" + newPath + "], newUri : [" + newUri + "]");
                        if (oldUri == null && newUri != null) {
                            nbBetter = nbBetter + 1;
                            myLog(i + " BETTER");
                        } else if (oldUri != null && newUri == null) {
                            nbWorse = nbWorse + 1;
                            myLog(i + " WORSE");
                        }
                    }
                    myLogW("nbBetter : " + nbBetter + ", worse : " + nbWorse);
                    if (nbBetter <= 0) {
                        myToastE(getString(R.string.new_location_not_better));
                    } else {
                        String textMsg = getString(R.string.AskChangeSource_popupText)
                                + "\n " + getString(R.string.from) + " [" + Tonio.getParentFolderOrEmpty(oldPath) + "]"
                                + "\n " + getString(R.string.to) + " [" + pickedFolderUri + "]";
                        new AlertDialog.Builder(ModifyFolderActivity.this)
                                .setTitle(getString((R.string.AskChangeSource_popupTitle)))
                                .setMessage(textMsg)
                                .setCancelable(true)
                                .setPositiveButton(getString(R.string.proceed), (dialog, ii) -> {
                                    myLogW("changing folder path...");
                                    folder.setPath(pickedFolderUri.getPath());
                                    AppDatabase.getDatabase(context).folderDao().update(folder);
                                    //RECHECK ET RELOAD ERROR STATUS
                                    checkZikFilesReadable();
                                })
                                .setNegativeButton(getString(R.string.cancel), (dialog, ii) -> {})
                                .show();
                    }
                });
                //RECHECK ET RELOAD ERROR STATUS
                //checkZikFilesReadable();
            } else {
                myToastE("returned Uri not OK");
            }
        } else {
            myLogE("result code not OK");
        }
    }

    private void pickNewLocation() {
        myLogI("------------ USER CLICKS : pick new location");
        if (isReadAudioPermissionGranted(this) || Option.getCopyFile()) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            try {
                activityResultLauncher.launch(intent);
            } catch (Exception e) {
                myToastEE(e, "could not open android folder explorer");
            }
        } else {
            myToast("permission read storage not granted");
            //askForPermission(); // check code in getOthers
        }
    }

    private void checkZikFilesReadable() {
    // CHECK zikFiles are readable
        AppDatabase.databaseReadExecutor.execute(() -> {
            Context context = getApplicationContext();
            String path;
            String masterMsg = "";
            Uri src;
            int i = 0;
            int nbKO = 0;
            List<ZikFile> list = AppDatabase.getDatabase(context).zikFileDao().getZikFiles(folder.getId());
            final int nbZikFiles = list.size();
            for (ZikFile zikFile : list) {
                i = i + 1;
                path = zikFile.getPath();
                String pathType = (path.startsWith("content://") ? "[CONTENT] " : "");
                String logStrPrefix = getString(R.string.track) + " " + i + "/" + nbZikFiles + " : [" + zikFile.getDisplayName() + "]";
                src = UriHelper.resolveUriFromPath(context, path);
                if (src == null) {
                    nbKO = nbKO + 1;
                    String errMessage = ErrorUi.getErrorMessage(context, path);
                    String fullError = logStrPrefix
                            + "\n" + errMessage
                            + "\npath = [" + pathType + path + "]";
                    masterMsg = masterMsg + "\n\n" + fullError;
                }
            }
            final String finalMasterMsg = masterMsg;
            final int finalNbKO = nbKO;
            runOnUiThread(() -> {
                if (finalNbKO > 0) {
                    TextView tv_zikfile_resolve_error = findViewById(R.id.tv_zikfile_resolve_error);
                    tv_zikfile_resolve_error.setText(finalMasterMsg);
                    TextView tv_error_title = findViewById(R.id.tv_error_title);
                    String errDetail = finalNbKO + "/" + nbZikFiles + " " + getString(R.string.zikFiles_not_readable);
                    if (finalNbKO==nbZikFiles) errDetail = getString(R.string.All_zikFiles_not_readable);
                    tv_error_title.setText(errDetail);
                    MaterialButton mbPickNewLocation = findViewById(R.id.mbPickNewLocation);
                    mbPickNewLocation.setOnClickListener((v -> pickNewLocation()));
                    ll_zikfile_resolve_error.setVisibility(View.VISIBLE);
                } else {
                    ll_zikfile_resolve_error.setVisibility(View.GONE);
                }
            });
        });
    }

}
