package com.driot.bookplayer.activities;

import android.Manifest;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
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
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.services.ExportService;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.io.File;



public class ExportActivity extends LoggingActivity {

    public static final String EXTRA_DEST_FILE_FULL_PATH = "EXTRA_DEST_FILE_FULL_PATH";
    public static final String EXTRA_DEST_URI = "EXTRA_DEST_URI";

    public static final int REQUEST_CODE_destinationFolder = 35737;
    private static final String STATE_EXPORT_TREE_URI = "STATE_EXPORT_TREE_URI";
    private Uri exportTreeUri = null;  // user-chosen folder (tree) Uri

    Folder folder;
    File fileFolder;
    private ProgressBar progressBar;
    private TextView progressText, tvCurrentTrack, tvExportAudioBookName;
    private EditText etDestinationFileName;
    private Button btnExport, btnCancel, b_destinationFolder;

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
                tvCurrentTrack.setTextColor(getColor(R.color.bp_onSurface));
                progressText.setText(displayText);
                progressBar.setProgress(progress);
            });
            myLog("broadcast progress received " + progress + "% : " + currentTrack);
        }
    };

    private final BroadcastReceiver exportFailReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String displayText = intent.getStringExtra("displayText");
            uiHandler.post(() -> {
                tvCurrentTrack.setText(displayText);
                tvCurrentTrack.setTextColor(getColor(R.color.red_700));
            });
            myLogE("broadcast FAIL received - " + displayText);
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

        folder = getIntent().getParcelableExtra(Intents.EXTRA_FOLDER);
        if (folder == null) {
            myLogEE(null, "folder is null");
            finish();
            return;
        }

        //ScreenLock //TODO later, we will have a room table to store export and status, with livedata for observing fragment/activity
        int currentOrientation = getResources().getConfiguration().orientation;
        if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }


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

        myLog("folderPath : " + folder.getPath());
        fileFolder = new File(folder.getPath());
        if (!fileFolder.exists() || !fileFolder.isDirectory()) {
            tvExportAudioBookName.setText(getString(R.string.Export_display_no_valid_audiobook));
            tvExportAudioBookName.setTextColor(getColor(R.color.red_700));
            btnExport.setEnabled(false);
            return;
        }

        String destinationFileName = "BookplayerExport_" + fileFolder.getName();
        etDestinationFileName = findViewById(R.id.etDestinationFileName);
        etDestinationFileName.setText(destinationFileName);

        tvExportAudioBookName.setText(fileFolder.getName());

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
    }

    private void goExport() {
        btnExport.setEnabled(true);

        btnExport.setOnClickListener(v -> {
            String destinationFileName = etDestinationFileName.getText().toString();
            myLogI("--- user click goExport ---  , exportFileName = " + destinationFileName);
            if (destinationFileName.isEmpty() || destinationFileName.length()<3) {
                tvCurrentTrack.setText(getString(R.string.Export_Progress_file_name_should_have_at_east_3_chars));
                tvCurrentTrack.setTextColor(getColor(R.color.red_700));
                return;
            }

            Intent serviceIntent = new Intent(this, ExportService.class);
            serviceIntent.putExtra(Intents.EXTRA_BOOK_SOURCE_FOLDER, folder);

            if (exportTreeUri != null) { //user picked a specific folder
                // Create the destination ZIP file inside the chosen folder
                DocumentFile tree = DocumentFile.fromTreeUri(this, exportTreeUri);
                if (tree == null || !tree.canWrite()) {
                    tvCurrentTrack.setText(getString(R.string.Cannot_write_to_the_chosen_folder));
                    tvCurrentTrack.setTextColor(getColor(R.color.red_700));
                    return;
                }
                // Ensure a clean filename (no extension duplication)
                if (destinationFileName.endsWith(".zip")) destinationFileName = destinationFileName.substring(0, destinationFileName.length() - 4);
                DocumentFile zipDoc = tree.createFile("application/zip", destinationFileName);
                if (zipDoc == null) {
                    tvCurrentTrack.setText(getString(R.string.Failed_to_create_the_destionation_file));
                    tvCurrentTrack.setTextColor(getColor(R.color.red_700));
                    return;
                }

                // Pass Uri to the service
                myLog("Export destination (SAF) = " + zipDoc.getUri());
                serviceIntent.putExtra(EXTRA_DEST_URI, zipDoc.getUri().toString());

            } else {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    // Legacy: real path + WRITE_EXTERNAL_STORAGE
                    File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    File outFile = new File(downloadsDir, destinationFileName);
                    String detFileFullPath = outFile.getAbsolutePath();
                    myLog("Export destination (legacy Downloads path) = " + detFileFullPath);
                    serviceIntent.putExtra(EXTRA_DEST_FILE_FULL_PATH, detFileFullPath);

                } else {
                    // Android 10+ : use MediaStore.Downloads so it shows in the Downloads app
                    myLog("Creating export file via MediaStore.Downloads");

                    android.content.ContentValues values = new android.content.ContentValues();
                    values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, destinationFileName);
                    values.put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/zip");

                    // optional: subfolder inside Downloads (e.g. "Download/BookPlayer")
                    values.put(android.provider.MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/BookPlayer");

                    Uri downloadsUri = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                    Uri destUri = getContentResolver().insert(downloadsUri, values);
                    if (destUri == null) {
                        myToastE(getString(R.string.Failed_to_create_the_destionation_file));
                        myLogE("MediaStore insert returned null for Downloads");
                        return;
                    }

                    myLog("Export destination (MediaStore Downloads) = " + destUri);
                    serviceIntent.putExtra(EXTRA_DEST_URI, destUri.toString());
                }

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
        LocalBroadcastManager.getInstance(this).registerReceiver(exportFailReceiver, new IntentFilter("EXPORT_FAIL"));
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(exportProgressReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(exportDoneReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(exportFailReceiver);
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
                myLogI("--- user chooses folder ---, " + tree.getPath());
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
                if (fileFolder != null && fileFolder.exists()) btnExport.setEnabled(true);
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
