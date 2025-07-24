package com.driot.bookplayer.activities;

import static com.driot.bookplayer.global.Var.FOLDER_UNZIPPED;
import static com.driot.bookplayer.utils.PodcastHelper.cancelAutoDownload;
import static com.driot.bookplayer.utils.Utils.recursiveRemove;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.utils.StorageHelper;
import com.driot.bookplayer.utils.Utils;
import com.driot.bookplayer.utils.log.LoggingViewModel;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CacheFilesViewModel extends LoggingViewModel {
    private final CacheFilesRepository cacheFilesRepository;
    private LiveData<List<ZikFile>> filesFromDb;
    private final MutableLiveData<List<File>> filesFromDisk = new MutableLiveData<>();
    private final MutableLiveData<Boolean> memoryStats = new MutableLiveData<>();
    private boolean useInternal = true;

    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    public LiveData<Boolean> getIsLoading() { return isLoading; }

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public CacheFilesViewModel(@NonNull Application application) {
        super(application);
        cacheFilesRepository = new CacheFilesRepository(application);
        loadBookFromDB();
        loadFilesFromDisk();
    }

    public LiveData<List<ZikFile>> getFilesOnDb() {
        return filesFromDb;
    }

    public MutableLiveData<List<File>> getFilesOnDisk() {
        return filesFromDisk;
    }

    public LiveData<Boolean> getMemoryStats() {
        return memoryStats;
    }

    public boolean isUsingInternal() {
        return useInternal;
    }

    public void setUseInternal(boolean useInternal) {
        this.useInternal = useInternal;
        loadFilesFromDisk();
    }

    private void loadBookFromDB() {
        filesFromDb = AppDatabase.getDatabase(getApplication()).ZikFileDao().getZikFileDistinctLocations();
    }

    private void loadFilesFromDisk() {
        isLoading.postValue(true);

        executorService.execute(() -> {
            try {
                String basePath = useInternal
                        ? getApplication().getFilesDir().getPath() + "/" + FOLDER_UNZIPPED
                        : StorageHelper.getSdCardUnzippedFolder(getApplication());

                if (basePath == null) {
                    myLogE("No valid base path found");
                    filesFromDisk.postValue(new ArrayList<>());
                    isLoading.postValue(false);
                    return;
                }

                File baseDir = new File(basePath);
                if (baseDir.exists() && baseDir.isDirectory()) {
                    File[] fileArray = baseDir.listFiles();
                    if (fileArray != null) {
                        List<File> files = Arrays.asList(fileArray);
                        filesFromDisk.postValue(files); // show early

                        files.sort(Comparator.comparingLong(Utils::getCustomLength));
                        Collections.reverse(files);
                        filesFromDisk.postValue(files); // show sorted

                        myLog(files.size() + " files in: [" + basePath + "]");
                    } else {
                        filesFromDisk.postValue(new ArrayList<>());
                        myLog("No files found in: [" + basePath + "]");
                    }
                } else {
                    filesFromDisk.postValue(new ArrayList<>());
                    myLog("Base dir doesn't exist: [" + basePath + "]");
                }

                memoryStats.postValue(true);
            } catch (Exception e) {
                myLogEE(e, "loadFilesFromDisk");
            } finally {
                isLoading.postValue(false);
            }
        });
    }

    public void deleteAudio(File file) {
        myLog("deleting file : [" + file.getPath() + "]");
        if (deleteBookFromDisk(file.getPath())) {
            loadFilesFromDisk();
            int idFolder = getBookFolderId(file);
            if (idFolder > 0) {
                cancelAutoDownload(getApplication(), idFolder);
                deleteBookFromDB(idFolder);
                loadBookFromDB();
            } else {
                myLogE("Book not found in DB");
            }
        } else {
            myLogE("Error deleting from disk");
        }
    }

    private int getBookFolderId(File file) {
        int idFolder = 0;
        if (filesFromDb != null && filesFromDb.getValue() != null) {
            for (ZikFile f : filesFromDb.getValue()) {
                if (file.getPath().equals(f.getPath())) {
                    idFolder = f.getIdFolder();
                    break;
                }
            }
        } else {
            myLogE("getBookFolderId() -> 0");
        }
        myLog("getBookFolderId for [" + file.getPath() + "] => [" + idFolder + "]");
        return idFolder;
    }

    private boolean deleteBookFromDisk(String strPath) {
        String starter = "file:///";
        strPath = strPath.replace(starter, "");
        try {
            File file = new File(strPath);
            return file.exists() && recursiveRemove(file);
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
}
