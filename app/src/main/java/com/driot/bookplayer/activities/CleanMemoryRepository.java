package com.driot.bookplayer.activities;

import android.app.Application;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.FolderDao;
import com.driot.bookplayer.db.CommonZikFileDao;
import com.driot.bookplayer.utils.log.LoggerHelper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 2024-05-27
 */
public class CleanMemoryRepository extends LoggerHelper {

    private final CommonZikFileDao zikFileDao;
    private final FolderDao folderDao;
    private final ExecutorService executorService;

    public CleanMemoryRepository(Application application) {
        super(CleanMemoryRepository.class);
        AppDatabase db = AppDatabase.getDatabase(application);
        zikFileDao = db.zikFileDao();
        folderDao = db.folderDao();
        executorService = Executors.newSingleThreadExecutor();
    }

    public void deleteBookFromDB(long idFolder, DeletionCallback callback) {
        myLog("deleteBookFromDB => executorService.execute()");
        executorService.execute(() -> {
            try {
                zikFileDao.deleteAllZikFilesInFolder(idFolder);
                myLog("deleteBookFromDB => deletion in ZikFile - done");
                folderDao.delete(idFolder);
                myLog("deleteBookFromDB => deletion in Folder - done");
                callback.onDeletionComplete(true);
            } catch (Exception e) {
                myLogEE(e,"deleteBookFromDB");
                callback.onDeletionComplete(false);
            }
        });
    }

    // Nested interface
    public interface DeletionCallback {
        void onDeletionComplete(boolean success);
    }
}
