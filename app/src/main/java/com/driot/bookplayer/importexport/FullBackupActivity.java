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
    private MaterialButton btnStart;
    private ProgressBar progressBar;

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
        progressBar = findViewById(R.id.progress_full_backup);

        btnStart.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/zip");
            intent.putExtra(Intent.EXTRA_TITLE, "BookPlayerFullBackup_" + Tonio.getCurrentDateTimeString() + ".zip");
            createDocumentLauncher.launch(intent);
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
}
