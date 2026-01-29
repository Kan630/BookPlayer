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
        return false;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        viewModel = new ViewModelProvider(this).get(MassImportViewModel.class);

        rootUri = getIntent().getParcelableExtra(EXTRA_URI);
        // If we don't have a URI, check if we already have results (re-entry from
        // notification/banner)
        boolean hasState = viewModel.getCandidates().getValue() != null
                && !viewModel.getCandidates().getValue().isEmpty();

        if (rootUri == null && !hasState) {
            myToast(getString(com.driot.bookplayer.R.string.error_no_folder_selected));
            finish();
            return;
        }

        initializeViews();

        setupRecyclerView();
        observeViewModel();

        btnCancelScan.setOnClickListener(v -> {
            viewModel.cancelScan();
            finish();
        });

        findViewById(R.id.btnCancel).setOnClickListener(v -> {
            viewModel.cancelScan();
            finish();
        });

        btnConfirmImport.setOnClickListener(v -> {
            startImport();
        });

        // Start scanning automatically only if not already done
        // This prevents recomputation on rotation
        // Start scanning automatically only if not already done and we have a URI
        if (rootUri != null) {
            viewModel.startScan(rootUri);
        }
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
                // Keep list visible to show progress
                clReport.setVisibility(View.VISIBLE);
                btnConfirmImport.setEnabled(false);
            } else {
                llScanning.setVisibility(View.GONE);
                clReport.setVisibility(View.VISIBLE);
                // Button state is handled by candidates observer
            }
        });

        viewModel.getProgressText().observe(this, text -> {
            tvProgress.setText(text);
        });

        viewModel.getCandidates().observe(this, candidates -> {
            adapter.setItems(candidates);

            // Filter out already-imported items for count and size
            List<BookCandidate> importableCandidates = new java.util.ArrayList<>();
            long totalSize = 0;
            for (BookCandidate c : candidates) {
                if (!c.isAlreadyImported()) {
                    importableCandidates.add(c);
                    totalSize += c.size;
                }
            }

            int importableCount = importableCandidates.size();
            int totalCount = candidates.size();
            String countText = importableCount + (importableCount > 1 ? " books" : " book");
            if (importableCount < totalCount) {
                // If some items are hidden/filtered, mention it? Or just show importable count.
                // User logic in previous code showed mixed.
                // Let's stick to "X books (Total Size) (+ Y already imported)" or simpler.
                // User said "3 books" instead of "Found 3 items".
                // I'll make it: "3 books ({size}) (+ 2 already imported)"
                tvCount.setText(countText + " (" + com.driot.bookplayer.utils.Tonio.getReadableSize(totalSize) + ") (+ "
                        + (totalCount - importableCount) + " already imported)");
            } else {
                tvCount.setText(countText + " (" + com.driot.bookplayer.utils.Tonio.getReadableSize(totalSize) + ")");
            }

            if (importableCount == 0 && Boolean.FALSE.equals(viewModel.getIsScanning().getValue())) {
                if (totalCount > 0) {
                    tvCount.setText(getString(R.string.mass_import_all_imported, totalCount));
                } else {
                    tvCount.setText(getString(R.string.no_items_found));
                }
                btnConfirmImport.setEnabled(false);
            } else {
                // Only enable if not scanning
                boolean scanning = Boolean.TRUE.equals(viewModel.getIsScanning().getValue());
                btnConfirmImport.setEnabled(importableCount > 0 && !scanning);
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
                // Skip already-imported items
                if (candidate.isAlreadyImported()) {
                    myLog(getString(R.string.skippring_already_imported_item) + ": [" + candidate.name + "] ("
                            + getString(R.string.imported_as) + ": " + candidate.existingBookName + ")");
                    continue;
                }

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

                // Use pre-computed originalHash from scanning (computed in MassImportScanner)
                s.originalHash = candidate.originalHash;
                if (s.originalHash != null && !s.originalHash.isEmpty()) {
                    myLog("Using pre-computed originalHash for [" + s.title + "]: " + s.originalHash);
                } else {
                    myLogW("No originalHash available for [" + s.title
                            + "] (hash computation may have failed during scanning)");
                }

                // Pass the cover image path if one was detected during scanning
                s.imagePath = candidate.coverImagePath;

                myLog("Launching import task for [" + s.title + "]:");
                myLog(" - type: " + s.dynamicType);
                myLog(" - uri: " + s.dynamicUri);
                myLog(" - futurePath: " + s.futureFolderPath);
                myLog(" - copy: " + s.optionCopy);
                myLog(" - originalHash: " + s.originalHash);
                myLog(" - imagePath: " + s.imagePath);

                // Launch sequential to prevent cover association issues and mixed progress
                // messages
                // Books will be processed one after another in a queue
                BookLoadingWorkLauncher.launch(getApplicationContext(), s, true);
            }

            // Clean up the scan state (fragment) but keep files
            viewModel.consumeScanState();

            runOnUiThread(() -> {
                int importableCount = 0;
                for (BookCandidate c : candidates) {
                    if (!c.isAlreadyImported()) {
                        importableCount++;
                    }
                }
                if (importableCount > 0) {
                    Toast.makeText(this, getString(R.string.import_started_for) + " " + importableCount + " book"
                            + (importableCount > 1 ? "s" : ""), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, getString(R.string.massimport_all_aready_imported), Toast.LENGTH_SHORT).show();
                }
                finish();
            });
        }).start();
    }
}
