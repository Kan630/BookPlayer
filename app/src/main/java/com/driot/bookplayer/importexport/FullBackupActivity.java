package com.driot.bookplayer.importexport;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.MsgBoxActivity;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.BaseActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class FullBackupActivity extends BaseActivity {

    private FullBackupHelper.Estimate estimate;
    private long backupStartNanos;

    private TextView tvSizeNeeded, tvDuration, tvProgressText, tvEta, tvResult;
    private View llProgress;
    private MaterialButton btnStart, btnStartRestore;
    private ProgressBar progressBar;
    private MaterialButton btnCancelOperation;

    private View llRestoreReading, llRestorePreview;
    private TextView tvPreviewDate, tvPreviewSize, tvPreviewDuration, tvPreviewContents;
    private MaterialButton btnConfirmRestorePreview;

    // --- Backup scope (FULL vs PARTIAL) ---
    private MaterialSwitch switchBackupScope;
    private TextView tvBackupScopeExplain;
    private View llPartialOptions;
    private CheckBox cbPreferences, cbRadios, cbPodcasts, cbLibrivox, cbBookProgress, cbPodcastHistory;
    private CheckBox cbIncludeBookFiles;
    private View progressLoadingBooks, tvNoBooksAvailable;
    private RecyclerView rvBackupBooks;
    private final FullBackupHelper.BackupSelection selection = new FullBackupHelper.BackupSelection();
    // Loaded once (lazily, on first "Include book files" check) rather than re-queried on every
    // toggle - only the selection Set changes after that, which the estimate recompute reads
    // straight from `selection` itself.
    private List<FullBackupHelper.BookFileCandidate> bookCandidates;

    // Set once a restore zip has been picked, so the confirmation dialog's result (which carries
    // no data of its own) knows what to actually restore.
    private Uri pickedRestoreZipUri;

    // Whether a backup or restore is currently running on the background executor - drives the
    // back-press behavior below (cancel instead of leaving the screen) independently of which of
    // the two operations it is.
    private boolean operationInProgress = false;
    // Polled cooperatively by FullBackupHelper between/within files - see its javadoc for exactly
    // where cancellation is (and isn't) honored, especially during a restore.
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

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
        btnCancelOperation = findViewById(R.id.btn_cancel_full_backup_operation);
        btnCancelOperation.setOnClickListener(v -> requestCancel());

        switchBackupScope = findViewById(R.id.switch_backup_scope);
        tvBackupScopeExplain = findViewById(R.id.tv_backup_scope_explain);
        llPartialOptions = findViewById(R.id.ll_partial_options);
        cbPreferences = findViewById(R.id.cb_partial_preferences);
        cbRadios = findViewById(R.id.cb_partial_radios);
        cbPodcasts = findViewById(R.id.cb_partial_podcasts);
        cbLibrivox = findViewById(R.id.cb_partial_librivox);
        cbBookProgress = findViewById(R.id.cb_partial_book_progress);
        cbPodcastHistory = findViewById(R.id.cb_partial_podcast_history);
        cbIncludeBookFiles = findViewById(R.id.cb_partial_include_book_files);
        progressLoadingBooks = findViewById(R.id.progress_loading_books);
        tvNoBooksAvailable = findViewById(R.id.tv_no_books_available);
        rvBackupBooks = findViewById(R.id.rv_backup_books);
        rvBackupBooks.setLayoutManager(new LinearLayoutManager(this));

        // All 6 checked by default (matches the classic screen's own default), independent of
        // the book-files opt-in below.
        cbPreferences.setChecked(selection.includePreferences);
        cbRadios.setChecked(selection.includeRadios);
        cbPodcasts.setChecked(selection.includePodcasts);
        cbLibrivox.setChecked(selection.includeLibrivox);
        cbBookProgress.setChecked(selection.includeBookProgress);
        cbPodcastHistory.setChecked(selection.includePodcastHistory);
        if (Tonio.isPure(this)) {
            cbRadios.setVisibility(View.GONE);
            cbPodcasts.setVisibility(View.GONE);
            cbLibrivox.setVisibility(View.GONE);
            cbPodcastHistory.setVisibility(View.GONE);
        }

        CompoundButton.OnCheckedChangeListener categoryListener = (buttonView, isChecked) -> {
            selection.includePreferences = cbPreferences.isChecked();
            selection.includeRadios = cbRadios.isChecked();
            selection.includePodcasts = cbPodcasts.isChecked();
            selection.includeLibrivox = cbLibrivox.isChecked();
            selection.includeBookProgress = cbBookProgress.isChecked();
            selection.includePodcastHistory = cbPodcastHistory.isChecked();
            refreshEstimate();
        };
        cbPreferences.setOnCheckedChangeListener(categoryListener);
        cbRadios.setOnCheckedChangeListener(categoryListener);
        cbPodcasts.setOnCheckedChangeListener(categoryListener);
        cbLibrivox.setOnCheckedChangeListener(categoryListener);
        cbBookProgress.setOnCheckedChangeListener(categoryListener);
        cbPodcastHistory.setOnCheckedChangeListener(categoryListener);

        cbIncludeBookFiles.setOnCheckedChangeListener((buttonView, isChecked) -> {
            rvBackupBooks.setVisibility(View.GONE);
            tvNoBooksAvailable.setVisibility(View.GONE);
            if (isChecked) {
                loadBookCandidatesAndShowList();
            } else {
                // Starting over next time this is checked again, rather than leaving stale
                // selections the user can no longer see or change.
                selection.includedBookFileFolderIds.clear();
                refreshEstimate();
            }
        });

        switchBackupScope.setOnCheckedChangeListener((buttonView, isChecked) -> applyScopeMode());
        applyScopeMode(); // sets initial FULL-mode UI state and triggers the first estimate

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

        // A plain zip write/read has no partial-progress guarantee if we just finish() mid-way
        // (the executor isn't tied to this Activity, so it would silently keep running in the
        // background - fine for correctness since runOnUiThread() no-ops safely on a dead
        // Activity, but the user would have no way to know it's still going, or to actually stop
        // it). While an operation is running, back press cancels it instead of leaving.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (operationInProgress) {
                    requestCancel();
                } else {
                    finish();
                }
            }
        });

    }

    /** Shows/hides the checkbox block, flips the explain text, and refreshes the estimate for
     *  whichever mode the switch is now in. */
    private void applyScopeMode() {
        boolean isPartial = switchBackupScope.isChecked();
        llPartialOptions.setVisibility(isPartial ? View.VISIBLE : View.GONE);
        tvBackupScopeExplain.setText(
                isPartial ? R.string.backup_scope_partial_explain : R.string.backup_scope_full_explain);
        refreshEstimate();
    }

    private void loadBookCandidatesAndShowList() {
        if (bookCandidates != null) {
            showBookList();
            return;
        }
        progressLoadingBooks.setVisibility(View.VISIBLE);
        AppDatabase.databaseReadExecutor.execute(() -> {
            List<FullBackupHelper.BookFileCandidate> candidates = FullBackupHelper
                    .listBookFileCandidates(getApplicationContext());
            runOnUiThread(() -> {
                bookCandidates = candidates;
                progressLoadingBooks.setVisibility(View.GONE);
                // The checkbox may have been unchecked again while this was loading.
                if (cbIncludeBookFiles.isChecked()) {
                    showBookList();
                }
            });
        });
    }

    private void showBookList() {
        if (bookCandidates.isEmpty()) {
            tvNoBooksAvailable.setVisibility(View.VISIBLE);
            rvBackupBooks.setVisibility(View.GONE);
            return;
        }
        rvBackupBooks.setAdapter(new BackupBookListAdapter(bookCandidates, selection.includedBookFileFolderIds,
                this::refreshEstimate));
        rvBackupBooks.setVisibility(View.VISIBLE);
        tvNoBooksAvailable.setVisibility(View.GONE);
    }

    /** Recomputes and displays the size/duration estimate for whatever mode + selection is
     *  currently active. Cheap enough (a real exportToJson() call for partial, a folder-size scan
     *  for full) to just rerun on every relevant toggle rather than debounce. */
    private void refreshEstimate() {
        boolean isPartial = switchBackupScope.isChecked();
        AppDatabase.databaseReadExecutor.execute(() -> {
            if (isPartial) {
                // Snapshot the selection state read on the background thread, in case a checkbox
                // changes again mid-computation.
                FullBackupHelper.BackupSelection snapshot = new FullBackupHelper.BackupSelection();
                snapshot.includePreferences = selection.includePreferences;
                snapshot.includeRadios = selection.includeRadios;
                snapshot.includePodcasts = selection.includePodcasts;
                snapshot.includeLibrivox = selection.includeLibrivox;
                snapshot.includeBookProgress = selection.includeBookProgress;
                snapshot.includePodcastHistory = selection.includePodcastHistory;
                snapshot.includedBookFileFolderIds.addAll(selection.includedBookFileFolderIds);

                FullBackupHelper.PartialEstimate e = FullBackupHelper.computePartialEstimate(getApplicationContext(),
                        snapshot);
                runOnUiThread(() -> {
                    if (!switchBackupScope.isChecked()) {
                        return; // mode changed again before this finished
                    }
                    tvSizeNeeded
                            .setText(getString(R.string.full_backup_size_needed, Tonio.getReadableSize(e.totalBytes)));
                    tvDuration.setText(
                            getString(R.string.full_backup_audio_duration, Tonio.formatTime(e.totalAudioDurationMs)));
                });
            } else {
                FullBackupHelper.Estimate e = FullBackupHelper.computeEstimate(getApplicationContext());
                runOnUiThread(() -> {
                    if (switchBackupScope.isChecked()) {
                        return; // mode changed again before this finished
                    }
                    estimate = e;
                    tvSizeNeeded
                            .setText(getString(R.string.full_backup_size_needed, Tonio.getReadableSize(e.totalBytes)));
                    tvDuration.setText(
                            getString(R.string.full_backup_audio_duration, Tonio.formatTime(e.totalAudioDurationMs)));
                });
            }
        });
    }

    private void requestCancel() {
        if (!operationInProgress || cancelled.get()) {
            return;
        }
        cancelled.set(true);
        btnCancelOperation.setEnabled(false);
        myToast(getString(R.string.full_backup_cancelling));
    }

    private void startFullBackup(Uri destFileUri) {
        operationInProgress = true;
        cancelled.set(false);
        btnStart.setEnabled(false);
        btnCancelOperation.setEnabled(true);
        llProgress.setVisibility(View.VISIBLE);
        tvResult.setVisibility(View.GONE);
        tvEta.setText("");
        progressBar.setProgress(0);
        backupStartNanos = System.nanoTime();

        boolean isPartial = switchBackupScope.isChecked();
        FullBackupHelper.ProgressListener progressListener = (copiedBytes, totalBytes, fileName) -> {
            int percent = totalBytes > 0 ? (int) ((copiedBytes * 100) / totalBytes) : 0;
            runOnUiThread(() -> {
                progressBar.setProgress(percent);
                tvProgressText.setText(getString(R.string.full_backup_progress, fileName, percent,
                        Tonio.getReadableSize(copiedBytes), Tonio.getReadableSize(totalBytes)));

                // Live-measured, not guessed: the actual observed rate of this transfer so far is
                // a better estimate than a small pre-flight probe would have been, and it
                // naturally reflects whatever this destination's real throughput is (fast local
                // write vs. a slower network-backed one).
                double elapsedSec = (System.nanoTime() - backupStartNanos) / 1_000_000_000.0;
                if (elapsedSec > 1.0 && copiedBytes > 0 && totalBytes > copiedBytes) {
                    double bytesPerSec = copiedBytes / elapsedSec;
                    long remainingMs = (long) ((totalBytes - copiedBytes) / bytesPerSec * 1000.0);
                    tvEta.setText(getString(R.string.full_backup_eta, Tonio.formatTime(remainingMs)));
                }
            });
        };

        AppDatabase.databaseWriteExecutor.execute(() -> {
            FullBackupHelper.Result result;
            try {
                if (isPartial) {
                    result = FullBackupHelper.runPartialBackup(getApplicationContext(), destFileUri, selection,
                            cancelled, progressListener);
                } else {
                    result = FullBackupHelper.runFullBackup(getApplicationContext(), destFileUri, cancelled,
                            progressListener);
                }
            } catch (Exception e) {
                myLogEE(e, "runFullBackup failed");
                runOnUiThread(() -> {
                    operationInProgress = false;
                    llProgress.setVisibility(View.GONE);
                    tvResult.setVisibility(View.VISIBLE);
                    tvResult.setText(getString(R.string.full_backup_failed, e.getMessage()));
                    btnStart.setEnabled(true);
                });
                return;
            }

            FullBackupHelper.Result finalResult = result;
            runOnUiThread(() -> {
                operationInProgress = false;
                llProgress.setVisibility(View.GONE);
                tvResult.setVisibility(View.VISIBLE);
                tvResult.setText(backupResultTextResId(finalResult));
                btnStart.setEnabled(true);
            });
        });
    }

    private static int backupResultTextResId(FullBackupHelper.Result result) {
        switch (result) {
            case SUCCESS:
                return R.string.full_backup_success;
            case CANCELLED:
                return R.string.full_backup_cancelled;
            case PARTIAL_FAILURE:
                return R.string.full_backup_partial_failure;
            case FAILED:
            default:
                return R.string.full_backup_could_not_start;
        }
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
            FullBackupHelper.RestorePreview preview = FullBackupHelper.peekRestorePreview(getApplicationContext(),
                    srcZipUri);
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
        operationInProgress = true;
        cancelled.set(false);
        btnStart.setEnabled(false);
        btnStartRestore.setEnabled(false);
        btnCancelOperation.setEnabled(true);
        llRestorePreview.setVisibility(View.GONE);
        llProgress.setVisibility(View.VISIBLE);
        tvResult.setVisibility(View.GONE);
        tvEta.setText("");
        progressBar.setProgress(0);
        backupStartNanos = System.nanoTime();

        AppDatabase.databaseWriteExecutor.execute(() -> {
            FullBackupHelper.Result result;
            try {
                result = FullBackupHelper.runFullRestore(getApplicationContext(), srcZipUri, cancelled,
                        (copiedBytes, totalBytes, fileName) -> {
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
                    operationInProgress = false;
                    llProgress.setVisibility(View.GONE);
                    tvResult.setVisibility(View.VISIBLE);
                    tvResult.setText(getString(R.string.full_restore_failed, e.getMessage()));
                    btnStart.setEnabled(true);
                    btnStartRestore.setEnabled(true);
                });
                return;
            }

            FullBackupHelper.Result finalResult = result;
            runOnUiThread(() -> {
                operationInProgress = false;
                llProgress.setVisibility(View.GONE);
                tvResult.setVisibility(View.VISIBLE);
                tvResult.setText(restoreResultTextResId(finalResult));
                btnStart.setEnabled(true);
                btnStartRestore.setEnabled(true);
            });
        });
    }

    private static int restoreResultTextResId(FullBackupHelper.Result result) {
        switch (result) {
            case SUCCESS:
                return R.string.full_restore_success;
            case CANCELLED:
                return R.string.full_restore_cancelled;
            case PARTIAL_FAILURE:
                return R.string.full_restore_partial_failure;
            case FAILED:
            default:
                return R.string.full_restore_could_not_start;
        }
    }
}
