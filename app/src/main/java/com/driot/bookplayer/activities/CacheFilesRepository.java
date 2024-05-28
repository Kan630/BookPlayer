package com.driot.bookplayer.activities;

import android.app.Application;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.FolderDao;
import com.driot.bookplayer.db.ZikFileDao;
import com.driot.tonylib.KanLogger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * created 2024-05-27 (re-created from 2 days ago)
 */
public class CacheFilesRepository {

    private ZikFileDao zikFileDao;
    private FolderDao folderDao;
    private ExecutorService executorService;

    public CacheFilesRepository(Application application) {
        AppDatabase db = AppDatabase.getDatabase(application);
        zikFileDao = db.ZikFileDao();
        folderDao = db.FolderDao();
        executorService = Executors.newSingleThreadExecutor();
    }

    public void deleteBookFromDB(int idFolder, DeletionCallback callback) {
        myLog("deleteBookFromDB => executorService.execute()");
        executorService.execute(() -> {
            try {
                zikFileDao.deleteFolder(idFolder);
                myLog("deleteBookFromDB => deletion in ZikFile - done");
                folderDao.delete(idFolder);
                myLog("deleteBookFromDB => deletion in Folder - done");
                callback.onDeletionComplete(true);
            } catch (Exception e) {
                myLogE("deleteBookFromDB - " + e.getMessage());
                callback.onDeletionComplete(false);
            }
        });
    }

    // Nested interface
    public interface DeletionCallback {
        void onDeletionComplete(boolean success);
    }

    //--- LOG --------------------------
    private void myLog(String str) {
        KanLogger.myLog(this.getClass().getName(), str);
    }

    private void myLogE(String str) {
        KanLogger.myLogE(this.getClass().getName(), str);
    }
}
