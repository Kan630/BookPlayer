package com.driot.bookplayer.imports;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.driot.bookplayer.utils.log.LoggerStaticHelper;

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

    // Additional state for UI (optional, helpful for TaskUiState)
    private final MutableLiveData<Integer> progressCurrent = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> progressTotal = new MutableLiveData<>(0);

    private MassImportScanner scanner;
    private Uri lastScannedUri;

    @Inject
    public MassImportRepository(@ApplicationContext Context context) {
        this.context = context;
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

        lastScannedUri = rootUri;
        isScanning.setValue(true);
        isScanFinished.setValue(false);
        candidates.setValue(Collections.emptyList());
        progressCurrent.setValue(0);
        progressTotal.setValue(0);
        progressText.setValue("Initializing scan...");

        final List<BookCandidate> runningList = new java.util.ArrayList<>();
        final long[] lastUpdate = { 0 };
        final long UPDATE_INTERVAL_MS = 250;

        scanner = new MassImportScanner(context, new MassImportScanner.Callback() {
            @Override
            public void onProgress(int current, int total, String currentPath) {
                // Throttle progress text updates?
                mainHandler.post(() -> {
                    progressText.setValue("Scanning " + current + "/" + total + ": " + currentPath);
                    progressCurrent.setValue(current);
                    progressTotal.setValue(total);
                });
            }

            @Override
            public void onFound(BookCandidate candidate) {
                runningList.add(candidate);
                long now = System.currentTimeMillis();
                if (now - lastUpdate[0] > UPDATE_INTERVAL_MS) {
                    lastUpdate[0] = now;
                    final List<BookCandidate> update = new java.util.ArrayList<>(runningList);
                    candidates.postValue(update);
                }
            }
        });

        executor.execute(() -> {
            List<BookCandidate> result = scanner.scan(rootUri);
            mainHandler.post(() -> {
                candidates.setValue(result);
                isScanning.setValue(false);
                isScanFinished.setValue(true);
                progressText.setValue("Scan complete. Found " + result.size() + " items.");
                progressCurrent.setValue(0); // Reset or keep? Resetting hides progress bar
                progressTotal.setValue(0);
            });
        });
    }

    public void cancelScan() {
        if (scanner != null) {
            scanner.cancel();
        }
        isScanning.setValue(false);
        isScanFinished.setValue(false);
    }

    // Clean up if needed, though Singleton lives forever
}
