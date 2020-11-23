package com.driot.bookplayer.activities;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.DatabaseClient;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 15/11/20
 */
public class FolderModifyActivity extends LifecycleLoggingActivity {

    private int idFolder;

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
        tvTitle.setText(getIntent().getStringExtra("FolderName"));
        tvRename.setText(getIntent().getStringExtra("FolderName"));

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
                .setTitle(getString(R.string.ModifyFolder_AskDeleteTitle))
                .setMessage(getString(R.string.ModifyFolder_AskDeleteText))
                .setCancelable(false)
                .setPositiveButton("ok", (dialog, which) -> deleteFolder())
                .setNegativeButton("cancel", (dialogInterface, i) -> {})
                .show();
    }


    private void deleteFolder() {
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
                        myToast(getString(R.string.Folder_Deleted));
                        finish();
                    }
                });

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
                            finish();
                        }
                    });
        }
    }

    private void bResetClick() {
        new AlertDialog.Builder(FolderModifyActivity.this)
                .setTitle(getString((R.string.ModifyFolder_AskResetTitle)))
                .setMessage(getString((R.string.ModifyFolder_AskResetText)))
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
                        myToast(getString(R.string.Folder_Reset));
                        finish();
                    }
                });
    }







    private void myToast(String str) {
        myLog(str);
        Toast.makeText(getApplicationContext(), str, Toast.LENGTH_SHORT).show();
    }

}
