package com.driot.bookplayer.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.DatabaseBackupHelper;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.utils.log.BaseActivity;

public class DebugDatabaseActivity extends BaseActivity {

    private static final String DEST = "Download/" + DatabaseBackupHelper.BACKUP_FOLDER_NAME + "/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug_database);
        InsetHelper.apply(this);

        ((TextView) findViewById(R.id.tvDbExplain)).setText(
                "Copies the live Room database (everything: books, tracks, progress, podcasts, episodes...)"
                        + " to " + DEST + " as a new timestamped .db file. Open it with any SQLite viewer."
                        + " Nothing in the app is changed.");
        ((TextView) findViewById(R.id.tvSnapshotExplain)).setText(
                "The small JSON safety net (progress, settings, favorites, podcast history) the app rewrites"
                        + " on its own at every periodic task, in its private files, for Android's Auto Backup /"
                        + " phone transfer. It is offered back once if the library is found empty after a"
                        + " reinstall. This writes it now and puts a readable copy in " + DEST + ".");

        Button dbBtn = findViewById(R.id.btnBackupDb);
        TextView dbStatus = findViewById(R.id.tvDbStatus);
        dbBtn.setOnClickListener(v -> runExport(dbBtn, dbStatus, true));

        Button snapshotBtn = findViewById(R.id.btnWriteAutoBackupSnapshot);
        TextView snapshotStatus = findViewById(R.id.tvSnapshotStatus);
        snapshotBtn.setOnClickListener(v -> runExport(snapshotBtn, snapshotStatus, false));
    }

    private void runExport(Button button, TextView status, boolean database) {
        button.setEnabled(false);
        AppDatabase.databaseWriteExecutor.execute(() -> {
            String name = database
                    ? DatabaseBackupHelper.exportDatabaseToDownloads(this)
                    : DatabaseBackupHelper.exportSnapshotToDownloads(this);
            runOnUiThread(() -> {
                button.setEnabled(true);
                status.setVisibility(View.VISIBLE);
                status.setText(name != null ? "✅ Exported: " + DEST + name : "❌ Export failed (see log)");
                Toast.makeText(this, name != null ? "Exported: " + name : "Export failed", Toast.LENGTH_SHORT).show();
            });
        });
    }
}
