package com.driot.bookplayer.activities;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.utils.AddResourceService;
import com.driot.bookplayer.utils.KanLogger;
import com.driot.bookplayer.utils.Tonio;
import com.google.gson.Gson;

import static com.driot.bookplayer.global.Var.PATH_CHECK_APPLICATION;
import static com.driot.bookplayer.utils.Tonio.getFileNameFromPath;
import static com.driot.bookplayer.utils.Tonio.formatNameForDisplay;

import java.util.Objects;


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
    private TextView tvErrorText, tvWarning;


    boolean boundToAddResourceService;
    AddResourceService mService;
    boolean mBound = false;
    private boolean HasBeenInitializedService = false;

    private String type;
    private String original_type = "xx";

    private static final long MAX_TIME_BETWEEN_OPEN_WIDTH_LOADS = 20000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_addresource);

        tvTitle = findViewById(R.id.tvTitle);
        progressBarText = findViewById(R.id.progressBarText);
        progressBar = findViewById(R.id.progressBar);
        tvErrorText = findViewById(R.id.errorText);
        tvWarning = findViewById(R.id.warningText);

        String url = getIntent().getStringExtra("url");
        String action = getIntent().getAction();
        boolean GoLaunchService = true;

        if (url != null) {   // DIRECT DOWNLOAD

            myLog("onCreate() - Download\nurl=[" + url + "]");
            type =  "Download";
            putTitle(url);


        /*
        } else if (Intent.ACTION_VIEW.equals(action)) { // OPEN WITH

            Uri uri = getIntent().getData();
            type = "OpenWith";
            original_type = "OpenWith";

            String str_Uri = (uri == null) ? "null" : uri.toString();
            myLog("onCreate() - from Open With :\nuri=[" + str_Uri + "]\ntype=[" + type + "]");

            long lastLoadTimeDiff = System.currentTimeMillis() - Pref.get_Last_OpenWith_File_Time(this);
            myLog("Last load : " + Pref.get_Last_OpenWith_FileUri(this) + ", " + lastLoadTimeDiff + " ms ago.");
            if (Pref.get_Last_OpenWith_FileUri(this).equals(str_Uri) && lastLoadTimeDiff < MAX_TIME_BETWEEN_OPEN_WIDTH_LOADS) {
                myLog("Already loaded.... (max time = " + MAX_TIME_BETWEEN_OPEN_WIDTH_LOADS + " ms.)  \nLast Time = " + Pref.get_Last_OpenWith_File_Time(this) + "\ncurrent time = " + System.currentTimeMillis() + "\nDiff = " + lastLoadTimeDiff);
                GoLaunchService = false;
                Intent mainIntent = new Intent(this, MainActivity.class);
                mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(mainIntent);
                finish();

            } else {
                Pref.set_Last_OpenWith_FileUri(this, str_Uri);
                Pref.set_Last_OpenWith_File_Time(this);

                if (uri != null) {

                    String fileNameFromUri = Tonio.getFileNameFromUri(this, uri);
                    boolean isZip = fileNameFromUri != null && fileNameFromUri.toLowerCase().endsWith(".zip");
                    if (isZip) {
                        type = "ZIP";
                    } else {
                        type = "File";
                    }
                    String title = formatNameForDisplay(fileNameFromUri);

                    Intent intentAddResourceService = new Intent(this, AddResourceService.class);
                    intentAddResourceService.putExtra("uri", uri);
                    intentAddResourceService.putExtra("type", type);
                    intentAddResourceService.putExtra("title", title);
                    startService(intentAddResourceService);

                    putTitle(title);
                }

            }
             */

        } else {  // FILE PICKER

            //Huawei Folder : uri=[content://com.android.externalstorage.documents/tree/primary%3Aaudiobooks%2FHarry%20Potter%20Audio%20Books%201-7%3B%20Read%20by%20Stephen%20Fry%20%5BMP3%5D%2FBook%2002%20-%20Harry%20Potter%20and%20the%20Chamber%20of%20Secrets] - type=[Folder]

            Uri uri = getIntent().getParcelableExtra("uri");
            type =  getIntent().getStringExtra("type");
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
        }

        if (GoLaunchService) {
            Intent intentAddResourceService = new Intent(this, AddResourceService.class);
            boundToAddResourceService = bindService(intentAddResourceService, addResourceServiceConnection, Context.BIND_AUTO_CREATE); //error Log : Activity XXX has leaked ServiceConnection
            myLog("call start & bind to AddResourceService from AddResourceActivity.onCreate() - bound result :" + boundToAddResourceService + "");
        }
    }

    @Override
    protected void onDestroy() {
        myLog("onDestroy - unbinding Services");
        super.onDestroy();
        try {
            if (mBound) unbindService(addResourceServiceConnection);
            mBound = false;
        } catch (Exception e) {
            myLogE("onDestroy - error unbindService : " + e.getMessage());
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
            myLogE("onStop - error unbindService : " + e.getMessage());
            e.printStackTrace();
        }
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

                Intent mainIntent = new Intent(this, MainActivity.class);
                mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(mainIntent);
                finish();
            }
        });
    }
    //--- FULL LOG --------------------------
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogInFile(String str) { KanLogger.myLogInFile(this.getClass().getName(), str); }
    private void myLogD(String str) { KanLogger.myLogD(this.getClass().getName(), str); }
    private void myLogI(String str) { KanLogger.myLogI(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
    private void myToast(String str) { KanLogger.myToast(this.getClass().getName(), str); }
    private void myToastE(String str) { KanLogger.myToastE(this.getClass().getName(), str); }
}
