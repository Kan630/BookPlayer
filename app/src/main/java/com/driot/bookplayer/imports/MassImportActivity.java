package com.driot.bookplayer.imports;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.BaseBottomNavActivity;
import com.driot.bookplayer.objects.LoadBookTaskState;

import java.util.List;

import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.helpers.StorageHelper;
import java.io.File;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MassImportActivity extends BaseBottomNavActivity {

    public static final String EXTRA_URI = "EXTRA_URI";

    private MassImportViewModel viewModel;
    private CandidateAdapter adapter;
    private Uri rootUri;

    // UI Elements
    private LinearLayout llScanning;
    private View clReport;
    private TextView tvProgress;
    private TextView tvCount;
    private Button btnConfirmImport;
    private Button btnCancelScan;

    @Override
    protected int getNavId() {
        return R.id.nav_add;
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_mass_import;
    }

    @Override
    protected boolean enableOngoingTaskOverlay() {
        return true;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        rootUri = getIntent().getParcelableExtra(EXTRA_URI);
        if (rootUri == null) {
            myToast("Error: No folder selected.");
            finish();
            return;
        }

        initializeViews();

        viewModel = new ViewModelProvider(this).get(MassImportViewModel.class);

        setupRecyclerView();
        observeViewModel();

        btnCancelScan.setOnClickListener(v -> {
            viewModel.cancelScan();
            finish();
        });

        findViewById(R.id.btnCancel).setOnClickListener(v -> finish());

        btnConfirmImport.setOnClickListener(v -> {
            startImport();
        });

        // Start scanning automatically
        viewModel.startScan(rootUri);
    }

    private void initializeViews() {
        llScanning = findViewById(R.id.llScanning);
        clReport = findViewById(R.id.clReport);
        tvProgress = findViewById(R.id.tvProgress);
        tvCount = findViewById(R.id.tvCount);
        btnConfirmImport = findViewById(R.id.btnConfirmImport);
        btnCancelScan = findViewById(R.id.btnCancelScan);
    }

    private void setupRecyclerView() {
        RecyclerView rv = findViewById(R.id.rvCandidates);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CandidateAdapter();
        rv.setAdapter(adapter);
    }

    private void observeViewModel() {
        viewModel.getIsScanning().observe(this, isScanning -> {
            if (isScanning) {
                llScanning.setVisibility(View.VISIBLE);
                clReport.setVisibility(View.GONE);
                btnConfirmImport.setEnabled(false);
            } else {
                llScanning.setVisibility(View.GONE);
                clReport.setVisibility(View.VISIBLE);
                btnConfirmImport.setEnabled(true);
            }
        });

        viewModel.getProgressText().observe(this, text -> {
            tvProgress.setText(text);
        });

        viewModel.getCandidates().observe(this, candidates -> {
            adapter.setItems(candidates);

            long totalSize = 0;
            for (BookCandidate c : candidates) {
                totalSize += c.size;
            }

            tvCount.setText("Found " + candidates.size() + " items ("
                    + com.driot.bookplayer.utils.Tonio.getReadableSize(totalSize) + ")");

            if (candidates.isEmpty() && Boolean.FALSE.equals(viewModel.getIsScanning().getValue())) {
                tvCount.setText("No items found.");
                btnConfirmImport.setEnabled(false);
            }
        });
    }

    private void startImport() {
        List<BookCandidate> candidates = adapter.getItems();
        if (candidates.isEmpty())
            return;

        myLog("Starting import of " + candidates.size() + " items.");

        // Disable button to prevent double click
        btnConfirmImport.setEnabled(false);

        new Thread(() -> {
            for (BookCandidate candidate : candidates) {
                LoadBookTaskState s = new LoadBookTaskState();
                // Format the name for display (remove underscores, extension, etc.)
                String formattedName = com.driot.bookplayer.utils.Tonio.formatNameForDisplay(candidate.name);

                s.title = formattedName;
                s.originalUri = candidate.uri;
                s.originalType = candidate.type;
                s.dynamicUri = candidate.uri;
                s.dynamicType = candidate.type; // Folder, ZIP, M4B, Ebook
                s.sourceLocation = "MassImport"; // Prevent NPE

                // Correctly configure path and options based on type
                if ("Folder".equals(candidate.type)) {
                    s.futureFolderName = formattedName;
                    s.futureFolderPath = candidate.uri.toString(); // For folders, this is the source (in-place)
                    s.fileExtension = null;
                    s.playType = "Folder";
                    s.mimeType = "vnd.android.document/directory";
                    s.optionCopy = false; // Scan in place
                    s.optionSplit = false;
                } else {
                    // Files (ZIP, M4B, Ebook) must be copied/extracted to internal storage (or
                    // configured SD card)
                    boolean useSd = Option.getUseSdCard();
                    File root = StorageHelper.getUnzipFolder(this, useSd);
                    s.futureFolderName = formattedName;
                    // Important: Destination path, not source path
                    s.futureFolderPath = new File(root, formattedName).getAbsolutePath();

                    if ("ZIP".equals(candidate.type)) {
                        s.fileExtension = "zip";
                        s.playType = "Folder";
                        s.optionCopy = true;
                    } else {
                        // Single file (M4B, Ebook)
                        s.fileExtension = com.driot.bookplayer.utils.Tonio.getExtension(candidate.name);
                        s.optionCopy = true;
                        if ("m4b".equalsIgnoreCase(s.fileExtension)) {
                            s.optionSplit = Option.getSplitM4b();
                        }
                    }

                    // For all file types (ZIP, M4B, Ebook), we must provide the filename
                    // so that CopyFileWorker knows what name to use (and doesn't create a "null"
                    // file)
                    s.originalFile = candidate.name;

                    if (!"Folder".equals(s.dynamicType)) {
                        s.dynamicType = "File";
                    }

                }

                myLog("Launching import task for [" + s.title + "]:");
                myLog(" - type: " + s.dynamicType);
                myLog(" - uri: " + s.dynamicUri);
                myLog(" - futurePath: " + s.futureFolderPath);
                myLog(" - copy: " + s.optionCopy);

                // Launch sequential to prevent cover association issues and mixed progress messages
                // Books will be processed one after another in a queue
                BookLoadingWorkLauncher.launch(getApplicationContext(), s, true);
            }

            runOnUiThread(() -> {
                Toast.makeText(this, "Import started for " + candidates.size() + " books", Toast.LENGTH_SHORT).show();
                finish();
            });
        }).start();
    }
}
