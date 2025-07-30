package com.driot.bookplayer.activities;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.services.AddResourceService;
import com.driot.bookplayer.services.DownloadForegroundService;
import com.driot.bookplayer.utils.log.LoggingActivity;

import static com.driot.bookplayer.utils.WorkFlow.cancelAllOngoingTasks;

import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;


/**
 * created by Antoine Driot -- antoine.driot.com -- on 23/11/20
 *
 * it is a waiting screen with progressbar
 *
 */
public class AddResourceActivity
        extends LoggingActivity
        implements AddResourceService.Callbacks
{

    private TextView tvTitle;
    private TextView progressBarText;
    private ProgressBar progressBar;
    private TextView tvErrorText, tvWarning;

    boolean boundToAddResourceService;
    AddResourceService mService;
    boolean mBound = false;

    Button bPause;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_addresource);

        tvTitle = findViewById(R.id.tvTitle);
        progressBarText = findViewById(R.id.progressBarText);
        progressBar = findViewById(R.id.progressBar);
        tvErrorText = findViewById(R.id.errorText);
        tvWarning = findViewById(R.id.warningText);

        Button bCancel = findViewById(R.id.bCancel);
        bCancel.setOnClickListener(v -> {performCancel();        });

        bPause = findViewById(R.id.bPause);
        bPause.setOnClickListener(v -> {            performPause();        });

        OngoingTaskViewModel viewModel = new ViewModelProvider(this).get(OngoingTaskViewModel.class);
        OngoingTaskViewModelBridge.bind(viewModel);
        viewModel.getTaskTitle().observe(this, title -> tvTitle.setText(title));
        viewModel.getProgressText().observe(this, text -> progressBarText.setText(text));
        viewModel.getProgressPercent().observe(this, percent -> progressBar.setProgress(percent));
        viewModel.isPauseAvailable().observe(this, available -> {
            bPause.setVisibility(available ? View.VISIBLE : View.GONE);
            if (available && progressBarText.getText().length() == 0) {
                progressBarText.setText("about to start download...");
            }
        });
        viewModel.isPaused().observe(this, paused -> {
            if (bPause.getVisibility() == View.VISIBLE) {
                bPause.setText(getString(paused ? R.string.Resume : R.string.Pause));
            }
        });

        Intent intentAddResourceService = new Intent(this, AddResourceService.class);
        boundToAddResourceService = bindService(intentAddResourceService, addResourceServiceConnection, Context.BIND_AUTO_CREATE); //error Log : Activity XXX has leaked ServiceConnection
        myLogD("call start & bind to AddResourceService from AddResourceActivity.onCreate() - bound result :" + boundToAddResourceService);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        OngoingTaskViewModelBridge.unbind();
        try {
            if (mBound) unbindService(addResourceServiceConnection);
            mBound = false;
        } catch (Exception e) {
            myLogEE(e,"onDestroy - error unbindService");
        }
    }
    @Override
    protected void onStop() {
        super.onStop();
        try {
            if (mBound) unbindService(addResourceServiceConnection);
            mBound = false;
        } catch (Exception e) {
            myLogEE(e,"onStop - error unbindService");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private final ServiceConnection addResourceServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            myLogD("onServiceConnected : [" + className.toString() + "]");
            AddResourceService.AddResourceServiceBackgroundBinder binder = (AddResourceService.AddResourceServiceBackgroundBinder) service;
            mService = binder.getService();
            mService.registerClient(AddResourceActivity.this); // to get callbacks
            mBound = true;
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            myLog("onServiceDisconnected : [" + arg0.toString() + "]");
            mService.unbindService(addResourceServiceConnection);
            mBound = false;
        }
    };

    // callback override
    @Override
    public void tellNonBlockingError(String txt) {
        runOnUiThread(() -> {
            myToastE(txt);
            tvErrorText.setText(txt);
            tvErrorText.setTextColor(getColor(R.color.red_500));
        });
    }
    @Override
    public void tellWarning(String txt) {
        runOnUiThread(() -> {
            tvWarning.setText(txt);
            tvWarning.setTextColor(getColor(R.color.orange));
        });
    }
    @Override
    public void tellProgress(String progressText, int progressVal) {
        runOnUiThread(() -> {
            if (!progressText.isEmpty()) {
                progressBarText.setText(progressText);
            }
            if (progressVal >= 0 && progressVal <= 100) {
                progressBar.setProgress(progressVal);
            }
        });
    }
    @Override
    public void tellError(String errorText) {
        runOnUiThread(() -> {
            progressBarText.setText(errorText);
            progressBarText.setTextColor(Color.RED);
        });
    }
    @Override
    public void tellEnd() {
        runOnUiThread(() -> {
            AddResourceActivity.this.setResult(Activity.RESULT_OK);
            if (tvErrorText.getText().length() > 0) {

                //wait some sec
                final Handler handler = new Handler();
                Runnable runnable = () -> {
                    myToast("Import finished with errors");
                    finish();                        };
                myLog("Let's wait some sec to display error... [" + tvErrorText.getText() + "]");
                handler.postDelayed(runnable, 5000);

            } else {
                myToast(getString(R.string.Import_Success) + "\n" + tvTitle.getText());
                cancelAllOngoingTasks(this);

                Intent mainIntent = new Intent(this, MainActivity.class);
                mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(mainIntent);
                finish();
            }
        });
    }

    private void performPause() {
        if (bPause.getText().equals(getString(R.string.Pause))) {
            myLogI("------ USER CLICKS btn PAUSE ----");
            Intent pauseIntent = new Intent(this, DownloadForegroundService.class);
            pauseIntent.setAction(DownloadForegroundService.ACTION_PAUSE);
            ContextCompat.startForegroundService(this, pauseIntent);
            bPause.setText(getString(R.string.Resume));
        } else {
            myLogI("------ USER CLICKS btn RESUME ----");
            Intent pauseIntent = new Intent(this, DownloadForegroundService.class);
            pauseIntent.setAction(DownloadForegroundService.ACTION_RESUME);
            ContextCompat.startForegroundService(this, pauseIntent);
            bPause.setText(getString(R.string.Pause));
        }
    }
    private void performCancel() {
        myLogI("------ USER CLICKS btn CANCEL ----");

        cancelAllOngoingTasks(this);

        finish();
    }
}
