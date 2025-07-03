package com.driot.bookplayer.activities;

import static com.driot.bookplayer.global.Var.PATH_CHECK_APPLICATION;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.text.Spannable;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Sql;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.io.File;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 2023-05-27
 */
public class ZikFileModifyActivity extends LoggingActivity {

    private ZikFile zikFile;
    private int zikFileId;
    private String zikFileName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_modifyzikfile);
        TextView tvTitle = findViewById(R.id.title);

        Button bReset = findViewById(R.id.bReset);
        Button bDelete = findViewById(R.id.bDelete);

        Button bRename = findViewById(R.id.bRename);
        EditText etRename = findViewById(R.id.etRename);

        EditText etChangePosition = findViewById(R.id.etChangePosition);
        Button bMove = findViewById(R.id.bMove);

        zikFile = (ZikFile) getIntent().getSerializableExtra("ZikFile");
        if (zikFile == null) {
            myToastEE(null, "could not identify track to modify");
            finish();
        }
        zikFileId = zikFile.getId();
        zikFileName = zikFile.getName();
        String zikFileDisplayName = zikFile.getDisplayName();
        double zikFilePosition = zikFile.getZeorder();

        tvTitle.setText(zikFileDisplayName);
        etRename.setText(zikFileDisplayName);
        etChangePosition.setText(String.valueOf(zikFilePosition));

        bRename.setOnClickListener(view -> {
            etRename.requestFocus();
            android.text.Selection.setSelection((Spannable) etRename.getText(), etRename.getText().length()); // put cursor at the end (TextView)
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(etRename, InputMethodManager.SHOW_IMPLICIT);
            }
        });

        bMove.setOnClickListener(view -> {
            etChangePosition.requestFocus();
            etChangePosition.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
             InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(etChangePosition, InputMethodManager.SHOW_IMPLICIT);
            }
            etChangePosition.setSelection(0, etChangePosition.getText().length());
        });

        bReset.setOnClickListener(view -> bResetClick(zikFile.getIdFolder(), zikFileName));

        bDelete.setOnClickListener(view -> bDeleteClick());

        bRename.setOnClickListener(view -> bRenameClick(etRename.getText().toString()));

        bMove.setOnClickListener(view -> bMoveClick(etChangePosition.getText().toString()));

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN); // Avoid keyboard on opening
    }

    private void bMoveClick(String newPosStr) {
        try
            {Double.parseDouble(newPosStr);}
        catch(NumberFormatException e)
            {myToast(getString(R.string.Error_ZikFilePositionOnlyDigits));}
        double newPos = Double.parseDouble(newPosStr);
        if (newPos < 0 || newPos > 1000) { // only digits
            myToast("cannot parse number");
        } else {
            new Thread(() -> {
                AppDatabase.getDatabase(this).ZikFileDao().changePosition(zikFileId, (double) newPos);
                runOnUiThread(() -> {
                    myToast(getString(R.string.ZikFile_RePositioned));
                    myLogInFile( getString(R.string.ZikFile_RePositioned) + " [" + newPosStr + "] : " + zikFileName);
                    finish();
                });
            }).start();
        }
    }

    private void bDeleteClick() {
        new AlertDialog.Builder(ZikFileModifyActivity.this)
                .setTitle(getString(R.string.AskDelete_popupTitle))
                .setMessage(getString(R.string.ModifyZikFile_AskDelete))
                .setCancelable(false)
                .setPositiveButton("ok", (dialog, which) -> deleteZikFile())
                .setNegativeButton("cancel", (dialogInterface, i) -> {})
                .show();
    }


    private void deleteZikFile() {
        // delete ZikFile if exist in app memory
        String myErr = "Error getting uri from ZikFile for deleting file in memory";
        if (DeleteZikFileFromDisk()) {
            myLog("Ok file deleted");
            deleteZikFileFromDB(); // to delete from DB
        } else {
            myLog("Error deleting zik file from internal app memory");
        }
    }

    private void deleteZikFileFromDB() {
        new Thread(() -> {
            AppDatabase.getDatabase(this).ZikFileDao().deleteZikFile(zikFileId);
            runOnUiThread(() -> {
                myToast(getString(R.string.ZikFile_Deleted));
                myLog(getString(R.string.ZikFile_Deleted) + " : " + zikFileName);
                finish();
            });
        }).start();
    }

    private boolean DeleteZikFileFromDisk() {
        new Thread(() -> {
            String zikFilePath = AppDatabase.getDatabase(this).ZikFileDao().getZikFilePath(zikFileId);
            runOnUiThread(() -> {
                eraseFileFromDisk("file://" + zikFilePath);
                finish();
            });
        }).start();
        return true;
    }

    private boolean eraseFileFromDisk(String strPath) {
        String starter = "file:///";
        myLog("Deleting ZikFile : " +strPath);
        if (strPath.length()>5) {
            if (!strPath.contains(PATH_CHECK_APPLICATION) ) { //strPath.startsWith(starter)
                myLog("NO DISK DELETE : Not a folder in user data (" + PATH_CHECK_APPLICATION + "), skip deletion of folder");
                return true;
            } else {
                if (strPath.startsWith(starter)) {
                    strPath = strPath.replace(starter, "");
                    try {
                        File zikFileToDelete = new File(strPath);
                        if (zikFileToDelete.exists()) {
                            zikFileToDelete.delete();
                        }
                        return true;
                    } catch (Exception e) {
                        myLogEE(e,"Error remove ZikFile from Disk");
                        return false;
                    }
                } else {
                    myLogEE(null, "NO DISK DELETE : weird Path, does not starts with [" + starter + "]");
                    return true;
                }
            }
        } else {
            myLogEE(null,"should not happen uri less than 5 chars");
            return false;
        }
    }

    private void bRenameClick(String newDisplayName) {
        if (newDisplayName.length() < 2) {
            myToast(getString(R.string.Error_NameTooShort));
        } else {
            new Thread(() -> {
                AppDatabase.getDatabase(this).ZikFileDao().setDisplayName(zikFileId, newDisplayName);
                runOnUiThread(() -> {
                    myToast(getString(R.string.ZikFile_Renamed));
                    myLog(getString(R.string.ZikFile_Renamed) + " : [" + zikFileName + "] -> [" + newDisplayName + "]");
                    finish();
                });
            }).start();
        }
    }

    private void bResetClick(int idFolder, String zikFileName) {
        new AlertDialog.Builder(ZikFileModifyActivity.this)
                .setTitle(ZikFileModifyActivity.this.getString(R.string.ModifyFolder_AskDeleteProgressFromZikFile_Title))
                .setMessage(ZikFileModifyActivity.this.getString(R.string.ModifyFolder_AskDeleteProgressFromZikFile_Text))
                .setCancelable(false)
                .setPositiveButton(ZikFileModifyActivity.this.getString(R.string.Yes), (dialog, which) -> deleteProgressFromThisZikFile(idFolder, zikFileName))
                .setNegativeButton(ZikFileModifyActivity.this.getString(R.string.Cancel), (dialogInterface, i) -> {})
                .show();
    }
    private void deleteProgressFromThisZikFile(int idFolder, String zikFileName) {
        new Thread(() -> {
            AppDatabase.getDatabase(this).ZikFileDao().resetProgressionFromThisZikFile(idFolder, zikFileName);
            Sql.calculateFolderProgress(ZikFileModifyActivity.this, idFolder);
            runOnUiThread(() -> {
                myToast(ZikFileModifyActivity.this.getString(R.string.Progression_reset_done));
                myLogInFile(ZikFileModifyActivity.this.getString(R.string.Progression_reset_done) + " beggining on " + zikFileName);
                finish(); //close activity
            });
        }).start();
    }

}
