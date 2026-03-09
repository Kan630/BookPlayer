package com.driot.bookplayer.imports;

import android.content.Context;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.FolderDao;
import com.driot.bookplayer.utils.log.KanLogger;

/**
 * Centralized helper for verifying if an import candidate already exists
 * in the BookPlayer database. Used by both Mass Import (via BookCandidate)
 * and Single Import (via ImportBookSingleActivity).
 */
public class ImportValidator {

    private static final String TAG = "ImportValidator";

    private static void myLog(String msg) {
        KanLogger.myLog(TAG, msg);
    }

    private static void myLogEE(Exception e, String msg) {
        KanLogger.myLogEE(e, TAG + " - " + msg);
    }

    /**
     * Checks if a specific file hash already exists in the database.
     * 
     * @param context Application context
     * @param hash    The generated file hash
     * @return The name of the existing book if found, null otherwise.
     */
    public static String checkHashExists(Context context, String hash) {
        if (hash == null || hash.isEmpty()) {
            return null;
        }
        try {
            FolderDao folderDao = AppDatabase.getDatabase(context).folderDao();
            return folderDao.originalHashAlreadyExist_getBookName(hash);
        } catch (Exception e) {
            myLogEE(e, "Error checking hash exists: " + hash);
            return null;
        }
    }

    /**
     * Checks if a specific folder path already exists in the database.
     * 
     * @param context    Application context
     * @param folderPath The exact path to check
     * @return The name of the existing book if found, null otherwise.
     */
    public static String checkPathExists(Context context, String folderPath) {
        if (folderPath == null || folderPath.isEmpty()) {
            return null;
        }
        try {
            FolderDao folderDao = AppDatabase.getDatabase(context).folderDao();
            return folderDao.folderAlreadyExist_checkFolderPath_getBookName(folderPath);
        } catch (Exception e) {
            myLogEE(e, "Error checking path exists: " + folderPath);
            return null;
        }
    }

    /**
     * Checks if a folder name already exists in the database. Usually used right
     * before inserting.
     * 
     * @param context    Application context
     * @param folderName The target folder name
     * @return true if the folder name already exists.
     */
    public static boolean checkNameExists(Context context, String folderName) {
        if (folderName == null || folderName.isEmpty()) {
            return false;
        }
        try {
            FolderDao folderDao = AppDatabase.getDatabase(context).folderDao();
            return folderDao.folderAlreadyExist_checkFolderName(folderName) > 0;
        } catch (Exception e) {
            myLogEE(e, "Error checking name exists: " + folderName);
            return false;
        }
    }
}
