package com.driot.bookplayer.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.FileHelper;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.io.File;

import static com.driot.bookplayer.helpers.PodcastHelper.cancelAutoDownload;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 15/11/20
 */
public class ModifyFolderActivity extends LoggingActivity {

    private Folder folder;

    EditText etIntroCut;
    EditText etRename;

    private ImageView ivCoverPreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_modify_folder);

        Button bDelete = findViewById(R.id.bDelete);
        Button bReset = findViewById(R.id.bReset);
        Button bExport = findViewById(R.id.bExport);
        TextView tvTitle = findViewById(R.id.title);
        TextView tvInfo = findViewById(R.id.tvInfo);

        ImageView ivStorageIcon = findViewById(R.id.imageViewStorageIcon);
        TextView tvStorageIcon = findViewById(R.id.textViewStorageIcon);

        folder = getIntent().getParcelableExtra("folder");
        if (folder == null) {
            throw new IllegalArgumentException("folder must not be null");
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

        bDelete.setOnClickListener(view -> bDeleteClick());

        bReset.setOnClickListener(view -> bResetClick());

        bExport.setOnClickListener(view -> bExportClick());

        etIntroCut = findViewById(R.id.etIntroCut);
        etIntroCut.setText(String.valueOf(Pref.getIntroCutFromPref(this, folder.getId())));

        ivCoverPreview = findViewById(R.id.ivCoverPreview);
        Button bChangeCover = findViewById(R.id.bChangeCover);

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
        getOnBackPressedDispatcher().addCallback(this, callback);

        bChangeCover.setOnClickListener(view -> {
            myLogI("user clicks - change image");
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            selectImageLauncher.launch(Intent.createChooser(intent, "Select Cover Image"));
        });

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN); // Avoid keyboard on opening

    }

    private void bDeleteClick() {
        myLogI("user clicks - delete");
        new AlertDialog.Builder(ModifyFolderActivity.this)
                .setTitle(getString(R.string.AskDelete_popupTitle))
                .setMessage(getString(R.string.ModifyFolder_AskDelete))
                .setCancelable(false)
                .setPositiveButton("ok", (dialog, which) -> deleteFolder())
                .setNegativeButton("cancel", (dialogInterface, i) -> {})
                .show();
    }

    private void deleteFolder() {
        new Thread(() -> {
            String folderPath = AppDatabase.getDatabase(this).ZikFileDao().getFolderPath(folder.getId());
            if (!eraseFolderAndFiles(folderPath)) {
                myToastEE(null,"Error deleting files from Disk " + folderPath);
                return;
            }

            Podcast podcast = AppDatabase.getDatabase(this).PodcastDao().getPodcastByFolderId(folder.getId());
            if (podcast==null) ImageHelper.deleteImage(this, folder);//not delete image if isFavorite, or is in Podcast table
            AppDatabase.getDatabase(this).FolderDao().delete(folder.getId());
            AppDatabase.getDatabase(this).ZikFileDao().deleteAllZikFilesInFolder(folder.getId());
            cancelAutoDownload(this, folder.getId());
            runOnUiThread(() -> {
                myToast(getString(R.string.Folder_Deleted_DB));
                myLog(getString(R.string.Folder_Deleted_DB) + " : " + folder.getName());
                finish();
            });
        }).start();
    }

    private boolean eraseFolderAndFiles(String strPath) {
        myLog("Deleting folder from Disk : [" + strPath + "]");
        if (strPath==null) return false;
        if (strPath.endsWith("files/unzipped") || strPath.endsWith("files/unzipped/")) {
            myLogE("You are not deleting all the books, fucking dickhead !!");
            return false;
        }
        String starter = "file:///";
        if (strPath.length()>5) {
            if (!strPath.contains(Var.PATH_CHECK_AUDIO_FILE_INTERNAL) ) { //strPath.startsWith(starter)
                myLog("NO DISK DELETE : Not a folder in user data (" + Var.PATH_CHECK_AUDIO_FILE_INTERNAL + "), skip deletion of folder");
                return true;
            } else {
                strPath = strPath.replace(starter, ""); //was a prefix in Folder table, field has been deprecated, now fill with dummies
                try {
                    File folderToDelete = new File(strPath);
                    myLog("is directory :    " + folderToDelete.isDirectory());
                    FileHelper.recursiveRemove(folderToDelete);
                    return true;
                } catch (Exception e) {
                    myToastE("Error remove folder & files from Disk - user data");
                    myLogE("Error remove folder & files from Disk - user data");
                    return false;
                }
            }
        } else {
            myToastE("Weird error while removing file from Disk");
            myLogE("should not happen : Path less than 5 chars");
            return true;
        }
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

    private final ActivityResultLauncher<Intent> selectImageLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri selectedImageUri = result.getData().getData();
                    if (selectedImageUri != null) {
                        new Thread(() -> {
                            try {
                                String fileName = "UserPic_" + ImageHelper.FOLDER_IMAGE_PREFIX + folder.getId() + ".jpg";
                                String newImagePath = ImageHelper.copyContentUriToImageFile(this, selectedImageUri.toString(), fileName);
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

}
