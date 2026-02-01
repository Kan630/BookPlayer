package com.driot.bookplayer.activities;

import static com.driot.bookplayer.db.DatabaseBackupHelper.BACKUP_NAME;
import static com.driot.bookplayer.db.DatabaseBackupHelper.getBackupDir;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.DatabaseBackupHelper;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.utils.log.BaseActivity;

import java.io.File;
import java.text.SimpleDateFormat;

public class DebugDatabaseActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug_database);
        InsetHelper.apply(this);

        Button backupBtn = findViewById(R.id.btnBackupDb);
        Button restoreBtn = findViewById(R.id.btnRestoreDb);
        TextView tvBackupInfo = findViewById(R.id.tvBackupInfo);

        try {
            File backupFile = new File(getBackupDir(), BACKUP_NAME);
            if (backupFile.exists()) {
                String info = "📁 Folder: " + getBackupDir().getAbsolutePath() + "\n\n" +
                        "📄 File: " + backupFile.getName() + "\n\n" +
                        "🕒 Date: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(backupFile.lastModified());
                tvBackupInfo.setText(info);
            } else {
                tvBackupInfo.setText(
                        getString(com.driot.bookplayer.R.string.no_backup_found) + getBackupDir().getAbsolutePath());
            }
        } catch (Exception e) {
            tvBackupInfo.setText(getString(com.driot.bookplayer.R.string.error_reading_backup) + e.getMessage());
        }

        backupBtn.setOnClickListener(v -> {
            boolean success = DatabaseBackupHelper.backupDatabase(this);
            Toast.makeText(this, success ? "Backup OK" : "Backup failed", Toast.LENGTH_SHORT).show();
        });

        restoreBtn.setOnClickListener(v -> {
            boolean success = DatabaseBackupHelper.restoreDatabase(this);
            Toast.makeText(this, success ? "Restore OK – please restart" : "Restore failed", Toast.LENGTH_LONG).show();
        });
    }
}
