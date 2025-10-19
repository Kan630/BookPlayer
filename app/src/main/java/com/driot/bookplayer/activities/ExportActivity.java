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
import androidx.documentfile.provider.DocumentFile;
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
    public static final String EXTRA_DEST_URI = "EXTRA_DEST_URI"; // New: Uri string of the created zip file
    private static final String STATE_EXPORT_TREE_URI = "STATE_EXPORT_TREE_URI";
    private Uri exportTreeUri = null;  // user-chosen folder (tree) Uri

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


        b_destinationFolder.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_CODE_destinationFolder);
        });

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
            String exportFileName = "BookplayerExport_" + folder.getName() + ".zip";

            Intent serviceIntent = new Intent(this, ExportService.class);
            serviceIntent.putExtra(EXTRA_FOLDER_PATH, folderPath);

            if (exportTreeUri != null) {
                // Create the destination ZIP file inside the chosen folder
                DocumentFile tree = DocumentFile.fromTreeUri(this, exportTreeUri);
                if (tree == null || !tree.canWrite()) {
                    myToastE(getString(R.string.Cannot_write_to_the_chosen_folder));
                    return;
                }
                // Ensure a clean filename (no extension duplication)
                if (exportFileName.endsWith(".zip")) exportFileName = exportFileName.substring(0, exportFileName.length() - 4);
                DocumentFile zipDoc = tree.createFile("application/zip", exportFileName);
                if (zipDoc == null) {
                    myToastE(getString(R.string.Failed_to_create_the_destionation_file));
                    return;
                }

                // Pass Uri to the service
                serviceIntent.putExtra(EXTRA_DEST_URI, zipDoc.getUri().toString());

            } else {
                // Legacy fallback: plain path in Downloads (pre-Q or when no folder chosen)
                String detFileFullPath = new File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "BookplayerExport_" + folder.getName() + ".zip"
                ).getPath();
                serviceIntent.putExtra(EXTRA_DEST_FILE_FULL_PATH, detFileFullPath);
            }

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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_destinationFolder && resultCode == RESULT_OK && data != null) {
            Uri tree = data.getData();
            if (tree != null) {
                // Persist access so we can use it later (and from the service)
                final int takeFlags = (data.getFlags()
                        & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
                try {
                    getContentResolver().takePersistableUriPermission(tree, takeFlags);
                } catch (SecurityException ignore) { /* some OEMs can throw if you already have it */ }

                exportTreeUri = tree;

                // Update button label to show chosen folder name
                DocumentFile picked = DocumentFile.fromTreeUri(this, tree);
                String name = (picked != null && picked.getName() != null) ? picked.getName() : tree.getLastPathSegment();
                b_destinationFolder.setText(name);

                // If everything else is ready, you can enable export here as well
                if (folder != null && folder.exists()) btnExport.setEnabled(true);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (exportTreeUri != null) outState.putString(STATE_EXPORT_TREE_URI, exportTreeUri.toString());
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        String s = savedInstanceState.getString(STATE_EXPORT_TREE_URI, null);
        if (s != null) {
            exportTreeUri = Uri.parse(s);
            DocumentFile picked = DocumentFile.fromTreeUri(this, exportTreeUri);
            String name = (picked != null && picked.getName() != null) ? picked.getName() : exportTreeUri.getLastPathSegment();
            b_destinationFolder.setText(name);
        }
    }
}
