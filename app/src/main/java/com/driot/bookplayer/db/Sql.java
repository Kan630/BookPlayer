package com.driot.bookplayer.db;

import android.content.Context;

import android.database.Cursor;
import android.text.format.Formatter;

import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SimpleSQLiteQuery;

import com.driot.bookplayer.utils.Tonio;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 10/12/20
 */
public class Sql {

    public static void updateFolderTable(Context c, long mFolderId) {
        String strSQL = "UPDATE Folder " +
                "SET " +
                "  duration = (SELECT IFNULL(SUM(duration), 0) FROM ZikFile WHERE ZikFile.idFolder = Folder.id), " +
                "  timeListened = (SELECT IFNULL(SUM(timeListened), 0) FROM ZikFile WHERE ZikFile.idFolder = Folder.id), "
                +
                "  nbZikFile = (SELECT COUNT(*) FROM ZikFile WHERE ZikFile.idFolder = Folder.id), " +
                "  percentdone = (" +
                "    SELECT CASE WHEN SUM(duration) > 0 THEN SUM(percentdone * duration) / SUM(duration) ELSE 0 END " +
                "    FROM ZikFile WHERE ZikFile.idFolder = Folder.id" +
                "  ), " +
                "  lLastAccess = CASE " +
                "    WHEN (SELECT MAX(lLastAccess) FROM ZikFile WHERE ZikFile.idFolder = Folder.id) IS NOT NULL " +
                "    THEN (SELECT MAX(lLastAccess) FROM ZikFile WHERE ZikFile.idFolder = Folder.id) " +
                "    ELSE lLastAccess " +
                "  END, " +
                "  lFirstAccess = (" +
                "    SELECT CASE " +
                "      WHEN Folder.lFirstAccess IS NOT NULL THEN Folder.lFirstAccess " +
                "      ELSE (SELECT MIN(lFirstAccess) FROM ZikFile WHERE ZikFile.idFolder = Folder.id) " +
                "    END" +
                "  ), " +
                "  date_last_zikfile_added = COALESCE( " +
                "    (SELECT MAX(date_added) FROM ZikFile WHERE ZikFile.idFolder = Folder.id), " +
                "    Folder.date_last_zikfile_added, " +
                "    0" + // or System.currentTimeMillis() if you prefer “now”
                "  ) " +
                "WHERE Folder.id = ?";
        SimpleSQLiteQuery query = new SimpleSQLiteQuery(strSQL, new Object[] { mFolderId });
        try {
            int sqlResult = AppDatabase.getDatabase(c).folderDao().runRawSql(query);
            myLogD("Folder Updated for ID " + mFolderId + " → runRawSQL result = " + sqlResult);
        } catch (Exception e) {
            myLogEE(e, "updateFolderTable");
        }
    }

    public static void calculateFolderProgress(Context c, long idFolder) {
        // SQLiteDatabase db = this.getWritableDatabase();
        // String selectQuery = "select sum(odometer) as odometer from tripmileagetable
        // where date like '2012-07%'";
        // Cursor cursor = db.rawQuery(selectQuery, null);
        String strSQL = "UPDATE Folder " +
                " SET percentdone = (SELECT SUM(percentdone*duration)/SUM(duration) " +
                "                   FROM ZikFile " +
                "                   WHERE Folder.id = ZikFile.idFolder )" +
                "   , timeListened = (SELECT SUM(timeListened) " +
                "                   FROM ZikFile " +
                "                   WHERE Folder.id = ZikFile.idFolder )" +
                "   , lLastAccess = strftime('%s','now') * 1000 " +
                " WHERE Folder.id = " + idFolder;

        SimpleSQLiteQuery query = new SimpleSQLiteQuery(strSQL);

        new Thread(() -> {
            try {
                int result = AppDatabase.getDatabase(c).folderDao().runRawSql(query);
                if (result > 0) {
                    // myLog("calculateFolderProgress done - result=[" + result + "]");
                } else {
                    // myLog("calculateFolderProgress error from result SQL - result=[" + result +
                    // "]"); // - [" + strSQL + "]"); // TODO check why return 0
                }
            } catch (Exception e) {
                myLogEE(e, "calculateFolderProgress");
            }
        }).start();
    }

    public static void log_all_Folders(Context c) {
        try {
            myLogD("-----------------");
            myLogD("-- FOLDERS");
            myLogD("-----------------");

            List<Folder> folders = AppDatabase.getDatabase(c)
                    .folderDao()
                    .getAll();

            if (folders == null || folders.isEmpty()) {
                myLogEE(null, "No folders found in database");
                return;
            }

            // Log column headers (optional)
            myLogD("Folders (sorted by last access):");
            myLogI("ID| Name | Path | Duration | originalHash | Hash | Last Access ...");
            myLogD("----------------------------------------");

            // Log each folder
            for (Folder folder : folders) {
                String logEntry = String.format(Locale.getDefault(),
                        "%d | %s | %s | %s | %s | %s | %s | %s", folder.getId(), folder.getName(), folder.getPath(),
                        Tonio.formatTime(folder.getDuration()), folder.getOriginalHash(), folder.getHash(),
                        Tonio.formatLastAccess(folder.lLastAccess, c),
                        Tonio.formatLastAccessInDays(folder.lLastAccess, c));
                myLog(logEntry);

                // Or simply: myLogD(folder.toString());
            }
            myLogD("----------------------------------------");
            myLogI("Total folders: " + folders.size());
            myLogD("----------------------------------------");

        } catch (Exception e) {
            myLogEE(e, "logAllFolders - Exception");
        }
    }

