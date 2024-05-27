package com.driot.bookplayer.activities;

import android.app.Activity;
import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.db.FolderDao;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.db.ZikFileDao;
import com.driot.tonylib.KanLogger;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class CacheFilesViewModel extends AndroidViewModel {
    //private CacheFilesRepository repository;
    private LiveData<List<ZikFile>> distinctZikFilePaths;
    private MutableLiveData<List<File>> filesFromDisk;
    private final ZikFileDao zikFileDao;
    private final FolderDao folderDao;

    public CacheFilesViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.getDatabase(application);
        zikFileDao = db.ZikFileDao();
        folderDao = db.FolderDao();
        myLog("CacheFilesViewModel instantiated");
    }

    public LiveData<List<ZikFile>> getFilesOnDb() {
        myLog("LiveData<List<ZikFile>> getFilesOnDb()");
        return zikFileDao.getZikFileDistinctLocations();
    }

    public LiveData<List<File>> getFilesOnDisk() {
        myLog("LiveData<List<ZikFile>> getFilesOnDisk()");
            String cachePath = getApplication().getFilesDir().getPath() + "/unzipped";
            File cacheDir = new File(cachePath);
            if (!(cacheDir.listFiles() == null)) {
                filesFromDisk = new MutableLiveData<List<File>>(Arrays.asList(cacheDir.listFiles()));
                return filesFromDisk;
            } else {
                myLogE("no files found on Disk in " + cacheDir.getPath());
                return null;
            }
    }

    public void deleteAudio(File file) {
        myLog("deleting file : [" + file.getPath() + "]");
        if (deleteBookFromDisk(file.getPath())) {
            myLog("File deleted from Disk, launching DB deletion...");
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
        boolean bFound = false;
        int idFolder = 0;
        String zeAudioStatus = "...";
        if (!(distinctZikFilePaths == null)) {
            for (ZikFile f : distinctZikFilePaths.getValue()) {
                if (file.getPath().equals(f.getPath())) {
                    idFolder = f.getIdFolder();
                    bFound = true;
                    break;
                }
            }
        }
        myLog("getBookFolderId for [" + file.getPath() + "] => [" + idFolder + "]");
        return idFolder;
    }

    private boolean deleteBookFromDisk(String strPath) {
        String starter = "file:///";
        myLog("Deleting ZikFile : " +strPath);
        if (strPath.length()>5) {
            //if (strPath.startsWith(starter)) {
                strPath = strPath.replace(starter,"");
                try {
                    File zikFileToDelete = new File(strPath);
                    if(zikFileToDelete.exists()) {
                        zikFileToDelete.delete();
                    }
                    return true;
                } catch (Exception e) {
                    myLogE("Error remove ZikFile from Disk: " + e.getMessage());
                    return false;
                }
            //} else {
            //    myLog("Not a ZikFile in user data, skip deletion of ZikFile");
            //    return true;
            //}
        } else {
            myLogE("should not happen uri less than 5 chars");
            return false;
        }
    }
    private void deleteBookFromDB(int idFolder) {
        new Thread(() -> {
            zikFileDao.deleteFolder(idFolder);
        }).start();
        new Thread(() -> {
            folderDao.delete(idFolder);
        }).start();
    }



    //--- LOG --------------------------
    private void myLog(String str) {
        KanLogger.myLog(this.getClass().getName(), str);
    }
    private void myLogE(String str) {
        KanLogger.myLogE(this.getClass().getName(), str);
    }
}