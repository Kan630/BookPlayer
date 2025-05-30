package com.driot.bookplayer.activities;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.utils.KanLogger;

import java.io.File;

import static com.driot.bookplayer.global.Var.PATH_CHECK_APPLICATION;
import static com.driot.bookplayer.utils.Utils.recursiveRemove;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 15/11/20
 */
public class FolderModifyActivity extends LifecycleLoggingActivity {

    private int idFolder;
    private String FolderName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_modifyfolder);

        Button bDelete = findViewById(R.id.bDelete);
        Button bReset = findViewById(R.id.bReset);
        Button bRename = findViewById(R.id.bRename);
        TextView tvTitle = findViewById(R.id.title);
        TextView tvRename = findViewById(R.id.etRename);

        idFolder = getIntent().getIntExtra("FolderId", 0);
        FolderName = getIntent().getStringExtra("FolderName");
        tvTitle.setText(FolderName);
        tvRename.setText(FolderName);

        bDelete.setOnClickListener(view -> bDeleteClick());

        bRename.setOnClickListener(view -> bRenameClick(tvRename.getText().toString()));

        bReset.setOnClickListener(view -> bResetClick());
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
            String folderPath = AppDatabase.getDatabase(this).ZikFileDao().getFolderPath(idFolder);
            if (!eraseFolderAndFiles(folderPath)) {
                myLogE("Error deleting files from Disk");
            }
            AppDatabase.getDatabase(this).FolderDao().delete(idFolder);
            AppDatabase.getDatabase(this).ZikFileDao().deleteFolder(idFolder);
            runOnUiThread(() -> {
                myToast(getString(R.string.Folder_Deleted_DB));
                myLog(getString(R.string.Folder_Deleted_DB) + " : " + FolderName);
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

    private void bRenameClick(String newName) {
        if (newName.length() < 2) {
            myToast(getString(R.string.Error_FolderNameTooShort));
        } else {
            new Thread(() -> {
                AppDatabase.getDatabase(this).FolderDao().changeName(idFolder, newName);
                runOnUiThread(() -> {
                    myToast(getString(R.string.Folder_Renamed));
                    myLogInFile(getString(R.string.Folder_Renamed) + " : [" + FolderName + "] - > [" + newName + "]");
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
                .setPositiveButton("ok", (dialog, which) -> resetFolder())
                .show();
    }

    private void resetFolder() {
        myLog("resetFolder()");
        new Thread(() -> {
            AppDatabase.getDatabase(this).FolderDao().resetProgression(idFolder);
            AppDatabase.getDatabase(this).ZikFileDao().resetFolderProgression(idFolder);
            runOnUiThread(() -> {
                myLogInFile(getString(R.string.Folder_Reset) + " : " + FolderName);
                myToast(getString(R.string.Folder_Reset));
                finish();
            });
        }).start();
    }

    //--- LOG --------------------------
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogInFile(String str) { KanLogger.myLogInFile(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
    private void myToast(String str) { KanLogger.myToast(this.getClass().getName(), str); }
    private void myToastE(String str) { KanLogger.myToastE(this.getClass().getName(), str); }

}
