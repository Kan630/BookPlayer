package com.driot.bookplayer.importexport;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.MsgBoxActivity;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.utils.BackupManager;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.BaseActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class ImportExportActivity extends BaseActivity {

    public static final String EXTRA_MODE = "extra_mode";
    public static final int MODE_BACKUP = 0;
    public static final int MODE_RESTORE = 1;

    private int mode = MODE_BACKUP;
    private BackupManager backupManager;
    private Uri pickedRestoreUri;

    private final ActivityResultLauncher<Intent> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    boolean prefs = ((MaterialCheckBox) findViewById(R.id.cb_include_preferences)).isChecked();
                    boolean radios = ((MaterialCheckBox) findViewById(R.id.cb_include_radios)).isChecked();
                    boolean podcasts = ((MaterialCheckBox) findViewById(R.id.cb_include_podcasts)).isChecked();
                    boolean librivox = ((MaterialCheckBox) findViewById(R.id.cb_include_librivox)).isChecked();
                    saveBackupToFile(result.getData().getData(), prefs, radios, podcasts, librivox);
                }
            });

    private final ActivityResultLauncher<Intent> liveShareLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    String json = result.getData().getStringExtra(BackupShareActivity.RESULT_JSON);
                    if (json != null) {
                        myLog("Received backup JSON via Live Share, inspecting...");
                        inspectBackupJson(json);
                    }
                }
            });

    private final ActivityResultLauncher<Intent> pickFileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        myLog("Picked file for inspection: " + uri.toString());
                        inspectBackupFile(uri);
                    }
                }
            });

    private final ActivityResultLauncher<Intent> confirmationLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    int which = result.getData().getIntExtra(MsgBoxActivity.RESULT_WHICH,
                            MsgBoxActivity.WHICH_NEGATIVE);
                    if (which == MsgBoxActivity.WHICH_POSITIVE) {
                        executeRestore();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_import_export);
        InsetHelper.apply(this);

        backupManager = new BackupManager(this);
        mode = getIntent().getIntExtra(EXTRA_MODE, MODE_BACKUP);

        setupUI();
    }

    private void setupUI() {
        TextView tvTitle = findViewById(R.id.tv_title);
        TextView tvDesc = findViewById(R.id.tv_description);
        MaterialButton btnPick = findViewById(R.id.btn_pick_file);
        MaterialButton btnAction = findViewById(R.id.btn_action);
        View optionsContainer = findViewById(R.id.ll_options_container);
        TextView tvFooter = findViewById(R.id.tv_footer_note);

        if (mode == MODE_BACKUP) {
            tvTitle.setText("Backup Library");
            tvDesc.setText("Save your library and preferences to a backup file.");
            btnPick.setVisibility(View.GONE);
            optionsContainer.setVisibility(View.VISIBLE);
            btnAction.setText("Create Backup File");
            btnAction.setOnClickListener(v -> startExportFlow());
            findViewById(R.id.btn_share_live).setVisibility(View.VISIBLE);
            tvFooter.setVisibility(View.GONE);
        } else {
            tvTitle.setText("Restore Library");
            tvDesc.setText("Restore your library from a previously saved backup file.");
            btnPick.setVisibility(View.VISIBLE);
            btnPick.setOnClickListener(v -> launchFilePicker());
            optionsContainer.setVisibility(View.GONE); // Hidden until file is picked
            btnAction.setVisibility(View.GONE); // Hidden until file is picked
            btnAction.setOnClickListener(v -> startRestoreFlow());
            tvFooter.setVisibility(View.VISIBLE);
            tvFooter.setText("Note: Restoration will overwrite your current library.");

            findViewById(R.id.ll_restore_step1).setVisibility(View.VISIBLE);
            findViewById(R.id.btn_receive_live).setOnClickListener(v -> startLiveReceive());
        }

        findViewById(R.id.btn_share_live).setOnClickListener(v -> startLiveShare());
    }

    private void startLiveShare() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                boolean prefs = ((MaterialCheckBox) findViewById(R.id.cb_include_preferences)).isChecked();
                boolean radios = ((MaterialCheckBox) findViewById(R.id.cb_include_radios)).isChecked();
                boolean podcasts = ((MaterialCheckBox) findViewById(R.id.cb_include_podcasts)).isChecked();
                boolean librivox = ((MaterialCheckBox) findViewById(R.id.cb_include_librivox)).isChecked();

                String json = backupManager.exportToJson(prefs, radios, podcasts, librivox);
                runOnUiThread(() -> {
                    Intent intent = new Intent(this, BackupShareActivity.class);
                    intent.putExtra(BackupShareActivity.EXTRA_MODE, BackupShareActivity.MODE_SEND);
                    intent.putExtra(BackupShareActivity.EXTRA_BACKUP_JSON, json);
                    liveShareLauncher.launch(intent);
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast
                        .makeText(this, "Failed to prepare backup: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void startLiveReceive() {
        Intent intent = new Intent(this, BackupShareActivity.class);
        intent.putExtra(BackupShareActivity.EXTRA_MODE, BackupShareActivity.MODE_RECEIVE);
        liveShareLauncher.launch(intent);
    }

    private void launchFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = { "application/json", "application/octet-stream" };
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        pickFileLauncher.launch(intent);
    }

    private void inspectBackupFile(Uri uri) {
        pickedRestoreUri = uri;
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream != null) {
                byte[] bytes = new byte[inputStream.available()];
                inputStream.read(bytes);
                String json = new String(bytes, StandardCharsets.UTF_8);
                inspectBackupJson(json);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error reading file: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void inspectBackupJson(String json) {
        try {
            BackupManager.BackupData data = backupManager.inspectJson(json);
            if (data != null) {
                // In live mode, we don't have a URI to write back to,
                // but we might need the JSON string for the final executeRestore.
                // However, the current executeRestore reads from pickedRestoreUri.
                // Let's store the JSON string if we're in "Live" mode.
                this.liveBackupJson = json;
                updateRestoreOptions(data);
            } else {
                Toast.makeText(this, "Invalid backup data", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error parsing backup: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String liveBackupJson;

    private void updateRestoreOptions(BackupManager.BackupData data) {
        findViewById(R.id.ll_options_container).setVisibility(View.VISIBLE);
        findViewById(R.id.btn_action).setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.tv_options_prompt)).setText("Select data to restore from file:");

        MaterialCheckBox cbPrefs = findViewById(R.id.cb_include_preferences);
        MaterialCheckBox cbRadios = findViewById(R.id.cb_include_radios);
        MaterialCheckBox cbPodcasts = findViewById(R.id.cb_include_podcasts);
        MaterialCheckBox cbLibrivox = findViewById(R.id.cb_include_librivox);

        cbPrefs.setVisibility(data.preferences != null ? View.VISIBLE : View.GONE);
        cbRadios.setVisibility(data.radioStations != null ? View.VISIBLE : View.GONE);
        cbPodcasts.setVisibility((data.podcasts != null || data.episodes != null) ? View.VISIBLE : View.GONE);
        cbLibrivox.setVisibility(data.bookSources != null ? View.VISIBLE : View.GONE);

        // Uncheck segments that are not in the file
        if (data.preferences == null)
            cbPrefs.setChecked(false);
        if (data.radioStations == null)
            cbRadios.setChecked(false);
        if (data.podcasts == null && data.episodes == null)
            cbPodcasts.setChecked(false);
        if (data.bookSources == null)
            cbLibrivox.setChecked(false);

        ((MaterialButton) findViewById(R.id.btn_pick_file)).setText("Change Backup File");
    }

    private void startExportFlow() {
        boolean prefs = ((MaterialCheckBox) findViewById(R.id.cb_include_preferences)).isChecked();
        boolean radios = ((MaterialCheckBox) findViewById(R.id.cb_include_radios)).isChecked();
        boolean podcasts = ((MaterialCheckBox) findViewById(R.id.cb_include_podcasts)).isChecked();
        boolean librivox = ((MaterialCheckBox) findViewById(R.id.cb_include_librivox)).isChecked();

        if (!prefs && !radios && !podcasts && !librivox) {
            Toast.makeText(this, "Please select at least one item to export", Toast.LENGTH_SHORT).show();
            return;
        }

        myLogI("--- user clicks CREATE BACKUP --- (prefs=" + prefs + ", radios=" + radios + ", podcasts=" + podcasts
                + ", librivox=" + librivox + ")");
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE,
                "bookplayer_backup_" + Tonio.getCurrentDateTimeString() + ".json");
        exportLauncher.launch(intent);
    }

    private void startRestoreFlow() {
        boolean prefs = ((MaterialCheckBox) findViewById(R.id.cb_include_preferences)).isChecked();
        boolean radios = ((MaterialCheckBox) findViewById(R.id.cb_include_radios)).isChecked();
        boolean podcasts = ((MaterialCheckBox) findViewById(R.id.cb_include_podcasts)).isChecked();
        boolean librivox = ((MaterialCheckBox) findViewById(R.id.cb_include_librivox)).isChecked();

        if (!prefs && !radios && !podcasts && !librivox) {
            Toast.makeText(this, "Please select at least one item to restore", Toast.LENGTH_SHORT).show();
            return;
        }

        myLogI("--- user clicks RESTORE library (waiting for confirmation) ---");
        Intent intent = MsgBoxActivity.buildQuestion(this,
                "Caution: Overwrite Library?",
                "This action will replace your current library and settings with the selected data from the backup file. All current data for selected items will be PERMANENTLY DELETED.",
                "Proceed with caution!",
                "RESTORE NOW", "CANCEL");
        confirmationLauncher.launch(intent);
    }

    private void executeRestore() {
        boolean prefs = ((MaterialCheckBox) findViewById(R.id.cb_include_preferences)).isChecked();
        boolean radios = ((MaterialCheckBox) findViewById(R.id.cb_include_radios)).isChecked();
        boolean podcasts = ((MaterialCheckBox) findViewById(R.id.cb_include_podcasts)).isChecked();
        boolean librivox = ((MaterialCheckBox) findViewById(R.id.cb_include_librivox)).isChecked();

        if (liveBackupJson != null) {
            executeRestoreFromJson(liveBackupJson, prefs, radios, podcasts, librivox);
            return;
        }

        try (InputStream inputStream = getContentResolver().openInputStream(pickedRestoreUri)) {
            if (inputStream != null) {
                byte[] bytes = new byte[inputStream.available()];
                inputStream.read(bytes);
                String json = new String(bytes, StandardCharsets.UTF_8);
                executeRestoreFromJson(json, prefs, radios, podcasts, librivox);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to restore backup: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void executeRestoreFromJson(String json, boolean prefs, boolean radios, boolean podcasts,
            boolean librivox) {
        try {
            backupManager.importFromJson(json, prefs, radios, podcasts, librivox);
            Toast.makeText(this,
                    "Restoration complete. Please restart the app if settings don't apply immediately.",
                    Toast.LENGTH_LONG).show();
            finish();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed during import: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void saveBackupToFile(Uri uri, boolean prefs, boolean radios, boolean podcasts, boolean librivox) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                String json = backupManager.exportToJson(prefs, radios, podcasts, librivox);
                try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
                    if (outputStream != null) {
                        myLog("Backup saved to " + uri.toString());
                        outputStream.write(json.getBytes(StandardCharsets.UTF_8));
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Backup saved successfully", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Failed to save backup: " + e.getMessage(), Toast.LENGTH_LONG)
                        .show());
            }
        });
    }
}
