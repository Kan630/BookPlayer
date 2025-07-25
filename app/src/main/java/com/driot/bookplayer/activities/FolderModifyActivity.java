package com.driot.bookplayer.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.io.File;

import static com.driot.bookplayer.global.Var.PATH_CHECK_APPLICATION;
import static com.driot.bookplayer.utils.PodcastHelper.cancelAutoDownload;
import static com.driot.bookplayer.utils.Utils.recursiveRemove;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 15/11/20
 */
public class FolderModifyActivity extends LoggingActivity {

    private Folder folder;

    EditText etIntroCut;
    EditText etRename;

    private static final int REQUEST_SELECT_IMAGE = 536861; //dummy code
    private ImageView ivCoverPreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_modifyfolder);

        Button bDelete = findViewById(R.id.bDelete);
        Button bReset = findViewById(R.id.bReset);
        Button bExport = findViewById(R.id.bExport);
        TextView tvTitle = findViewById(R.id.title);
        //TextView tvImportedOn = findViewById(R.id.importedOn);
        TextView tvLastAccess = findViewById(R.id.lastAccess);

        ImageView ivStorageIcon = findViewById(R.id.imageViewStorageIcon);
        TextView tvStorageIcon = findViewById(R.id.textViewStorageIcon);

        folder = getIntent().getParcelableExtra("folder");
        if (folder == null) {
            throw new IllegalArgumentException("folder must not be null");
        }
        tvTitle.setText(folder.getName());

        etRename = findViewById(R.id.etRename);
        etRename.setText(folder.getName());

        //String importedOn = getString(R.string.ImportedOn) + " : " + getIntent().getStringExtra("ImportedOn");   /// Only time is saved in Folder, could get date from ZokFile
        //tvImportedOn.setText(importedOn);
        String lastAccess = getString(R.string.LastAccess) + " : " + getIntent().getStringExtra("LastAccessInDays") + " (" + getIntent().getStringExtra("LastAccess") + ")";
        tvLastAccess.setText(lastAccess);

        String memoryLocationText = getString(R.string.AudioLocation) + " : " + getIntent().getStringExtra("MemoryLocationText");
        int memoryLocationIcon = getIntent().getIntExtra("MemoryLocation",0);
        myLog("Audio Location : " + memoryLocationText + " - Icon : [" + memoryLocationIcon + "]" );
        ivStorageIcon.setImageResource(memoryLocationIcon);
        tvStorageIcon.setText(memoryLocationText);

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
            ivCoverPreview.setImageResource(R.drawable.placeholder_cover);
        }

        bChangeCover.setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(Intent.createChooser(intent, "Select Cover Image"), REQUEST_SELECT_IMAGE);
        });

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN); // Avoid keyboard on opening

    }

    private void bDeleteClick() {
        new AlertDialog.Builder(FolderModifyActivity.this)
                .setTitle(getString(R.string.AskDelete_popupTitle))
                .setMessage(getString(R.string.ModifyFolder_AskDelete))
                .setCancelable(false)
                .setPositiveButton("ok", (dialog, which) -> deleteFolder())
                .setNegativeButton("cancel", (dialogInterface, i) -> {})
                .show();
    }

    private void deleteFolder() {
        String myErr = "Error getting uri from folder for deleting file in memory";
        new Thread(() -> {
            String folderPath = AppDatabase.getDatabase(this).ZikFileDao().getFolderPath(folder.getId());
            if (!eraseFolderAndFiles(folderPath)) {
                myLogE("Error deleting files from Disk");
            }
            AppDatabase.getDatabase(this).FolderDao().delete(folder.getId());
            AppDatabase.getDatabase(this).ZikFileDao().deleteFolder(folder.getId());
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
            if (!strPath.contains(PATH_CHECK_APPLICATION) ) { //strPath.startsWith(starter)
                myLog("NO DISK DELETE : Not a folder in user data (" + PATH_CHECK_APPLICATION + "), skip deletion of folder");
                return true;
            } else {
                strPath = strPath.replace(starter, ""); //was a prefix in Folder table, field has been deprecated, now fill with dummies
                try {
                    File folderToDelete = new File(strPath);
                    myLog("is directory :    " + folderToDelete.isDirectory());
                    recursiveRemove(folderToDelete);
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
        new AlertDialog.Builder(FolderModifyActivity.this)
                .setTitle(getString((R.string.AskReset_popupTitle)))
                .setMessage(getString((R.string.ModifyFolder_AskReset)))
                .setCancelable(true)
                .setPositiveButton("ok", (dialog, i) -> resetFolder())
                .setNegativeButton("cancel", (dialog, i) -> {})
                .show();
    }

    private void bExportClick() {
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

    @Override
    public void onBackPressed() {
        String newName = ((TextView) findViewById(R.id.etRename)).getText().toString().trim();
        if (!newName.equals(folder.getName())) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.AskRename_popupTitle)
                    .setMessage(getString(R.string.AskRename_Book) + "\n[ " + newName + " ]")
                    .setPositiveButton(R.string.Yes, (dialog, which) -> {
                        renameBook(newName);
                    })
                    .setNegativeButton(R.string.No, (dialog, which) -> {
                        super.onBackPressed(); // Just leave
                    })
                    .show();
        } else {
            super.onBackPressed(); // No changes, just leave
        }
    }

}
