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
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.services.AddResourceService;

import static com.driot.bookplayer.utils.Tonio.getFileNameFromPath;
import static com.driot.bookplayer.utils.Tonio.formatNameForDisplay;
import static com.driot.bookplayer.utils.WorkFlow.cancelAllOngoingTasks;


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
    private boolean HasBeenInitializedService = false;

    private String type;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_addresource);

        tvTitle = findViewById(R.id.tvTitle);
        progressBarText = findViewById(R.id.progressBarText);
        progressBar = findViewById(R.id.progressBar);
        tvErrorText = findViewById(R.id.errorText);
        tvWarning = findViewById(R.id.warningText);

/*
        Uri uri = state.uri;
        String str_Uri = uri==null ? "null" : uri.toString();
        myLog("onCreate() - from File Picker\nuri=[" + str_Uri + "]\ntype=[" + type + "]");
        if (!str_Uri.contains(PATH_CHECK_APPLICATION)) {
            try {
                this.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Exception e) {
                myLogE("error while using takePersistableUriPermission for selected URI - " + e.getMessage());
            }
        }
        if (uri != null) {
            putTitle(uri.getLastPathSegment());
        }

 */

        Button bCancel = findViewById(R.id.bCancel);
        bCancel.setOnClickListener(v -> { performCancel(); });

        Intent intentAddResourceService = new Intent(this, AddResourceService.class);
        boundToAddResourceService = bindService(intentAddResourceService, addResourceServiceConnection, Context.BIND_AUTO_CREATE); //error Log : Activity XXX has leaked ServiceConnection
        myLog("call start & bind to AddResourceService from AddResourceActivity.onCreate() - bound result :" + boundToAddResourceService);


    }

    @Override
    protected void onDestroy() {
        myLog("onDestroy - unbinding Services");
        super.onDestroy();
        try {
            if (mBound) unbindService(addResourceServiceConnection);
            mBound = false;
        } catch (Exception e) {
            myLogEE(e,"onDestroy - error unbindService : " + e.getMessage());
        }
    }
    @Override
    protected void onStop() {
        myLog("onStop - unbinding Services");
        super.onStop();
        try {
            if (mBound) unbindService(addResourceServiceConnection);
            mBound = false;
        } catch (Exception e) {
            myLogEE(e,"onStop - error unbindService : " + e.getMessage());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
/*
        Intent intentAddResourceService = new Intent(this, AddResourceService.class);
        boundToAddResourceService = bindService(intentAddResourceService, addResourceServiceConnection, Context.BIND_AUTO_CREATE);
        myLog("call start & bind to AddResourceService from AddResourceActivity.onResume() - bound result :" + boundToAddResourceService);

 */

        //maybeResumeWorkFlow(this);
    }

    private final ServiceConnection addResourceServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            myLog("AddResourceService - onServiceConnected : [" + className.toString() + "]");
            AddResourceService.AddResourceServiceBackgroundBinder binder = (AddResourceService.AddResourceServiceBackgroundBinder) service;
            mService = binder.getService();
            mService.registerClient(AddResourceActivity.this); //to get the CallBacks
            mBound = true;
            // Get PlayList
            if (!HasBeenInitializedService) { mService.init(); }
            HasBeenInitializedService = true;
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            myLog("AddResourceService - OnServiceDisconnected : [" + arg0.toString() + "]");
            mService.unbindService(addResourceServiceConnection);
            mBound = false;
        }
    };

    private void putTitle(String name) {
        name = "[" + type + "] - " + formatNameForDisplay(getFileNameFromPath(name));
        tvTitle.setText(name);
    }

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
    public void updateProgress(String progressText, int progressVal) {
        runOnUiThread(() -> {
            if (progressVal >= 0 && progressVal <= 100) {
                progressBar.setProgress(progressVal);
            }
            if (!progressText.isEmpty()) {
                progressBarText.setText(progressText);
            }
        });
    }
    @Override
    public void updateError(String errorText) {
        runOnUiThread(() -> {
            progressBarText.setText(errorText);
            progressBarText.setTextColor(Color.RED);
        });
    }
    @Override
    public void tellHeader(String txt) {
        runOnUiThread(() -> putTitle(txt));
    }
    @Override
    public void updateEnd() {
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

    private void performCancel() {
        cancelAllOngoingTasks(this);
        finish();
    }


}
