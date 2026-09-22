package com.driot.bookplayer.importexport;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.MsgBoxActivity;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.BaseActivity;
import com.google.android.material.button.MaterialButton;

public class FullBackupActivity extends BaseActivity {

    private FullBackupHelper.Estimate estimate;
    private long backupStartNanos;

    private TextView tvSizeNeeded, tvDuration, tvProgressText, tvEta, tvResult;
    private View llProgress;
    private MaterialButton btnStart, btnStartRestore;
    private ProgressBar progressBar;

    private View llRestoreReading, llRestorePreview;
    private TextView tvPreviewDate, tvPreviewSize, tvPreviewDuration, tvPreviewContents;
    private MaterialButton btnConfirmRestorePreview;

    // Set once a restore zip has been picked, so the confirmation dialog's result (which carries
    // no data of its own) knows what to actually restore.
    private Uri pickedRestoreZipUri;

    private final ActivityResultLauncher<Intent> createDocumentLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null
                        && result.getData().getData() != null) {
                    startFullBackup(result.getData().getData());
                }
                // Cancelled - matches the classic backup screen's behavior of simply doing
                // nothing when the user backs out of the picker.
            });

    private final ActivityResultLauncher<Intent> pickRestoreZipLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null
                        && result.getData().getData() != null) {
                    pickedRestoreZipUri = result.getData().getData();
                    startPeekPreview(pickedRestoreZipUri);
                }
            });

    private final ActivityResultLauncher<Intent> restoreConfirmationLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    int which = result.getData().getIntExtra(MsgBoxActivity.RESULT_WHICH,
                            MsgBoxActivity.WHICH_NEGATIVE);
                    if (which == MsgBoxActivity.WHICH_POSITIVE && pickedRestoreZipUri != null) {
                        startFullRestore(pickedRestoreZipUri);
                    }
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_full_backup);
        InsetHelper.apply(this);

        tvSizeNeeded = findViewById(R.id.tv_full_backup_size_needed);
        tvDuration = findViewById(R.id.tv_full_backup_duration);
        tvProgressText = findViewById(R.id.tv_full_backup_progress_text);
        tvEta = findViewById(R.id.tv_full_backup_eta);
        tvResult = findViewById(R.id.tv_full_backup_result);
        llProgress = findViewById(R.id.ll_full_backup_progress);
        btnStart = findViewById(R.id.btn_start_full_backup);
        btnStartRestore = findViewById(R.id.btn_start_full_restore);
        progressBar = findViewById(R.id.progress_full_backup);

        llRestoreReading = findViewById(R.id.ll_restore_reading);
        llRestorePreview = findViewById(R.id.ll_restore_preview);
        tvPreviewDate = findViewById(R.id.tv_restore_preview_date);
        tvPreviewSize = findViewById(R.id.tv_restore_preview_size);
        tvPreviewDuration = findViewById(R.id.tv_restore_preview_duration);
        tvPreviewContents = findViewById(R.id.tv_restore_preview_contents);
        btnConfirmRestorePreview = findViewById(R.id.btn_confirm_restore_preview);

        btnConfirmRestorePreview.setOnClickListener(v -> confirmAndStartFullRestore());
        findViewById(R.id.btn_cancel_restore_preview).setOnClickListener(v -> {
            pickedRestoreZipUri = null;
            llRestorePreview.setVisibility(View.GONE);
        });

        btnStart.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/zip");
            intent.putExtra(Intent.EXTRA_TITLE, "BookPlayerFullBackup_" + Tonio.getCurrentDateTimeString() + ".zip");
            createDocumentLauncher.launch(intent);
        });

        btnStartRestore.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/zip");
            // Some file providers don't tag a zip as application/zip - accept generic binary too.
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] { "application/zip", "application/octet-stream" });
            pickRestoreZipLauncher.launch(intent);
        });

        AppDatabase.databaseReadExecutor.execute(() -> {
            FullBackupHelper.Estimate e = FullBackupHelper.computeEstimate(this);
            runOnUiThread(() -> {
                estimate = e;
                tvSizeNeeded.setText(getString(R.string.full_backup_size_needed, Tonio.getReadableSize(e.totalBytes)));
                tvDuration
                        .setText(getString(R.string.full_backup_audio_duration, Tonio.formatTime(e.totalAudioDurationMs)));
            });
        });
    }

    private void startFullBackup(Uri destFileUri) {
        btnStart.setEnabled(false);
        llProgress.setVisibility(View.VISIBLE);
        tvResult.setVisibility(View.GONE);
        tvEta.setText("");
        progressBar.setProgress(0);
        backupStartNanos = System.nanoTime();

        AppDatabase.databaseWriteExecutor.execute(() -> {
            boolean success;
            try {
                success = FullBackupHelper.runFullBackup(this, destFileUri, (copiedBytes, totalBytes, fileName) -> {
                    int percent = totalBytes > 0 ? (int) ((copiedBytes * 100) / totalBytes) : 0;
                    runOnUiThread(() -> {
                        progressBar.setProgress(percent);
                        tvProgressText.setText(getString(R.string.full_backup_progress, fileName, percent,
                                Tonio.getReadableSize(copiedBytes), Tonio.getReadableSize(totalBytes)));

                        // Live-measured, not guessed: the actual observed rate of this transfer
                        // so far is a better estimate than a small pre-flight probe would have
                        // been, and it naturally reflects whatever this destination's real
                        // throughput is (fast local write vs. a slower network-backed one).
                        double elapsedSec = (System.nanoTime() - backupStartNanos) / 1_000_000_000.0;
                        if (elapsedSec > 1.0 && copiedBytes > 0 && totalBytes > copiedBytes) {
                            double bytesPerSec = copiedBytes / elapsedSec;
                            long remainingMs = (long) ((totalBytes - copiedBytes) / bytesPerSec * 1000.0);
                            tvEta.setText(getString(R.string.full_backup_eta, Tonio.formatTime(remainingMs)));
                        }
                    });
                });
            } catch (Exception e) {
                myLogEE(e, "runFullBackup failed");
                runOnUiThread(() -> {
                    tvResult.setVisibility(View.VISIBLE);
                    tvResult.setText(getString(R.string.full_backup_failed, e.getMessage()));
                    btnStart.setEnabled(true);
                });
                return;
            }

            boolean finalSuccess = success;
            runOnUiThread(() -> {
                tvResult.setVisibility(View.VISIBLE);
                tvResult.setText(finalSuccess ? R.string.full_backup_success : R.string.full_backup_partial_failure);
                btnStart.setEnabled(true);
            });
        });
    }

    /** Reads the picked zip's own contents (size, audio duration, date, category counts) and
     *  shows them before the caution popup, so the user knows what they're about to overwrite
     *  their library with - not just "trust me". */
    private void startPeekPreview(Uri srcZipUri) {
        tvResult.setVisibility(View.GONE);
        llRestorePreview.setVisibility(View.GONE);
        llRestoreReading.setVisibility(View.VISIBLE);
        btnStart.setEnabled(false);
        btnStartRestore.setEnabled(false);

        AppDatabase.databaseReadExecutor.execute(() -> {
            FullBackupHelper.RestorePreview preview = FullBackupHelper.peekRestorePreview(this, srcZipUri);
            runOnUiThread(() -> {
                llRestoreReading.setVisibility(View.GONE);
                btnStart.setEnabled(true);
                btnStartRestore.setEnabled(true);

                if (!preview.valid) {
                    pickedRestoreZipUri = null;
                    tvResult.setVisibility(View.VISIBLE);
                    tvResult.setText(R.string.full_restore_preview_invalid);
                    return;
                }

                String dateStr = preview.timestamp > 0
                        ? new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
                                .format(new java.util.Date(preview.timestamp))
                        : "?";
                tvPreviewDate.setText(getString(R.string.full_restore_preview_date, dateStr));
                tvPreviewSize.setText(getString(R.string.full_restore_preview_size,
                        Tonio.getReadableSize(preview.zipTotalBytes)));
                tvPreviewDuration.setText(
                        getString(R.string.full_backup_audio_duration, Tonio.formatTime(preview.totalAudioDurationMs)));

                java.util.List<String> parts = new java.util.ArrayList<>();
                if (preview.bookCount > 0)
                    parts.add(getString(R.string.full_restore_preview_count_books, preview.bookCount));
                if (preview.zikFileCount > 0)
                    parts.add(getString(R.string.full_restore_preview_count_zikfiles, preview.zikFileCount));
                if (preview.librivoxSourceCount > 0)
                    parts.add(getString(R.string.full_restore_preview_count_librivox, preview.librivoxSourceCount));
                if (preview.radioCount > 0)
                    parts.add(getString(R.string.full_restore_preview_count_radios, preview.radioCount));
                if (preview.podcastCount > 0)
                    parts.add(getString(R.string.full_restore_preview_count_podcasts, preview.podcastCount));
                if (preview.podcastHistoryCount > 0)
                    parts.add(getString(R.string.full_restore_preview_count_podcast_history,
                            preview.podcastHistoryCount));
                if (preview.hasPreferences)
                    parts.add(getString(R.string.full_restore_preview_preferences));
                tvPreviewContents.setText(android.text.TextUtils.join("\n", parts));

                llRestorePreview.setVisibility(View.VISIBLE);
            });
        });
    }

    private void confirmAndStartFullRestore() {
        // Reuses the classic restore screen's own "this overwrites everything" warning - a Full
        // Backup restore is exactly that, just with the actual files included too.
        Intent intent = MsgBoxActivity.buildQuestion(this,
                getString(R.string.import_export_caution_title),
                getString(R.string.import_export_caution_desc),
                getString(R.string.import_export_caution_footer),
                getString(R.string.import_export_caution_positive), getString(R.string.import_export_caution_negative));
        restoreConfirmationLauncher.launch(intent);
    }

    private void startFullRestore(Uri srcZipUri) {
        btnStart.setEnabled(false);
        btnStartRestore.setEnabled(false);
        llRestorePreview.setVisibility(View.GONE);
        llProgress.setVisibility(View.VISIBLE);
        tvResult.setVisibility(View.GONE);
        tvEta.setText("");
        progressBar.setProgress(0);
        backupStartNanos = System.nanoTime();

        AppDatabase.databaseWriteExecutor.execute(() -> {
            boolean success;
            try {
                success = FullBackupHelper.runFullRestore(this, srcZipUri, (copiedBytes, totalBytes, fileName) -> {
                    int percent = totalBytes > 0 ? (int) ((copiedBytes * 100) / totalBytes) : 0;
                    runOnUiThread(() -> {
                        progressBar.setProgress(percent);
                        tvProgressText.setText(getString(R.string.full_backup_progress, fileName, percent,
                                Tonio.getReadableSize(copiedBytes), Tonio.getReadableSize(totalBytes)));

                        double elapsedSec = (System.nanoTime() - backupStartNanos) / 1_000_000_000.0;
                        if (elapsedSec > 1.0 && copiedBytes > 0 && totalBytes > copiedBytes) {
                            double bytesPerSec = copiedBytes / elapsedSec;
                            long remainingMs = (long) ((totalBytes - copiedBytes) / bytesPerSec * 1000.0);
                            tvEta.setText(getString(R.string.full_backup_eta, Tonio.formatTime(remainingMs)));
                        }
                    });
                });
            } catch (Exception e) {
                myLogEE(e, "runFullRestore failed");
                runOnUiThread(() -> {
                    tvResult.setVisibility(View.VISIBLE);
                    tvResult.setText(getString(R.string.full_restore_failed, e.getMessage()));
                    btnStart.setEnabled(true);
                    btnStartRestore.setEnabled(true);
                });
                return;
            }

            boolean finalSuccess = success;
            runOnUiThread(() -> {
                tvResult.setVisibility(View.VISIBLE);
                tvResult.setText(
                        finalSuccess ? R.string.full_restore_success : R.string.full_restore_partial_failure);
                btnStart.setEnabled(true);
                btnStartRestore.setEnabled(true);
            });
        });
    }
}
