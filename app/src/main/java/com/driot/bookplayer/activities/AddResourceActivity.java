// com/driot/bookplayer/activities/AddResourceActivity.java
package com.driot.bookplayer.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.driot.bookplayer.R;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.objects.AppViewModelStoreOwner;
import com.driot.bookplayer.objects.TaskStateRepository;
import com.driot.bookplayer.objects.WorkFlow;
import com.driot.bookplayer.services.DownloadForegroundService;
import com.driot.bookplayer.utils.log.LoggingActivity;

public class AddResourceActivity extends LoggingActivity {

    private static final int DELAY_END_WAIT_ERROR = 5000;
    private static final int DELAY_END_WAIT_NO_ERROR = 1000;

    private TextView tvTitle;
    private TextView progressBarText;
    private ProgressBar progressBar;
    private TextView tvErrorText, tvWarning;

    private Button bPauseResume;
    private Button bCancel;

    private Handler delayedFinishHandler;
    private Runnable delayedFinishRunnable;

    private OngoingTaskViewModel viewModel;   // keep reference

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
        viewModel.getWarningText().observe(this, warningText -> tvWarning.setText(warningText));
        viewModel.getErrorText().observe(this, errorText -> tvErrorText.setText(errorText));

        viewModel.isPauseAvailable().observe(this, available -> {
            bPauseResume.setVisibility(Boolean.TRUE.equals(available) ? View.VISIBLE : View.GONE);
            if (Boolean.TRUE.equals(available) && progressBarText.getText().length() == 0) {
                progressBarText.setText(getString(R.string.About_to_start_download));
            }
        });

        viewModel.isPaused().observe(this, paused -> {
            if (bPauseResume.getVisibility() == View.VISIBLE) {
                bPauseResume.setText(getString(Boolean.TRUE.equals(paused) ? R.string.Resume : R.string.Pause));
            }
        });

        viewModel.isFinished().observe(this, finished -> {
            if (Boolean.TRUE.equals(finished)) {
                checkAndClose();
            }
        });

        // NOTE: no viewModel.reinit(); repository already holds the current state.
    }

    private void performPauseOrResume() {
        // Keep your existing service control; the VM/repo only reflects state.
        boolean isPauseLabel = bPauseResume.getText().toString().contentEquals(getString(R.string.Pause));
        if (isPauseLabel) {
            myLogI("------ USER CLICKS btn PAUSE ----");
            ContextCompat.startForegroundService(
                    this,
                    new Intent(this, DownloadForegroundService.class).setAction(DownloadForegroundService.ACTION_PAUSE)
            );
            // Button text will be updated by VM when repo sets paused=true
        } else {
            myLogI("------ USER CLICKS btn RESUME ----");
            ContextCompat.startForegroundService(
                    this,
                    new Intent(this, DownloadForegroundService.class).setAction(DownloadForegroundService.ACTION_RESUME)
            );
            // Button text will be updated by VM when repo sets paused=false
        }
    }

    private void performCancel() {
        myLogI("------ USER CLICKS btn CANCEL ----");
        WorkFlow.cancelAllOngoingTasks(this);
        TaskStateRepository.get().resetToIdle();
        finish();
    }

    private void checkAndClose() {
        myLogD("Set Activity result OK");
        AddResourceActivity.this.setResult(Activity.RESULT_OK);
        bCancel.setText(getString(R.string.Exit));

        if (tvErrorText.getText().length() > 0) {
            myToast(getString(R.string.Import_failed));
        } else if (tvWarning.getText().length() > 0) {
            myToast(getString(R.string.Import_finished_with_errors));
        } else {
            delayedFinishHandler = new Handler();
            delayedFinishRunnable = () -> {
                myToast(getString(R.string.Import_Success) + "\n" + tvTitle.getText());
                WorkFlow.cancelAllOngoingTasks(this);
                Intent mainIntent = new Intent(this, MainActivity.class);
                mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(mainIntent);
                finish();
            };
            myLog("Let's wait some " + DELAY_END_WAIT_NO_ERROR / 1000 + " sec to display finish...");
            delayedFinishHandler.postDelayed(delayedFinishRunnable, DELAY_END_WAIT_NO_ERROR);
        }
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
