package com.driot.bookplayer.importexport;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.graphics.Typeface;
import androidx.core.content.FileProvider;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.MsgBoxActivity;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.db.BackupManager;
import com.driot.bookplayer.podcasts.PodcastHelper;
import com.driot.bookplayer.radio.RadioHelper;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.BaseActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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
                if (result.getResultCode() == Activity.RESULT_OK) {
                    String json = BackupShareActivity.sBackupPayload;
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
            tvTitle.setText(getString(R.string.import_export_btn_backup));
            tvDesc.setText(getString(R.string.import_export_backup_note));
            tvDesc.setTypeface(null, Typeface.ITALIC);
            btnPick.setVisibility(View.GONE);
            optionsContainer.setVisibility(View.VISIBLE);
            btnAction.setText(getString(R.string.import_export_btn_backup));
            btnAction.setIconResource(R.drawable.ic_download_action_24);
            btnAction.setOnClickListener(v -> {
                myLogI("--- user clicks CREATE BACKUP FILE ---");
                startExportFlow();
            });
            findViewById(R.id.btn_share_live).setVisibility(View.VISIBLE);
            View btnShareFile = findViewById(R.id.btn_share_file);
            btnShareFile.setVisibility(View.VISIBLE);
            btnShareFile.setOnClickListener(v -> {
                myLogI("--- user clicks SHARE BACKUP FILE (External) ---");
                startFileShare();
            });
            tvFooter.setVisibility(View.GONE);

            if (Tonio.isPure(this)) {
                findViewById(R.id.cb_include_radios).setVisibility(View.GONE);
                findViewById(R.id.cb_include_podcasts).setVisibility(View.GONE);
            }
        } else {
            tvTitle.setText(getString(R.string.import_export_btn_restore));
            tvDesc.setVisibility(View.GONE);
            btnPick.setVisibility(View.VISIBLE);
            btnPick.setOnClickListener(v -> {
                myLogI("--- user clicks PICK BACKUP FILE ---");
                launchFilePicker();
            });
            optionsContainer.setVisibility(View.GONE); // Hidden until file is picked
            btnAction.setVisibility(View.GONE); // Hidden until file is picked
            btnAction.setOnClickListener(v -> {
                myLogI("--- user clicks PROCEED WITH RESTORATION ---");
                startRestoreFlow();
            });
            tvFooter.setVisibility(View.VISIBLE);
            tvFooter.setText(getString(R.string.import_export_restore_footer));

            findViewById(R.id.ll_restore_step1).setVisibility(View.VISIBLE);
            findViewById(R.id.btn_receive_live).setOnClickListener(v -> {
                myLogI("--- user clicks RECEIVE LIVE ---");
                startLiveReceive();
            });
        }

        findViewById(R.id.btn_share_live).setOnClickListener(v -> {
            myLogI("--- user clicks SHARE LIVE ---");
            startLiveShare();
        });
    }

    private void startLiveShare() {
        boolean prefs = ((MaterialCheckBox) findViewById(R.id.cb_include_preferences)).isChecked();
        boolean pure = Tonio.isPure(this);
        boolean radios = !pure && ((MaterialCheckBox) findViewById(R.id.cb_include_radios)).isChecked();
        boolean podcasts = !pure && ((MaterialCheckBox) findViewById(R.id.cb_include_podcasts)).isChecked();
        boolean librivox = ((MaterialCheckBox) findViewById(R.id.cb_include_librivox)).isChecked();

        if (!prefs && !radios && !podcasts && !librivox) {
            myToast(getString(R.string.import_export_select_at_least_one_to_share));
            return;
        }

        myLogI("Preparing live share (prefs=" + prefs + ", radios=" + radios + ", podcasts=" + podcasts
                + ", librivox=" + librivox + ")");

        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                String json = backupManager.exportToJson(prefs, radios, podcasts, librivox);
                runOnUiThread(() -> {
                    myLog("Launching BackupShareActivity (SEND mode)");
                    BackupShareActivity.sBackupPayload = json;
                    Intent intent = new Intent(this, BackupShareActivity.class);
                    intent.putExtra(BackupShareActivity.EXTRA_MODE, BackupShareActivity.MODE_SEND);
                    liveShareLauncher.launch(intent);
                });
            } catch (Exception e) {
                myLogEE(e, "Failed to prepare live backup");
                runOnUiThread(() -> myLongToast(getString(R.string.import_export_failed_prepare, e.getMessage())));
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
            myLongToast(getString(R.string.import_export_error_reading, e.getMessage()));
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
                myToast(getString(R.string.import_export_invalid_data));
            }
        } catch (Exception e) {
            e.printStackTrace();
            myLongToast(getString(R.string.import_export_error_parsing, e.getMessage()));
        }
    }

    private String liveBackupJson;

    private void updateRestoreOptions(BackupManager.BackupData data) {
        findViewById(R.id.ll_restore_step1).setVisibility(View.GONE);

        TextView tvInfo = findViewById(R.id.tv_backup_info);
        tvInfo.setVisibility(View.VISIBLE);

        String dateStr = "Unknown date";
        if (data.timestamp > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
            dateStr = sdf.format(new Date(data.timestamp));
        }
        tvInfo.setText(getString(R.string.import_export_backup_content_from, dateStr));

        findViewById(R.id.ll_options_container).setVisibility(View.VISIBLE);
        MaterialButton btnAction = findViewById(R.id.btn_action);
        btnAction.setVisibility(View.VISIBLE);
        btnAction.setText(getString(R.string.import_export_btn_restore));
        btnAction.setIconResource(R.drawable.ic_check_24px);
        ((TextView) findViewById(R.id.tv_options_prompt))
                .setText(getString(R.string.import_export_select_data_restore));

        MaterialCheckBox cbPrefs = findViewById(R.id.cb_include_preferences);
        MaterialCheckBox cbRadios = findViewById(R.id.cb_include_radios);
        MaterialCheckBox cbPodcasts = findViewById(R.id.cb_include_podcasts);
        MaterialCheckBox cbLibrivox = findViewById(R.id.cb_include_librivox);

        boolean hasPrefs = data.preferences != null && !data.preferences.isEmpty();
        boolean hasRadios = RadioHelper.backupDataHasRadios(data);
        boolean hasPodcasts = PodcastHelper.backupDataHasPodcasts(data);
        boolean hasLibrivox = data.bookSources != null && !data.bookSources.isEmpty();

        cbPrefs.setEnabled(hasPrefs);
        cbPrefs.setChecked(hasPrefs);

        cbRadios.setEnabled(hasRadios);
        cbRadios.setChecked(hasRadios);

        cbPodcasts.setEnabled(hasPodcasts);
        cbPodcasts.setChecked(hasPodcasts);

        cbLibrivox.setEnabled(hasLibrivox);
        cbLibrivox.setChecked(hasLibrivox);

        if (Tonio.isPure(this)) {
            cbRadios.setVisibility(View.GONE);
            cbRadios.setChecked(false);
            cbPodcasts.setVisibility(View.GONE);
            cbPodcasts.setChecked(false);
        }
    }

    private void startExportFlow() {
        boolean prefs = ((MaterialCheckBox) findViewById(R.id.cb_include_preferences)).isChecked();
        boolean pure = Tonio.isPure(this);
        boolean radios = !pure && ((MaterialCheckBox) findViewById(R.id.cb_include_radios)).isChecked();
        boolean podcasts = !pure && ((MaterialCheckBox) findViewById(R.id.cb_include_podcasts)).isChecked();
        boolean librivox = ((MaterialCheckBox) findViewById(R.id.cb_include_librivox)).isChecked();

        if (!prefs && !radios && !podcasts && !librivox) {
            myToast(getString(R.string.import_export_select_at_least_one_to_backup));
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
        boolean pure = Tonio.isPure(this);
        boolean radios = !pure && ((MaterialCheckBox) findViewById(R.id.cb_include_radios)).isChecked();
        boolean podcasts = !pure && ((MaterialCheckBox) findViewById(R.id.cb_include_podcasts)).isChecked();
        boolean librivox = ((MaterialCheckBox) findViewById(R.id.cb_include_librivox)).isChecked();

        if (!prefs && !radios && !podcasts && !librivox) {
            myToast(getString(R.string.import_export_select_at_least_one_to_restore));
            return;
        }

        myLogI("--- user clicks RESTORE library (waiting for confirmation) ---");
        Intent intent = MsgBoxActivity.buildQuestion(this,
                getString(R.string.import_export_caution_title),
                getString(R.string.import_export_caution_desc),
                getString(R.string.import_export_caution_footer),
                getString(R.string.import_export_caution_positive), getString(R.string.import_export_caution_negative));
        confirmationLauncher.launch(intent);
    }

    private void executeRestore() {
        boolean prefs = ((MaterialCheckBox) findViewById(R.id.cb_include_preferences)).isChecked();
        boolean pure = Tonio.isPure(this);
        boolean radios = !pure && ((MaterialCheckBox) findViewById(R.id.cb_include_radios)).isChecked();
        boolean podcasts = !pure && ((MaterialCheckBox) findViewById(R.id.cb_include_podcasts)).isChecked();
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
            myLogEE(e, "Failed to restore backup from file");
            myLongToast(getString(R.string.import_export_failed_restore, e.getMessage()));
        }
    }

    private void executeRestoreFromJson(String json, boolean prefs, boolean radios, boolean podcasts,
            boolean librivox) {
        try {
            backupManager.importFromJson(json, prefs, radios, podcasts, librivox);
            myLongToast(getString(R.string.import_export_restore_complete));
            finish();
        } catch (Exception e) {
            e.printStackTrace();
            myLongToast(getString(R.string.import_export_failed_import, e.getMessage()));
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
                            myToast(getString(R.string.import_export_backup_saved));
                            finish();
                        });
                    }
                }
            } catch (Exception e) {
                myLogEE(e, "Failed to save backup to file");
                runOnUiThread(() -> myLongToast(getString(R.string.import_export_failed_prepare, e.getMessage())));
            }
        });
    }

    private void startFileShare() {
        boolean prefs = ((MaterialCheckBox) findViewById(R.id.cb_include_preferences)).isChecked();
        boolean pure = Tonio.isPure(this);
        boolean radios = !pure && ((MaterialCheckBox) findViewById(R.id.cb_include_radios)).isChecked();
        boolean podcasts = !pure && ((MaterialCheckBox) findViewById(R.id.cb_include_podcasts)).isChecked();
        boolean librivox = ((MaterialCheckBox) findViewById(R.id.cb_include_librivox)).isChecked();

        if (!prefs && !radios && !podcasts && !librivox) {
            myToast(getString(R.string.import_export_select_at_least_one_to_share));
            return;
        }

        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                String json = backupManager.exportToJson(prefs, radios, podcasts, librivox);
                File cacheDir = new File(getCacheDir(), "backups");
                if (!cacheDir.exists())
                    cacheDir.mkdirs();
                File file = new File(cacheDir, "bookplayer_backup_" + Tonio.getCurrentDateTimeString() + ".json");
                try (FileOutputStream out = new FileOutputStream(file)) {
                    out.write(json.getBytes(StandardCharsets.UTF_8));
                }

                Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".FileProvider", file);

                runOnUiThread(() -> {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("application/json");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(intent, getString(R.string.import_export_share_file)));
                });
            } catch (Exception e) {
                myLogEE(e, "Failed to share backup file");
                runOnUiThread(() -> myLongToast(getString(R.string.import_export_failed_share, e.getMessage())));
            }
        });
    }
}
