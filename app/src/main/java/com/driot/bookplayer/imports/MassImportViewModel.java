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
        if (Boolean.TRUE.equals(isScanning.getValue()))
            return;

        isScanning.setValue(true);
        candidates.setValue(Collections.emptyList());

        scanner = new MassImportScanner(getApplication(), new MassImportScanner.Callback() {
            @Override
            public void onProgress(String currentPath) {
                mainHandler.post(() -> progressText.setValue("Scanning: " + currentPath));
            }

            @Override
            public void onFound(BookCandidate candidate) {
                // Determine if we should update LiveData immediately or batch.
                // For now, let's just collect in the background list and update at the end
                // OR update progressively.
                // Since user might want to see them appearing, let's just not update LiveData
                // on every item to avoid UI lag
                // if there are thousands.
                // But MassImportScanner returns the full list at the end.
                // The callback onFound is extra.
                // Let's rely on the final list for the full update, or update periodically.
            }
        });

        executor.execute(() -> {
            List<BookCandidate> result = scanner.scan(rootUri);
            myLog("Scan complete. Items found: " + result.size());
            for (BookCandidate c : result) {
                myLogD("Candidate: " + c.name + " [" + c.type + "] -> " + c.uri);
            }
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
