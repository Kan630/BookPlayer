package com.driot.bookplayer.utils;

import static com.driot.bookplayer.db.Sql.calculateFolderProgress;
import static com.driot.bookplayer.db.Sql.updateFolderDuration;
import static com.driot.bookplayer.utils.KanFiles.getMediaDurationFromPath;
import static com.driot.bookplayer.utils.Tonio.formatNameForDisplay;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.FolderDao;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.db.ZikFileDao;

import java.io.File;

public class PodcastSyncWorker extends Worker {
    public static final String KEY_FOLDER_PATH = "folder_path";
    public static final String KEY_FOLDER_NAME = "folder_name";

    public PodcastSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String path = getInputData().getString(KEY_FOLDER_PATH);
        String name = getInputData().getString(KEY_FOLDER_NAME);
        File folder = new File(path);
        if (!folder.exists() || !folder.isDirectory()) return Result.failure();

        AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
        FolderDao folderDao = db.FolderDao();
        ZikFileDao zikFileDao = db.ZikFileDao();

        // 1. Ensure folder is registered
        Folder folderDb = folderDao.getByName(name);
        int idFolder = -1;
        if (folderDb != null) {
            idFolder = folderDb.getId();
        } else {
            folderDb = new Folder();

            folderDb.setName(name);
            folderDb.setPath(path);
            folderDb.setUri(path); //2023-10-22 deprecated
            folderDb.setPercentdone(0.0);
            folderDb.setFinished(false);
            folderDb.setIszipfile(false); //2023-10-22 deprecated (live zip reading - code has been removed)
            folderDb.setOriginalHash("");
            folderDb.setSourceLocation("podcast");

            long newId = folderDao.insert(folderDb); // Room returns the new ID
            idFolder = (int) newId; // safely cast to int
        }

        // 2. Scan files
        File[] files = folder.listFiles((dir, filename) -> filename.endsWith(".mp3"));
        if (files == null) return Result.success();

        int newFilesCount = 0;
        for (File file : files) {
            int idFile = zikFileDao.getId(idFolder, file.getName());

            if (idFile < 1) { // not in DB
                double zeOrder = zikFileDao.getMaxOrder(idFolder) + 1;

                myLogD("getting duration for file : [" +  file.getAbsolutePath() + ']');
                long duration = 0;
                duration = getMediaDurationFromPath(file.getAbsolutePath());
                if (duration > 0) {
                    ZikFile zikFile = new ZikFile();
                    zikFile.setIdFolder(idFolder);
                    zikFile.setName(file.getName());
                    zikFile.setPath(file.getAbsolutePath());
                    zikFile.setDisplayName(formatNameForDisplay(file.getName()));
                    zikFile.setZeorder(zeOrder);
                    zikFile.setFolderName(folderDb.getName());
                    zikFile.setPercentdone(0.0);
                    zikFile.setPosition(0);
                    zikFile.setIszipfile(false); //2023-10-22 code removed for live zip reading
                    zikFile.setFinished(false);
                    zikFile.setDuration(duration);
                    zikFileDao.insert(zikFile);
                    newFilesCount++;
                }
            }
        }

        // 3. Notify user
        if (newFilesCount > 0) {
            updateFolderDuration(getApplicationContext(), idFolder);
            calculateFolderProgress(getApplicationContext(), idFolder);
            folderDao.updateLastAccess(idFolder, new java.sql.Date(System.currentTimeMillis())); //triggers livedata update and reload of Book list
            Handler handler = new Handler(Looper.getMainLooper());
            int finalNewFilesCount = newFilesCount;
            handler.post(() -> myToast(name + " synchronized: " + finalNewFilesCount + " new episodes"));
        }

        return Result.success();
    }

    //--- FULL LOG --------------------------
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
