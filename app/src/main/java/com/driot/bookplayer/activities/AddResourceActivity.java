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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.driot.bookplayer.R;
import com.driot.bookplayer.utils.AddResourceService;

import static com.driot.bookplayer.utils.AddResourceService.NOTIFICATION_ADDRESOURCE_NAME;
import static com.driot.bookplayer.utils.AddResourceService.NOTIFICATION_ADDRESOURCE_PROGRESS;
import static com.driot.bookplayer.utils.AddResourceService.NOTIFICATION_ADDRESOURCE_ERROR;
import static com.driot.bookplayer.utils.AddResourceService.NOTIFICATION_ADDRESOURCE_END;
import static com.driot.bookplayer.utils.Tonio.getFileNameFromPath;
import static com.driot.bookplayer.utils.Tonio.FormatNameForDisplay;


/**
 * created by Antoine Driot -- antoine.driot.com -- on 23/11/20
 */
public class AddResourceActivity extends LifecycleLoggingActivity {

    private ProgressBar progressBar;
    private TextView progressBarText;
    private TextView tvTitle;

    private Intent intentAddResourceService;
    boolean boundToService;
    AddResourceService mService;
    boolean mBound = false;
    private boolean HasBeenInitializedService = false;

    private Uri uri;
    private String type;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_addresource);

        progressBar = findViewById(R.id.progressBar);
        progressBarText = findViewById(R.id.progressBarText);
        tvTitle = findViewById(R.id.tvTitle);

        uri = getIntent().getParcelableExtra("Uri");
        type =  getIntent().getStringExtra("type");

        putTitle(uri.getLastPathSegment());

        intentAddResourceService = new Intent(AddResourceActivity.this, AddResourceService.class);
        startService(intentAddResourceService);
        boundToService = bindService(intentAddResourceService, connection, Context.BIND_AUTO_CREATE);
        myLog("call start & bind to Service in Activity.onCreate() - bound result :" + boundToService + "");
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(receiver, new IntentFilter(NOTIFICATION_ADDRESOURCE_PROGRESS));
        registerReceiver(receiver, new IntentFilter(NOTIFICATION_ADDRESOURCE_ERROR));
        registerReceiver(receiver, new IntentFilter(NOTIFICATION_ADDRESOURCE_END));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(receiver);
        stopService(intentAddResourceService);
    }

    private BroadcastReceiver receiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case NOTIFICATION_ADDRESOURCE_NAME:
                    myLog("broadcast received NAME");
                    putTitle(intent.getStringExtra("name"));
                    break;
                case NOTIFICATION_ADDRESOURCE_PROGRESS:
                    myLog("broadcast received PROGRESS");
                    progressBar.setProgress(intent.getIntExtra("progress",0));
                    progressBarText.setText(intent.getStringExtra("progressText"));
                    break;
                case NOTIFICATION_ADDRESOURCE_ERROR:
                    String errorMessage = intent.getStringExtra("message");
                    progressBarText.setText("ERROR :" + errorMessage);
                    progressBarText.setTextColor(Color.RED);
                    myToast(errorMessage);
                    myLogE("broadcast received ERROR : " + errorMessage);
                case NOTIFICATION_ADDRESOURCE_END:
                    myLog("broadcast received END");
                    if (intent.getBooleanExtra("ok",false)) {
                        myToast(getString(R.string.Import_Success));
                        setResultCode(Activity.RESULT_OK);
                    } else {
                        myToast("IMPORT CANCELLED !");
                    }
                    finish();
            }
        }
    };
    private ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            myLog("onServiceConnected");
            AddResourceService.BackgroundBinder binder = (AddResourceService.BackgroundBinder) service;
            mService = binder.getService();
            mBound = true;

            // Get PlayList
            if (!HasBeenInitializedService) { mService.init(uri, type); }
            HasBeenInitializedService = true;

        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            myLog("OnServiceDisconnected");
            mBound = false;
        }

    };

    private void putTitle(String name) {
        name = type + " - " + FormatNameForDisplay(getFileNameFromPath(name));
        tvTitle.setText(name);
    }

    private void myToast(String str) {
        myLog(str);
        Toast.makeText(getApplicationContext(),str,Toast.LENGTH_SHORT).show();
    }
}
