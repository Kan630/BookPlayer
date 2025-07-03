package com.driot.bookplayer.activities;

import android.app.Application;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.FolderDao;
import com.driot.bookplayer.db.ZikFileDao;
import com.driot.bookplayer.utils.KanLogger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 2024-05-27
 */
public class CacheFilesRepository {

    private final ZikFileDao zikFileDao;
    private final FolderDao folderDao;
    private final ExecutorService executorService;

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
                myLogEE(e,"deleteBookFromDB");
                callback.onDeletionComplete(false);
            }
        });
    }

    // Nested interface
    public interface DeletionCallback {
        void onDeletionComplete(boolean success);
    }

    //--- LOG --------------------------
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogInFile(String str) { KanLogger.myLogInFile(this.getClass().getName(), str); }
    private void myLogD(String str) { KanLogger.myLogD(this.getClass().getName(), str); }
    private void myLogI(String str) { KanLogger.myLogI(this.getClass().getName(), str); }
    private void myLogW(String str) { KanLogger.myLogW(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
    private void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, this.getClass().getName(), str); }
    private void myToast(String str) { KanLogger.myToast(this.getClass().getName(), str); }
    private void myToastE(String str) { KanLogger.myToastE(this.getClass().getName(), str); }
    private void myKeyFirebase(String strKey, String strValue) {KanLogger.myKeyFirebase(strKey, strValue);}
    private void myLogFirebase(String strLog) {KanLogger.myLogFirebase(strLog);}
}
