package com.driot.bookplayer.db;

import android.content.Context;

import androidx.sqlite.db.SimpleSQLiteQuery;

import static com.driot.tonylib.KanLogger.myLog;
import static com.driot.tonylib.KanLogger.myLogE;

import com.driot.tonylib.KanLogger;

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
                    myLog("calculateFolderProgress done");
                } else {
                    myLogE("calculateFolderProgress error from result SQL");
                }
            } catch (Exception e) {
                myLogE("calculateFolderProgress - Exception : " + e.getMessage());
            }
        }).start();
    }
}
