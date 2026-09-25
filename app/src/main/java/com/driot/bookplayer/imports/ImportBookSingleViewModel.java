package com.driot.bookplayer.imports;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.driot.bookplayer.utils.log.LoggingAndroidViewModel;

import com.driot.bookplayer.objects.AudioFileInfo;
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
    private final MutableLiveData<java.util.List<AudioFileInfo>> realTimeTracks = new MutableLiveData<>();

    // ...

    /**
     * Set BookCandidate directly (e.g. passed from another activity).
     * Skips background initialization.
     */
    public void setBookCandidate(BookCandidate candidate) {
        if (candidate == null)
            return;
        isLoading.setValue(false);
        loadingStatus.setValue(2); // Done
        bookCandidate.setValue(candidate); // This will trigger UI updates in Activity

        // If audioFileInfoArrayList is already populated, update realTimeTracks
        if (candidate.getAudioFileInfoArrayList() != null && !candidate.getAudioFileInfoArrayList().isEmpty()) {
            realTimeTracks.setValue(new java.util.ArrayList<>(candidate.getAudioFileInfoArrayList()));
        }
    }

    /**
     * Initialize BookCandidate from URI in background thread.
     * Result will be posted to bookCandidate LiveData.
     */
    public void initializeBookCandidate(Uri uri) {
        isLoading.postValue(true);
        loadingStatus.postValue(0); // Yellow/Fast start
        realTimeTracks.postValue(new java.util.ArrayList<>());

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
                candidate.loadHeavyMetadata(getApplication(), new BookCandidate.OnMetadataListener() {
                    @Override
                    public void onTrackFound(AudioFileInfo info) {
                        java.util.List<AudioFileInfo> copy;
                        synchronized (candidate.getAudioFileInfoArrayList()) {
                            copy = new java.util.ArrayList<>(candidate.getAudioFileInfoArrayList());
                        }
                        realTimeTracks.postValue(copy);
                    }

                    @Override
                    public void onCoverFound(String imagePath) {
                        myLog("Early cover found: " + imagePath);
                        bookCandidate.postValue(candidate); // Trigger UI update to show cover
                    }
                });

                if (Thread.currentThread().isInterrupted())
                    return;

                myLog("-----------------------");
                myLog("HEAVY init DONE: " + candidate.name);
                myLog("-----------------------");
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

    private java.util.concurrent.Future<?> rescanFuture;

    /**
     * Re-runs the ebook chapter scan with an explicit EPUB split-mode override (see
     * {@link BookCandidate#rescanEbookChaptersForPreview}) - used by the import screen's
     * split-mode toggle so the user sees the effect on the chapter list immediately. Reuses
     * loadingStatus (1 = scanning, 2 = done) so the existing "Now scanning…" indicator shows
     * during the rescan too, without new UI plumbing. A rapid second toggle cancels whichever
     * rescan was still in flight rather than letting two race each other.
     */
    public void rescanEbookChapters(String epubSplitMode) {
        BookCandidate candidate = bookCandidate.getValue();
        if (candidate == null)
            return;
        if (rescanFuture != null && !rescanFuture.isDone()) {
            rescanFuture.cancel(true);
        }
        loadingStatus.postValue(1);
        realTimeTracks.postValue(new java.util.ArrayList<>());
        rescanFuture = executorService.submit(() -> {
            try {
                candidate.rescanEbookChaptersForPreview(getApplication(), epubSplitMode,
                        new BookCandidate.OnMetadataListener() {
                            @Override
                            public void onTrackFound(AudioFileInfo info) {
                                java.util.List<AudioFileInfo> copy;
                                synchronized (candidate.getAudioFileInfoArrayList()) {
                                    copy = new java.util.ArrayList<>(candidate.getAudioFileInfoArrayList());
                                }
                                realTimeTracks.postValue(copy);
                            }

                            @Override
                            public void onCoverFound(String imagePath) {
                                // Chapter rescan doesn't touch the cover - nothing to do here.
                            }
                        });
            } catch (Exception e) {
                myLogEE(e, "Error rescanning ebook chapters");
            } finally {
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

    public LiveData<BookCandidate> getBookCandidate() {
        return bookCandidate;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<java.util.List<AudioFileInfo>> getRealTimeTracks() {
        return realTimeTracks;
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
