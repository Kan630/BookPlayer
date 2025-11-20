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
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.imports.ImportHelper;
import com.driot.bookplayer.imports.OngoingTaskViewModel;
import com.driot.bookplayer.imports.TaskUiState;
import com.driot.bookplayer.objects.AppViewModelStoreOwner;
import com.driot.bookplayer.services.DownloadControl;

public class AddResourceActivity extends BaseBottomNavActivity {

    private static final int DELAY_END_WAIT_WARNINGS = 5*60_000;
    private static final int DELAY_END_WAIT_NO_ERROR = 2_000;

    private TextView tvTitle;
    private TextView progressBarText;
    private ProgressBar progressBar;
    private TextView tvErrorText, tvWarning;
    NestedScrollView warningScroll;

    private Button bPauseResume;
    private Button bCancel;

    private Handler delayedFinishHandler;
    private Runnable delayedFinishRunnable;

    private OngoingTaskViewModel viewModel;   // keep reference
    private boolean didEnterExitMode = false;

    @Override protected int getNavId() { return R.id.nav_add; }
    @Override protected int getLayoutResId() { return R.layout.activity_add_resource; }
    @Override protected boolean enableOngoingTaskOverlay() { return false; }

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
        bCancel.setText(getString(R.string.Cancel));
        bCancel.setOnClickListener(v -> performCancel());

        bPauseResume = findViewById(R.id.bPauseResume);
        bPauseResume.setOnClickListener(v -> performPauseOrResume());

        viewModel = new ViewModelProvider(
                AppViewModelStoreOwner.getInstance(),
                ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication())
        ).get(OngoingTaskViewModel.class);

        //myLogD("ViewModel instance: " + System.identityHashCode(viewModel));

        viewModel.getUi().observe(this, ui -> {
            //myLog("observing UI state [" + ui.title + "] - showToUser=[" + ui.showToUser + "] - result=[" + ui.result + "] - progressPercent=[" + ui.progressPercent + "]");
            // Bind UI
            tvTitle.setText(ui.title);
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

    private void checkAndClose(TaskUiState ui) {
        myLog("checkAndClose(result=" + ui.status + ")");

        enterExitMode(); // buttons → Exit mode

        // Failure => keep activity visible until user exits (or you can auto-close later)
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

        // Other Cases (should only be SUCCESS) Briefly show, then hide banner + return to Main (ask scroll)
        scheduleFinish(DELAY_END_WAIT_NO_ERROR);
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

    private void enterExitMode() {
        myLog("enterExitMode");
        didEnterExitMode = true;
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