    public static void log_all_ZikFiles(Context c) {
        try {
            myLogD("-----------------");
            myLogD("-- FILES");
            myLogD("-----------------");

            List<ZikFile> zikFiles = AppDatabase.getDatabase(c)
                    .zikFileDao() // adjust if your DAO name differs
                    .getAll(); // adjust if your query method differs

            if (zikFiles == null || zikFiles.isEmpty()) {
                myLogEE(null, "No ZikFiles found in database");
                return;
            }

            // Optional: sort for readability (by idFolder then zeorder then id)
            try {
                zikFiles.sort((a, b) -> {
                    int byFolder = Long.compare(a.getIdFolder(), b.getIdFolder());
                    if (byFolder != 0)
                        return byFolder;
                    int byOrder = Double.compare(a.getZeorder(), b.getZeorder());
                    if (byOrder != 0)
                        return byOrder;
                    return Long.compare(a.getId(), b.getId());
                });
            } catch (Exception ignore) {
                /* non-fatal */ }

            myLogD("ZikFiles (grouped by folder, then zeorder):");
            myLogI("ID | idFolder | Name | Display | Path | Size | Dur | Pos | % | Done | zip/m4b | Last Access ...");
            myLogD("-----------------------------------------------------------------------------------------------");

            for (ZikFile z : zikFiles) {
                String sizePretty;
                try {
                    // Use whichever you have available in Tonio:
                    // size is a double; cast safely for human readable helpers
                    sizePretty = Tonio.getReadableSizeForCleanActivity((long) z.getSize());
                } catch (Throwable t) {
                    // Fallback: raw number
                    sizePretty = String.valueOf((long) z.getSize());
                }

                String logEntry = String.format(Locale.getDefault(),
                        "%d | %d | %s | %s | %s | %s | %s | %s | %4.1f | %s | %s/%s | %s (%s)", z.getId(),
                        z.getIdFolder(), nullSafe(z.getName()), nullSafe(z.getDisplayName()), nullSafe(z.getPath()),
                        sizePretty, Tonio.formatTime(z.getDuration()), Tonio.formatTime(z.getPosition()),
                        z.getPercentdone(), z.isFinished() ? "✓" : " ", z.isIszipfile() ? "zip" : "-",
                        z.isM4b() ? "m4b" : "-", Tonio.formatLastAccess(z.lLastAccess, c),
                        z.lLastAccess == null ? "" : Tonio.formatLastAccessInDays(z.lLastAccess, c));
                myLog(logEntry);
                // Or: myLogD(z.toString());
            }

            myLogD("----------------------------------------");
            myLogI("Total ZikFiles: " + zikFiles.size());
            myLogD("----------------------------------------");

        } catch (Exception e) {
            myLogEE(e, "log_all_ZikFiles - Exception");
        }
    }

    public static String getDbStats(Context c) {
        StringBuilder sb = new StringBuilder();
        try {
            SupportSQLiteDatabase db = AppDatabase.getDatabase(c).getOpenHelper().getReadableDatabase();

            // 1. Database File Size
            File dbFile = c.getDatabasePath(DatabaseClient.DATABASE_NAME);
            if (dbFile.exists()) {
                sb.append("Database File: ").append(Formatter.formatFileSize(c, dbFile.length())).append("\n\n");
            }

            // 2. Table row counts and sizes
            try (Cursor cursor = db.query(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_master_table' AND name NOT LIKE 'android_metadata'")) {
                while (cursor.moveToNext()) {
                    String tableName = cursor.getString(0);

                    // Row count
                    long rowCount = 0;
                    try (Cursor countCursor = db.query("SELECT COUNT(*) FROM " + tableName)) {
                        if (countCursor.moveToFirst()) {
                            rowCount = countCursor.getLong(0);
                        }
                    }

                    // Size (using dbstat if available)
                    String sizeStr = "n/a";
                    try (Cursor sizeCursor = db.query("SELECT SUM(pgsize) FROM dbstat WHERE name = ?",
                            new String[] { tableName })) {
                        if (sizeCursor.moveToFirst() && !sizeCursor.isNull(0)) {
                            sizeStr = Formatter.formatFileSize(c, sizeCursor.getLong(0));
                        }
                    } catch (Exception e) {
                        // dbstat might not be enabled
                    }

                    sb.append(
                            String.format(Locale.getDefault(), "%-15s : %d rows (%s)\n", tableName, rowCount, sizeStr));
                }
            }
        } catch (Exception e) {
            myLogEE(e, "getDbStats");
            sb.append("Error retrieving stats: ").append(e.getMessage());
        }
        return sb.toString();
    }

    public static void log_db_stats(Context c) {
        myLogD("-----------------");
        myLogD("-- DB STATS");
        myLogD("-----------------");
        myLogD(getDbStats(c));
        myLogD("----------------------------------------");
    }

    private static String nullSafe(String s) {
        return (s == null) ? "" : s;
    }

}
