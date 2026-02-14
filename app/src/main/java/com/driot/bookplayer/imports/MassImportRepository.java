package com.driot.bookplayer.imports;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.utils.log.LoggerStaticHelper;

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
            LoggerStaticHelper.myLogD("Repository: Scan already completed for this URI, skipping rescan.");
            // We might want to notify that we are "done" or restore state
            isScanFinished.setValue(true);
            return;
        }

        int currentScanId = ++scanId;

        isScanning.setValue(true);
        isScanFinished.setValue(false);
        progressText.setValue("Initializing scan...");
        progressCurrent.setValue(0);
        progressTotal.setValue(0);
        // Do NOT clear candidates immediately if we want to support resume later?
        // But for now, clear them.
        candidates.setValue(new ArrayList<>());
        lastScannedUri = rootUri;

        // Reset scanner cancelled state if reused? No, we create new one.
        scanner = new MassImportScanner(context, new MassImportScanner.Callback() {
            private long[] lastUpdate = new long[] { 0 };
            private List<BookCandidate> runningList = new ArrayList<>();
            final long UPDATE_INTERVAL_MS = 250;

            @Override
            public void onProgress(int current, int total, String currentPath) {
                if (scanId != currentScanId)
                    return;
                mainHandler.post(() -> {
                    if (scanId != currentScanId)
                        return;
                    progressText.setValue("Scanning " + current + "/" + total + ": " + currentPath);
                    progressCurrent.setValue(current);
                    progressTotal.setValue(total);
                });
            }

            @Override
            public void onFound(BookCandidate candidate) {
                if (scanId != currentScanId)
                    return;
                runningList.add(candidate);
                long now = System.currentTimeMillis();
                if (now - lastUpdate[0] > UPDATE_INTERVAL_MS) {
                    lastUpdate[0] = now;
                    final List<BookCandidate> update = new java.util.ArrayList<>(runningList);
                    if (scanId == currentScanId) {
                        candidates.postValue(update);
                    }
                }
            }
        });

        executor.execute(() -> {
            boolean includeSubfolders = Option.getMassImportIncludeSubfolders();
            List<BookCandidate> result = scanner.scan(rootUri, includeSubfolders);
            mainHandler.post(() -> {
                // Robust cancellation check using scanId
                if (scanId != currentScanId) {
                    LoggerStaticHelper.myLogD("Scan result ignored because scanId mismatch (cancelled?)");
                    return;
                }

                // Update scanning state FIRST so observers who check it (like candidates
                // observer) see the correct state
                isScanning.setValue(false);
                candidates.setValue(result);
                isScanFinished.setValue(true);
                progressText.setValue("Scan complete. Found " + result.size() + " items.");
                progressCurrent.setValue(0);
                progressTotal.setValue(0);
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
        candidates.setValue(Collections.emptyList());

        // Clean up temp images
        executor.execute(() -> {
            com.driot.bookplayer.helpers.ImageHelper.deleteAllTempImages(context);
        });
    }

    public void consumeScanState() {
        isScanning.setValue(false);
        isScanFinished.setValue(false);
        candidates.setValue(Collections.emptyList());
        // Do NOT delete temp images here, they are needed for import
    }

    // Clean up if needed, though Singleton lives forever
}
