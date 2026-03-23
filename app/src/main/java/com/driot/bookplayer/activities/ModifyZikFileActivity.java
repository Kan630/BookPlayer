package com.driot.bookplayer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Sql;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.player.PlaybackUiBus;
import com.driot.bookplayer.podcasts.PodcastHelper;
import com.driot.bookplayer.utils.MetaJson;
import com.driot.bookplayer.utils.MetadataFormatter;
import com.driot.bookplayer.utils.MsgBox;
import com.driot.bookplayer.utils.log.BaseActivity;

import java.io.File;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 2023-05-27
 */
public class ModifyZikFileActivity extends BaseActivity {

    private static final int REQ_DELETE_ZIKFILE = 2001;
    private static final int REQ_RESET_PROGRESS = 2002;
    private static final int REQ_RESET_FROM_THIS = 2003;
    private static final int REQ_RENAME_TRACK = 2004;

    private ZikFile zikFile;
    private String pendingNewName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_modify_zikfile);
        InsetHelper.apply(this);

        TextView tvTitle = findViewById(R.id.title);

        Button bResetOnlyThisTrack = findViewById(R.id.bResetOnlyThisTrack);
        Button bResetFromThisTrack = findViewById(R.id.bResetFromThisTrack);
        Button bDelete = findViewById(R.id.bDelete);

        EditText etRename = findViewById(R.id.etRename);

        LinearLayout ll_metadata = findViewById(R.id.ll_metadata);
        TextView tvMetadata = findViewById(R.id.tv_metadata);

        zikFile = (ZikFile) getIntent().getSerializableExtra("ZikFile");
        if (zikFile == null) {
            myLogEE(null, "could_not_identify_track_to_modify");
            myToastE(getString(R.string.could_not_identify_track_to_modify));
            finish();
            return;
        }
        String zikFileDisplayName = zikFile.getDisplayName();

        tvTitle.setText(zikFileDisplayName);
        etRename.setText(zikFileDisplayName);

        // METADATA
        String json = null;
        try {
            json = zikFile.metadataJson;
        } catch (Throwable ignore) {
        }
        java.util.Map<String, String> meta = MetaJson.fromJson(json);
        CharSequence pretty = MetadataFormatter.format(this, meta);
        if (pretty != null && pretty.length() > 0) {
            SpannableStringBuilder sb = new SpannableStringBuilder(); // for bold to stay bold
            String header = "   " + getString(R.string.metadata) + " :";
            int start = sb.length();
            sb.append(header).append('\n').append('\n');
            sb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    start, start + header.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.append(pretty);
            ll_metadata.setVisibility(View.VISIBLE);
            tvMetadata.setText(sb);
        } else {
            ll_metadata.setVisibility(View.GONE);
        }

        findViewById(R.id.bChangeTracksOrder).setOnClickListener(view -> {
            startActivity(new Intent(this, ZikFileActivity.class)
                    .putExtra(Intents.EXTRA_FOLDER_ID, zikFile.getIdFolder())
                    .putExtra(Intents.EXTRA_ACTIVATE_CHANGE_TRACK_ORDER, true));
            String warning = null;
            if (PlaybackUiBus.get().state().getValue() != null) {
                warning = getString(R.string.Quit_the_player_to_move_playing_tracks);
            }
            MsgBox.info(this, getString(R.string.ChangeTrackOrder_Title), getString(R.string.ChangeTrackOrder_Text),
                    warning);
        });

        bResetOnlyThisTrack.setOnClickListener(view -> bResetClick());
        bResetFromThisTrack.setOnClickListener(view -> bResetFromThisZikFileClick());

        bDelete.setOnClickListener(view -> bDeleteClick());

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN); // Avoid keyboard on opening
    }

    private void bDeleteClick() {
        MsgBox.ask(this,
                getString(R.string.AskDelete_popupTitle),
                getString(R.string.ModifyZikFile_AskDelete),
                null,
                getString(android.R.string.ok),
                getString(android.R.string.cancel),
                REQ_DELETE_ZIKFILE);
    }

    private void deleteZikFile() {
        // delete ZikFile if exist in app memory
        if (deleteZikFileFromDisk()) {
            myLog("Ok file deleted");
            deleteZikFileFromDB(zikFile.getId()); // to delete from DB
        } else {
            myToastEE(null, "Error deleting zik file from internal app memory");
        }
    }

    private void deleteZikFileFromDB(long id) {
        new Thread(() -> {
            PodcastHelper.deleteEpisode(id, this);
            AppDatabase.getDatabase(this).zikFileDao().deleteZikFile(zikFile.getId());
            runOnUiThread(() -> {
                myToast(getString(R.string.ZikFile_Deleted));
                myLog(getString(R.string.ZikFile_Deleted) + " : " + zikFile.getDisplayName());
                finish();
            });
        }).start();
    }

    private boolean deleteZikFileFromDisk() {
        new Thread(() -> {
            String zikFilePath = AppDatabase.getDatabase(this).zikFileDao().getZikFilePath(zikFile.getId());
            runOnUiThread(() -> {
                eraseFileFromDisk("file://" + zikFilePath);
                finish();
            });
        }).start();
        return true;
    }

    private void eraseFileFromDisk(String strPath) {
        String starter = "file:///";
        myLog("Deleting ZikFile : [" + strPath + "]");
        if (strPath.length() > 5) {
            if (!StorageHelper.isInInternalMemory(strPath)) {
                myLog("NO DISK DELETE : Not a folder in user data, skip deletion");
            } else {
                if (strPath.startsWith(starter)) {
                    strPath = strPath.replace(starter, "");
                    try {
                        File zikFileToDelete = new File(strPath);
                        if (zikFileToDelete.exists()) {
                            if (zikFileToDelete.delete()) {
                                myLog("Ok file deleted");
                            } else {
                                myToastEE(null, "Error remove ZikFile from Disk - [" + strPath + "]");
                            }
                        }
                    } catch (Exception e) {
                        myLogEE(e, "Error remove ZikFile from Disk - [" + strPath + "]");
                    }
                } else {
                    myLogEE(null, "NO DISK DELETE : weird Path, does not starts with [" + starter + "] - [" + strPath + "]");
                }
            }
        } else {
            myLogEE(null, "should not happen uri less than 5 chars - [" + strPath + "]");
        }
    }

    private void bResetClick() {
        MsgBox.ask(this,
                getString(R.string.ModifyFolder_AskDeleteProgressForThisZikFile_Title),
                getString(R.string.ModifyFolder_AskDeleteProgressForThisZikFile_Text),
                null,
                getString(R.string.Yes),
                getString(android.R.string.cancel),
                REQ_RESET_PROGRESS);
    }

    private void bResetFromThisZikFileClick() {
        MsgBox.ask(this,
                getString(R.string.ModifyFolder_AskDeleteProgressFromThisZikFile_Title),
                getString(R.string.ModifyFolder_AskDeleteProgressFromThisZikFile_Text),
                null,
                getString(R.string.Yes),
                getString(android.R.string.cancel),
                REQ_RESET_FROM_THIS);
    }

    private void deleteProgress(ZikFile zikFile) {
        new Thread(() -> {
            AppDatabase.getDatabase(this).zikFileDao().resetProgressionFully(zikFile.getId());
            Sql.calculateFolderProgress(ModifyZikFileActivity.this, zikFile.getIdFolder());
            runOnUiThread(() -> {
                myToast(ModifyZikFileActivity.this.getString(R.string.Progression_reset_done));
                finish(); // close activity
            });
        }).start();
    }

    private void deleteProgressFromThisZikFile(ZikFile zikFile) {
        new Thread(() -> {
            AppDatabase.getDatabase(this).zikFileDao().resetProgressionFromThisZikFileFully(zikFile.getIdFolder(),
                    zikFile.getZeorder());
            Sql.calculateFolderProgress(ModifyZikFileActivity.this, zikFile.getIdFolder());
            runOnUiThread(() -> {
                myToast(ModifyZikFileActivity.this.getString(R.string.Progression_reset_done));
                finish(); // close activity
            });
        }).start();
    }

    @Override
    public void onBackPressed() {
        String newName = ((TextView) findViewById(R.id.etRename)).getText().toString().trim();
        if (!newName.equals(zikFile.getDisplayName())) {
            this.pendingNewName = newName;
            MsgBox.ask(this,
                    getString(R.string.AskRename_popupTitle),
                    getString(R.string.AskRename_Track) + "\n[ " + newName + " ]",
                    null,
                    getString(R.string.Yes),
                    getString(R.string.No),
                    REQ_RENAME_TRACK);
        } else {
            super.onBackPressed(); // No changes, just leave
        }
    }

    private void renameTrack(String newDisplayName) {
        if (newDisplayName.length() < 2) {
            myToast(getString(R.string.Error_NameTooShort));
        } else {
            new Thread(() -> {
                AppDatabase.getDatabase(this).zikFileDao().setDisplayName(zikFile.getId(), newDisplayName);
                runOnUiThread(() -> {
                    myToast(getString(R.string.ZikFile_Renamed));
                    myLogInFile(getString(R.string.ZikFile_Renamed) + " : [" + zikFile.getDisplayName() + "] -> ["
                            + newDisplayName + "]");
                    finish();
                });
            }).start();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQ_DELETE_ZIKFILE) {
                deleteZikFile();
            } else if (requestCode == REQ_RESET_PROGRESS) {
                deleteProgress(zikFile);
            } else if (requestCode == REQ_RESET_FROM_THIS) {
                deleteProgressFromThisZikFile(zikFile);
            } else if (requestCode == REQ_RENAME_TRACK) {
                if (pendingNewName != null) {
                    renameTrack(pendingNewName);
                }
            }
        } else if (resultCode == RESULT_CANCELED && requestCode == REQ_RENAME_TRACK) {
            super.onBackPressed();
        }
    }
}
