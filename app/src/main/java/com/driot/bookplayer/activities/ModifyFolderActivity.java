package com.driot.bookplayer.activities;

import static com.driot.bookplayer.utils.PermissionRequest.isReadAudioPermissionGranted;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
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
import androidx.annotation.Nullable;
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
import com.driot.bookplayer.podcasts.PodcastHelper;
import com.driot.bookplayer.quickshare.NearbyShareActivity;
import com.driot.bookplayer.services.DeleteFolderWorker;
import com.driot.bookplayer.utils.MsgBox;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.BaseActivity;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.util.List;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 15/11/20
 */
public class ModifyFolderActivity extends BaseActivity {

    private static final int REQ_DELETE_FOLDER = 2001;
    private static final int REQ_RESET_FOLDER = 2002;
    private static final int REQ_RENAME_FOLDER = 2003;
    private static final int REQ_DELETE_COVER = 2004;
    private static final int REQ_RESET_TRACKS_ORDER = 2005;
    private static final int REQ_CHANGE_SOURCE = 2006;

    private String pendingNewName;
    private Uri pendingPickedTreeUri;
    private String pendingTreeDocumentId;

    private Folder folder;
    private View blockingOverlay;
    private TextView tvBlockingText;
    private Button bDelete, bReset, bExport, bShare;
    private Button bChangeCover, bDeleteCover, bGenerateCover, bWebSearch, bResetToOriginal;
    private LinearLayout ll_zikfile_resolve_error;

    EditText etIntroCut;
    EditText etEndCut;
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
        bShare = findViewById(R.id.bShare);
        blockingOverlay = findViewById(R.id.blockingOverlay);
        tvBlockingText = findViewById(R.id.tvBlockingText);
        bDeleteCover = findViewById(R.id.bDeleteCover);
        bGenerateCover = findViewById(R.id.bGenerateCover);
        bChangeCover = findViewById(R.id.bChangeCover);
        bWebSearch = findViewById(R.id.bWebSearch);
        bResetToOriginal = findViewById(R.id.bResetToOriginal);
        ll_zikfile_resolve_error = findViewById(R.id.ll_zikfile_resolve_error);

        ll_zikfile_resolve_error.setVisibility(View.GONE);

        TextView tvTitle = findViewById(R.id.title);
        TextView tvInfo = findViewById(R.id.tvModifyFolderInfo);
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

        findViewById(R.id.bResetTracksOrder).setOnClickListener(view -> {
            clickResetTracksOrder();
        });

        findViewById(R.id.bChangeTracksOrder).setOnClickListener(view -> {
            startActivity(new Intent(this, ZikFileActivity.class)
                    .putExtra(Intents.EXTRA_FOLDER, folder)
                    .putExtra(Intents.EXTRA_ACTIVATE_CHANGE_TRACK_ORDER, true));
            String warning = null;
            if (Pref.getShowMsgBox_ChangeTrackOrder() > 0) {
                if (PlaybackUiBus.get().state().getValue() != null) {
                    warning = getString(R.string.Quit_the_player_to_move_playing_tracks);
                }
                MsgBox.info(this, getString(R.string.ChangeTrackOrder_Title), getString(R.string.ChangeTrackOrder_Text),
                        warning);
            }
        });

        String memoryLocationText = getString(R.string.Audio_location) + " :\n" + folder.getMemoryLocationText(this);
        int memoryLocationIcon = folder.getMemoryLocationIcon(this);
        ivStorageIcon.setImageResource(memoryLocationIcon);
        tvStorageIcon.setText(memoryLocationText);
        ivStorageIcon.setOnClickListener(view -> {
            myLogI("user clicks - storage icon");
            openFolderInFileExplorer(folder.getUri());
        });

        checkZikFilesReadable();

