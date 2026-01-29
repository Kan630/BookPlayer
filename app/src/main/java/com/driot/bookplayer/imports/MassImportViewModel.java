package com.driot.bookplayer.imports;

import static com.driot.bookplayer.helpers.StorageHelper.getAvailableInternalMemorySize;
import static com.driot.bookplayer.helpers.StorageHelper.getAvailableRemovableSDCardSize;
import static com.driot.bookplayer.helpers.StorageHelper.getTotaLInternalMemorySize;
import static com.driot.bookplayer.helpers.StorageHelper.getTotalRemovableSDCardSize;
import static com.driot.bookplayer.utils.Tonio.getAppSize;
import static com.driot.bookplayer.utils.Tonio.getFolderSize;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.driot.bookplayer.activities.StorageInfo;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.utils.log.LoggingAndroidViewModel;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class MassImportViewModel extends LoggingAndroidViewModel {

    private final MassImportRepository repository;
    private final MutableLiveData<StorageInfo> storageInfo = new MutableLiveData<>();
    // Use a separate executor for storage calculations to avoid blocking scanning
    private final ExecutorService storageExecutorService = Executors.newSingleThreadExecutor();
    
    // Cache to avoid recalculating SD card folder size repeatedly
    private long cachedSDCardFolderSize = -1;
    private long lastSDCardFolderSizeCalculation = 0;
    private static final long SD_CARD_FOLDER_SIZE_CACHE_DURATION_MS = 10000; // Cache for 10 seconds

    @Inject
    public MassImportViewModel(@NonNull Application application, MassImportRepository repository) {
        super(application);
        this.repository = repository;
        // Don't load storage info here - it's secondary and shouldn't block scanning
        // Storage info will be calculated when candidates are available
    }

    public LiveData<List<BookCandidate>> getCandidates() {
        return repository.getCandidates();
    }

    public LiveData<String> getProgressText() {
        return repository.getProgressText();
    }

    public LiveData<Boolean> getIsScanning() {
        return repository.getIsScanning();
    }

    public void startScan(Uri rootUri) {
        repository.startScan(rootUri);
    }

    public void cancelScan() {
        repository.cancelScan();
    }

    public void consumeScanState() {
        repository.consumeScanState();
    }

    public LiveData<StorageInfo> getStorageInfo() {
        return storageInfo;
    }

    public void updateStorageInfo(long expectedAddedMemoryBytes) {
        // Use separate executor to avoid blocking scanning operations
        storageExecutorService.execute(() -> {
            try {
                calculateStorageInfo(expectedAddedMemoryBytes);
            } catch (Exception e) {
                myLogEE(e, "Error calculating storage info");
            }
        });
    }

    private void calculateStorageInfo(long expectedAddedMemoryBytes) {
        Application app = getApplication();
        boolean useSdCard = Option.getUseSdCard();
        long totalStorage;
        long availableStorage;
        long usedByBookPlayer;

        if (useSdCard && StorageHelper.isExternalSDCardAvailable(app)) {
            // SD Card storage
            totalStorage = getTotalRemovableSDCardSize(app);
            availableStorage = getAvailableRemovableSDCardSize(app);
            if (totalStorage > 0 && availableStorage >= 0) {
                // Use cached SD card folder size if available and recent
                long currentTime = System.currentTimeMillis();
                if (cachedSDCardFolderSize >= 0 && 
                    (currentTime - lastSDCardFolderSizeCalculation) < SD_CARD_FOLDER_SIZE_CACHE_DURATION_MS) {
                    // Use cached value
                    usedByBookPlayer = cachedSDCardFolderSize;
                    myLogI("MassImportViewModel: Using cached SD card folder size: " + (usedByBookPlayer / 1048576L) + " MB");
                } else {
                    // Calculate BookPlayer usage on SD card (slow operation)
                    String sdCardUnzipFolder = StorageHelper.getSdCardUnzippedFolder(app);
                    if (sdCardUnzipFolder != null) {
                        myLogI("MassImportViewModel: Calculating SD card folder size (cache expired or first time)");
                        long startTime = System.currentTimeMillis();
                        usedByBookPlayer = getFolderSize(sdCardUnzipFolder);
                        long duration = System.currentTimeMillis() - startTime;
                        myLogI("MassImportViewModel: SD card folder size calculated in " + duration + "ms: " + (usedByBookPlayer / 1048576L) + " MB");
                        
                        // Cache the result
                        cachedSDCardFolderSize = usedByBookPlayer;
                        lastSDCardFolderSizeCalculation = currentTime;
                    } else {
                        usedByBookPlayer = 0;
                        cachedSDCardFolderSize = 0;
                        lastSDCardFolderSizeCalculation = currentTime;
                    }
                }
                
                long usedTotal = totalStorage - availableStorage;
                long usedByOthers = usedTotal - usedByBookPlayer;
                if (usedByOthers < 0) usedByOthers = 0;
                if (usedByBookPlayer < 0) usedByBookPlayer = 0;
                
                storageInfo.postValue(new StorageInfo(
                    totalStorage, 
                    usedByOthers, 
                    usedByBookPlayer, 
                    expectedAddedMemoryBytes,
                    null
                ));
            }
        } else {
            // Internal storage
            totalStorage = getTotaLInternalMemorySize();
            availableStorage = getAvailableInternalMemorySize();
            if (totalStorage > 0 && availableStorage >= 0) {
                usedByBookPlayer = getAppSize(app);
                
                long usedTotal = totalStorage - availableStorage;
                long usedByOthers = usedTotal - usedByBookPlayer;
                if (usedByOthers < 0) usedByOthers = 0;
                if (usedByBookPlayer < 0) usedByBookPlayer = 0;
                
                storageInfo.postValue(new StorageInfo(
                    totalStorage, 
                    usedByOthers, 
                    usedByBookPlayer, 
                    expectedAddedMemoryBytes,
                    null
                ));
            }
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        storageExecutorService.shutdown();
        // Do NOT cancel scan on clear, to allow background scanning
        // repository.cancelScan();
    }
}
