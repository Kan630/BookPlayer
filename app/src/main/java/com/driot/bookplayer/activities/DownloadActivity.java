package com.driot.bookplayer.activities;

/******  Antoine Driot
 * 2024-05-29
 *
 **/

import static com.driot.bookplayer.global.Var.FOLDER_DOWNLOAD;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.driot.bookplayer.utils.DownloadService;
import com.driot.bookplayer.R;
import com.driot.tonylib.KanLogger;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

public class DownloadActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private TextView tv_belowProgressBar;

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (DownloadService.ACTION_PROGRESS.equals(action)) {
                int progress = intent.getIntExtra(DownloadService.EXTRA_PROGRESS_VALUE, 0);
                String txt_progress = intent.getStringExtra(DownloadService.EXTRA_PROGRESS_TEXT);
                progressBar.setProgress(progress);
                tv_belowProgressBar.setText(txt_progress);
            } else if (DownloadService.ACTION_COMPLETE.equals(action)) {
                myLog("BroadcastReceiver ACTION_COMPLETE");
                endThisActivity(intent.getStringExtra(DownloadService.EXTRA_URL));
            } else if (DownloadService.ACTION_ERROR.equals(action)) {
                myLog("BroadcastReceiver ACTION_ERROR");
                tv_belowProgressBar.setText(intent.getStringExtra(DownloadService.EXTRA_ERROR_STRING));
                tv_belowProgressBar.setTextColor(getResources().getColor(R.color.lightred));
            } else {
                myLogE("BroadcastReceiver - unknown received broadcast");
            }
        }
    };

    private void endThisActivity(String downloadedFilePath) {
        myLog("endThisActivity - endMessage = [" + downloadedFilePath + "]");
        Intent resultIntent = new Intent();
        ArrayList<String> aa = new ArrayList<>(Collections.singleton(downloadedFilePath));
        resultIntent.putStringArrayListExtra("data", aa);
        Uri uri = Uri.fromFile(new File(downloadedFilePath));
        myLog("uri = " + uri.toString());
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download);
        Intent receivedIntent = getIntent();
        String filePathToDownload = receivedIntent.getStringExtra("filePathToDownload");

        progressBar = findViewById(R.id.download_progressBar);
        tv_belowProgressBar = findViewById(R.id.tv_textBelowProgressBar);

        LocalBroadcastManager.getInstance(this).registerReceiver(downloadReceiver, new IntentFilter(DownloadService.ACTION_PROGRESS));
        LocalBroadcastManager.getInstance(this).registerReceiver(downloadReceiver, new IntentFilter(DownloadService.ACTION_COMPLETE));
        LocalBroadcastManager.getInstance(this).registerReceiver(downloadReceiver, new IntentFilter(DownloadService.ACTION_ERROR));

        // Start the download service
        Intent intent = new Intent(this, DownloadService.class);
        intent.putExtra(DownloadService.EXTRA_URL, filePathToDownload);
        intent.putExtra(DownloadService.EXTRA_DESTINATION_FOLDER, getFilesDir().getAbsolutePath() + "/" + FOLDER_DOWNLOAD);
        startService(intent);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(downloadReceiver);
    }

    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
}