        String info = "";
        info = info + Tonio.formatTime(folder.getDuration()) + "  .  " + folder.nbZikFile + " "
                + getString(R.string.audio_tracks);
        info = info + "\n" + getString(R.string.Added) + " : " + Tonio.formatLastAccessAsDate(folder.date_added);
        info = info + "\n";
        info = info + "\n" + getString(R.string.Last_access) + " : " + Tonio.formatLastAccessInDays(folder.lLastAccess)
                + " (" + Tonio.formatLastAccess(folder.lLastAccess, this) + ")";
        info = info + "\n" + Tonio.formatPercentString(folder.getPercentdone()) + " " + getString(R.string.completed);
        if (folder.timeListened > 0) {
            info = info + "\n" + getString(R.string.listened) + " : " + Tonio.formatTime(folder.timeListened * 1000);
        }

        tvInfo.setText(info);

        restoreDeletionIfActive();

        bDelete.setOnClickListener(view -> bDeleteClick());

        bReset.setOnClickListener(view -> bResetClick());

        bExport.setOnClickListener(view -> bExportClick());

        bShare.setOnClickListener(view -> bShareClick());

        etIntroCut = findViewById(R.id.etIntroCut);
        etIntroCut.setText(String.valueOf(folder.cutIntro));

        etEndCut = findViewById(R.id.etEndCut);
        etEndCut.setText(String.valueOf(folder.cutEnd));

        ivCoverPreview = findViewById(R.id.ivCoverPreview);
        ivCoverPreview.setImageResource(R.drawable.no_image_icon);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
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
        if (Tonio.isPure(this)) {
            bWebSearch.setVisibility(View.GONE);
        } else {
            bWebSearch.setOnClickListener(view -> clickWebSearch());
        }
        bResetToOriginal.setOnClickListener(view -> clickResetToOriginal());

