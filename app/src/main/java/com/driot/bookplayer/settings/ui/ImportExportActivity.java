package com.driot.bookplayer.settings.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.utils.BackupManager;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.BaseActivity;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class ImportExportActivity extends BaseActivity {

    private BackupManager backupManager;

    private final ActivityResultLauncher<Intent> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    saveBackupToFile(result.getData().getData());
                }
            });

    private final ActivityResultLauncher<Intent> importLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    restoreBackupFromFile(result.getData().getData());
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_import_export);
        InsetHelper.apply(this);

        backupManager = new BackupManager(this);

        findViewById(R.id.btn_export).setOnClickListener(v -> {
            myLogI("--- user clicks EXPORT library ---");
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE,
                    "bookplayer_backup_" + Tonio.getCurrentDateTimeString() + ".json");
            exportLauncher.launch(intent);
        });

        findViewById(R.id.btn_import).setOnClickListener(v -> {
            myLogI("--- user clicks RESTORE library ---");
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            importLauncher.launch(intent);
        });
    }

    private void saveBackupToFile(Uri uri) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                String json = backupManager.exportToJson();
                try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
                    if (outputStream != null) {
                        outputStream.write(json.getBytes(StandardCharsets.UTF_8));
                        runOnUiThread(
                                () -> Toast.makeText(this, "Backup saved successfully", Toast.LENGTH_SHORT).show());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Failed to save backup: " + e.getMessage(), Toast.LENGTH_LONG)
                        .show());
            }
        });
    }

    private void restoreBackupFromFile(Uri uri) {
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream != null) {
                byte[] bytes = new byte[inputStream.available()];
                inputStream.read(bytes);
                String json = new String(bytes, StandardCharsets.UTF_8);
                backupManager.importFromJson(json);
                Toast.makeText(this,
                        "Restoration complete. Please restart the app if settings don't apply immediately.",
                        Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to restore backup: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
