package com.driot.bookplayer.activities;

import static com.driot.bookplayer.helpers.StorageHelper.getAvailableInternalMemorySize;
import static com.driot.bookplayer.helpers.StorageHelper.getAvailableRemovableSDCardSize;
import static com.driot.bookplayer.helpers.StorageHelper.getTotaLInternalMemorySize;
import static com.driot.bookplayer.helpers.StorageHelper.getTotalRemovableSDCardSize;
import static com.driot.bookplayer.utils.Tonio.getAppSize;
import static com.driot.bookplayer.utils.Tonio.getFolderSize;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.driot.bookplayer.R;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingAndroidViewModel;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StatsViewModel extends LoggingAndroidViewModel {

    private final MutableLiveData<StorageInfo> internalStorageInfo = new MutableLiveData<>();
    private final MutableLiveData<StorageInfo> sdCardStorageInfo = new MutableLiveData<>();
    private final MutableLiveData<String> internalStorageText = new MutableLiveData<>();
    private final MutableLiveData<String> sdCardStorageText = new MutableLiveData<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public StatsViewModel(@NonNull Application application) {
        super(application);
        loadStorageInfo();
    }

    public LiveData<StorageInfo> getInternalStorageInfo() {
        return internalStorageInfo;
    }

    public LiveData<StorageInfo> getSdCardStorageInfo() {
        return sdCardStorageInfo;
    }

    public LiveData<String> getInternalStorageText() {
        return internalStorageText;
    }

    public LiveData<String> getSdCardStorageText() {
        return sdCardStorageText;
    }

    public void loadStorageInfo() {
        executorService.execute(() -> {
            try {
                // Calculate and post internal storage first (fast)
                calculateInternalStorage();
                
                // Then calculate SD card storage separately (can be slow, but doesn't block UI)
                calculateSDCardStorage();
            } catch (Exception e) {
                myLogEE(e, "Error calculating storage info");
            }
        });
    }

    private void calculateInternalStorage() {
        Application app = getApplication();
        
        // Calculate internal storage - post results immediately when ready
        long totalMemory = getTotaLInternalMemorySize() / 1048576L;
        long availableMegs2 = getAvailableInternalMemorySize() / 1048576L;
        long currentAppSize = getAppSize(app) / 1048576L;

        // Only calculate folder sizes if folders exist (faster)
        File unzipFolder = StorageHelper.getUnzipFolder(app, false);
        long currentAudiosSizeInternal = 0;
        if (unzipFolder != null && unzipFolder.exists()) {
            myLogI("StatsViewModel: Calculating getFolderSize for internal unzipped folder: " + unzipFolder.getAbsolutePath());
            long startTime = System.currentTimeMillis();
            currentAudiosSizeInternal = Tonio.getFolderSize(unzipFolder) / 1048576L;
            long duration = System.currentTimeMillis() - startTime;
            myLogI("StatsViewModel: getFolderSize for internal unzipped completed in " + duration + "ms, size: " + currentAudiosSizeInternal + " MB");
        }
        
        File imagesFolder = new File(app.getFilesDir(), "images");
        long sizeImages = 0;
        if (imagesFolder.exists()) {
            myLogI("StatsViewModel: Calculating getFolderSize for images folder: " + imagesFolder.getAbsolutePath());
            long startTime = System.currentTimeMillis();
            sizeImages = getFolderSize(imagesFolder.getPath()) / 1048576L;
            long duration = System.currentTimeMillis() - startTime;
            myLogI("StatsViewModel: getFolderSize for images completed in " + duration + "ms, size: " + sizeImages + " MB");
        }
        
        File logsFolder = new File(app.getFilesDir(), "log");
        long sizeLogs = 0;
        if (logsFolder.exists()) {
            myLogI("StatsViewModel: Calculating getFolderSize for log folder: " + logsFolder.getAbsolutePath());
            long startTime = System.currentTimeMillis();
            sizeLogs = getFolderSize(logsFolder.getPath()) / 1048576L;
            long duration = System.currentTimeMillis() - startTime;
            myLogI("StatsViewModel: getFolderSize for log completed in " + duration + "ms, size: " + sizeLogs + " MB");
        }
        
        long sizeDB = 0;
        File parentFile = app.getFilesDir().getParentFile();
        if (parentFile != null) {
            File dbFolder = new File(parentFile, "databases");
            if (dbFolder.exists()) {
                myLogI("StatsViewModel: Calculating getFolderSize for databases folder: " + dbFolder.getAbsolutePath());
                long startTime = System.currentTimeMillis();
                sizeDB = getFolderSize(dbFolder.getPath()) / 1048576L;
                long duration = System.currentTimeMillis() - startTime;
                myLogI("StatsViewModel: getFolderSize for databases completed in " + duration + "ms, size: " + sizeDB + " MB");
            }
        }
        
        File cachedImagesFolder = new File(app.getFilesDir(), "cached_images");
        long sizeCachedImages = 0;
        if (cachedImagesFolder.exists()) {
            myLogI("StatsViewModel: Calculating getFolderSize for cached_images folder: " + cachedImagesFolder.getAbsolutePath());
            long startTime = System.currentTimeMillis();
            sizeCachedImages = getFolderSize(cachedImagesFolder.getPath()) / 1048576L;
            long duration = System.currentTimeMillis() - startTime;
            myLogI("StatsViewModel: getFolderSize for cached_images completed in " + duration + "ms, size: " + sizeCachedImages + " MB");
        }

        // Build display text for internal storage
        String internalText = Tonio.formatMemPadding(totalMemory) + app.getString(R.string.MB_device_memory)
                + "\n" + "\n" + Tonio.formatMemPadding(availableMegs2) + app.getString(R.string.MB_available_on_device)
                + "\n" + "\n" + Tonio.formatMemPadding(currentAudiosSizeInternal)
                + app.getString(R.string.MB_taken_by_audio_files)
                + "\n" + "\n" + Tonio.formatMemPadding(currentAppSize) + app.getString(R.string.MB_taken_by_BookPlayer_app)
                + "\n" + "\n" + Tonio.formatMemPadding(sizeImages) + app.getString(R.string.MB_taken_by_images)
                + "\n" + "\n" + Tonio.formatMemPadding(sizeLogs) + app.getString(R.string.MB_taken_by_logs)
                + "\n" + "\n" + Tonio.formatMemPadding(sizeDB) + app.getString(R.string.MB_taken_by_databases);

        // Post internal storage results immediately (don't wait for SD card)
        if (totalMemory > 0) {
            long totalMemoryBytes = totalMemory * 1048576L;
            long availableBytes = availableMegs2 * 1048576L;
            long usedByBookPlayerBytes = currentAppSize * 1048576L;
            long usedTotalBytes = totalMemoryBytes - availableBytes;
            long usedByOthersBytes = usedTotalBytes - usedByBookPlayerBytes;
            
            if (usedByOthersBytes < 0) usedByOthersBytes = 0;
            if (usedByBookPlayerBytes < 0) usedByBookPlayerBytes = 0;
            
            internalStorageInfo.postValue(new StorageInfo(
                totalMemoryBytes, 
                usedByOthersBytes, 
                usedByBookPlayerBytes, 
                internalText
            ));
            internalStorageText.postValue(internalText);
        }
    }

    private void calculateSDCardStorage() {
        Application app = getApplication();
        
        // Calculate SD card storage separately (can be slow)
        long total = getTotalRemovableSDCardSize(app) / 1048576L;
        if (total > 0) {
            long available = getAvailableRemovableSDCardSize(app) / 1048576L;
            
            // Post initial SD card info without folder size (fast)
            String sdCardTextInitial = Tonio.formatMemPadding(total) + app.getString(R.string.MB_SD_card_memory)
                    + "\n\n" + Tonio.formatMemPadding(available) + app.getString(R.string.MB_available_on_SD_card)
                    + "\n\n" + app.getString(R.string.calculating_storage)
                    + " " + app.getString(R.string.MB_taken_by_audio_files);

            long totalSDCardBytes = total * 1048576L;
            long availableSDCardBytes = available * 1048576L;
            long usedTotalSDCardBytes = totalSDCardBytes - availableSDCardBytes;
            long usedByOthersSDCardBytes = usedTotalSDCardBytes; // Approximate initially
            
            if (usedByOthersSDCardBytes < 0) usedByOthersSDCardBytes = 0;
            
            sdCardStorageInfo.postValue(new StorageInfo(
                totalSDCardBytes, 
                usedByOthersSDCardBytes, 
                0, // Will be updated after folder scan
                sdCardTextInitial
            ));
            sdCardStorageText.postValue(sdCardTextInitial);
            
            // Now calculate folder size in background (can be slow, but doesn't block UI)
            executorService.execute(() -> {
                try {
                    myLogI("StatsViewModel: Starting SD card folder size calculation");
                    File sdUnzipFolder = StorageHelper.getUnzipFolder(app, true);
                    long currentAudiosSizeSD = 0;
                    if (sdUnzipFolder != null && sdUnzipFolder.exists()) {
                        myLogI("StatsViewModel: Calculating getFolderSize for SD card: " + sdUnzipFolder.getAbsolutePath());
                        long startTime = System.currentTimeMillis();
                        currentAudiosSizeSD = Tonio.getFolderSize(sdUnzipFolder) / 1048576L;
                        long duration = System.currentTimeMillis() - startTime;
                        myLogI("StatsViewModel: getFolderSize for SD card completed in " + Tonio.formatTime(duration) + ", size: " + currentAudiosSizeSD  + " MB");
                    } else {
                        myLogI("StatsViewModel: SD card unzip folder does not exist, skipping folder size calculation");
                    }
                    
                    // Update with actual folder size
                    String sdCardText = Tonio.formatMemPadding(total) + app.getString(R.string.MB_SD_card_memory)
                            + "\n\n" + Tonio.formatMemPadding(available) + app.getString(R.string.MB_available_on_SD_card)
                            + "\n\n" + Tonio.formatMemPadding(currentAudiosSizeSD)
                            + app.getString(R.string.MB_taken_by_audio_files);

                    long usedByBookPlayerSDCardBytes = currentAudiosSizeSD * 1048576L;
                    long usedByOthersSDCardBytesUpdated = usedTotalSDCardBytes - usedByBookPlayerSDCardBytes;
                    
                    if (usedByOthersSDCardBytesUpdated < 0) usedByOthersSDCardBytesUpdated = 0;
                    if (usedByBookPlayerSDCardBytes < 0) usedByBookPlayerSDCardBytes = 0;
                    
                    sdCardStorageInfo.postValue(new StorageInfo(
                        totalSDCardBytes, 
                        usedByOthersSDCardBytesUpdated, 
                        usedByBookPlayerSDCardBytes,
                        sdCardText
                    ));
                    sdCardStorageText.postValue(sdCardText);
                    myLogI("StatsViewModel: SD card storage info updated with folder size");
                } catch (Exception e) {
                    myLogEE(e, "StatsViewModel: Error calculating SD card folder size");
                }
            });
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}
