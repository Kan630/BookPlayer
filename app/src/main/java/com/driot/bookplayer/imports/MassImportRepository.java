package com.driot.bookplayer.imports;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.driot.bookplayer.global.Option;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

@Singleton
public class MassImportRepository {

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final MutableLiveData<List<BookCandidate>> candidates = new MutableLiveData<>();
    private final MutableLiveData<String> progressText = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isScanning = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isScanFinished = new MutableLiveData<>(false);
    // 0 = Scanning (Yellow), 1 = Heavy Loading (Green), 2 = Done (Gone)
    private final MutableLiveData<Integer> loadingStatus = new MutableLiveData<>(2);

    // Additional state for UI (optional, helpful for OngoingTaskUiState)
    private final MutableLiveData<Integer> progressCurrent = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> progressTotal = new MutableLiveData<>(0);

    private MassImportScanner scanner;
    private Uri lastScannedUri;

    private static MassImportRepository instance;
    private volatile int scanId = 0;

    @Inject
    public MassImportRepository(@ApplicationContext Context context) {
        this.context = context;
        instance = this;
    }

    public static MassImportRepository getInstance() {
        return instance;
    }

    public LiveData<List<BookCandidate>> getCandidates() {
        return candidates;
    }

    public LiveData<String> getProgressText() {
        return progressText;
    }

    public LiveData<Boolean> getIsScanning() {
        return isScanning;
    }

    public LiveData<Boolean> getIsScanFinished() {
        return isScanFinished;
    }

    public LiveData<Integer> getLoadingStatus() {
        return loadingStatus;
    }

    public boolean hasResults() {
        return candidates.getValue() != null && !candidates.getValue().isEmpty();
    }

    public LiveData<Integer> getProgressCurrent() {
        return progressCurrent;
    }

    public LiveData<Integer> getProgressTotal() {
        return progressTotal;
    }

    public void startScan(Uri rootUri) {
        if (Boolean.TRUE.equals(isScanning.getValue()))
            return;

        // If we already have candidates for this URI, don't rescan (unless force?)
        // Similar logic to ViewModel
        if (rootUri.equals(lastScannedUri) && candidates.getValue() != null && !candidates.getValue().isEmpty()) {
            myLogD("Repository: Scan already completed for this URI, skipping rescan.");
            // We might want to notify that we are "done" or restore state
            isScanFinished.setValue(true);
            return;
        }

        int currentScanId = ++scanId;

        isScanning.setValue(true);
        isScanFinished.setValue(false);
        loadingStatus.setValue(0); // Scanning phase
        progressText.setValue("Initializing scan...");
        progressCurrent.setValue(0);
        progressTotal.setValue(0);
        // Do NOT clear candidates immediately if we want to support resume later?
        // But for now, clear them.
        candidates.setValue(new ArrayList<>());
        lastScannedUri = rootUri;

        // Reset scanner cancelled state if reused? No, we create new one.
        scanner = new MassImportScanner(context, new MassImportScanner.Callback() {
            @Override
            public void onProgress(String progressType, int current, int total, String currentPath) {
                if (scanId != currentScanId)
                    return;
                mainHandler.post(() -> {
                    if (scanId != currentScanId)
                        return;
                    if (("scanning").equalsIgnoreCase(progressType)) {
                        progressText.setValue("Scanning " + current + "/" + total + ": " + currentPath);
                    } else if (("counting").equalsIgnoreCase(progressType)) {
                        progressText.setValue("Counting...   " + current + " items found");
                    } else {
                        progressText.setValue("Error...");
                    }
                    progressCurrent.setValue(current);
                    progressTotal.setValue(total);
                });
            }

            @Override
            public void onFound(BookCandidate candidate) {
                if (scanId != currentScanId)
                    return;
                // Simplified: we'll post everything at the end of Phase 1 or throttle
                // For Phase 1 (Scanning), we don't necessarily need to update the list live
                // if it's very fast, but let's keep it responsive.
                mainHandler.post(() -> {
                    if (scanId != currentScanId)
                        return;
                    List<BookCandidate> currentList = candidates.getValue();
                    if (currentList == null)
                        currentList = new ArrayList<>();
                    currentList.add(candidate);
                    candidates.setValue(new ArrayList<>(currentList));
                });
            }
        });

        executor.execute(() -> {
            boolean includeSubfolders = Option.getMassImportIncludeSubfolders();
            List<BookCandidate> result = scanner.scan(rootUri, includeSubfolders);

            if (scanId != currentScanId || result.isEmpty()) {
                mainHandler.post(() -> {
                    if (scanId == currentScanId) {
                        isScanning.setValue(false);
                        isScanFinished.setValue(true);
                        loadingStatus.setValue(2);
                        progressText.setValue("No books found.");
                    }
                });
                return;
            }
            myLog("-------------------------------------------------------------");
            myLogI("start EASY ENRICHMENT");
            myLog("-------------------------------------------------------------");

            // PHASE 2: EASY ENRICHMENT
            mainHandler.post(() -> {
                progressText.setValue("Initializing " + result.size() + " books...");
                progressTotal.setValue(result.size());
            });

            for (int i = 0; i < result.size(); i++) {
                if (scanId != currentScanId)
                    return;
                BookCandidate c = result.get(i);
                int current = i + 1;
                int total = result.size();
                c.isEasyCalculating = true;
                mainHandler.post(() -> {
                    progressText.setValue("checking " + current + "/" + total + "... (if not already imported)");
                    progressCurrent.setValue(current);
                    candidates.setValue(result); // Refresh list for names/types and highlighting
                });

                c.loadEasyMetadata(context);

                c.isEasyCalculating = false;
            }

            mainHandler.post(() -> {
                // Update scanning state AFTER Easy Enrichment
                isScanning.setValue(false);
                isScanFinished.setValue(true);
                progressText.setValue("Found " + result.size() + " items.");
                progressCurrent.setValue(0);
                progressTotal.setValue(0);

                // Start Phase 3: Heavy Load automatically
                startHeavyLoad(result, currentScanId);
            });
        });
    }

