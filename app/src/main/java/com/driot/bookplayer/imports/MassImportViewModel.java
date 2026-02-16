package com.driot.bookplayer.imports;

import static com.driot.bookplayer.helpers.StorageHelper.getAvailableInternalMemorySize;
import static com.driot.bookplayer.helpers.StorageHelper.getAvailableRemovableSDCardSize;
import static com.driot.bookplayer.helpers.StorageHelper.getTotaLInternalMemorySize;
import static com.driot.bookplayer.helpers.StorageHelper.getTotalRemovableSDCardSize;
import static com.driot.bookplayer.helpers.StorageHelper.getAppSize;
import static com.driot.bookplayer.helpers.StorageHelper.getFolderSize;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.driot.bookplayer.activities.StorageInfo;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.helpers.StorageInfoCacheHelper;
import com.driot.bookplayer.utils.log.LoggingAndroidViewModel;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class MassImportViewModel extends LoggingAndroidViewModel {

    private final MassImportRepository repository;
    private final MutableLiveData<StorageInfo> internalStorageInfo = new MutableLiveData<>();
    private final MutableLiveData<StorageInfo> sdCardStorageInfo = new MutableLiveData<>();
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

    public LiveData<Integer> getLoadingStatus() {
        return repository.getLoadingStatus();
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

    public LiveData<StorageInfo> getInternalStorageInfo() {
        return internalStorageInfo;
    }

    public LiveData<StorageInfo> getSdCardStorageInfo() {
        return sdCardStorageInfo;
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
        boolean sdCardAvailable = StorageHelper.isExternalSDCardAvailable(app);

        // Always compute internal storage
        long totalInternal = 0, usedByOthersInternal = 0, usedByBookPlayerInternal = 0;
        long cachedTimestampInternal = StorageInfoCacheHelper.getCachedInternalTimestamp();
        if (cachedTimestampInternal > 0) {
            // myLogD("MassImportViewModel: Using cached internal storage info");
            totalInternal = StorageInfoCacheHelper.getCachedInternalTotal();
            usedByOthersInternal = StorageInfoCacheHelper.getCachedInternalUsedByOthers();
            usedByBookPlayerInternal = StorageInfoCacheHelper.getCachedInternalUsedByBookPlayer();
        } else {
            myLogD("MassImportViewModel: No cached internal storage, calculating now");
            totalInternal = getTotaLInternalMemorySize();
            long availableInternal = getAvailableInternalMemorySize();
            if (totalInternal > 0 && availableInternal >= 0) {
                usedByBookPlayerInternal = getAppSize(app);
                long usedTotal = totalInternal - availableInternal;
                usedByOthersInternal = usedTotal - usedByBookPlayerInternal;
                if (usedByOthersInternal < 0)
                    usedByOthersInternal = 0;
                if (usedByBookPlayerInternal < 0)
                    usedByBookPlayerInternal = 0;
            }
        }
        if (totalInternal > 0) {
            long linkedInternal = StorageInfoCacheHelper.getCachedInternalLinkedAudios();
            long expectedInternal = useSdCard ? 0 : expectedAddedMemoryBytes;
            internalStorageInfo.postValue(new StorageInfo(
                    totalInternal, usedByOthersInternal, usedByBookPlayerInternal,
                    expectedInternal, linkedInternal, null));
        }

        // If SD card exists, also compute SD card storage
        if (sdCardAvailable) {
            long totalSd = 0, usedByOthersSd = 0, usedByBookPlayerSd = 0;
            long cachedTimestampSd = StorageInfoCacheHelper.getCachedSDCardTimestamp();
            if (cachedTimestampSd > 0) {
                // myLogD("MassImportViewModel: Using cached SD card storage info");
                totalSd = StorageInfoCacheHelper.getCachedSDCardTotal();
                usedByOthersSd = StorageInfoCacheHelper.getCachedSDCardUsedByOthers();
                usedByBookPlayerSd = StorageInfoCacheHelper.getCachedSDCardUsedByBookPlayer();
            } else {
                myLogD("MassImportViewModel: No cached SD card storage, calculating now");
                totalSd = getTotalRemovableSDCardSize(app);
                long availableSd = getAvailableRemovableSDCardSize(app);
                if (totalSd > 0 && availableSd >= 0) {
                    long currentTime = System.currentTimeMillis();
                    if (cachedSDCardFolderSize >= 0 &&
                            (currentTime - lastSDCardFolderSizeCalculation) < SD_CARD_FOLDER_SIZE_CACHE_DURATION_MS) {
                        usedByBookPlayerSd = cachedSDCardFolderSize;
                    } else {
                        String sdCardUnzipFolder = StorageHelper.getSdCardUnzippedFolder(app);
                        if (sdCardUnzipFolder != null) {
                            usedByBookPlayerSd = getFolderSize(sdCardUnzipFolder);
                            cachedSDCardFolderSize = usedByBookPlayerSd;
                            lastSDCardFolderSizeCalculation = currentTime;
                        } else {
                            usedByBookPlayerSd = 0;
                            cachedSDCardFolderSize = 0;
                            lastSDCardFolderSizeCalculation = currentTime;
                        }
                    }
                    long usedTotal = totalSd - availableSd;
                    usedByOthersSd = usedTotal - usedByBookPlayerSd;
                    if (usedByOthersSd < 0)
                        usedByOthersSd = 0;
                    if (usedByBookPlayerSd < 0)
                        usedByBookPlayerSd = 0;
                }
            }
            if (totalSd > 0) {
                long linkedSd = StorageInfoCacheHelper.getCachedSDCardLinkedAudios();
                long expectedSd = useSdCard ? expectedAddedMemoryBytes : 0;
                sdCardStorageInfo.postValue(new StorageInfo(
                        totalSd, usedByOthersSd, usedByBookPlayerSd,
                        expectedSd, linkedSd, null));
            } else {
                sdCardStorageInfo.postValue(null);
            }
        } else {
            sdCardStorageInfo.postValue(null);
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
