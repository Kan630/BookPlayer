package com.driot.bookplayer.activities;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.services.ExportService;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.io.File;



public class ExportActivity extends LoggingActivity {

    public static final String EXTRA_FOLDER_ID = "EXTRA_FOLDER_ID";
    public static final String EXTRA_FOLDER_PATH = "EXTRA_FOLDER_PATH";
    public static final String EXTRA_DEST_FILE_FULL_PATH = "EXTRA_FILE_NAME";

    public static final int REQUEST_CODE_destinationFolder = 35737;

    private int folderId;
    File folder;
    private String folderPath;
    private ProgressBar progressBar;
    private TextView progressText, tvCurrentTrack, tvExportAudioBookName;
    private Button btnExport, btnCancel, b_destinationFolder;

    private String exportFolder;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());


    private final BroadcastReceiver exportProgressReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String currentTrack = intent.getStringExtra("currentTrack");
            int progress = intent.getIntExtra("progressPercent", 0);
            String displayText = intent.getStringExtra("displayText");
            uiHandler.post(() -> {
                String zeText = getString(R.string.Processing) + ":\n" + currentTrack;
                tvCurrentTrack.setText(zeText);
                progressText.setText(displayText);
                progressBar.setProgress(progress);
            });
            myLog("broadcast progress received " + progress + "% : " + currentTrack);
        }
    };


    private final BroadcastReceiver exportDoneReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            uiHandler.post(() -> {
                tvCurrentTrack.setTextColor(getColor(R.color.green_700));
                btnCancel.setText(getString(R.string.ok));
            });
            Uri zipUri = intent.getParcelableExtra("zipUri");
            if (zipUri != null) {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("application/zip");
                shareIntent.putExtra(Intent.EXTRA_STREAM, zipUri);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(shareIntent, getString(R.string.Export_share_title)));
            }

        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_export);
        InsetHelper.apply(this);

        folderId = getIntent().getIntExtra(EXTRA_FOLDER_ID, -1);

        progressBar = findViewById(R.id.progressBarExport);
        progressText = findViewById(R.id.tvProgressText);
        TextView tvExportTitle = findViewById(R.id.tvExportTitle);
        tvExportAudioBookName = findViewById(R.id.tvExportAudioBookName);
        tvCurrentTrack = findViewById(R.id.tvCurrentTrack);
        btnExport = findViewById(R.id.btnStartExport);
        btnCancel = findViewById(R.id.btnCancelExport);
        b_destinationFolder = findViewById(R.id.b_destinationFolder);

        // Initially disable the export button and show loading message
        btnExport.setEnabled(false);
        tvExportTitle.setText(getString(R.string.Export_display_text_export_title));
        tvExportAudioBookName.setText("---");
        progressText.setText("");
        tvCurrentTrack.setText("");

        // Cancel button can always close
        btnCancel.setOnClickListener(v -> finish());


        //TODO
/*
        b_destinationFolder.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TITLE, );
            startActivityForResult(intent, REQUEST_CODE_destinationFolder);

            // on result : exportFolder = result
        });

 */


        // Load folder path in a background thread
        new Thread(() -> {
            folderPath = AppDatabase.getDatabase(this).zikFileDao().getFolderPath(folderId);
            myLog("folderPath : " + folderPath);
            folder = new File(folderPath);
            onFolderPathLoaded();
        }).start();
    }

    private void onFolderPathLoaded() {
        runOnUiThread(() -> {
            if (folderPath == null || !folder.exists()) {
                tvExportAudioBookName.setText(getString(R.string.Export_display_no_valid_audiobook));
                tvExportAudioBookName.setTextColor(getColor(R.color.red_700));
                btnExport.setEnabled(false);
                return;
            }

            tvExportAudioBookName.setText(folder.getName());

            // below Android 10, need permission to write to Downloads
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {

                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            123); // arbitrary request code
                    return; // wait for user to accept before continuing
                }
            }

            goExport();

        });
    }

    private void goExport() {
        btnExport.setEnabled(true);

        btnExport.setOnClickListener(v -> {

            //TODO continue dev
            String detFileFullPath = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),"BookplayerExport_" + folder.getName() + ".zip").getPath();

            Intent serviceIntent = new Intent(this, ExportService.class);
            serviceIntent.putExtra(EXTRA_FOLDER_PATH, folderPath);
            serviceIntent.putExtra(EXTRA_DEST_FILE_FULL_PATH, detFileFullPath);
            startService(serviceIntent);
            btnExport.setEnabled(false);
            progressText.setText(getString(R.string.Export_display_text_preparing_export));
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

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 123) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                goExport();
            } else {
                myToastE("Permission denied, cannot export to Downloads.");
            }
        }
    }
}
