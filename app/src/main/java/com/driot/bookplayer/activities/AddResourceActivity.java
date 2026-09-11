package com.driot.bookplayer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.ViewModelProvider;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.imports.ImportHelper;
import com.driot.bookplayer.imports.OngoingTaskUiState;
import com.driot.bookplayer.imports.OngoingTaskViewModel;
import com.driot.bookplayer.nav.FullActivity;
import com.driot.bookplayer.player.PlaybackUiBus;
import com.driot.bookplayer.player.PlaybackUiState;
import com.driot.bookplayer.services.DownloadControl;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class AddResourceActivity extends FullActivity {

    private static final int DELAY_END_WAIT_WARNINGS = 5 * 60_000;
    private static final int DELAY_END_WAIT_NO_ERROR = 1_000;

    private TextView tvTitle;
    private TextView progressBarText;
    private ProgressBar progressBar;
    private TextView tvErrorText, tvWarning;
    NestedScrollView warningScroll;

    private Button bPauseResume;
    private Button bCancel;

    private Handler delayedFinishHandler;
    private Runnable delayedFinishRunnable;

    private OngoingTaskViewModel viewModel; // keep reference
    private boolean didEnterExitMode = false;

    @Override
    protected int getNavSectionId() {
        return R.id.nav_add;
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_add_resource;
    }

    @Override
    protected boolean enableOngoingTaskOverlay() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        tvTitle = findViewById(R.id.tvTitle);
        progressBarText = findViewById(R.id.progressBarText);
        progressBar = findViewById(R.id.progressBar);
        tvErrorText = findViewById(R.id.errorText);
        tvWarning = findViewById(R.id.warningText);
        warningScroll = findViewById(R.id.warningScroll);

        bCancel = findViewById(R.id.bCancel);
        bCancel.setText(getString(android.R.string.cancel));
        bCancel.setOnClickListener(v -> performCancel());

        bPauseResume = findViewById(R.id.bPauseResume);
        bPauseResume.setOnClickListener(v -> performPauseOrResume());

        viewModel = new ViewModelProvider(this).get(OngoingTaskViewModel.class);

        // myLogD("ViewModel instance: " + System.identityHashCode(viewModel));

        viewModel.getUi().observe(this, ui -> {
            // myLog("observing UI state [" + ui.title + "] - showToUser=[" + ui.showToUser
            // + "] - result=[" + ui.result + "] - progressPercent=[" + ui.progressPercent +
            // "]");
            // Bind UI
            // Add counter if available (for mass import)
            String titleText = ui.title.isEmpty() ? getString(R.string.Import_in_progress) : ui.title;
            if (ui.currentPosition >= 0 && ui.totalCount > 0) {
                // Format: "5/12 Medee" - currentPosition is already 1-based
                titleText = ui.currentPosition + "/" + ui.totalCount + " " + titleText;
            }
            tvTitle.setText(titleText);
            progressBarText.setText(ui.progressText);
            progressBar.setProgress(ui.progressPercent);
            tvErrorText.setText(ui.errorText);
            tvWarning.setText(ui.warningText);
            warningScroll.post(() -> warningScroll.fullScroll(View.FOCUS_DOWN));

            bPauseResume.setVisibility(!didEnterExitMode && ui.pauseAvailable ? View.VISIBLE : View.GONE);
            if (ui.pauseAvailable) {
                bPauseResume.setText(getString(ui.paused ? R.string.Resume : R.string.Pause));
            }

            // When no longer running (FAILED / SUCCEEDED / CANCELLED), close flow once
            if (!didEnterExitMode && ui.isFinished()) {
                myLog("observing UI state => closing [" + ui.title + "] - showToUser=[" + ui.showToUser + "]");
                // Defer to end-of-frame to avoid re-entrancy with other observers
                getWindow().getDecorView().post(() -> checkAndClose(ui));
            }
        });
    }

    private void performPauseOrResume() {
        // Keep your existing service control; the VM/repo only reflects state.
        boolean isPausedNow = viewModel.getUi().getValue() != null && viewModel.getUi().getValue().paused;
        if (!isPausedNow) {
            myLogI("------ USER CLICKS btn PAUSE ----");
            DownloadControl.sendPause(this);
            // Button text will be updated by VM when repo sets paused=true
        } else {
            myLogI("------ USER CLICKS btn RESUME ----");
            DownloadControl.sendResume(this);
        }
    }

    private void performCancel() {
        myLogI("------ USER CLICKS btn CANCEL ----");
        DownloadControl.sendCancel(this);
        ImportHelper.cancelCurrentImport(this);
        ImportHelper.cancelAll_in_DB(this);
        enterExitMode();
    }

    private void checkAndClose(OngoingTaskUiState ui) {
        myLog("checkAndClose(result=" + ui.status + ")");

        enterExitMode(); // buttons → Exit mode

        // Failure => keep activity visible until user exits (or you can auto-close
        // later)
        if (Var.IMPORT_STATUS_FAILED.equals(ui.status)) {
            return;
        }

        // Finished with meaningful warnings => short display then close
        boolean hasWarn = ui.warningText != null && !ui.warningText.trim().isEmpty();
        if (hasWarn && !Var.IMPORT_STATUS_CANCELLED.equals(ui.status)) {
            bCancel.setText(getString(R.string.Exit));
            scheduleFinish(DELAY_END_WAIT_WARNINGS);
            return;
        }

        // Cancelled:
        if (Var.IMPORT_STATUS_CANCELLED.equals(ui.status)) {
            ImportHelper.setShowToUser(this, false);
            scheduleFinish(0);
            return;
        }

        // Other Cases (should only be SUCCESS) Briefly show, then hide banner. If we know which
        // book/track this job just created (single-file "Open with"/sibling-book imports only -
        // see ImportJob.getTargetPlaybackFileName), jump straight into playing it instead of just
        // returning to the library.
        if (ui.futureFolderPath != null) {
            schedulePlaybackJump(DELAY_END_WAIT_NO_ERROR, ui.futureFolderPath, ui.targetPlaybackFileName);
        } else {
            scheduleFinish(DELAY_END_WAIT_NO_ERROR);
        }
    }

    private void scheduleFinish(int delayMs) {
        delayedFinishHandler = new Handler();
        delayedFinishRunnable = () -> {
            // Now we can hide banners
            ImportHelper.setShowToUser(this, false);
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("scrollToTop", true));
            finish();
        };
        myLog("Let's wait " + delayMs + " ms before closing activity...");
        delayedFinishHandler.postDelayed(delayedFinishRunnable, delayMs);
    }

    private void schedulePlaybackJump(int delayMs, String futureFolderPath,
            @androidx.annotation.Nullable String targetPlaybackFileName) {
        delayedFinishHandler = new Handler();
        delayedFinishRunnable = () -> {
            ImportHelper.setShowToUser(this, false);

            // If this exact file was still live-previewing (see MiniPlayUnregisteredFragment /
            // Var.PLAY_MODE_PREVIEW) when the import finished, grab its current position now -
            // before the DB read below - so book playback can resume from there instead of
            // restarting at 0.
            PlaybackUiState previewState = PlaybackUiBus.get().state().getValue();
            boolean wasPreviewing = previewState != null && Var.PLAY_MODE_PREVIEW.equals(previewState.playMode);
            long previewPositionMs = wasPreviewing ? previewState.positionMs : -1;

            com.driot.bookplayer.db.AppDatabase.databaseReadExecutor.execute(() -> {
                com.driot.bookplayer.db.AppDatabase db = com.driot.bookplayer.db.AppDatabase.getDatabase(this);
                com.driot.bookplayer.db.Folder folder = db.folderDao().getFolderByPath(futureFolderPath);
                com.driot.bookplayer.db.ZikFile target = null;
                java.util.List<com.driot.bookplayer.db.ZikFile> files = null;
                if (folder != null) {
                    files = db.zikFileDao().getZikFiles(folder.getId());
                    if (files != null && !files.isEmpty()) {
                        if (targetPlaybackFileName != null) {
                            for (com.driot.bookplayer.db.ZikFile zf : files) {
                                if (targetPlaybackFileName.equalsIgnoreCase(zf.getName())) {
                                    target = zf;
                                    break;
                                }
                            }
                        }
                        if (target == null) {
                            target = files.get(0); // single-file import, or no exact match found
                        }
                    }
                }

                if (target != null && wasPreviewing && previewPositionMs > 0) {
                    myLog("Carrying over live-preview position (" + previewPositionMs
                            + "ms) to the just-imported track, instead of restarting from 0.");
                    target.setPosition(previewPositionMs);
                    db.zikFileDao().update(target);

                    // The [0, previewPositionMs) stretch was genuinely listened to already, just
                    // before this ZikFile row existed to attach a PlaySession to (preview mode is
                    // DB-free - see StartPlayHelper.playPreview()). Backfill it now so the
                    // heat-map/progress bar doesn't show that stretch as never-played. Only
                    // happens here, i.e. only once the track has actually been imported - a plain
                    // preview that's never imported has no ZikFile row to attach a session to and
                    // never reaches this method at all.
                    long now = System.currentTimeMillis();
                    com.driot.bookplayer.player.heatmaps.PlaySessionHelper.registerSession(
                            this, target.getId(), now - previewPositionMs, 0, now, previewPositionMs);
                }

                com.driot.bookplayer.db.ZikFile finalTarget = target;
                int trackCount = (files != null) ? files.size() : 0;
                runOnUiThread(() -> {
                    if (finalTarget != null) {
                        myLog("Jumping straight into playback for the just-imported track: " + finalTarget.getName());
                        // onZikFileClick() swaps cleanly from the preview stream engine to the book
                        // engine (MediaService.setEngine() stops/releases the old one first) and,
                        // now that we've persisted previewPositionMs above, resumes from there
                        // rather than 0 - MiniPlayHostFragment follows the playMode change and
                        // swaps itself from MiniPlayUnregisteredFragment to MiniPlayBookFragment
                        // automatically, no extra wiring needed here.
                        com.driot.bookplayer.player.StartPlayHelper.onZikFileClick(this, finalTarget,
                                "AddResourceActivity-openWithImportSuccess");
                        // There is no other app UI left in this task (it was launched externally
                        // via "Open with"), so without navigating somewhere the app would
                        // otherwise vanish to the home screen while audio plays in the background.
                        if (trackCount > 1) {
                            // Multi-track book: show the track list, not the single-track player.
                            startActivity(new Intent(this, ZikFileActivity.class)
                                    .putExtra(Intents.EXTRA_FOLDER_ID, finalTarget.getIdFolder())
                                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK));
                        } else {
                            // Single-file "book": no track list to show - land on the library,
                            // with the mini-player still visible/playing at the bottom.
                            startActivity(new Intent(this, MainActivity.class)
                                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK)
                                    .putExtra("scrollToTop", true));
                        }
                    } else {
                        myLogW("Could not resolve the just-imported folder/track - falling back to the library");
                        startActivity(new Intent(this, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK)
                                .putExtra("scrollToTop", true));
                    }
                    finish();
                });
            });
        };
        myLog("Let's wait " + delayMs + " ms before jumping to playback...");
        delayedFinishHandler.postDelayed(delayedFinishRunnable, delayMs);
    }

    private void enterExitMode() {
        myLog("enterExitMode");
        didEnterExitMode = true;
        navHelper.removeAddBookNavSpecial();
        bPauseResume.setVisibility(View.GONE);
        bCancel.setText(getString(R.string.Exit));
        bCancel.setOnClickListener(v -> {
            myLogI("------ USER CLICKS btn EXIT ----");
            ImportHelper.setShowToUser(this, false);
            if (delayedFinishHandler != null && delayedFinishRunnable != null) {
                delayedFinishHandler.removeCallbacks(delayedFinishRunnable);
            }
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra("scrollToTop", true));
            finish();
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (delayedFinishHandler != null && delayedFinishRunnable != null) {
            delayedFinishHandler.removeCallbacks(delayedFinishRunnable);
            myLog("Delayed finish runnable cancelled in onPause()");
        }
    }

}
