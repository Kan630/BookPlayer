package com.driot.bookplayer.activities;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.utils.AddResourceService;
import com.driot.tonylib.KanLogger;

import static com.driot.bookplayer.utils.AddResourceService.NOTIFICATION_ADDRESOURCE_NAME;
import static com.driot.bookplayer.utils.AddResourceService.NOTIFICATION_ADDRESOURCE_PROGRESS;
import static com.driot.bookplayer.utils.AddResourceService.NOTIFICATION_ADDRESOURCE_ERROR;
import static com.driot.bookplayer.utils.AddResourceService.NOTIFICATION_ADDRESOURCE_END;
import static com.driot.bookplayer.utils.Tonio.getFileNameFromPath;
import static com.driot.bookplayer.utils.Tonio.FormatNameForDisplay;
import static com.driot.tonylib.KanLogger.myToast;

import androidx.core.content.ContextCompat;


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

    static final String TAG = "AddResourceActivity";
    private static final boolean LOG_TRACE = true;

    private ProgressBar progressBar;
    private TextView progressBarText;
    private TextView tvTitle;

    boolean boundToAddResourceService;
    AddResourceService mService;
    boolean mBound = false;
    private boolean HasBeenInitializedService = false;

    private String type;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_addresource);

        progressBar = findViewById(R.id.progressBar);
        progressBarText = findViewById(R.id.progressBarText);
        tvTitle = findViewById(R.id.tvTitle);

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
        /*
        registerReceiver(addResourceActivityBroadcastReceiver, new IntentFilter(NOTIFICATION_ADDRESOURCE_NAME)); // RECEIVER_NOT_EXPORTED
        registerReceiver(addResourceActivityBroadcastReceiver, new IntentFilter(NOTIFICATION_ADDRESOURCE_PROGRESS));
        registerReceiver(addResourceActivityBroadcastReceiver, new IntentFilter(NOTIFICATION_ADDRESOURCE_ERROR));
        registerReceiver(addResourceActivityBroadcastReceiver, new IntentFilter(NOTIFICATION_ADDRESOURCE_END));
        */
        //registerReceiver(addResourceActivityBroadcastReceiver, new IntentFilter(NOTIFICATION_COPYFILE_SERVICE_PROGRESS));
        //registerReceiver(addResourceActivityBroadcastReceiver, new IntentFilter(NOTIFICATION_UNZIP_SERVICE_PROGRESS));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(addResourceActivityBroadcastReceiver);
        } catch (Exception e) {
            myLogE("onDestroy - error unregisterReceiver");
        }
        try { //TODO should we.... ?
            unbindService(addResourceServiceConnection);
        } catch (Exception e) {
            myLogE("onDestroy - error unbindService");
        }
    }

    private final BroadcastReceiver addResourceActivityBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case NOTIFICATION_ADDRESOURCE_NAME:
                    myLog("broadcast received NAME : " + intent.getStringExtra("name"));
                    putTitle(intent.getStringExtra("name"));
                    break;

                case NOTIFICATION_ADDRESOURCE_PROGRESS:
                    progressBar.setProgress(intent.getIntExtra("progressVal",0));
                    progressBarText.setText(intent.getStringExtra("progressText"));
                    break;
/*
                case NOTIFICATION_COPYFILE_SERVICE_PROGRESS:
                    int ProgressBarVal =  intent.getIntExtra("progressVal",0);
                    //myLog("broadcast received PROGRESS : " + intent.getIntExtra("progress",0));
                    progressBar.setProgress(intent.getIntExtra("progressVal",0));
                    progressBarText.setText(intent.getStringExtra("progressText"));
                    //progressBar.setProgress(20);
                    //progressBarText.setText("hello toto copy");
                    myLog("Progess CopyFile received : " + intent.getIntExtra("progressVal",0) + " - " + intent.getStringExtra("progressText"));
                    break;

                case NOTIFICATION_UNZIP_SERVICE_PROGRESS:
                    progressBar.setProgress(intent.getIntExtra("progressVal",0));
                    progressBarText.setText(intent.getStringExtra("progressText"));
                    //progressBar.setProgress(50);
                    //progressBarText.setText("hello toto unzip");
                    myLog("Progess Unzip received : " + intent.getIntExtra("progressVal",0) + " - " + intent.getStringExtra("progressText"));
                    break;
*/
                case NOTIFICATION_ADDRESOURCE_ERROR:
                    String errorMessage = getString(R.string.ERROR) + " :" + intent.getStringExtra("message");
                    progressBarText.setText(errorMessage);
                    progressBarText.setTextColor(Color.RED);
                    //myToast(errorMessage);
                    myLogE("broadcast received ERROR : " + errorMessage);
                    break;

                case NOTIFICATION_ADDRESOURCE_END:
                    myLog("broadcast received END");
                    if (intent.getBooleanExtra("ok",false)) {
                        myToast(getString(R.string.Import_Success));
                        AddResourceActivity.this.setResult(Activity.RESULT_OK);
                    } else {
                        String message = intent.getStringExtra("message");
                        if (!message.equals("")) {
                            myToast(message);
                        } else {
                            myToast("IMPORT CANCELLED !");
                        }
                    }
                    finish();
            }
        }
    };
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
    public void updateProgress(String progressText, int progressVal) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Update UI elements here
                progressBar.setProgress(progressVal);
                progressBarText.setText(progressText);
            }
        });
    }
    @Override
    public void updateError(String errorText) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Update UI elements here
                progressBarText.setText(errorText);
                progressBarText.setTextColor(Color.RED);
            }
        });
    }
}