    private void startHeavyLoad(List<BookCandidate> candidates, int processingScanId) {
        if (candidates == null || candidates.isEmpty())
            return;

        myLog("-------------------------------------------------------------");
        myLogI("start HEAVY ENRICHMENT : Heavy Load for " + candidates.size() + " candidates.");
        myLog("-------------------------------------------------------------");
        loadingStatus.postValue(1); // Heavy Loading phase

        // Use the single-thread repository executor for sequential processing
        executor.execute(() -> {
            int total = candidates.size();

            for (int i = 0; i < total; i++) {
                // Check if scan cancelled or changed
                if (scanId != processingScanId)
                    break;
                if (Thread.currentThread().isInterrupted())
                    break;

                BookCandidate candidate = candidates.get(i);

                // Skip if already loaded (unlikely here but ensuring idempotency)
                if (!candidate.isHeavyLoaded) {
                    // Update UI to show "calculating..."
                    candidate.isCalculating = true;
                    mainHandler.post(() -> {
                        if (scanId == processingScanId) {
                            this.candidates.setValue(candidates);
                        }
                    });

                    try {
                        // Throttled UI update listener
                        BookCandidate.OnMetadataListener listener = new BookCandidate.OnMetadataListener() {
                            long lastUpdate = 0;

                            @Override
                            public void onTrackFound(String name) {
                                long now = System.currentTimeMillis();
                                if (now - lastUpdate > 500) { // Update every 500ms
                                    lastUpdate = now;
                                    mainHandler.post(() -> {
                                        if (scanId == processingScanId) {
                                            MassImportRepository.this.candidates.setValue(candidates);
                                        }
                                    });
                                }
                            }

                            @Override
                            public void onCoverFound(String imagePath) {
                                mainHandler.post(() -> {
                                    if (scanId == processingScanId) {
                                        MassImportRepository.this.candidates.setValue(candidates);
                                    }
                                });
                            }
                        };
                        candidate.loadHeavyMetadata(context, listener);
                    } catch (Exception e) {
                        myLogEE(e, "Error inside loadHeavyMetadata for " + candidate.name);
                    }

                    candidate.isCalculating = false;
                }

                if (scanId != processingScanId)
                    break;

                // Update UI immediately after this candidate is done
                // We must post to main thread to update LiveData
                mainHandler.post(() -> {
                    if (scanId == processingScanId) {
                        // Notify observers. Since the list reference is the same,
                        // observers relying on DiffUtil might need a nudge, but
                        // Adapter.setItems simply notifies changed, which redraws visible items.
                        this.candidates.setValue(candidates);
                    }
                });
            }

            // Final update/log
            mainHandler.post(() -> {
                if (scanId == processingScanId) {
                    loadingStatus.setValue(2); // Done
                    myLog("-------------------------------------");
                    myLogI("-- MASS SCANNING COMPLETED --");
                    myLog("-------------------------------------");
                }
            });
        });
    }

    public void cancelScan() {
        scanId++; // Invalidate current scan
        if (scanner != null) {
            scanner.cancel();
        }
        isScanning.setValue(false);
        isScanFinished.setValue(false);
        loadingStatus.setValue(2);
        candidates.setValue(Collections.emptyList());

        // Clean up temp images
        executor.execute(() -> {
            com.driot.bookplayer.helpers.ImageHelper.deleteAllTempImages(context);
        });
    }

    public void consumeScanState() {
        isScanning.setValue(false);
        isScanFinished.setValue(false);
        loadingStatus.setValue(2);
        candidates.setValue(Collections.emptyList());
        // Do NOT delete temp images here, they are needed for import
    }

    // Clean up if needed, though Singleton lives forever
}
