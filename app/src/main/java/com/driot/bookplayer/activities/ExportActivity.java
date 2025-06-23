package com.driot.bookplayer.activities;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.*;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.utils.ExportService;
import com.driot.bookplayer.utils.KanLogger;

import java.io.File;

public class ExportActivity extends Activity {

    public static final String EXTRA_FOLDER_ID = "EXTRA_FOLDER_ID";
    public static final String EXTRA_FOLDER_PATH = "EXTRA_FOLDER_PATH";

    private int folderId;
    File folder;
    private String folderPath;
    private ProgressBar progressBar;
    private TextView progressText, fileInfoText, tvCurrentTrack;
    private Button btnExport, btnCancel;

    private BroadcastReceiver exportProgressReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String currentTrack = intent.getStringExtra("currentTrack");
            int progress = intent.getIntExtra("progressPercent", 0);
            String displayText = intent.getStringExtra("displayText");
            tvCurrentTrack.setText("Zipping: " + currentTrack);
            progressText.setText(displayText);
            progressBar.setProgress(progress);
        }
    };
    private final BroadcastReceiver exportDoneReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Uri zipUri = intent.getParcelableExtra("zipUri");
            if (zipUri != null) {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("application/zip");
                shareIntent.putExtra(Intent.EXTRA_STREAM, zipUri);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(shareIntent, "Share ZIP file"));
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_export);

        folderId = getIntent().getIntExtra(EXTRA_FOLDER_ID, -1);

        progressBar = findViewById(R.id.progressBarExport);
        progressText = findViewById(R.id.tvProgressText);
        fileInfoText = findViewById(R.id.tvExportInfo);
        tvCurrentTrack = findViewById(R.id.tvCurrentTrack);
        btnExport = findViewById(R.id.btnStartExport);
        btnCancel = findViewById(R.id.btnCancelExport);

        // Initially disable the export button and show loading message
        btnExport.setEnabled(false);
        fileInfoText.setText("Loading folder info...");
        progressText.setText("");
        tvCurrentTrack.setText("");

        // Cancel button can always close
        btnCancel.setOnClickListener(v -> finish());

        // Load folder path in a background thread
        new Thread(() -> {
            folderPath = AppDatabase.getDatabase(this).ZikFileDao().getFolderPath(folderId);
            myLog("folderPath : " + folderPath);
            folder = new File(folderPath);
            onFolderPathLoaded();
        }).start();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("export_channel",
                    "Export Notifications", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void onFolderPathLoaded() {
        runOnUiThread(() -> {
            if (folderPath == null || !folder.exists()) {
                fileInfoText.setText("No valid folder selected.");
                btnExport.setEnabled(false);
                return;
            }

            fileInfoText.setText("Exporting audiobook: " + folder.getName());
            btnExport.setEnabled(true);

            btnExport.setOnClickListener(v -> {
                Intent serviceIntent = new Intent(this, ExportService.class);
                serviceIntent.putExtra(EXTRA_FOLDER_PATH, folderPath);
                startService(serviceIntent);
                btnExport.setEnabled(false);
                progressText.setText("Preparing export...");
            });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(exportProgressReceiver, new IntentFilter("EXPORT_PROGRESS"));
        LocalBroadcastManager.getInstance(this).registerReceiver(exportDoneReceiver, new IntentFilter("EXPORT_DONE"));
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(exportProgressReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(exportDoneReceiver);
    }
    //--- LOG --------------------------
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogInFile(String str) { KanLogger.myLogInFile(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
    private void myToast(String str) { KanLogger.myToast(this.getClass().getName(), str); }
    private void myToastE(String str) { KanLogger.myToastE(this.getClass().getName(), str); }
}
