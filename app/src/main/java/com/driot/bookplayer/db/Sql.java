package com.driot.bookplayer.db;

import android.content.Context;
import android.database.Cursor;

import androidx.sqlite.db.SimpleSQLiteQuery;

import static com.driot.bookplayer.utils.KanLogger.myLog;
import static com.driot.bookplayer.utils.KanLogger.myLogI;
import static com.driot.bookplayer.utils.KanLogger.myLogE;

import java.util.List;
import java.util.Locale;


/**
 * created by Antoine Driot -- antoine.driot.com -- on 10/12/20
 */
public class Sql {


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
                myLogE("calculateFolderProgress - Exception : " + e.getMessage());
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
                    myLogE(TAG, "No folders found in database");
                    return;
                }

                // Log column headers (optional)
                myLogI(TAG, "Folders (sorted by last access):");
                myLogI(TAG, "ID | Name | Path | Last Access...");
                myLogI(TAG, "----------------------------------------");

                // Log each folder
                for (Folder folder : folders) {
                    String logEntry = String.format(Locale.getDefault(),
                            "%d | %s | %s",  // Adjust format as needed
                            folder.getId(),
                            folder.getName(),
                            folder.getPath());
                    myLogI(TAG, logEntry);

                    // Or simply: myLogD(folder.toString());
                }

                myLogI(TAG, "Total folders: " + folders.size());

            } catch (Exception e) {
                myLogE(TAG, "logAllFolders - Exception: " + e.getMessage());
            }
        }).start();
    }


}
