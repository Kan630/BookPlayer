package com.driot.bookplayer.activities;

import static com.driot.bookplayer.global.Var.FOLDER_UNZIPPED;
import static com.driot.bookplayer.podcasts.PodcastHelper.cancelAutoDownload;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.helpers.FileHelper;
import com.driot.bookplayer.helpers.StorageInfoCacheHelper;
import com.driot.bookplayer.objects.FolderSummary;
import com.driot.bookplayer.objects.FolderWithSummary;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.utils.log.LoggingAndroidViewModel;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CleanMemoryViewModel extends LoggingAndroidViewModel {
    private final CleanMemoryRepository cacheFilesRepository;
    private final MediatorLiveData<List<FolderWithSummary>> enrichedFolders = new MediatorLiveData<>();
    private final MutableLiveData<List<File>> foldersFromDisk = new MutableLiveData<>();
    private final LiveData<List<FolderSummary>> foldersFromDb;
    private final MutableLiveData<Boolean> memoryStats = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private final MutableLiveData<Long> totalAudioSizeMB = new MutableLiveData<>(0L);
    public LiveData<Long> getTotalAudioSizeMB() {
        return totalAudioSizeMB;
    }
    private boolean useInternal = true;
    private final Map<String, Long> folderSizeCache = new HashMap<>();

    private final MutableLiveData<Boolean> isRefreshing = new MutableLiveData<>(false);
    private final MutableLiveData<Long> refreshStartTime = new MutableLiveData<>(0L);

    public LiveData<Boolean> getIsRefreshing() {
        return isRefreshing;
    }
    public LiveData<Long> getRefreshStartTime() {
        return refreshStartTime;
    }

    public CleanMemoryViewModel(@NonNull Application application) {
        super(application);
        cacheFilesRepository = new CleanMemoryRepository(application);

        foldersFromDb = AppDatabase.getDatabase(getApplication()).folderDao().getFoldersForCleaning();

        enrichedFolders.addSource(foldersFromDisk, diskFiles -> updateEnrichedFiles(diskFiles, foldersFromDb.getValue()));
        enrichedFolders.addSource(foldersFromDb, dbSummaries -> updateEnrichedFiles(foldersFromDisk.getValue(), dbSummaries));

        loadFilesFromDisk();
    }

    public LiveData<List<FolderWithSummary>> getEnrichedFolders() {
        return enrichedFolders;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<Boolean> getMemoryStats() {
        return memoryStats;
    }

    public boolean isUsingInternal() {
        return useInternal;
    }

    public void setUseInternal(boolean useInternal) {
        this.useInternal = useInternal;
        foldersFromDisk.postValue(new ArrayList<>()); // clear while loading
        loadFilesFromDisk();
    }

    /**
     * Trigger storage recalculate for the current view; show progress in UI until complete, then reload from cache.
     * @param context       application context
     * @param internalView  true if user is on internal storage view (recalc internal only), false for SD card view (recalc SD only)
     */
    public void refreshStorageCache(android.content.Context context, boolean internalView) {
        if (isRefreshing.getValue() != null && isRefreshing.getValue()) return;
        refreshStartTime.postValue(System.currentTimeMillis());
        isRefreshing.postValue(true);
        final boolean internalOnly = internalView;
        final boolean sdCardOnly = !internalView;
        myLog("refreshing storage cache - internal: [" + internalOnly + "], SD: [" + sdCardOnly + "]");
        StorageInfoCacheHelper.recalculate(context, internalOnly, sdCardOnly, new StorageInfoCacheHelper.RecalculateProgressCallback() {
            @Override
            public void onProgress(int elapsedSec, String phaseMessage, boolean isSdCardPhase) {
                // Optional: could post progress for phase-based message
            }

            @Override
            public void onComplete() {
                isRefreshing.postValue(false);
                loadFilesFromDisk(); // reload list from updated cache
            }
        });
    }

    private void loadFilesFromDisk() {
        isLoading.postValue(true);
        executorService.execute(() -> {
            try {
                String basePath = useInternal
                        ? getApplication().getFilesDir().getPath() + "/" + FOLDER_UNZIPPED
                        : StorageHelper.getSdCardUnzippedFolder(getApplication());

                List<File> unzip_folders = new ArrayList<>();

                if (basePath != null) {
                    File baseDir = new File(basePath);
                    File[] foldersArray = baseDir.listFiles();

                    if (baseDir.exists() && baseDir.isDirectory() && foldersArray != null) {
                        for (File f : foldersArray) {
                            if (f.isDirectory()) {
                                unzip_folders.add(f);
                            }
                        }
                        myLog(unzip_folders.size() + " folders in: [" + basePath + "]");
                    } else {
                        myLog("No valid files found in base directory: [" + basePath + "]");
                    }
                } else {
                    myLogE("No valid base path found");
                }

                // Use cached folder sizes from StorageInfoCacheHelper (fast)
                Map<String, Long> cachedSizes = StorageInfoCacheHelper.getCachedFolderSizes(useInternal);
                folderSizeCache.clear();
                long totalSizeBytes = 0L;
                for (File f : unzip_folders) {
                    Long sizeBytes = cachedSizes.get(f.getAbsolutePath());
                    if (sizeBytes == null) {
                        sizeBytes = StorageHelper.getFolderSize(f); // fallback for new folders not in cache
                    }
                    folderSizeCache.put(f.getPath(), sizeBytes);
                    totalSizeBytes += sizeBytes;
                }
                totalAudioSizeMB.postValue(totalSizeBytes / 1024 / 1024);

                foldersFromDisk.postValue(unzip_folders);
                memoryStats.postValue(true);
            } catch (Exception e) {
                myLogEE(e, "loadFilesFromDisk");
            } finally {
                isLoading.postValue(false);
            }
        });
    }

    private void updateEnrichedFiles(List<File> diskFiles, List<FolderSummary> dbSummaries) {
        if (diskFiles == null || dbSummaries == null) return;

        Map<String, FolderSummary> summaryMap = new HashMap<>();
        for (FolderSummary summary : dbSummaries) {
            summaryMap.put(summary.path, summary);
        }

        List<FolderWithSummary> enriched = new ArrayList<>(diskFiles.size());
        for (File file : diskFiles) {
            FolderSummary summary = summaryMap.get(file.getPath());

            double percentDone = summary != null ? summary.percentDone : 0;
            String sourceLocation = summary != null ? summary.sourceLocation : "";
            String playType = summary != null ? summary.playType : "";
            String image = summary != null ? summary.image : "";
            String folderName = (summary != null && summary.name != null) ? summary.name : null;

            long folderSizeBytes = folderSizeCache.containsKey(file.getPath())
                    ? folderSizeCache.get(file.getPath())
                    : (folderSizeCache.containsKey(file.getAbsolutePath()) ? folderSizeCache.get(file.getAbsolutePath())
                    : StorageHelper.getFolderSize(file)); // fallback for cache miss
            folderSizeCache.putIfAbsent(file.getPath(), folderSizeBytes);

            enriched.add(new FolderWithSummary(file, percentDone, sourceLocation, playType, folderSizeBytes, image, folderName));
        }
        enriched.sort(Comparator.comparingLong(f -> f.folderSizeInBytes));
        Collections.reverse(enriched);
        enrichedFolders.postValue(enriched);
    }


    public void deleteAudio(File file) {
        myLog("deleting file : [" + file.getPath() + "]");
        long idFolder = getBookFolderId(file);
        if (deleteBookFromDisk(file.getPath())) {
            Long sizeBytes = folderSizeCache.remove(file.getPath());
            if (sizeBytes != null && totalAudioSizeMB.getValue() != null) {
                long sizeMB = sizeBytes / 1024 / 1024;
                totalAudioSizeMB.postValue(totalAudioSizeMB.getValue() - sizeMB);
            }
            List<File> currentDisk = foldersFromDisk.getValue();
            if (currentDisk != null) {
                currentDisk.removeIf(f -> f.getPath().equals(file.getPath()));
                foldersFromDisk.postValue(new ArrayList<>(currentDisk));
            }

            if (idFolder > 0) {
                cancelAutoDownload(getApplication(), idFolder);
                deleteBookFromDB(idFolder);
            } else {
                myLogE("deleteAudio -> Book not found in DB");
            }
        } else {
            myLogE("Error deleting from disk");
        }
    }

    private int getBookFolderId(File file) {
        List<FolderSummary> summaries = foldersFromDb.getValue();
        if (summaries != null) {
            for (FolderSummary f : summaries) {
                if (file.getPath().equals(f.path)) {
                    return f.id;
                }
            }
        }
        return 0;
    }

    private boolean deleteBookFromDisk(String strPath) {
        try {
            File file = new File(normalizeFsPath(strPath));
            return file.exists() && FileHelper.recursiveRemove(file);
        } catch (Exception e) {
            myLogEE(e, "deleteBookFromDisk");
            return false;
        }
    }


    private void deleteBookFromDB(long idFolder) {
        cacheFilesRepository.deleteBookFromDB(idFolder, success -> {
            if (success) {
                myLog("Deleted from DB");
            } else {
                myLogE("Failed to delete from DB");
            }
        });
    }

    private static String normalizeFsPath(String p) {
        if (p == null) return null;
        // Drop file:// or file:/// prefixes, collapse slashes a bit
        p = p.replaceFirst("^file:/+", "/");
        try {
            return new File(p).getAbsolutePath();
        } catch (Throwable t) {
            return p;
        }
    }

}
