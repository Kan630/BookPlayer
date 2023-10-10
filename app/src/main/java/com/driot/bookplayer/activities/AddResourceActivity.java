package com.driot.bookplayer.activities;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.utils.AddResourceService;
import com.driot.tonylib.KanLogger;

import static com.driot.bookplayer.utils.Tonio.getFileNameFromPath;
import static com.driot.bookplayer.utils.Tonio.FormatNameForDisplay;
import static com.driot.tonylib.KanLogger.myToast;
import static com.driot.tonylib.KanLogger.myToastE;


/**
 * created by Antoine Driot -- antoine.driot.com -- on 23/11/20
 *
 * it is a waiting screen with progressbar
 *
 */
public class AddResourceActivity
        extends LifecycleLoggingActivity
        implements AddResourceService.Callbacks
{

    private TextView tvTitle;
    private TextView progressBarText;
    private ProgressBar progressBar;
    private TextView tvErrorText;


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

        Uri uri = getIntent().getParcelableExtra("Uri");
        type =  getIntent().getStringExtra("type");

        this.getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        putTitle(uri.getLastPathSegment());

        Intent intentAddResourceService = new Intent(AddResourceActivity.this, AddResourceService.class);
        intentAddResourceService.putExtra("Uri", uri);
        intentAddResourceService.putExtra("type", type);
        startService(intentAddResourceService);
        boundToAddResourceService = bindService(intentAddResourceService, addResourceServiceConnection, Context.BIND_AUTO_CREATE); //error Log : Activity XXX has leaked ServiceConnection
        myLog("call start & bind to AddResourceService from AddResourceActivity.onCreate() - bound result :" + boundToAddResourceService + "");
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { //TODO should we.... ?
            if (mBound) unbindService(addResourceServiceConnection);
            mBound = false;
        } catch (Exception e) {
            myLogE("onDestroy - error unbindService : " + e.getMessage());
            e.printStackTrace();
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
            myLogE("onDestroy - error unbindService : " + e.getMessage());
            e.printStackTrace();
        }
    }

    private final ServiceConnection addResourceServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            myLog("AddResourceService - onServiceConnected");
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
            myLog("AddResourceService - OnServiceDisconnected");
            mService.unbindService(addResourceServiceConnection);
            mBound = false;
        }
    };

    private void putTitle(String name) {
        name = type + " - " + FormatNameForDisplay(getFileNameFromPath(name));
        tvTitle.setText(name);
    }

    //-----------------------------
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }

    // callback override
    @Override
    public void tellNonBlockingError(String txt) {
        runOnUiThread(() -> {
            myToastE(txt);
            tvErrorText.setText(txt);
            tvErrorText.setTextColor(Color.RED);
        });
    }
    @Override
    public void updateProgress(String progressText, int progressVal) {
        runOnUiThread(() -> {
            progressBar.setProgress(progressVal);
            progressBarText.setText(progressText);
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
                myToast(getString(R.string.Import_Success));
                finish();
            }
        });
    }

}
