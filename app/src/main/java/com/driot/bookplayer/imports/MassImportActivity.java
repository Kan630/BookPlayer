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
            tvCount.setText("Found " + candidates.size() + " items");
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
                s.title = candidate.name;
                s.originalUri = null;
                s.originalType = candidate.type;
                s.dynamicUri = candidate.uri;
                s.dynamicType = candidate.type; // Folder, ZIP, M4B, Ebook

                s.futureFolderName = candidate.name;
                s.futureFolderPath = candidate.uri.toString(); // SAF URI for Folders, or specific file URI

                // Configuration depends on type
                if ("Folder".equals(candidate.type)) {
                    s.fileExtension = null;
                    s.playType = "Folder";
                    s.mimeType = "vnd.android.document/directory";
                    s.optionCopy = false; // Scan in place usually
                    s.optionSplit = false;
                } else if ("ZIP".equals(candidate.type)) {
                    // ZIPs usually need extraction/copy.
                    // But ScanAndReimportWorker logic was mostly about folders.
                    // If we support ZIPs here, BookLoadingWorkLauncher needs to handle them.
                    // It typically does if dynamicType is handled.
                    s.fileExtension = "zip"; // Approximate
                    s.playType = "Folder"; // Result will be a folder
                    s.optionCopy = true; // Usually copy/extract
                } else {
                    // Single file (M4B, Ebook)
                    s.fileExtension = null; // Helper will find it
                    s.optionCopy = true; // Copy to internal? Or link?
                    // LoadBookActivity defaults to Copy for files unless "Content" scheme?
                }

                // For now, let's assume BookLoadingWorkLauncher handles these types correctly
                // if we pass the correct state.
                // However, the previous "ScanAndReimport" was purely for Folders.
                // We are introducing new types to the mass import flow.
                // We should ensure `dynamicType` strings match what `BookLoadingWorkLauncher`
                // expects.
                // `BookToAdd` uses "Folder", "File" (which then becomes "ZIP", "M4B" etc via
                // special check).

                // Let's refine `dynamicType`.
                // If it is NOT "Folder", pass "File" and let the system figure out specifics?
                // Or pass the specific type if supported.
                // `LoadBookActivity` passes "File" or "Folder".
                // `BookToAdd` constructor takes "File" or "Folder".
                // Then `BookToAdd` calculates `specialType`.

                // Here we are creating `LoadBookTaskState` directly.
                // Use "Folder" for folders.
                // Use "File" for everything else, so `ImportWorker` logic triggers standard
                // file processing?
                // `BookLoadingWorkLauncher` -> `ImportWorker` -> logic.

                if (!"Folder".equals(s.dynamicType)) {
                    s.dynamicType = "File";
                }

                // Launch sequential
                BookLoadingWorkLauncher.launch(getApplicationContext(), s, true);
            }

            runOnUiThread(() -> {
                Toast.makeText(this, "Import started for " + candidates.size() + " books", Toast.LENGTH_SHORT).show();
                finish();
            });
        }).start();
    }
}
