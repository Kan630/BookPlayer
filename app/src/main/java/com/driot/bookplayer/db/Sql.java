package com.driot.bookplayer.db;

import android.content.Context;

import androidx.sqlite.db.SimpleSQLiteQuery;

import static com.driot.bookplayer.utils.Tonio.FormatLastAccess;
import static com.driot.bookplayer.utils.Tonio.formatLastAccessInDays;
import static com.driot.bookplayer.utils.Tonio.formatTime;

import com.driot.bookplayer.R;
import com.driot.bookplayer.utils.KanLogger;

import java.util.List;
import java.util.Locale;


/**
 * created by Antoine Driot -- antoine.driot.com -- on 10/12/20
 */
public class Sql {


    public static void updateFolderTable(Context c, int mFolderId) {
        String strSQL = "UPDATE Folder " +
                "SET " +
                "  duration = (SELECT IFNULL(SUM(duration), 0) FROM ZikFile WHERE ZikFile.idFolder = Folder.id), " +
                "  nbZikFile = (SELECT COUNT(*) FROM ZikFile WHERE ZikFile.idFolder = Folder.id), " +
                "  percentdone = (" +
                "    SELECT CASE WHEN SUM(duration) > 0 THEN SUM(percentdone * duration) / SUM(duration) ELSE 0 END " +
                "    FROM ZikFile WHERE ZikFile.idFolder = Folder.id" +
                "  ), " +
                "  LastAccess = (SELECT MAX(lastAccess) FROM ZikFile WHERE ZikFile.idFolder = Folder.id), " +
                "  FirstAccess = (" +
                "    SELECT CASE " +
                "      WHEN Folder.FirstAccess IS NOT NULL THEN Folder.FirstAccess " +
                "      ELSE (SELECT MIN(firstAccess) FROM ZikFile WHERE ZikFile.idFolder = Folder.id) " +
                "    END" +
                "  ), " +
                "  date_last_zikfile_added = (SELECT MAX(date_added) FROM ZikFile WHERE ZikFile.idFolder = Folder.id) " +
                "WHERE Folder.id = ?";
        SimpleSQLiteQuery query = new SimpleSQLiteQuery(strSQL, new Object[]{mFolderId});
        try {
            int sqlResult = AppDatabase.getDatabase(c).FolderDao().runRawSql(query);
            myLogD("Folder Updated for ID " + mFolderId + " → runRawSQL result = " + sqlResult);
        } catch (Exception e) {
            myLogEE(e,"updateFolderTable");
        }
    }



    public static void calculateFolderProgress(Context c, int idFolder) {
        //SQLiteDatabase db = this.getWritableDatabase();
        //String selectQuery = "select sum(odometer) as odometer from tripmileagetable where date like '2012-07%'";
        //Cursor cursor = db.rawQuery(selectQuery, null);
        String strSQL = "UPDATE Folder " +
                " SET percentdone = (SELECT SUM(percentdone*duration)/SUM(duration) " +
                "                   FROM ZikFile " +
                "                   WHERE Folder.id = ZikFile.idFolder )" +
                "   , LastAccess = strftime('%s','now') * 1000" +
                "   , LastAccessTime = strftime('%s','now') * 1000 " +
                " WHERE Folder.id = " + idFolder;

        SimpleSQLiteQuery query = new SimpleSQLiteQuery(strSQL);

        new Thread(() -> {
            try {
                int result = AppDatabase.getDatabase(c).FolderDao().runRawSql(query);
                if (result > 0) {
                    //myLog("calculateFolderProgress done - result=[" + result + "]");
                } else {
                    //myLog("calculateFolderProgress error from result SQL - result=[" + result + "]"); // - [" + strSQL + "]"); // TODO check why return 0
                }
            } catch (Exception e) {
                myLogEE(e,"calculateFolderProgress");
            }
        }).start();
    }

    public static void log_all_Folders(Context c) {
        String TAG = "SQL log";
        new Thread(() -> {
            try {
                List<Folder> folders = AppDatabase.getDatabase(c)
                        .FolderDao()
                        .getAll();

                if (folders == null || folders.isEmpty()) {
                    myLogEE(null, "No folders found in database");
                    return;
                }

                // Log column headers (optional)
                myLogI("Folders (sorted by last access):");
                myLogI("ID | originalHash | Hash | Name | Path | Duration | Last Access ...");
                myLogI("----------------------------------------");

                // Log each folder
                for (Folder folder : folders) {
                    String logEntry = String.format(Locale.getDefault(),
                            "%d | %s | %s | %s | %s | %s | %s | %s"
                            ,folder.getId()
                            ,folder.getOriginalHash()
                            ,folder.getHash()
                            ,folder.getName()
                            ,folder.getPath()
                            ,formatTime(folder.getDuration())
                            ,FormatLastAccess(folder.getLastaccess(), folder.getLastaccessTime(), c.getString(R.string.yesterday))
                            ,formatLastAccessInDays(folder.getLastaccess())
                    );
                    myLogI(logEntry);

                    // Or simply: myLogD(folder.toString());
                }

                myLogI("Total folders: " + folders.size());

            } catch (Exception e) {
                myLogEE(e,"logAllFolders - Exception");
            }
        }).start();
    }



    // ----------------------- LOG -----------------------
    private static final String TAG = "Sql";
    private static void myLog(String str) { KanLogger.myLog(TAG, str); }
    private static void myLogD(String str) { KanLogger.myLogD(TAG, str); }
    private static void myLogI(String str) { KanLogger.myLogI(TAG, str); }
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
    private static void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, TAG, str); }
    private static void myToastEE(Throwable t, String str) { KanLogger.myToastEE(t, TAG, str); }
}
