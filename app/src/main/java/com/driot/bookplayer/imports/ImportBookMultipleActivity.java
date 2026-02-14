package com.driot.bookplayer.imports;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.BaseBottomNavActivity;
import com.driot.bookplayer.activities.SettingsHostActivity;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.settings.ui.MassiveImportSettingsFragment;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.widgets.StorageBarView;

import java.io.File;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ImportBookMultipleActivity extends BaseBottomNavActivity {

    public static final String EXTRA_URI = "EXTRA_URI";

    private MassImportViewModel viewModel;
    private CandidateAdapter adapter;
    private Uri rootUri;

    // UI Elements
    private LinearLayout llScanning;
    private TextView tvProgress;
    private TextView tvCount;
    private Button btnConfirmImport;
    private StorageBarView storageBarInternal;
    private StorageBarView storageBarSdCard;
    private TextView tvStorageLabelInternal;
    private LinearLayout llSdCardStorage;
    private LinearLayout llStorageSection;
    private TextView tvSelectedSummary;

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
        
        // If we already have candidates (re-entry), calculate storage once
        if (hasState) {
            recalculateStorageBar();
        }

        // Override back press to navigate directly to MainActivity (bypassing
        // AddBookActivity)
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Do NOT (longer) cancel scan on back press. Just navigate back.
                navigateBackToMain();
            }
        });
        findViewById(R.id.btnCancel).setOnClickListener(v -> {
            viewModel.cancelScan();
            navigateBackToMain();
        });

        btnConfirmImport.setOnClickListener(v -> {
            myLogI("user clicked CONFIRM");
            startImport();
        });

        findViewById(R.id.ibSettings).setOnClickListener(v -> clickSettings());

        // Start scanning automatically only if not already done
        // This prevents recomputation on rotation
        // Start scanning automatically only if not already done and we have a URI
        if (rootUri != null) {
            viewModel.startScan(rootUri);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // When returning from Massive Import settings, refresh storage section visibility
        if (llStorageSection != null) {
            boolean showStorageBar = Option.getMassImportDisplayStorageBar();
            llStorageSection.setVisibility(showStorageBar ? View.VISIBLE : View.GONE);
            if (showStorageBar) {
                recalculateStorageBar();
            }
        }
    }

    private void navigateBackToMain() {
        android.content.Intent intent = new android.content.Intent(this,
                com.driot.bookplayer.activities.MainActivity.class);
        intent.addFlags(
                android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    /** Opens Massive Import settings fragment (Display Storage Bar, etc.) in SettingsHostActivity. */
    private void clickSettings() {
        myLogI("--- User clicks MASSIVE IMPORT SETTINGS ---");
        SettingsHostActivity.start(this, MassiveImportSettingsFragment.class, true, R.string.Mass_Import);
    }

    private void initializeViews() {
        llScanning = findViewById(R.id.llScanning);
        tvProgress = findViewById(R.id.tvProgress);
        tvCount = findViewById(R.id.tvCount);
        btnConfirmImport = findViewById(R.id.btnConfirmImport);
        storageBarInternal = findViewById(R.id.storageBarInternal);
        storageBarSdCard = findViewById(R.id.storageBarSdCard);
        tvStorageLabelInternal = findViewById(R.id.tvStorageLabelInternal);
        llSdCardStorage = findViewById(R.id.llSdCardStorage);
        llStorageSection = findViewById(R.id.llStorageSection);
        tvSelectedSummary = findViewById(R.id.tvSelectedSummary);

        // Storage section visible only when "Display Storage Bar" is enabled in Settings > Massive Import
        boolean showStorageBar = Option.getMassImportDisplayStorageBar();
        llStorageSection.setVisibility(showStorageBar ? View.VISIBLE : View.GONE);

        // Observe internal storage: one bar with label "Storage" or "Device storage" (when SD exists)
        viewModel.getInternalStorageInfo().observe(this, info -> {
            if (info == null || storageBarInternal == null || !Option.getMassImportDisplayStorageBar()) return;
            if (info.totalStorageBytes > 0) {
                storageBarInternal.setStorageValues(
                    info.totalStorageBytes,
                    info.usedByOthersBytes,
                    info.usedByBookPlayerBytes,
                    info.expectedAddedMemoryBytes,
                    info.linkedAudiosBytes
                );
                storageBarInternal.setVisibility(View.VISIBLE);
                llStorageSection.setVisibility(View.VISIBLE);
            }
        });

        // Observe SD card storage: second bar when SD exists; update internal label to "Device storage"
        viewModel.getSdCardStorageInfo().observe(this, info -> {
            if (tvStorageLabelInternal != null) {
                tvStorageLabelInternal.setText(info != null ? R.string.storage_device : R.string.Storage);
            }
            if (llSdCardStorage == null || storageBarSdCard == null) return;
            if (info != null && info.totalStorageBytes > 0 && Option.getMassImportDisplayStorageBar()) {
                storageBarSdCard.setStorageValues(
                    info.totalStorageBytes,
                    info.usedByOthersBytes,
                    info.usedByBookPlayerBytes,
                    info.expectedAddedMemoryBytes,
                    info.linkedAudiosBytes
                );
                llSdCardStorage.setVisibility(View.VISIBLE);
            } else {
                llSdCardStorage.setVisibility(View.GONE);
            }
        });

        // Load storage immediately from cache (don't wait for scan to finish)
        viewModel.updateStorageInfo(0);
    }

    /** Updates "x book candidates selected for import (size)" from current selection. */
    private void updateSelectedSummary() {
        if (adapter == null || tvSelectedSummary == null) return;
        List<BookCandidate> items = adapter.getItems();
        int selectedCount = 0;
        long selectedSize = 0;
        for (BookCandidate c : items) {
            if (c.isSelected() && !c.isAlreadyImported()) {
                selectedCount++;
                selectedSize += c.size;
            }
        }
        String sizeStr = com.driot.bookplayer.utils.Tonio.getReadableSize(selectedSize);
        tvSelectedSummary.setText(getString(R.string.mass_import_selected_summary, selectedCount, sizeStr));
    }
    
    private void recalculateStorageBar() {
        if (adapter == null) return;

        List<BookCandidate> candidates = adapter.getItems();

        // Expected memory = selected candidates that need copying (not Folder)
        long expectedSize = 0;
        for (BookCandidate c : candidates) {
            if (c.isSelected() && !c.isAlreadyImported() && !"Folder".equals(c.type)) {
                expectedSize += c.size;
            }
        }

        viewModel.updateStorageInfo(expectedSize);
    }

    private void setupRecyclerView() {
        RecyclerView rv = findViewById(R.id.rvCandidates);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CandidateAdapter();
        adapter.setOnSelectionChanged(() -> {
            updateSelectedSummary();
            recalculateStorageBar();
        });
        rv.setAdapter(adapter);
    }

    private void observeViewModel() {
        viewModel.getIsScanning().observe(this, isScanning -> {
            if (isScanning) {
                llScanning.setVisibility(View.VISIBLE);
                btnConfirmImport.setEnabled(false);
            } else {
                llScanning.setVisibility(View.GONE);
                // Update selection summary and storage bar when scan completes
                updateSelectedSummary();
                recalculateStorageBar();
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

            String sizeStr = com.driot.bookplayer.utils.Tonio.getReadableSize(totalSize);

            if (importableCount < totalCount) {
                int importedCount = totalCount - importableCount;
                tvCount.setText(
                        getString(R.string.mass_import_found_items_mixed, importableCount, sizeStr, importedCount));
            } else {
                tvCount.setText(getString(R.string.mass_import_found_items, importableCount, sizeStr));
            }

            if (importableCount == 0 && Boolean.FALSE.equals(viewModel.getIsScanning().getValue())) {
                if (totalCount > 0) {
                    tvCount.setText(getString(R.string.mass_import_all_imported, totalCount));
                } else {
                    tvCount.setText(getString(R.string.no_items_found));
                }
                btnConfirmImport.setEnabled(false);
            } else {
                boolean scanning = Boolean.TRUE.equals(viewModel.getIsScanning().getValue());
                btnConfirmImport.setEnabled(importableCount > 0 && !scanning);
            }

            updateSelectedSummary();
            recalculateStorageBar();
        });
    }

    private void startImport() {
        List<BookCandidate> candidates = adapter.getItems();
        if (candidates.isEmpty())
            return;

        // Only proceed with selected candidates (and skip already-imported)
        int batchTotal = 0;
        for (BookCandidate candidate : candidates) {
            if (candidate.isSelected() && !candidate.isAlreadyImported()) {
                batchTotal++;
            }
        }
        if (batchTotal == 0) {
            myToast(getString(R.string.select_at_least_one_item));
            return;
        }

        myLog("Starting import of " + batchTotal + " selected items.");

        // Disable button to prevent double click
        btnConfirmImport.setEnabled(false);

        final int totalInBatch = batchTotal;
        new Thread(() -> {
            int batchIndex = 0;
            for (BookCandidate candidate : candidates) {
                // Skip non-selected or already-imported items
                if (!candidate.isSelected()) {
                    continue;
                }
                if (candidate.isAlreadyImported()) {
                    myLog(getString(R.string.skippring_already_imported_item) + ": [" + candidate.name + "] ("
                            + getString(R.string.imported_as) + ": " + candidate.existingBookName + ")");
                    continue;
                }
                
                batchIndex++; // Increment before creating job (1-based)

                ImportBookTaskState s = new ImportBookTaskState();
                // Set batch tracking info
                s.batchIndex = batchIndex;
                s.batchTotal = totalInBatch;
                // Format the name for display (remove underscores, extension, etc.)
                String formattedName = com.driot.bookplayer.utils.Tonio.formatNameForDisplay(candidate.name);

                s.title = formattedName;
                s.originalUri = candidate.uri;
                s.originalType = candidate.type;
                s.dynamicUri = candidate.uri;
                s.dynamicType = candidate.type; // Folder, ZIP, M4B, Ebook
                s.sourceLocation = "MassImport"; // Prevent NPE

                // Use global copy file preference
                boolean copyEnabled = Option.getCopyFile();

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
                        s.optionCopy = true; // ZIP always requires copy
                    } else {
                        // Single file (M4B, Ebook)
                        s.fileExtension = com.driot.bookplayer.utils.Tonio.getExtension(candidate.name);
                        // M4B always requires copy, other types only if checkbox is enabled
                        if ("M4B".equals(candidate.type) || "m4b".equalsIgnoreCase(s.fileExtension)) {
                            s.optionCopy = true; // M4B always requires copy
                            s.optionSplit = Option.getSplitM4b();
                        } else {
                            s.optionCopy = copyEnabled; // Ebook and other types only copy if checkbox enabled
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

            runOnUiThread(() -> {
                // Clean up the scan state (fragment) but keep files
                // Must be called on main thread since it updates LiveData
                viewModel.consumeScanState();

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
