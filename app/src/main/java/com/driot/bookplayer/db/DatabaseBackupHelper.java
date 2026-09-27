package com.driot.bookplayer.db;

import static com.driot.bookplayer.db.DatabaseClient.DATABASE_NAME;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.sqlite.db.SupportSQLiteDatabase;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import java.io.*;

public class DatabaseBackupHelper {
    public static final String BACKUP_FOLDER_NAME = "BookPlayerBackup";


    /**
     * Copies the live database to Download/BookPlayerBackup/ under a new timestamped name, and
     * returns that name (null on failure). Goes through MediaStore: a plain File write there
     * fails with EACCES as soon as a file of the same name was left by another install (scoped
     * storage), which is what the old fixed-name backup ran into. Room keeps recent writes in
     * the -wal file, so they are checkpointed into the main file first; if the checkpoint could
     * not finish, the -wal file is exported next to it (same name + "-wal", sqlite3 picks it up).
     * Call off the main thread.
     */
    public static String exportDatabaseToDownloads(Context context) {
        try {
            SupportSQLiteDatabase db = AppDatabase.getDatabase(context).getOpenHelper().getWritableDatabase();
            int busy = -1;
            try (Cursor c = db.query("PRAGMA wal_checkpoint(TRUNCATE)")) {
                if (c.moveToFirst())
                    busy = c.getInt(0);
            }
            File dbFile = context.getDatabasePath(DATABASE_NAME);
            String name = "BookPlayer_db_v" + db.getVersion() + "_" + timestamp() + ".db";
            copyToDownloads(context, dbFile, name, "application/octet-stream");

            File walFile = new File(dbFile.getPath() + "-wal");
            if (busy != 0 && walFile.length() > 0) {
                myLogW("exportDatabase: checkpoint incomplete (busy=" + busy + "), exporting -wal too");
                copyToDownloads(context, walFile, name + "-wal", "application/octet-stream");
            }
            myLogI("exportDatabase: Download/" + BACKUP_FOLDER_NAME + "/" + name + " (" + dbFile.length() + " bytes)");
            return name;
        } catch (Exception e) {
            myLogEE(e, "exportDatabase");
            return null;
        }
    }

    /**
     * Writes a fresh auto-backup snapshot (AutoBackupSnapshotManager), then copies it to
     * Download/BookPlayerBackup/ under a timestamped name so it can be read. The private copy
     * stays where it is. Returns the exported name, null on failure. Call off the main thread.
     */
    public static String exportSnapshotToDownloads(Context context) {
        try {
            long start = System.currentTimeMillis();
            com.driot.bookplayer.importexport.AutoBackupSnapshotManager.writeSnapshot(context);
            File snapshot = com.driot.bookplayer.importexport.AutoBackupSnapshotManager.getSnapshotFile(context);
            // writeSnapshot() logs and swallows its own errors: don't pass an older file off as fresh.
            if (!snapshot.exists() || snapshot.lastModified() < start - 2000) {
                myLogE("exportSnapshot: snapshot was not (re)written");
                return null;
            }
            String name = "BookPlayer_snapshot_" + timestamp() + ".json";
            copyToDownloads(context, snapshot, name, "application/json");
            myLogI("exportSnapshot: Download/" + BACKUP_FOLDER_NAME + "/" + name + " (" + snapshot.length() + " bytes)");
            return name;
        } catch (Exception e) {
            myLogEE(e, "exportSnapshot");
            return null;
        }
    }

    private static String timestamp() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd_HHmmss", java.util.Locale.US).format(new java.util.Date());
    }

    private static void copyToDownloads(Context context, File src, String displayName, String mimeType)
            throws IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, displayName);
        values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + BACKUP_FOLDER_NAME + "/");
        Uri dest = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (dest == null)
            throw new IOException("MediaStore insert returned null for " + displayName);
        try (InputStream in = new FileInputStream(src);
             OutputStream out = context.getContentResolver().openOutputStream(dest)) {
            if (out == null)
                throw new IOException("no output stream for " + dest);
            byte[] buf = new byte[64 * 1024];
            int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
        } catch (IOException e) {
            context.getContentResolver().delete(dest, null, null); // don't leave a truncated copy behind
            throw e;
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
