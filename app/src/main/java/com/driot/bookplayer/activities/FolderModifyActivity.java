package com.driot.bookplayer.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.DatabaseClient;
import com.driot.tonylib.KanLogger;

import java.io.File;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import static com.driot.bookplayer.global.Var.PATH_CHECK_APPLICATION;
import static com.driot.bookplayer.utils.Utils.recursiveRemove;
import static com.driot.tonylib.KanLogger.myLogInFile;
import static com.driot.tonylib.KanLogger.myToast;
import static com.driot.tonylib.KanLogger.myToastE;

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
        Button bRenameOk = findViewById(R.id.bRenameOk);
        TextView tvTitle = findViewById(R.id.title);
        TextView tvRename = findViewById(R.id.tvRename);

        idFolder = getIntent().getIntExtra("FolderId", 0);
        FolderName = getIntent().getStringExtra("FolderName");
        tvTitle.setText(FolderName);
        tvRename.setText(FolderName);

        tvRename.setVisibility(View.INVISIBLE);
        bRenameOk.setVisibility(View.INVISIBLE);

        bDelete.setOnClickListener(view -> bDeleteClick());

        bRename.setOnClickListener(view -> {
            tvRename.setVisibility(View.VISIBLE);
            bRenameOk.setVisibility(View.VISIBLE);
            bRename.setVisibility(View.INVISIBLE);
        });

        bRenameOk.setOnClickListener(view -> bRenameOkClick(tvRename.getText().toString()));

        bReset.setOnClickListener(view -> bResetClick());
    }

    private void bDeleteClick() {
        new AlertDialog.Builder(FolderModifyActivity.this)
                .setTitle(getString(R.string.AskDelete_popupTitle))
                .setMessage(getString(R.string.ModifyFolder_AskDelete))
                .setCancelable(false)
                .setPositiveButton("ok", (dialog, which) -> deleteFolder1())
                .setNegativeButton("cancel", (dialogInterface, i) -> {})
                .show();
    }


    private void deleteFolder1() {
        // delete folder if exist in app memory
        String myErr = "Error getting uri from folder for deleting file in memory";
        Observable.fromCallable(() -> DatabaseClient
                .getInstance(getApplicationContext())
                .getAppDatabase()
                .ZikFileDao()
                .getFolderPath(idFolder)).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((result) -> {
                    if (eraseFolderAndFiles(result)) {
                        myLog("Ok files deleted from Disk");
                        deleteFolder2();
                    } else {
                        myLogE("Error deleting files from Disk");
                        deleteFolder2(); // on supprime quand meme, on va pas laisser des fantomes, et puis il y a une interface pour les fichiers sur le disk
                    }
                }, throwable -> {
                    myToastE(myErr);
                    myLogE(myErr + " :" + throwable.getMessage());
                    throwable.printStackTrace();
                });
    }

    private void deleteFolder2() {
        // delete folder in database
        Observable.fromCallable(() -> {
            DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .FolderDao()
                    .delete(idFolder);
            DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .ZikFileDao()
                    .deleteFolder(idFolder);
            return true;
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((result) -> {
                    if (result) {
                        myToast(getString(R.string.Folder_Deleted_DB));
                        myLog(getString(R.string.Folder_Deleted_DB) + " : " + FolderName);
                        finish();
                    }
                });
    }


    private boolean eraseFolderAndFiles(String strPath) {
        if (strPath.endsWith("files/unzipped") || strPath.endsWith("files/unzipped/")) {
            myLogE("You are not deleting all the books, malheureux !!");
            return false;
        }
        String starter = "file:///";
        myLog("Deleting folder from Disk : [" + strPath + "]");
        if (strPath.length()>5) {
            if (!strPath.contains(PATH_CHECK_APPLICATION) ) { //strPath.startsWith(starter)
                myLog("NO DISK DELETE : Not a folder in user data (" + PATH_CHECK_APPLICATION + "), skip deletion of folder");
                return true;
            } else {
                //if (strPath.startsWith(starter)) {
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
                //} else {
                //    myLog("NO DISK DELETE : weird Path, does not starts with [" + starter + "]");
                //    return true;
                //}
            }
        } else {
            myToastE("Weird error while removing file from Disk");
            myLogE("should not happen : Path less than 5 chars");
            return true;
        }
    }

    private void bRenameOkClick(String newName) {
        if (newName.length() < 2) {
            myToast(getString(R.string.Error_FolderNameTooShort));
        } else {
            Observable.fromCallable(() -> {
                DatabaseClient
                        .getInstance(getApplicationContext())
                        .getAppDatabase()
                        .FolderDao()
                        .changeName(idFolder, newName);
                return true;
            })
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe((result) -> {
                        if (result) {
                            myToast(getString(R.string.Folder_Renamed));
                            myLogInFile(getString(R.string.Folder_Renamed) + " : " + FolderName);
                            finish();
                        }
                    });
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
        Observable.fromCallable(() -> {
            DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .FolderDao()
                    .resetProgression(idFolder);
            DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .ZikFileDao()
                    .resetFolderProgression(idFolder);
            return true;
        })
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((result) -> {
                    if (result) {
                        myLogInFile(getString(R.string.Folder_Reset) + " : " + FolderName);
                        myToast(getString(R.string.Folder_Reset));
                        finish();
                    }
                });
    }

    //--- LOG --------------------------
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }

}
