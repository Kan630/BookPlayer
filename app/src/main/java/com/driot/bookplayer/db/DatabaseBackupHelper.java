package com.driot.bookplayer.db;

import static com.driot.bookplayer.db.DatabaseClient.DATABASE_NAME;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;

import androidx.sqlite.db.SupportSQLiteDatabase;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import java.io.*;

public class DatabaseBackupHelper {
    public static final String BACKUP_NAME = "bookplayer_backup.db";
    public static final String BACKUP_FOLDER_NAME = "BookPlayerBackup";


    public static File getBackupDir() {
        File backupDir = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                BACKUP_FOLDER_NAME
        );
        if (!backupDir.exists()) {
            backupDir.mkdirs(); // create folder if needed
        }
        return backupDir;
    }

    public static boolean backupDatabase(Context context) {
        try {
            File dbFile = context.getDatabasePath(DATABASE_NAME);

            //main save
            File backupFile = new File(getBackupDir(), BACKUP_NAME);
            copyFile(dbFile, backupFile);
            myLogI(backupFile.getAbsolutePath());

            //just to be sure
            File backupFile2 = new File(context.getExternalFilesDir(null), BACKUP_NAME);
            copyFile(dbFile, backupFile2);
            myLogI(backupFile2.getAbsolutePath());

            return true;
        } catch (IOException e) {
            myLogEE(e,"backupDatabase");
            return false;
        }
    }

    public static boolean restoreDatabase(Context context) {
        try {
            File dbFile = context.getDatabasePath(DATABASE_NAME);
            File backupFile = new File(getBackupDir(), BACKUP_NAME);
            if (!backupFile.exists()) return false;
            copyFile(backupFile, dbFile);
            return true;
        } catch (IOException e) {
            myLogEE(e,"restoreDatabase");
            return false;
        }
    }

    private static void copyFile(File src, File dst) throws IOException {
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
        }
    }

    public static int getDatabaseVersion(File dbFile) {
        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(dbFile.getPath(), null, SQLiteDatabase.OPEN_READONLY);
            return db.getVersion();
        } catch (Exception e) {
            return -1;
        } finally {
            if (db != null) db.close();
        }
    }

    public static String getSQLiteVersion(SupportSQLiteDatabase db) {
        try (Cursor cursor = db.query("SELECT sqlite_version() AS sqlite_version")) {
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (Exception e) {
            myLogE("Failed to get SQLite version : " + e.getMessage());
        }
        return "unknown";
    }

}