        // Hide Reset to Original for old books (imported before feature launch ~Jan 29
        // 2026)
        if (folder.date_added < 1769644800000L) {
            bResetToOriginal.setVisibility(View.GONE);
        }

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN); // Avoid keyboard on opening

        AppDatabase.getDatabase(this).folderDao()
                .observeById(folder.getId())
                .observe(this, fresh -> {
                    if (fresh == null)
                        return;
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
                        // ivCoverPreview.setImageURI(Uri.parse(fresh.image));
                    }
                });

        etRename.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateTitleBorder(s.toString().trim());
            }
        });
    }

    private void updateTitleBorder(String currentName) {
        TextView tvTitle = findViewById(R.id.title);
        if (tvTitle == null || tvTitle.getBackground() == null)
            return;

        if (!(tvTitle.getBackground() instanceof GradientDrawable)) {
            // It might not be a GradientDrawable if it's something else, but bg_chip is a
            // shape.
            // If it's a RippleDrawable (which many Material themes use), we might need to
            // get the content.
            return;
        }

        GradientDrawable bg = (GradientDrawable) tvTitle.getBackground().mutate();
        int strokeColor;
        if (!currentName.equals(folder.getName().trim())) {
            strokeColor = getResources().getColor(R.color.red_500, getTheme());
        } else {
            // Restore original outline color from theme
            TypedValue typedValue = new TypedValue();
            getTheme().resolveAttribute(com.google.android.material.R.attr.colorOutline, typedValue, true);
            strokeColor = typedValue.data;
        }
        bg.setStroke((int) Tonio.dpToPx(1, this), strokeColor);
    }

    private void bDeleteClick() {
        WorkManager wm = WorkManager.getInstance(getApplicationContext());
        wm.getWorkInfosByTag(deleteTag(folder.getId()))
                .addListener(() -> {
                }, Runnable::run); // no-op; example if you wanted async

        // simpler: use getWorkInfosByTag (blocking) from a background thread, or just
        // rely on UI lock:
        // If overlay is visible, do nothing:
        if (blockingOverlay.getVisibility() == View.VISIBLE) {
            // already deleting → ignore tap
            return;
        }

        MsgBox.ask(this,
                getString(R.string.AskDelete_popupTitle),
                getString(R.string.ModifyFolder_AskDelete),
                null,
                getString(android.R.string.ok),
                getString(android.R.string.cancel),
                REQ_DELETE_FOLDER);
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
                req);

        // Observe by TAG so we can reattach later, even if we lose the request id
        attachDeletionObserverByTag(deleteTag(folder.getId()));
    }

    private void setUiDeleting(boolean deleting) {
        if (deleting) {
            // block taps visually
            if (blockingOverlay != null)
                blockingOverlay.setVisibility(View.VISIBLE);
        } else {
            if (blockingOverlay != null)
                blockingOverlay.setVisibility(View.GONE);
        }

        // Disable all action buttons to prevent multiple clicks
        if (bDelete != null)
            bDelete.setEnabled(!deleting);
        if (bReset != null)
            bReset.setEnabled(!deleting);
        if (bExport != null)
            bExport.setEnabled(!deleting);
        if (bShare != null)
            bShare.setEnabled(!deleting);
        if (etRename != null)
            etRename.setEnabled(!deleting);
        if (bChangeCover != null)
            bChangeCover.setEnabled(!deleting);
        if (bDeleteCover != null)
            bDeleteCover.setEnabled(!deleting);
        if (bGenerateCover != null)
            bGenerateCover.setEnabled(!deleting);
    }

    private void renameBook(String newName) {
        if (newName.length() < 2) {
            myToast(getString(R.string.Error_FolderNameTooShort));
        } else {
            final long folderId = folder.getId();
            AppDatabase.databaseWriteExecutor.execute(() -> {
                AppDatabase.getDatabase(this).folderDao().changeName(folderId, newName);
                AppDatabase.getDatabase(this).folderDao().updateFolderNameInZikFile(folderId, newName);
                runOnUiThread(() -> {
                    folder.setName(newName);
                    setResult(RESULT_OK, new Intent().putExtra(Intents.EXTRA_FOLDER_ID, folderId));
                    myToast(getString(R.string.Folder_Renamed));
                    myLogInFile(
                            getString(R.string.Folder_Renamed) + " : [" + folder.getName() + "] - > [" + newName + "]");
                    finish();
                });
            });
        }
    }

    private void bResetClick() {
        myLogI("user clicks - reset");
        MsgBox.ask(this,
                getString(R.string.AskReset_popupTitle),
                getString(R.string.ModifyFolder_AskReset),
                null,
                getString(android.R.string.ok),
                getString(android.R.string.cancel),
                REQ_RESET_FOLDER);
    }

    private void bExportClick() {
        myLogI("user clicks - export");
        Intent intent = new Intent(this, ExportActivity.class);
        intent.putExtra(Intents.EXTRA_FOLDER, folder);
        this.startActivity(intent);
    }

    private void bShareClick() {
        myLogI("user clicks - QUICK SHARE");
        Intent intent = new Intent(this, NearbyShareActivity.class);
        intent.putExtra(Intents.EXTRA_FOLDER, folder);
        this.startActivity(intent);
    }

    private void clickResetToOriginal() {
        myLogI("user clicks - reset to original cover");

        // Run in background to avoid blocking UI
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                // Try to get the original cover file
                String originalPath = ImageHelper.getOriginalCoverPath(this, folder.getId());

                if (originalPath != null) {
                    // Original cover exists - simple pointer swap
                    folder.image = originalPath;
                    saveImageInDB(folder.getId(), originalPath);
                    myToast("Original cover restored");
                    myLog("Cover reset to original: " + originalPath);
                } else {
                    myToastE("Could not restore original cover");
                }
            } catch (Exception e) {
                myLogEE(e, "Error resetting cover to original");
                myToastE("Error resetting cover");
            }
        });
    }

    private void resetFolder() {
        myLog("resetFolder()");
        new Thread(() -> {
            AppDatabase.getDatabase(this).folderDao().resetProgression(folder.getId());
            AppDatabase.getDatabase(this).zikFileDao().resetFolderProgression(folder.getId());
            runOnUiThread(() -> {
                myLogInFile(getString(R.string.Folder_Reset) + " : " + folder.getName());
                myToast(getString(R.string.Folder_Reset));
                setResult(RESULT_OK, new Intent().putExtra(Intents.EXTRA_FOLDER_ID, folder.getId()));
                finish();
            });
        }).start();
    }

    @Override
    protected void onDestroy() {
        boolean folderChanged = false;
        try {
            int introCut = Integer.parseInt(etIntroCut.getText().toString());
            if (folder.cutIntro != introCut) {
                folder.cutIntro = introCut;
                folderChanged = true;
            }
        } catch (Exception e) {
            myLogE("Bad introCut value");
        }
        try {
            int endCut = Integer.parseInt(etEndCut.getText().toString());
            if (folder.cutEnd != endCut) {
                folder.cutEnd = endCut;
                folderChanged = true;
            }
        } catch (Exception e) {
            myLogE("Bad endCut value");
        }
        if (folderChanged) {
            AppDatabase.databaseWriteExecutor.execute(() -> {
                AppDatabase.getInstance(ModifyFolderActivity.this).folderDao().update(folder);
            });
        }
        super.onDestroy();
    }

    public void checkBeforeLeave() {
        String newName = etRename.getText().toString().trim();
        if (!newName.equals(folder.getName().trim())) {
            pendingNewName = newName;
            MsgBox.ask(this,
                    getString(R.string.AskRename_popupTitle),
                    getString(R.string.AskRename_Book) + "\n[ " + newName + " ]",
                    null,
                    getString(R.string.Yes),
                    getString(R.string.No),
                    REQ_RENAME_FOLDER);
        } else {
            finish(); // No changes, just leave
        }
    }

    private final ActivityResultLauncher<Intent> activityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                checkResults(result);
            });

    private void clickChangeCover() {
        myLogI("user clicks - CHANGE cover IMAGE");
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        selectImageLauncher.launch(Intent.createChooser(intent, "Select Cover Image"));
    }

    private final ActivityResultLauncher<Intent> selectImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri selectedImageUri = result.getData().getData();
                    if (selectedImageUri != null) {
                        new Thread(() -> {
                            try {
                                String newImagePath = ImageHelper.saveUserSelectedImageToBookCoverVersioned(this,
                                        folder.getId(), selectedImageUri.toString());
                                if (newImagePath == null) {
                                    myToastEE(null, "error while changing image");
                                } else {
                                    folder.image = newImagePath;
                                    saveImageInDB(folder.getId(), newImagePath);
                                    runOnUiThread(() -> {
                                        myLog("reset ivCoverPreview after activity result : " + newImagePath);
                                        ivCoverPreview.setImageURI(Uri.fromFile(new File(newImagePath)));
                                    });
                                }
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
        MsgBox.ask(this,
                getString(R.string.AskDelete_popupTitle),
                getString(R.string.DeleteCoverImage_AskDelete),
                null,
                getString(android.R.string.ok),
                getString(android.R.string.cancel),
                REQ_DELETE_COVER);
    }

    private void deleteCover() {
        new Thread(() -> {
            try {
                // Check if the current cover is the original one
                String originalPath = ImageHelper.getOriginalCoverPath(this, folder.getId());
                boolean isOriginal = folder.image != null && folder.image.equals(originalPath);

                // Only delete the file if it conflicts with original or is a custom/temp image
                // specified instructions: "should not actually delete the original cover"
                if (!isOriginal) {
                    FileHelper.deleteFile(this, folder.image);
                } else {
                    myLogI("Preserving original cover file on disk: " + folder.image);
                }

                folder.image = null;
                saveImageInDB(folder.getId(), folder.image);
                runOnUiThread(() -> ivCoverPreview.setImageResource(R.drawable.no_image_icon));
            } catch (Exception e) {
                myLogEE(e, "delete cover");
            }
        }).start();
    }

    private final ActivityResultLauncher<Intent> coverGenLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                myLogW("result from CoverGenerationActivity " + result.getResultCode() + " " + result.getData());
                if (result.getResultCode() != RESULT_OK || result.getData() == null)
                    return;
                String savedPath = result.getData().getStringExtra(CoverGenerationActivity.RESULT_SAVED_PATH);
                myLogW("savedPath = " + savedPath);
                if (savedPath == null || savedPath.isEmpty())
                    return;

                // get extra data
                String initials = result.getData().getStringExtra(CoverGenerationActivity.RESULT_INITIALS);
                int color = result.getData().getIntExtra(CoverGenerationActivity.RESULT_COLOR, 0);
                boolean rounded = result.getData().getBooleanExtra(CoverGenerationActivity.RESULT_ROUNDED, true);
                int textSize = result.getData().getIntExtra(CoverGenerationActivity.RESULT_TEXT_SIZE, 16);

                new Thread(() -> {
                    try {
                        // UPDATE JSON DATA
                        org.json.JSONObject coverObj = new org.json.JSONObject();
                        if (initials != null)
                            coverObj.put("initials", initials);
                        coverObj.put("color", color);
                        coverObj.put("rounded", rounded);
                        coverObj.put("textSize", textSize);

                        org.json.JSONObject rootObj = new org.json.JSONObject();
                        if (folder.jsonData != null && !folder.jsonData.isEmpty()) {
                            try {
                                rootObj = new org.json.JSONObject(folder.jsonData);
                            } catch (Exception ignored) {
                            }
                        }
                        rootObj.put("cover", coverObj);
                        folder.jsonData = rootObj.toString();

                        saveImageInDB(folder.getId(), savedPath);
                        folder.image = savedPath;
                        AppDatabase.getDatabase(this).folderDao().update(folder); // to save JSON data

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
        String savedInitials = null;
        Integer savedColor = null;
        Boolean savedRounded = null;

        if (folder.jsonData != null) {
            try {
                org.json.JSONObject root = new org.json.JSONObject(folder.jsonData);
                if (root.has("cover")) {
                    org.json.JSONObject cover = root.getJSONObject("cover");
                    if (cover.has("initials") && !cover.isNull("initials"))
                        savedInitials = cover.getString("initials");
                    if (cover.has("color"))
                        savedColor = cover.getInt("color");
                    if (cover.has("rounded"))
                        savedRounded = cover.getBoolean("rounded");
                }
            } catch (Exception e) {
                myLogEE(e, "Error parsing jsonData for cover info");
            }
        }

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
            // Otherwise, just fall back to the picker at that location (see fallback
            // below).
            File file = new File(pathOrUri);
            File folder = file.isDirectory() ? file : file.getParentFile();
            if (folder == null || !folder.exists()) {
                myToastE("Folder does not exist");
                return;
            }
            // We cannot build a valid SAF uri from a raw path here without prior SAF
            // access.
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
            // Prefer the standard Files app when present (AOSP/Pixel); safe to try and
            // ignore if missing
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

        // 2) Fallback: open the system folder picker AT that location (user sees the
        // directory).
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
                // (optional: store/retrieve a matching treeUri in your DB when the user selects
                // a folder).
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

            // If any already SUCCEEDED → finish immediately (covers the “came back later”
            // case)
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
                if (!wi.getState().isFinished()) {
                    active = wi;
                    break;
                }
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
                    // Update progress text from active.getProgress()
                    Data progress = active.getProgress();
                    int count = progress.getInt("p_count", 0);
                    String name = progress.getString("p_name");
                    if (count > 0 && name != null) {
                        String txt = getString(R.string.Deleting) + " item N°" + count + " : " + name + "....";
                        tvBlockingText.setText(txt);
                    } else {
                        tvBlockingText.setText(R.string.Deleting);
                    }
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
                    myToastE(err != null ? err
                            : (active.getState() == WorkInfo.State.CANCELLED ? "Delete cancelled" : "Delete failed"));
                    myLogEE(null, "Worker Delete folder : " + err);
                    break;
            }
        });
    }

    private void clickResetTracksOrder() {
        myLogI("user clicks - RESET tracks ORDER");
        MsgBox.ask(this,
                getString(R.string.AskReset_popupTitle),
                getString(R.string.AskReset_popupText_order),
                null,
                getString(android.R.string.ok),
                getString(android.R.string.cancel),
                REQ_RESET_TRACKS_ORDER);
    }

    private void resetTrackOrder() {
        AppDatabase db = AppDatabase.getInstance(getApplicationContext());
        AppDatabase.databaseWriteExecutor.execute(() -> {
            db.zikFileDao().resetSmartChapterOrderForFolder(folder.getId());
            runOnUiThread(() -> myToast(getString(R.string.Tracks_order_has_been_reset)));
        });
    }

    private void checkResults(ActivityResult result) {
        if (result.getResultCode() == RESULT_OK) {
            if (UriHelper.isReturnedUriOk(result.getData())) {
                final Uri pickedTreeUri = result.getData().getData();
                myLog("pickedTreeUri : " + pickedTreeUri.toString());

                // Persist the URI permission so it survives app restarts
                try {
                    getContentResolver().takePersistableUriPermission(pickedTreeUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                } catch (Exception e) {
                    myLogEE(e, "Failed to persist URI permission");
                }

                AppDatabase.databaseReadExecutor.execute(() -> {
                    Context context = getApplicationContext();
                    int nbBetter = 0;
                    int nbWorse = 0;
                    int nbSame = 0;

                    List<ZikFile> list = AppDatabase.getDatabase(context).zikFileDao().getZikFiles(folder.getId());
                    final int nbZikFiles = list.size();
                    myLogW("nb zik files : " + nbZikFiles);

                    // Get the tree document ID from the tree URI
                    String treeDocumentId = DocumentsContract.getTreeDocumentId(pickedTreeUri);

                    for (ZikFile zikFile : list) {
                        String oldPath = zikFile.getPath();
                        Uri oldUri = UriHelper.resolveUriFromPath(context, oldPath);

                        // Build a content URI for the file in the new location
                        // Construct child document ID: treeDocumentId + "/" + filename
                        String childDocumentId = treeDocumentId + "/" + zikFile.getName();
                        Uri newUri = DocumentsContract.buildDocumentUriUsingTree(pickedTreeUri, childDocumentId);

                        myLog("Checking file: " + zikFile.getName());
                        myLog("  oldUri: " + oldUri);
                        myLog("  newUri: " + newUri);

                        // Check if the new URI actually points to a readable file
                        boolean newUriValid = isUriReadable(context, newUri);
                        boolean oldUriValid = (oldUri != null);

                        myLog("  oldUriValid: " + oldUriValid + ", newUriValid: " + newUriValid);

                        if (!oldUriValid && newUriValid) {
                            nbBetter++;
                            myLog("  BETTER");
                        } else if (oldUriValid && !newUriValid) {
                            nbWorse++;
                            myLog("  WORSE");
                        } else {
                            nbSame++;
                            myLog("  SAME");
                        }
                    }

                    myLogW("nbBetter : " + nbBetter + ", worse : " + nbWorse + ", same : " + nbSame);

                    if (nbBetter <= 0) {
                        runOnUiThread(() -> myToastE(getString(R.string.newlocation_not_better)));
                    } else {
                        final int nbBetterFinal = nbBetter;
                        final int nbWorseFinal = nbWorse;
                        runOnUiThread(() -> {
                            String oldPathExample = list.isEmpty() ? ""
                                    : Tonio.getParentFolderOrEmpty(list.get(0).getPath());
                            String fixText = (nbWorseFinal > 0 ? getString(R.string.newlocation_better_and_worse)
                                    : getString(R.string.newlocation_better));
                            String textMsg = fixText
                                    + "\n " + getString(R.string.from) + " [" + oldPathExample + "]"
                                    + "\n " + getString(R.string.to) + " [" + pickedTreeUri + "]"
                                    + "\n\n" + nbBetterFinal + " files will be fixed"
                                    + (nbWorseFinal > 0 ? "\n" + "BUT " + nbWorseFinal + " files will be broken" : "");

                            pendingPickedTreeUri = pickedTreeUri;
                            pendingTreeDocumentId = treeDocumentId;
                            MsgBox.ask(ModifyFolderActivity.this,
                                    getString(R.string.AskChangeSource_popupTitle),
                                    textMsg,
                                    null,
                                    getString(R.string.proceed),
                                    getString(android.R.string.cancel),
                                    REQ_CHANGE_SOURCE);
                        });
                    }
                });
            } else {
                myToastE("returned Uri not OK");
            }
        } else {
            myLogE("result code not OK");
        }
    }

    private boolean isUriReadable(Context context, Uri uri) {
        try {
            String[] projection = { DocumentsContract.Document.COLUMN_DOCUMENT_ID };
            try (android.database.Cursor cursor = context.getContentResolver().query(uri, projection, null, null,
                    null)) {
                return cursor != null && cursor.getCount() > 0;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private void updateZikFilePaths(Uri pickedTreeUri, String treeDocumentId) {
        myLogW("Updating file paths to new location...");

        AppDatabase.databaseWriteExecutor.execute(() -> {
            Context context = getApplicationContext();
            List<ZikFile> list = AppDatabase.getDatabase(context).zikFileDao().getZikFiles(folder.getId());

            int updated = 0;
            for (ZikFile zikFile : list) {
                // Build the content URI for this file in the new location
                String childDocumentId = treeDocumentId + "/" + zikFile.getName();
                Uri newUri = DocumentsContract.buildDocumentUriUsingTree(pickedTreeUri, childDocumentId);

                // Verify it's readable before updating
                if (isUriReadable(context, newUri)) {
                    zikFile.setPath(newUri.toString());
                    AppDatabase.getDatabase(context).zikFileDao().update(zikFile);
                    updated++;
                    myLog("Updated: " + zikFile.getName() + " -> " + newUri);
                }
            }

            // Update the folder path to store the tree URI
            folder.setPath(pickedTreeUri.toString());
            AppDatabase.getDatabase(context).folderDao().update(folder);

            final int finalUpdated = updated;
            runOnUiThread(() -> {
                myToast(finalUpdated + " files updated successfully");
                checkZikFilesReadable(); // Recheck to update UI
            });
        });
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
            myToast(getString(R.string.permission_read_storage_not_granted));
            // askForPermission(); // check code in getOthers
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
                String logStrPrefix = getString(R.string.Track) + " " + i + "/" + nbZikFiles + " : ["
                        + zikFile.getDisplayName() + "]";
                src = UriHelper.resolveUriFromPath(context, path);
                if (src == null) {
                    nbKO = nbKO + 1;
                    String errMessage = ErrorUi.getErrorMessageConsideringZikFilePath(context, path);
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
                    if (finalNbKO == nbZikFiles)
                        errDetail = getString(R.string.All_zikFiles_not_readable);
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQ_DELETE_FOLDER) {
                startDeleteWorker();
            } else if (requestCode == REQ_RESET_FOLDER) {
                resetFolder();
            } else if (requestCode == REQ_RENAME_FOLDER) {
                if (pendingNewName != null) {
                    renameBook(pendingNewName);
                    pendingNewName = null;
                }
            } else if (requestCode == REQ_DELETE_COVER) {
                deleteCover();
            } else if (requestCode == REQ_RESET_TRACKS_ORDER) {
                resetTrackOrder();
            } else if (requestCode == REQ_CHANGE_SOURCE) {
                if (pendingPickedTreeUri != null && pendingTreeDocumentId != null) {
                    updateZikFilePaths(pendingPickedTreeUri, pendingTreeDocumentId);
                    pendingPickedTreeUri = null;
                    pendingTreeDocumentId = null;
                }
            }
        } else {
            if (requestCode == REQ_RENAME_FOLDER) {
                pendingNewName = null;
                finish(); // Handle 'No' action for rename gracefully
            } else if (requestCode == REQ_CHANGE_SOURCE) {
                pendingPickedTreeUri = null;
                pendingTreeDocumentId = null;
            }
        }
    }

    private void saveImageInDB(long folderId, String imagePath) {
        AppDatabase.getDatabase(this).folderDao().updateImage(folderId, imagePath);
        PodcastHelper.updateImage(folderId, imagePath, this);
    }

}
