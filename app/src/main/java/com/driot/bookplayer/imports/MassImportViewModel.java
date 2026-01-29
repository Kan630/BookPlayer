package com.driot.bookplayer.imports;

import android.app.Application;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.driot.bookplayer.utils.log.LoggingAndroidViewModel;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MassImportViewModel extends LoggingAndroidViewModel {

    private final MutableLiveData<List<BookCandidate>> candidates = new MutableLiveData<>();
    private final MutableLiveData<String> progressText = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isScanning = new MutableLiveData<>(false);

    private MassImportScanner scanner;
    private Uri lastScannedUri; // Store the last scanned URI to prevent rescanning on rotation
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public MassImportViewModel(@NonNull Application application) {
        super(application);
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

    public void startScan(Uri rootUri) {
        // Don't restart scan if already scanning
        if (Boolean.TRUE.equals(isScanning.getValue()))
            return;

        // If we already have candidates for this URI, don't rescan (prevents
        // recomputation on rotation)
        if (rootUri.equals(lastScannedUri) && candidates.getValue() != null && !candidates.getValue().isEmpty()) {
            myLogD("Scan already completed for this URI, skipping rescan. Candidates: " + candidates.getValue().size());
            return;
        }

        lastScannedUri = rootUri;
        isScanning.setValue(true);
        candidates.setValue(Collections.emptyList());

        // List to accumulate results for real-time updates
        // Use ArrayList (single threaded scanner) instead of CopyOnWriteArrayList
        // (O(N^2) on adds)
        final List<BookCandidate> runningList = new java.util.ArrayList<>();

        // Throttling mechanism
        final long[] lastUpdate = { 0 };
        final long UPDATE_INTERVAL_MS = 250;

        scanner = new MassImportScanner(getApplication(), new MassImportScanner.Callback() {
            @Override
            public void onProgress(String currentPath) {
                // Throttle progress updates if needed, but for now direct post is okay
                // mainHandler.post(() -> progressText.setValue("Scanning: " + currentPath));
                // Optimization: use postValue which drops intermediate values if too fast
                progressText.postValue("Scanning: " + currentPath);
            }

            @Override
            public void onFound(BookCandidate candidate) {
                runningList.add(candidate);

                long now = System.currentTimeMillis();
                if (now - lastUpdate[0] > UPDATE_INTERVAL_MS) {
                    myLogD("ViewModel: Throttled update triggered. Posting " + runningList.size() + " candidates.");
                    lastUpdate[0] = now;
                    // Create copy for LiveData
                    final List<BookCandidate> update = new java.util.ArrayList<>(runningList);
                    candidates.postValue(update);
                } else {
                    // myLogD("ViewModel: Update throttled (buffer: " + runningList.size() + ")");
                }
            }
        });

        executor.execute(() -> {
            List<BookCandidate> result = scanner.scan(rootUri);
            myLog("Scan complete. Items found: " + result.size());
            for (BookCandidate c : result) {
                myLogD("Candidate: " + c.name + " [" + c.type + "] -> " + c.uri);
            }
            // Final update ensures we show everything at the end
            mainHandler.post(() -> {
                candidates.setValue(result);
                isScanning.setValue(false);
                progressText.setValue("Scan complete. Found " + result.size() + " items.");
            });
        });
    }

    public void cancelScan() {
        if (scanner != null) {
            scanner.cancel();
        }
        isScanning.setValue(false);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        cancelScan();
        executor.shutdown();
    }
}
