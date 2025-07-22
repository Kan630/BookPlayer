package com.driot.bookplayer.activities;

import static com.driot.bookplayer.global.Var.FOLDER_UNZIPPED;
import static com.driot.bookplayer.utils.PodcastIndexHelper.cancelAutoDownload;
import static com.driot.bookplayer.utils.Utils.recursiveRemove;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.PodcastDao;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.utils.Utils;
import com.driot.bookplayer.utils.log.LoggingViewModel;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


public class CacheFilesViewModel extends LoggingViewModel {
    private LiveData<List<ZikFile>> filesFromDb;
    private final MutableLiveData<List<File>> filesFromDisk = new MutableLiveData<>();
    private final CacheFilesRepository cacheFilesRepository; // manage deletions
    public CacheFilesViewModel(@NonNull Application application) {
        super(application);
        cacheFilesRepository = new CacheFilesRepository(application);
        loadFilesFromDisk();
        loadBookFromDB();
        myLog("CacheFilesViewModel instantiated");
    }

    public LiveData<List<ZikFile>> getFilesOnDb() {
        return filesFromDb;
    }

    public MutableLiveData<List<File>> getFilesOnDisk() {
        return filesFromDisk;
    }

    private MutableLiveData<Boolean> memoryStats = new MutableLiveData<>();
    public LiveData<Boolean> getMemoryStats() {
        return memoryStats;
    }


    private void loadBookFromDB() {
        myLog("LiveData<List<ZikFile>> loadBookFromDB()");
        filesFromDb = AppDatabase.getDatabase(getApplication()).ZikFileDao().getZikFileDistinctLocations();
    }

    private void loadFilesFromDisk() {
        myLog("MutableLiveData<List<ZikFile>> loadFilesFromDisk()");
        try {
            String cachePath = getApplication().getFilesDir().getPath() + "/" + FOLDER_UNZIPPED;
            File cacheDir = new File(cachePath);
            if (cacheDir.exists() && cacheDir.isDirectory()) {
                if (cacheDir.listFiles() != null && cacheDir.listFiles()!=null) {
                    List<File> files = Arrays.asList(cacheDir.listFiles());
                    files.sort(Comparator.comparingLong(Utils::getCustomLength));
                    Collections.reverse(files);
                    filesFromDisk.setValue(files); // = new MutableLiveData<List<File>>(files);
                    myLog(files.size() + " files in cachePath : [" + cachePath + "]");
                } else {
                    filesFromDisk.setValue(new ArrayList<>()); // Set an empty list if no files found
                    myLog("no file in cachePath : [" + cachePath + "]");
                }
            } else {
                filesFromDisk.setValue(new ArrayList<>());
                myLog("directory cachePath does not exist: [" + cachePath + "]");
            }
            memoryStats.postValue(true); // notify for header update
        } catch (Exception e) {
            myLogEE(e, "loadFilesFromDisk");
        }
    }

    public void deleteAudio(File file) {
        myLog("deleting file : [" + file.getPath() + "]");
        if (deleteBookFromDisk(file.getPath())) {
            //filesFromDisk.notify(); => java.lang.IllegalMonitorStateException: object not locked by thread before notify()
            myLog("File deleted from Disk, launching DB deletion...");
            loadFilesFromDisk();
            int idFolder = getBookFolderId(file);
            if (idFolder > 0) {
                cancelAutoDownload(getApplication(), idFolder);
                deleteBookFromDB(idFolder);
                loadBookFromDB();
            } else {
                myLogE("Error getting book reference in database, so no deletion in database");
            }
        } else {
            myLogE("Error deleting book from internal app memory");
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
        if (strPath.length()>5) {
                strPath = strPath.replace(starter,"");
                try {
                    File zikFileToDelete = new File(strPath);
                    if(zikFileToDelete.exists()) {
                        if (recursiveRemove(zikFileToDelete)) {
                            myLog("Deleted from Disk : [" + strPath + "]");
                            return true;
                        } else {
                            myLog("NOT Deleted from Disk : [" + strPath + "]");
                            return false;
                        }
                    } else {
                        myLogE("file does not exist : [" + strPath + "]");
                        return false;
                    }
                } catch (Exception e) {
                    myLogEE(e,"Error remove ZikFile from Disk : [" + strPath + "]");
                    return false;
                }
        } else {
            myLogE("should not happen uri less than 5 chars for path [" + strPath + "]");
            return false;
        }
    }

    private void deleteBookFromDB(int idFolder) {
        cacheFilesRepository.deleteBookFromDB(idFolder, success -> {
            if (success) {
                myLog("Successful deletion from DB");
            } else {
                myLogE("Error deleting from DB");
            }
        });
    }

}