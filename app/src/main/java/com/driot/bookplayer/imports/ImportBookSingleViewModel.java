package com.driot.bookplayer.imports;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.driot.bookplayer.utils.log.LoggingAndroidViewModel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * ViewModel for ImportBookSingleActivity.
 * Handles async initialization of BookCandidate and hash computation.
 */
@HiltViewModel
public class ImportBookSingleViewModel extends LoggingAndroidViewModel {

    private final MutableLiveData<BookCandidate> bookCandidate = new MutableLiveData<>();
    private final MutableLiveData<String> originalHash = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(true);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Inject
    public ImportBookSingleViewModel(@NonNull Application application) {
        super(application);
    }

    /**
     * Initialize BookCandidate from URI in background thread.
     * Result will be posted to bookCandidate LiveData.
     */
    // 0 = Fast Init (Yellow), 1 = Heavy Init (Green), 2 = Done (Gone)
    private final MutableLiveData<Integer> loadingStatus = new MutableLiveData<>(0);

    // ...

    /**
     * Initialize BookCandidate from URI in background thread.
     * Result will be posted to bookCandidate LiveData.
     */
    private java.util.concurrent.Future<?> loadingFuture;

    /**
     * Initialize BookCandidate from URI in background thread.
     * Result will be posted to bookCandidate LiveData.
     */
    public void initializeBookCandidate(Uri uri) {
        isLoading.postValue(true);
        loadingStatus.postValue(0); // Yellow/Fast start

        loadingFuture = executorService.submit(() -> {
            try {
                // Phase 1: Fast Init
                BookCandidate candidate = new BookCandidate(getApplication(), uri);
                myLogD("BookCandidate FAST init DONE: " + candidate.name);
                bookCandidate.postValue(candidate);

                if (Thread.currentThread().isInterrupted())
                    return;

                // Signal Phase 2 start
                loadingStatus.postValue(1); // Green/Heavy start

                // Phase 2: Heavy Init
                candidate.loadHeavyMetadata(getApplication());

                if (Thread.currentThread().isInterrupted())
                    return;

                myLogD("BookCandidate HEAVY init DONE: " + candidate.name);
                bookCandidate.postValue(candidate); // Post again with full data

                isLoading.postValue(false);
                loadingStatus.postValue(2); // Done

            } catch (Exception e) {
                myLogEE(e, "Error initializing BookCandidate");
                errorMessage.postValue("Error loading file: " + e.getMessage());
                isLoading.postValue(false);
                loadingStatus.postValue(2);
            }
        });
    }

    public void cancelInitialization() {
        if (loadingFuture != null && !loadingFuture.isDone()) {
            myLogD("Cancelling BookCandidate initialization");
            loadingFuture.cancel(true); // Interrupt running thread
        }
    }

    public LiveData<Integer> getLoadingStatus() {
        return loadingStatus;
    }

    /**
     * Compute hash for the given URI in background thread.
     * Result will be posted to originalHash LiveData.
     */
    public void computeHash(Uri uri) {
        executorService.execute(() -> {
            try {
                String hash = com.driot.bookplayer.utils.HashWorker.computeHashFromUri(getApplication(), uri);
                originalHash.postValue(hash);
                myLogD("Hash computed: " + hash);
            } catch (Exception e) {
                myLogEE(e, "Error computing hash");
                originalHash.postValue(null);
            }
        });
    }

    public LiveData<BookCandidate> getBookCandidate() {
        return bookCandidate;
    }

    public LiveData<String> getOriginalHash() {
        return originalHash;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}
