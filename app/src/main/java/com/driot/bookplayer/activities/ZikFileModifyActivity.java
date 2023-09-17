package com.driot.bookplayer.activities;

import static com.driot.bookplayer.utils.Utils.recursiveRemove;
import static com.driot.tonylib.KanLogger.myLog;
import static com.driot.tonylib.KanLogger.myLogE;
import static com.driot.tonylib.KanLogger.myLogInFile;
import static com.driot.tonylib.KanLogger.myToast;
import static com.driot.tonylib.KanLogger.myToastE;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.text.Spannable;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.db.FolderDao;
import com.driot.bookplayer.db.ZikFile;

import java.io.File;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 2023-05-27
 */
public class ZikFileModifyActivity extends LifecycleLoggingActivity {

    private ZikFile zikFile;

    private int zikFileId;
    private String zikFileName;
    private String zikFileDisplayName;
    private double zikFilePosition;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_modifyzikfile);

        Button bDelete = findViewById(R.id.bDelete);
        Button bRename = findViewById(R.id.bRename);
        Button bRenameOk = findViewById(R.id.bRenameOk);
        TextView tvTitle = findViewById(R.id.title);
        TextView tvRename = findViewById(R.id.tvRename);
        EditText etChangePosition = findViewById(R.id.etChangePosition);
        Button bMove = findViewById(R.id.bMove);
        Button bMoveOk = findViewById(R.id.bMoveOk);

        zikFile = (ZikFile) getIntent().getSerializableExtra("ZikFile");
        zikFileId = zikFile.getId();
        zikFileName = zikFile.getName();
        zikFileDisplayName = zikFile.getDisplayName();
        zikFilePosition = zikFile.getZeorder();

        tvTitle.setText(zikFileDisplayName);
        tvRename.setText(zikFileDisplayName);
        etChangePosition.setText(String.valueOf(zikFilePosition));

        tvRename.setVisibility(View.INVISIBLE);
        bRenameOk.setVisibility(View.INVISIBLE);

        bRename.setOnClickListener(view -> {
            tvRename.setVisibility(View.VISIBLE);
            bRenameOk.setVisibility(View.VISIBLE);
            bRename.setVisibility(View.INVISIBLE);
            tvRename.requestFocus();
            android.text.Selection.setSelection((Spannable) tvRename.getText(), tvRename.getText().length()); // put cursor at the end (TextView)
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(tvRename, InputMethodManager.SHOW_IMPLICIT);
            }
        });

        etChangePosition.setVisibility(View.INVISIBLE);
        bMoveOk.setVisibility(View.INVISIBLE);
        bMove.setOnClickListener(view -> {
            etChangePosition.setVisibility(View.VISIBLE);
            bMoveOk.setVisibility(View.VISIBLE);
            bMove.setVisibility(View.INVISIBLE);
            etChangePosition.requestFocus();
            etChangePosition.setInputType(InputType.TYPE_CLASS_NUMBER);
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(etChangePosition, InputMethodManager.SHOW_IMPLICIT);
            }
            etChangePosition.setSelection(0, etChangePosition.getText().length());
        });

        bMoveOk.setOnClickListener(view -> bMoveOkClick(etChangePosition.getText().toString()));

        bDelete.setOnClickListener(view -> bDeleteClick());

        bRenameOk.setOnClickListener(view -> bRenameOkClick(tvRename.getText().toString()));

    }

    private void bMoveOkClick(String newPosStr) {
        try
            {Double.parseDouble(newPosStr);}
        catch(NumberFormatException e)
            {myToast(getString(R.string.Error_ZikFilePositionOnlyDigits));}
        double newPos = Double.parseDouble(newPosStr);
        if (newPos < 0 || newPos > 1000) { // only digits
            myToast("cannot parse number");
        } else {
            Observable.fromCallable(() -> {
                        DatabaseClient
                                .getInstance(getApplicationContext())
                                .getAppDatabase()
                                .ZikFileDao()
                                .changePosition(zikFileId, (double) newPos);
                        return true;
                    })
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe((result) -> {
                        if (result) {
                            myToast(getString(R.string.ZikFile_RePositioned));
                            myLogInFile( getString(R.string.ZikFile_RePositioned) + " [" + newPosStr + "] : " + zikFileName);
                            finish();
                        }
                    });
        }
    }

    private void bDeleteClick() {
        new AlertDialog.Builder(ZikFileModifyActivity.this)
                .setTitle(getString(R.string.AskDelete_popupTitle))
                .setMessage(getString(R.string.ModifyZikFile_AskDelete))
                .setCancelable(false)
                .setPositiveButton("ok", (dialog, which) -> deleteZikFile1())
                .setNegativeButton("cancel", (dialogInterface, i) -> {})
                .show();
    }


    private void deleteZikFile1() {
        // delete ZikFile if exist in app memory
        String myErr = "ZikFileModifyActivity : Error getting uri from ZikFile for deleting file in memory";
        if (DeleteZikFileFromDisk()) {
            myLog("Ok file deleted");
            deleteZikFileFromDB(); // to delete from DB
        } else {
            myLog("Error deleting zik file from internal app memory");
        }
    }

    private void deleteZikFileFromDB() {
        // delete ZikFile in database
        Observable.fromCallable(() -> {
            DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .ZikFileDao()
                    .deleteZikFile(zikFileId);
            return true;
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((result) -> {
                    if (result) {
                        myToast(getString(R.string.ZikFile_Deleted));
                        myLog(getString(R.string.ZikFile_Deleted) + " : " + zikFileName);
                        finish();
                    }
                });
    }

    private boolean DeleteZikFileFromDisk() {
        Observable.fromCallable(() ->
                    DatabaseClient
                            .getInstance(getApplicationContext())
                            .getAppDatabase()
                            .ZikFileDao()
                            .getZikFilePath(zikFileId)).subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
        .subscribe((result) -> {
                eraseFileFromDisk("file://" + result);
                finish();
        });
        return true;
    }

    private boolean eraseFileFromDisk(String strPath) {
        String starter = "file:///";
        myLog("Deleting ZikFile : " +strPath);
        if (strPath.length()>5) {
            if (strPath.startsWith(starter)) {
                strPath = strPath.replace(starter,"");
                try {
                    File zikFileToDelete = new File(strPath);
                    if(zikFileToDelete.exists()) {
                        zikFileToDelete.delete();
                    }
                    return true;
                } catch (Exception e) {
                    myLogE("Error remove ZikFile from Disk");
                    return false;
                }
            } else {
                myLog("Not a ZikFile in user data, skip deletion of ZikFile");
                return true;
            }
        } else {
            myLogE("should not happen uri less than 5 chars");
            return false;
        }
    }

    private void bRenameOkClick(String newDisplayName) {
        if (newDisplayName.length() < 2) {
            myToast(getString(R.string.Error_NameTooShort));
        } else {
            Observable.fromCallable(() -> {
                DatabaseClient
                        .getInstance(getApplicationContext())
                        .getAppDatabase()
                        .ZikFileDao()
                        .setDisplayName(zikFileId, newDisplayName);
                return true;
            })
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe((result) -> {
                        if (result) {
                            myToast(getString(R.string.ZikFile_Renamed));
                            myLogInFile(getString(R.string.ZikFile_Renamed) + " : " + zikFileName);
                            finish();
                        }
                    });
        }
    }


}
