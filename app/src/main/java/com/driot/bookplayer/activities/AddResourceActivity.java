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
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.imports.ImportHelper;
import com.driot.bookplayer.imports.OngoingTaskUiState;
import com.driot.bookplayer.imports.OngoingTaskViewModel;
import com.driot.bookplayer.nav.FullActivity;
import com.driot.bookplayer.player.PlaybackUiBus;
import com.driot.bookplayer.player.PlaybackUiState;

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

    private Button bCancel;

    private Handler delayedFinishHandler;
    private Runnable delayedFinishRunnable;

    private OngoingTaskViewModel viewModel; // keep reference
    private boolean didEnterExitMode = false;
    // Which job Exit mode was entered for - see leaveExitModeIfNewJob()
    private String exitModeJobKey;

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

            leaveExitModeIfNewJob(ui);

            // When no longer running (FAILED / SUCCEEDED / CANCELLED), close flow once
            if (!didEnterExitMode && ui.isFinished()) {
                myLog("observing UI state => closing [" + ui.title + "] - showToUser=[" + ui.showToUser + "]");
                // Defer to end-of-frame to avoid re-entrancy with other observers
                getWindow().getDecorView().post(() -> checkAndClose(ui));
            }
        });
    }

    private void performCancel() {
        myLogI("------ USER CLICKS btn CANCEL ----");
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

        // Other Cases (should only be SUCCESS) Briefly show, then hide banner. What happens next
        // is user-configurable (Settings > Import > "When an import finishes:") - do nothing (just
        // return to the library), open the resulting book's track list, or jump straight into
        // playing it (optionally using the single-file "Open with"/sibling-book target hint - see
        // ImportJob.getTargetPlaybackFileName - to pick which track). Independently of that
        // setting, schedulePostImportNavigation() always hands off a live preview (Open With,
        // played before the import finished) to the newly-created ZikFile if there is one.
        if (ui.futureFolderPath != null) {
            schedulePostImportNavigation(DELAY_END_WAIT_NO_ERROR, ui.futureFolderPath, ui.targetPlaybackFileName);
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

    /**
     * Regardless of Option.getImportCompletionAction(): if this exact file was still
     * live-previewing (see MiniPlayUnregisteredFragment / Var.PLAY_MODE_PREVIEW) when the import
     * finished, hands playback off to the newly-created ZikFile, carrying over the current
     * position instead of restarting at 0 - MiniPlayHostFragment follows the playMode change and
     * swaps itself from MiniPlayUnregisteredFragment to MiniPlayBookFragment automatically, no
     * extra wiring needed here. Not doing this only when the setting says "do nothing" would
     * silently orphan whatever was already playing, still tagged as an unregistered preview even
     * though a real book now exists for it.
     * <p>
     * On top of that (unconditionally), the setting decides what to additionally do: nothing
     * (stay wherever the handoff above landed, if it happened at all), show the resulting book's
     * track list ("open"), or - if nothing was already playing - start playing it now ("play").
     */
    private void schedulePostImportNavigation(int delayMs, String futureFolderPath,
            @androidx.annotation.Nullable String targetPlaybackFileName) {
        delayedFinishHandler = new Handler();
        delayedFinishRunnable = () -> {
            ImportHelper.setShowToUser(this, false);

            int completionAction = Option.getImportCompletionAction();

            // Grab the live-preview position now, before the DB read below.
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
                long folderId = (folder != null) ? folder.getId() : -1;
                int trackCount = (files != null) ? files.size() : 0;
                boolean handedOffPreview = finalTarget != null && wasPreviewing;
                runOnUiThread(() -> {
                    if (finalTarget != null) {
                        if (handedOffPreview) {
                            myLog("Handing off live preview to the just-imported track: " + finalTarget.getName());
                            com.driot.bookplayer.player.StartPlayHelper.onZikFileClick(this, finalTarget,
                                    "AddResourceActivity-openWithImportSuccess-previewHandoff");
                        }

                        if (completionAction == Option.IMPORT_COMPLETION_DO_NOTHING) {
                            startActivity(new Intent(this, MainActivity.class)
                                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK)
                                    .putExtra("scrollToTop", true));
                            finish();
                            return;
                        }

                        boolean startPlayback = completionAction == Option.IMPORT_COMPLETION_PLAY_BOOK;
                        if (startPlayback && !handedOffPreview) {
                            // Nothing was already playing - actively start it now. If it WAS
                            // already playing (handedOffPreview above), it's already underway.
                            myLog("Jumping straight into playback for the just-imported track: "
                                    + finalTarget.getName());
                            com.driot.bookplayer.player.StartPlayHelper.onZikFileClick(this, finalTarget,
                                    "AddResourceActivity-openWithImportSuccess");
                        }
                        if (!startPlayback || trackCount > 1) {
                            // "Open the book" always shows the track list, regardless of track count
                            // (so the user can see/reorder it); "Play the book" only needs it for a
                            // multi-track book - a single-track one already shows the mini-player.
                            startActivity(new Intent(this, MainActivity.class)
                                    .putExtra(Intents.EXTRA_FOLDER_ID, folderId)
                                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK));
                        } else {
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
        myLog("Let's wait " + delayMs + " ms before navigating...");
        delayedFinishHandler.postDelayed(delayedFinishRunnable, delayMs);
    }

    private void enterExitMode() {
        myLog("enterExitMode");
        didEnterExitMode = true;
        OngoingTaskUiState current = viewModel.getUi().getValue();
        exitModeJobKey = current != null ? jobKey(current) : null;
        navHelper.removeAddBookNavSpecial();
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

    /**
     * This screen can stay alive in Exit mode (e.g. left in the back stack after a cancel) and then
     * be shown again while the next import runs - it must offer Cancel again for that new job,
     * not keep the finished one's Exit button.
     */
    private void leaveExitModeIfNewJob(OngoingTaskUiState ui) {
        if (!didEnterExitMode || !ui.isRunningLike() || jobKey(ui).equals(exitModeJobKey))
            return;
        myLog("leaveExitMode - new job running: [" + ui.title + "]");
        didEnterExitMode = false;
        exitModeJobKey = null;
        if (delayedFinishHandler != null && delayedFinishRunnable != null) {
            delayedFinishHandler.removeCallbacks(delayedFinishRunnable);
        }
        bCancel.setText(getString(android.R.string.cancel));
        bCancel.setOnClickListener(v -> performCancel());
    }

    private static String jobKey(OngoingTaskUiState ui) {
        return ui.title + "|" + ui.futureFolderPath;
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
