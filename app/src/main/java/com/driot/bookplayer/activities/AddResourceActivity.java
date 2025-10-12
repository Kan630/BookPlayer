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
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.imports.ImportHelper;
import com.driot.bookplayer.imports.OngoingTaskViewModel;
import com.driot.bookplayer.objects.AppViewModelStoreOwner;
import com.driot.bookplayer.services.DownloadControl;
import com.driot.bookplayer.utils.log.LoggingActivity;

public class AddResourceActivity extends LoggingActivity {

    private static final int DELAY_END_WAIT_WARNINGS = 5000;
    private static final int DELAY_END_WAIT_NO_ERROR = 1000;

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
    private boolean didClose = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_resource);
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

        bPauseResume = findViewById(R.id.bPause);
        bPauseResume.setOnClickListener(v -> performPauseOrResume());

        viewModel = new ViewModelProvider(
                AppViewModelStoreOwner.getInstance(),
                ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication())
        ).get(OngoingTaskViewModel.class);

        myLogD("ViewModel instance: " + System.identityHashCode(viewModel));

        // Bind UI
        viewModel.getTaskTitle().observe(this, title -> tvTitle.setText(title));
        viewModel.getProgressText().observe(this, text -> progressBarText.setText(text));
        viewModel.getProgressPercent().observe(this, percent -> progressBar.setProgress(percent));
        viewModel.getWarningText().observe(this, warningText -> {
            tvWarning.setText(warningText);
            warningScroll.post(() -> warningScroll.fullScroll(View.FOCUS_DOWN));
        });
        viewModel.getErrorText().observe(this, errorText -> tvErrorText.setText(errorText));

        viewModel.isPauseAvailable().observe(this, available -> {
            myLog("observe isPauseAvailable = " + available);
            bPauseResume.setVisibility(Boolean.TRUE.equals(available) ? View.VISIBLE : View.GONE);
            if (Boolean.TRUE.equals(available)) {
                boolean pausedNow = Boolean.TRUE.equals(viewModel.isPaused().getValue());
                bPauseResume.setText(getString(pausedNow ? R.string.Resume : R.string.Pause));
            }
        });

        viewModel.isPaused().observe(this, paused -> {
            myLog("observe isPaused = " + paused);
            if (bPauseResume.getVisibility() == View.VISIBLE) {
                bPauseResume.setText(getString(Boolean.TRUE.equals(paused) ? R.string.Resume : R.string.Pause));
            }
        });

        viewModel.isTaskRunning().observe(this, running -> {
            String errorText = viewModel.getErrorText().getValue();
            boolean hasErrorText = errorText != null && !errorText.isEmpty();
            myLog("observe isTaskRunning = " + running + (hasErrorText ? " - errorText=" + errorText : ""));
            checkAndClose();
            /*
            if (Boolean.TRUE.equals(running) || hasErrorText) {
                //button cancel => exit
            } else {
            }

             */
        });

        /*
        viewModel.isFinished().observe(this, finished -> {
            myLog("observe isFinished = " + finished);
            if (Boolean.TRUE.equals(finished)) {
                if (!didClose) {
                    didClose = true;
                    checkAndClose();
                }
            }
        });

         */
    }

    private void performPauseOrResume() {
        // Keep your existing service control; the VM/repo only reflects state.
        boolean isPausedNow = Boolean.TRUE.equals(viewModel.isPaused().getValue());
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
        ImportHelper.cancelCurrentImport(this);
        enterExitMode();
    }

    private void checkAndClose() {
        myLog("checkAndClose");
        //ImportHelper.cancelCurrentImport(this);
        //ImportHelper.cleanUp(this, false, null);
        //ImportHelper.setShowToUser(this, false);
        enterExitMode();

        final String err  = viewModel.getErrorText().getValue();
        final String warn = viewModel.getWarningText().getValue();

        if (err != null && !err.isEmpty() && !"Cancelled".equals(err)) {
            bCancel.setText(getString(R.string.Exit));
            // Ensure the card stays up:
            ImportHelper.setShowToUser(this, true);
            myToast(getString(R.string.Import_failed));
            enterExitMode(); // changes the button behavior to Exit without hiding
            return;
        }

        // Collapse trivial warnings
        String trimmedWarn = null;
        if (warn != null && !warn.isEmpty()) {
            trimmedWarn = warn
                    .replace("\n", "")
                    //.replace(getString(R.string.Download_paused_by_user), "")
                    //.replace(getString(R.string.connection_aborted) + " (" + getString(R.string.no_internet_connection) + "?)","")
                    //.replace(getString(R.string.no_internet_connection), "")
                    .trim();
        }

        // If meaningful warnings: show "finished with errors" but still allow auto-close
        if (trimmedWarn != null && !trimmedWarn.isEmpty()) {
            bCancel.setText(getString(R.string.Exit));
            ImportHelper.setShowToUser(this, true); // keep visible for the brief display
            myToast(getString(R.string.Import_finished_with_errors));
            scheduleFinish(DELAY_END_WAIT_WARNINGS /* e.g., 5000ms */);
            return;
        }

        // Success path: short display then close (and hide banners)
        myToast(getString(R.string.Import_Success) + "\n" + tvTitle.getText());
        ImportHelper.setShowToUser(this, true); // (optional) keep visible during the short success toast
        scheduleFinish(DELAY_END_WAIT_NO_ERROR /* e.g., 1000ms */);
        /*
        AddResourceActivity.this.setResult(Activity.RESULT_OK);
        bCancel.setText(getString(R.string.Exit));

        if (err != null && !err.isEmpty()) {
            myToast(getString(R.string.Import_failed));
            return;
        }
        if (warn != null && !warn.isEmpty()) {
            String strRealWarning = warn
                    .replace("\n", "")
                    .replace(getString(R.string.Download_paused_by_user), "")
                    .replace(getString(R.string.connection_aborted) + " (" + getString(R.string.no_internet_connection) + "?)","")
                    .replace(getString(R.string.no_internet_connection),"")
                    .trim();
            if (!strRealWarning.isEmpty()) {
                myLog("chapped warning string : [" + strRealWarning + "]");
                myToast(getString(R.string.Import_finished_with_errors));
                return;
            }
        }


        // Success path
        myToast(getString(R.string.Import_Success) + "\n" + tvTitle.getText());
        delayedFinishHandler = new Handler();
        delayedFinishRunnable = () -> {
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
        };
        myLog("Let's wait some " + DELAY_END_WAIT_NO_ERROR + " ms. to close activity...");
        delayedFinishHandler.postDelayed(delayedFinishRunnable, DELAY_END_WAIT_NO_ERROR);
         */
    }
    private void scheduleFinish(int delayMs) {
        delayedFinishHandler = new Handler();
        delayedFinishRunnable = () -> {
            // Now we can hide banners
            ImportHelper.setShowToUser(this, false);
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
        };
        myLog("Let's wait " + delayMs + " ms before closing activity...");
        delayedFinishHandler.postDelayed(delayedFinishRunnable, delayMs);
    }

    private void enterExitMode() {
        viewModel.isPauseAvailable().removeObservers(this);
        viewModel.isPaused().removeObservers(this);
        viewModel.isTaskRunning().removeObservers(this);
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
                    //.putExtra("forceRefresh", true));
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
