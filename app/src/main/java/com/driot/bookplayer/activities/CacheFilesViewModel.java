package com.driot.bookplayer.activities;

import static com.driot.bookplayer.global.Var.FOLDER_UNZIPPED;
import static com.driot.bookplayer.utils.Utils.getCustomLength;
import static com.driot.bookplayer.utils.Utils.recursiveRemove;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.FolderDao;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.db.ZikFileDao;
import com.driot.bookplayer.utils.FileSorter;
import com.driot.bookplayer.utils.Utils;
import com.driot.tonylib.KanLogger;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


public class CacheFilesViewModel extends AndroidViewModel {
    private LiveData<List<ZikFile>> filesFromDb;
    private MutableLiveData<List<File>> filesFromDisk = new MutableLiveData<>();
    private final ZikFileDao zikFileDao;
    private final FolderDao folderDao;
    private CacheFilesRepository cacheFilesRepository; // manage deletions
    private FileSorter fileSorter;
    public CacheFilesViewModel(@NonNull Application application) {
        super(application);
        cacheFilesRepository = new CacheFilesRepository(application);
        AppDatabase db = AppDatabase.getDatabase(application);
        zikFileDao = db.ZikFileDao();
        folderDao = db.FolderDao();
        loadFilesFromDisk();
        myLog("CacheFilesViewModel instantiated");
    }

    public LiveData<List<ZikFile>> getFilesOnDb() {
        myLog("LiveData<List<ZikFile>> getFilesOnDb()");
        filesFromDb = zikFileDao.getZikFileDistinctLocations();
        return filesFromDb;
    }

    public MutableLiveData<List<File>> getFilesOnDisk() {
        return filesFromDisk;
    }
    private void loadFilesFromDisk() {
        myLog("LiveData<List<ZikFile>> getFilesOnDisk()");
        String cachePath = getApplication().getFilesDir().getPath() + "/" + FOLDER_UNZIPPED;
        File cacheDir = new File(cachePath);
        if (cacheDir.exists() && cacheDir.isDirectory()) {
            if (cacheDir.listFiles() != null) {
                List<File> files = Arrays.asList(cacheDir.listFiles());
                files.sort(Comparator.comparingLong(Utils::getCustomLength));
                Collections.reverse(files);
                filesFromDisk.setValue(files); // = new MutableLiveData<List<File>>(files);
            } else {
                filesFromDisk.setValue(new ArrayList<>()); // Set an empty list if no files found
            }
        } else {
            filesFromDisk.setValue(new ArrayList<>());
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
                deleteBookFromDB(idFolder);
            } else {
                myLogE("Error getting book reference in database, so no deletion in database");
            }

        } else {
            myLogE("Error deleting book from internal app memory");
        }
    }
    private int getBookFolderId(File file) {
        int idFolder = 0;
        if (filesFromDb != null) {
            for (ZikFile f : filesFromDb.getValue()) {
                if (file.getPath().equals(f.getPath())) {
                    idFolder = f.getIdFolder();
                    break;
                }
            }
        } else {
            myLogE("distinctZikFilePaths == null");
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
                            myLog("Deleted from Disk");
                            return true;
                        } else {
                            myLog("NOT Deleted from Disk");
                            return false;
                        }
                    } else {
                        myLogE("file does not exist");
                        return false;
                    }
                } catch (Exception e) {
                    myLogE("Error remove ZikFile from Disk: " + e.getMessage());
                    return false;
                }
        } else {
            myLogE("should not happen uri less than 5 chars");
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



    //--- LOG --------------------------
    private void myLog(String str) {
        KanLogger.myLog(this.getClass().getName(), str);
    }
    private void myLogE(String str) {
        KanLogger.myLogE(this.getClass().getName(), str);
    }
}