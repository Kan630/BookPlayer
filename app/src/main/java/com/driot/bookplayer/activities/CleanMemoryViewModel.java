package com.driot.bookplayer.activities;

import static com.driot.bookplayer.global.Var.FOLDER_UNZIPPED;
import static com.driot.bookplayer.helpers.PodcastHelper.cancelAutoDownload;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.helpers.FileHelper;
import com.driot.bookplayer.objects.FileWithSummary;
import com.driot.bookplayer.objects.ZikFileSummary;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingAndroidViewModel;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CleanMemoryViewModel extends LoggingAndroidViewModel {
    private final CleanMemoryRepository cacheFilesRepository;
    private final MediatorLiveData<List<FileWithSummary>> enrichedFiles = new MediatorLiveData<>();
    private final MutableLiveData<List<File>> foldersFromDisk = new MutableLiveData<>();
    private final LiveData<List<ZikFileSummary>> filesFromDb;
    private final MutableLiveData<Boolean> memoryStats = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private final MutableLiveData<Long> totalAudioSizeMB = new MutableLiveData<>(0L);
    public LiveData<Long> getTotalAudioSizeMB() {
        return totalAudioSizeMB;
    }
    private boolean useInternal = true;
    private final Map<String, Long> folderSizeCache = new HashMap<>();

    public CleanMemoryViewModel(@NonNull Application application) {
        super(application);
        cacheFilesRepository = new CleanMemoryRepository(application);

        filesFromDb = AppDatabase.getDatabase(getApplication())
                .ZikFileDao()
                .getZikFileDistinctLocations();

        enrichedFiles.addSource(foldersFromDisk, diskFiles -> updateEnrichedFiles(diskFiles, filesFromDb.getValue()));
        enrichedFiles.addSource(filesFromDb, dbSummaries -> updateEnrichedFiles(foldersFromDisk.getValue(), dbSummaries));

        loadFilesFromDisk();
    }

    public LiveData<List<FileWithSummary>> getEnrichedFiles() {
        return enrichedFiles;
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
                    File[] fileArray = baseDir.listFiles();

                    if (baseDir.exists() && baseDir.isDirectory() && fileArray != null) {
                        unzip_folders = new ArrayList<>(Arrays.asList(fileArray));
                        myLog(unzip_folders.size() + " folders in: [" + basePath + "]");
                    } else {
                        myLog("No valid files found in base directory: [" + basePath + "]");
                    }
                } else {
                    myLogE("No valid base path found");
                }

                long totalSize = 0L;
                for (File f : unzip_folders) {
                    long size = Tonio.getFolderSize(f);
                    folderSizeCache.put(f.getPath(), size / 1024 / 1024); // Store in MB
                    totalSize += size;
                }
                totalAudioSizeMB.postValue(totalSize / 1024 / 1024);

                foldersFromDisk.postValue(unzip_folders);
                memoryStats.postValue(true);
            } catch (Exception e) {
                myLogEE(e, "loadFilesFromDisk");
            } finally {
                isLoading.postValue(false);
            }
        });
    }

    private void updateEnrichedFiles(List<File> diskFiles, List<ZikFileSummary> dbSummaries) {
        if (diskFiles == null || dbSummaries == null) return;

        Map<String, ZikFileSummary> summaryMap = new HashMap<>();
        for (ZikFileSummary summary : dbSummaries) {
            String k = folderKey(summary.path);
            if (k != null) summaryMap.put(k, summary);
        }

        List<FileWithSummary> enriched = new ArrayList<>(diskFiles.size());
        for (File file : diskFiles) {
            ZikFileSummary summary = summaryMap.get(file.getPath());

            double percentDone = summary != null ? summary.percentDone : 0;
            String sourceLocation = summary != null ? summary.sourceLocation : "";
            String playType = summary != null ? summary.playType : "";

            long sizeMB = folderSizeCache.containsKey(file.getPath())
                    ? folderSizeCache.get(file.getPath())
                    : (Tonio.getFolderSize(file) / 1024 / 1024);
            folderSizeCache.putIfAbsent(file.getPath(), sizeMB);

            enriched.add(new FileWithSummary(file, percentDone, sourceLocation, playType, sizeMB));
        }
        enriched.sort(Comparator.comparingLong(f -> f.fileSizeMB));
        Collections.reverse(enriched);
        enrichedFiles.postValue(enriched);
    }


    public void deleteAudio(File file) {
        myLog("deleting file : [" + file.getPath() + "]");
        int idFolder = getBookFolderId(file);
        if (deleteBookFromDisk(file.getPath())) {
            Long sizeMB = folderSizeCache.remove(file.getPath());
            if (sizeMB != null && totalAudioSizeMB.getValue() != null) {
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
        List<ZikFileSummary> summaries = filesFromDb.getValue();
        if (summaries != null) {
            for (ZikFileSummary f : summaries) {
                if (file.getPath().equals(f.path)) {
                    return f.idFolder;
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


    private void deleteBookFromDB(int idFolder) {
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
    private static String folderKey(String pathOrFile) {
        String n = normalizeFsPath(pathOrFile);
        if (n == null) return null;
        java.io.File f = new java.io.File(n);
        return f.isDirectory() ? n : f.getParent();
    }

}
