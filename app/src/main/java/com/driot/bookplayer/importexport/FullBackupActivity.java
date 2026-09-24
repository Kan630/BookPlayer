package com.driot.bookplayer.importexport;

import android.content.Intent;
import android.content.res.ColorStateList;
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
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.MsgBoxActivity;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.BaseActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class FullBackupActivity extends BaseActivity {

    public static final String EXTRA_MODE = "extra_mode";
    public static final int MODE_BACKUP = 0;
    public static final int MODE_RESTORE = 1;

    private FullBackupHelper.Estimate estimate;
    private long backupStartNanos;

    private TextView tvSizeNeeded, tvDuration, tvEbooksDuration, tvProgressText, tvEta, tvResult;
    private View llProgress;
    private MaterialButton btnStart, btnStartRestore;
    private ProgressBar progressBar;
    private MaterialButton btnCancelOperation;

    private View llRestoreReading, llRestorePreview;
    private TextView tvPreviewDate, tvPreviewSize, tvPreviewDuration, tvPreviewContents;
    private MaterialButton btnConfirmRestorePreview;

    // --- Backup scope (FULL vs PARTIAL) ---
    private MaterialButtonToggleGroup groupBackupScope;
    private MaterialButtonToggleGroup groupBackupDestination;
    private MaterialButtonToggleGroup groupRestoreSource;
    private MaterialButton btnDestQuickShare;
    private View tvBackupDestinationWarning;
    // Set right before starting a backup write, when the destination is Quick Share: the write
    // itself reuses startFullBackup()/startFullRestore() unchanged (a local cache file is just
    // another Uri to them), and on success this tells the completion callback to launch the send
    // screen with that file instead of just reporting "done".
    private boolean pendingQuickShareSend = false;
    private File pendingQuickShareFile;
    // Captured once at init from the button itself, so "un-warning" it means restoring these
    // exact values rather than guessing at whatever color the OutlinedButton style resolves to.
    private ColorStateList quickShareDefaultStroke, quickShareDefaultIconTint, quickShareDefaultTextColor;
    private long lastEstimatedBytes = 0;
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

    private final ActivityResultLauncher<Intent> quickShareSendLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                // Best-effort cleanup of the local temp copy regardless of how the send screen
                // was left (sent, cancelled, backed out of).
                if (pendingQuickShareFile != null) {
                    pendingQuickShareFile.delete();
                    pendingQuickShareFile = null;
                }
            });

    private final ActivityResultLauncher<Intent> quickShareReceiveLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String path = result.getData().getStringExtra(BackupQuickShareActivity.EXTRA_RECEIVED_FILE_PATH);
                    if (path != null) {
                        pickedRestoreZipUri = Uri.fromFile(new File(path));
                        startPeekPreview(pickedRestoreZipUri);
                    }
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_full_backup);
        InsetHelper.apply(this);

        // One screen, two entry points (Settings' Backup/Restore buttons): showing only the
        // relevant section keeps each feeling like its own focused screen without duplicating
        // the cancellation/progress/result plumbing shared by both operations.
        int mode = getIntent().getIntExtra(EXTRA_MODE, MODE_BACKUP);
        findViewById(R.id.ll_backup_section).setVisibility(mode == MODE_BACKUP ? View.VISIBLE : View.GONE);
        findViewById(R.id.ll_restore_section).setVisibility(mode == MODE_RESTORE ? View.VISIBLE : View.GONE);

        tvSizeNeeded = findViewById(R.id.tv_full_backup_size_needed);
        tvDuration = findViewById(R.id.tv_full_backup_duration);
        tvEbooksDuration = findViewById(R.id.tv_full_backup_ebooks_duration);
        tvProgressText = findViewById(R.id.tv_full_backup_progress_text);
        tvEta = findViewById(R.id.tv_full_backup_eta);
        tvResult = findViewById(R.id.tv_full_backup_result);
        llProgress = findViewById(R.id.ll_full_backup_progress);
        btnStart = findViewById(R.id.btn_start_full_backup);
        btnStartRestore = findViewById(R.id.btn_start_full_restore);
        progressBar = findViewById(R.id.progress_full_backup);
        btnCancelOperation = findViewById(R.id.btn_cancel_full_backup_operation);
        btnCancelOperation.setOnClickListener(v -> requestCancel());

        groupBackupScope = findViewById(R.id.group_backup_scope);
        groupBackupDestination = findViewById(R.id.group_backup_destination);
        btnDestQuickShare = findViewById(R.id.btn_dest_quick_share);
        tvBackupDestinationWarning = findViewById(R.id.tv_backup_destination_warning);
        quickShareDefaultStroke = btnDestQuickShare.getStrokeColor();
        quickShareDefaultIconTint = btnDestQuickShare.getIconTint();
        quickShareDefaultTextColor = btnDestQuickShare.getTextColors();
        groupBackupDestination.check(R.id.btn_dest_file);
        groupBackupDestination.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                applyDestinationWarning(lastEstimatedBytes);
            }
        });
        tvBackupScopeExplain = findViewById(R.id.tv_backup_scope_explain);
        llPartialOptions = findViewById(R.id.ll_partial_options);
        cbPreferences = findViewById(R.id.cb_partial_preferences);
        cbRadios = findViewById(R.id.cb_partial_radios);
        cbPodcasts = findViewById(R.id.cb_partial_podcasts);
        cbLibrivox = findViewById(R.id.cb_partial_librivox);
        cbBookProgress = findViewById(R.id.cb_partial_book_progress);
        cbPodcastHistory = findViewById(R.id.cb_partial_podcast_history);
        cbIncludeBookFiles = findViewById(R.id.cb_partial_include_book_files);
        CheckBox cbSkipRedownloadable = findViewById(R.id.cb_skip_redownloadable);
        cbSkipRedownloadable.setOnCheckedChangeListener((buttonView, isChecked) -> {
            selection.skipRedownloadable = isChecked;
            refreshEstimate();
        });
        // Nothing to skip without a removable SD card in the device.
        findViewById(R.id.ll_skip_sdcard).setVisibility(
                StorageHelper.isExternalSDCardAvailable(this) ? View.VISIBLE : View.GONE);
        CheckBox cbSkipSdCard = findViewById(R.id.cb_skip_sdcard);
        cbSkipSdCard.setOnCheckedChangeListener((buttonView, isChecked) -> {
            selection.skipSdCard = isChecked;
            refreshEstimate();
        });
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

        groupBackupScope.check(R.id.btn_scope_full);
        groupBackupScope.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                applyScopeMode();
            }
        });
        applyScopeMode(); // sets initial FULL-mode UI state and triggers the first estimate

        groupRestoreSource = findViewById(R.id.group_restore_source);
        groupRestoreSource.check(R.id.btn_restore_src_file);

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
            String fileName = "BookPlayerFullBackup_" + Tonio.getCurrentDateTimeString() + ".zip";
            if (groupBackupDestination.getCheckedButtonId() == R.id.btn_dest_quick_share) {
                // Same write path either way (startFullBackup() doesn't care whether the Uri is
                // SAF-picked or a local cache file) - only the destination differs, and only
                // after a successful write does this one also launch the send screen.
                File cacheDir = new File(getCacheDir(), "quick_share_out");
                cacheDir.mkdirs();
                pendingQuickShareFile = new File(cacheDir, fileName);
                pendingQuickShareSend = true;
                startFullBackup(Uri.fromFile(pendingQuickShareFile));
                return;
            }
            pendingQuickShareSend = false;
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/zip");
            intent.putExtra(Intent.EXTRA_TITLE, fileName);
            createDocumentLauncher.launch(intent);
        });

        btnStartRestore.setOnClickListener(v -> {
            if (groupRestoreSource.getCheckedButtonId() == R.id.btn_restore_src_quick_share) {
                Intent intent = new Intent(this, BackupQuickShareActivity.class);
                intent.putExtra(BackupQuickShareActivity.EXTRA_MODE, BackupQuickShareActivity.MODE_RECEIVE);
                quickShareReceiveLauncher.launch(intent);
                return;
            }
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

    private boolean isPartialScope() {
        return groupBackupScope.getCheckedButtonId() == R.id.btn_scope_partial;
    }

    /** Shows/hides the checkbox block, flips the explain text, and refreshes the estimate for
     *  whichever mode the switch is now in. */
    private void applyScopeMode() {
        boolean isPartial = isPartialScope();
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
        rvBackupBooks.setVisibility(View.VISIBLE);
        rvBackupBooks.setAdapter(new BackupBookListAdapter(bookCandidates, selection.includedBookFileFolderIds,
                this::refreshEstimate));
        tvNoBooksAvailable.setVisibility(View.GONE);
    }

    /** Recomputes and displays the size/duration estimate for whatever mode + selection is
     *  currently active. Cheap enough (a real exportToJson() call for partial, a folder-size scan
     *  for full) to just rerun on every relevant toggle rather than debounce. */
    // Each call gets its own id, and only the latest one is allowed to actually update the UI -
    // without this, two computations in flight at once (e.g. two checkbox taps in quick
    // succession) could finish out of order on AppDatabase.databaseReadExecutor (it's a pool,
    // not a single thread) and silently leave a STALE result on screen even though it was for an
    // earlier, now-superseded selection. The old guards here only checked "is the mode still the
    // same", which didn't catch two same-mode requests racing each other.
    private final java.util.concurrent.atomic.AtomicLong estimateRequestId = new java.util.concurrent.atomic.AtomicLong();

    private void refreshEstimate() {
        long requestId = estimateRequestId.incrementAndGet();
        // Old numbers would be misleading while the new ones are being computed.
        tvSizeNeeded.setText(getString(R.string.full_backup_size_needed, "..."));
        tvDuration.setText(getString(R.string.full_backup_audio_duration, "..."));
        if (tvEbooksDuration.getVisibility() == View.VISIBLE) {
            tvEbooksDuration.setText(getString(R.string.full_backup_ebooks_duration, "..."));
        }
        boolean isPartial = isPartialScope();
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
                snapshot.skipRedownloadable = selection.skipRedownloadable;
                snapshot.skipSdCard = selection.skipSdCard;

                FullBackupHelper.PartialEstimate e = FullBackupHelper.computePartialEstimate(getApplicationContext(),
                        snapshot);
                runOnUiThread(() -> {
                    if (requestId != estimateRequestId.get()) {
                        return; // superseded by a newer request - don't overwrite its result
                    }
                    tvSizeNeeded
                            .setText(getString(R.string.full_backup_size_needed, Tonio.getReadableSize(e.totalBytes)));
                    showDurations(e.totalAudioDurationMs, e.totalEbookDurationMs);
                    applyDestinationWarning(e.totalBytes);
                });
            } else {
                FullBackupHelper.Estimate e = FullBackupHelper.computeEstimate(getApplicationContext(),
                        selection.skipRedownloadable, selection.skipSdCard);
                runOnUiThread(() -> {
                    if (requestId != estimateRequestId.get()) {
                        return; // superseded by a newer request - don't overwrite its result
                    }
                    estimate = e;
                    tvSizeNeeded
                            .setText(getString(R.string.full_backup_size_needed, Tonio.getReadableSize(e.totalBytes)));
                    showDurations(e.totalAudioDurationMs, e.totalEbookDurationMs);
                    applyDestinationWarning(e.totalBytes);
                });
            }
        });
    }

    /** The ebooks line only exists when there are ebooks read aloud by text-to-speech. */
    private void showDurations(long audioMs, long ebookMs) {
        // formatTime() gives an empty string for zero, which would leave "Total audio: " blank.
        tvDuration.setText(getString(R.string.full_backup_audio_duration,
                audioMs > 0 ? Tonio.formatTime(audioMs) : "-"));
        if (ebookMs > 0) {
            tvEbooksDuration.setText(getString(R.string.full_backup_ebooks_duration, Tonio.formatTime(ebookMs)));
            tvEbooksDuration.setVisibility(View.VISIBLE);
        } else {
            tvEbooksDuration.setVisibility(View.GONE);
        }
    }

    // Above this, a P2P transfer (once Quick Share for backups is wired up) risks being slow or
    // dropping mid-transfer - not a hard limit, just a heads-up. Tune freely; nothing else reads
    // this constant.
    private static final long QUICK_SHARE_SIZE_WARNING_THRESHOLD_BYTES = 50L * 1024 * 1024;

    /** Colors the Quick Share option (and shows an explanatory line) once the current estimate
     *  crosses the threshold above - but only while Quick Share is actually the selected
     *  destination. Save to Zip File has no such risk (local/SAF write doesn't care about size
     *  the way a P2P transfer does), so flagging it too would just look alarming for no reason.
     *  "Un-warning" restores the exact stroke/icon/text colors captured from the button in
     *  onCreate, rather than guessing at what the OutlinedButton style would otherwise resolve
     *  to. */
    private void applyDestinationWarning(long totalBytes) {
        lastEstimatedBytes = totalBytes;
        boolean quickShareSelected = groupBackupDestination.getCheckedButtonId() == R.id.btn_dest_quick_share;
        boolean warn = quickShareSelected && totalBytes > QUICK_SHARE_SIZE_WARNING_THRESHOLD_BYTES;
        if (warn) {
            ColorStateList orange = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.orange_700));
            btnDestQuickShare.setStrokeColor(orange);
            btnDestQuickShare.setIconTint(orange);
            btnDestQuickShare.setTextColor(orange);
        } else {
            btnDestQuickShare.setStrokeColor(quickShareDefaultStroke);
            btnDestQuickShare.setIconTint(quickShareDefaultIconTint);
            btnDestQuickShare.setTextColor(quickShareDefaultTextColor);
        }
        tvBackupDestinationWarning.setVisibility(warn ? View.VISIBLE : View.GONE);
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

        boolean isPartial = isPartialScope();
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
                            progressListener, selection.skipRedownloadable, selection.skipSdCard);
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
                btnStart.setEnabled(true);

                if (pendingQuickShareSend) {
                    pendingQuickShareSend = false;
                    if (finalResult == FullBackupHelper.Result.SUCCESS && pendingQuickShareFile != null) {
                        // Skip the plain "backup complete" text here - the send screen itself
                        // reports what happens next, and quickShareSendLauncher's callback cleans
                        // up the temp file once that screen is done with it either way.
                        Intent intent = new Intent(this, BackupQuickShareActivity.class);
                        intent.putExtra(BackupQuickShareActivity.EXTRA_MODE, BackupQuickShareActivity.MODE_SEND);
                        intent.putExtra(BackupQuickShareActivity.EXTRA_FILE_PATH,
                                pendingQuickShareFile.getAbsolutePath());
                        intent.putExtra(BackupQuickShareActivity.EXTRA_DISPLAY_NAME, pendingQuickShareFile.getName());
                        quickShareSendLauncher.launch(intent);
                        return;
                    }
                    // Writing the local copy itself failed/was cancelled - nothing to send, clean
                    // up and fall through to the normal result text below.
                    if (pendingQuickShareFile != null) {
                        pendingQuickShareFile.delete();
                        pendingQuickShareFile = null;
                    }
                }

                tvResult.setVisibility(View.VISIBLE);
                tvResult.setText(backupResultTextResId(finalResult));
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
                TextView tvPreviewEbooks = findViewById(R.id.tv_restore_preview_ebooks);
                if (preview.totalEbookDurationMs > 0) {
                    tvPreviewEbooks.setText(getString(R.string.full_backup_ebooks_duration,
                            Tonio.formatTime(preview.totalEbookDurationMs)));
                    tvPreviewEbooks.setVisibility(View.VISIBLE);
                } else {
                    tvPreviewEbooks.setVisibility(View.GONE);
                }

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
            // Books the backup left out because they can be downloaded again: offer to fetch them.
            List<Long> missingFolderIds = new java.util.ArrayList<>();
            if (finalResult == FullBackupHelper.Result.SUCCESS
                    || finalResult == FullBackupHelper.Result.PARTIAL_FAILURE) {
                try {
                    missingFolderIds = com.driot.bookplayer.redownload.RedownloadHelper
                            .findFoldersToRedownload(getApplicationContext());
                } catch (Exception e) {
                    myLogEE(e, "findFoldersToRedownload failed");
                }
            }
            final List<Long> toDownload = missingFolderIds;
            runOnUiThread(() -> {
                operationInProgress = false;
                llProgress.setVisibility(View.GONE);
                tvResult.setVisibility(View.VISIBLE);
                tvResult.setText(restoreResultTextResId(finalResult));
                btnStart.setEnabled(true);
                btnStartRestore.setEnabled(true);
                MaterialButton btnRedownload = findViewById(R.id.btn_redownload_missing);
                if (!toDownload.isEmpty()) {
                    btnRedownload.setText(getString(R.string.redownload_missing_books_button, toDownload.size()));
                    btnRedownload.setVisibility(View.VISIBLE);
                    btnRedownload.setOnClickListener(v -> {
                        for (long id : toDownload) {
                            com.driot.bookplayer.redownload.RedownloadHelper.start(getApplicationContext(), id);
                        }
                        btnRedownload.setEnabled(false);
                        myToast(getString(R.string.redownload_started));
                    });
                } else {
                    btnRedownload.setVisibility(View.GONE);
                }
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
