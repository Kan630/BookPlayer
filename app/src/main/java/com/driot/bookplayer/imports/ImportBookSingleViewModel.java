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
    public void initializeBookCandidate(Uri uri) {
        isLoading.postValue(true);
        executorService.execute(() -> {
            try {
                BookCandidate candidate = new BookCandidate(getApplication(), uri);
                bookCandidate.postValue(candidate);
                isLoading.postValue(false);
            } catch (Exception e) {
                myLogEE(e, "Error initializing BookCandidate");
                errorMessage.postValue("Error loading file: " + e.getMessage());
                isLoading.postValue(false);
            }
        });
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
