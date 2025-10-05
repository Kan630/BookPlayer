package com.driot.bookplayer.db;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 28/10/20
 */

import static com.driot.bookplayer.db.AppDatabase.APP_DATABASE_VERSION;
import static com.driot.bookplayer.db.DatabaseBackupHelper.backupDatabase;
import static com.driot.bookplayer.db.DatabaseBackupHelper.getSQLiteVersion;

import android.content.Context;

import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.driot.bookplayer.utils.KanLogger;

import java.io.File;

public class DatabaseClient {

    public static final String DATABASE_NAME = "BookPlayer";

    private static DatabaseClient mInstance;

    //our app database object
    private AppDatabase appDatabase;

    private DatabaseClient(Context mCtx) {
        int currentVersion = 0;

        //mCtx.deleteDatabase(DATABASE_NAME);

        try {
            File dbPath = mCtx.getDatabasePath(DATABASE_NAME);
            if (dbPath.exists()) {
                currentVersion = DatabaseBackupHelper.getDatabaseVersion(dbPath);
                myLogD("Current DB version : " + currentVersion);
                if (currentVersion < APP_DATABASE_VERSION) {
                    myLogI("Code DB version : " + APP_DATABASE_VERSION);
                    //backupDatabase(mCtx);
                }
            }
        } catch (Exception e) {
            myLogE("db version logging error");
        }


        //creating the app database with Room database builder
        //MyToDos is the name of the database
        try {
            appDatabase = Room.databaseBuilder(mCtx, AppDatabase.class,DATABASE_NAME )

                    //-------------------------------------------------------
                    //.fallbackToDestructiveMigration()  // <--- ATTENTION !!
                    //                              change version BDD => truncate all tables !!
                    //       => better, just uncomment the deleteDatabase at the top of this method
                    //-------------------------------------------------------

                    .addMigrations(
                              DatabaseMigrations.MIGRATION_1_2
                            , DatabaseMigrations.MIGRATION_2_3
                            , DatabaseMigrations.MIGRATION_3_4
                            , DatabaseMigrations.MIGRATION_4_5
                            , DatabaseMigrations.MIGRATION_5_6
                            , DatabaseMigrations.MIGRATION_6_7
                            , DatabaseMigrations.MIGRATION_7_8
                            , DatabaseMigrations.MIGRATION_8_9
                            , DatabaseMigrations.MIGRATION_9_10
                            , DatabaseMigrations.MIGRATION_10_11
                            , DatabaseMigrations.MIGRATION_11_12
                            , DatabaseMigrations.MIGRATION_12_13
                            , DatabaseMigrations.MIGRATION_13_14
                            , DatabaseMigrations.MIGRATION_14_15
                    ).build();

            // Force early access to trigger DB open and migrations (and also check SQL version)
            SupportSQLiteDatabase db = appDatabase.getOpenHelper().getWritableDatabase();
            myLog("SQL lite version = " + getSQLiteVersion(db));


        } catch (Exception e) {
            myLogEE(e, "Database will CRASH !! - current version : " + currentVersion + " - code version : " + APP_DATABASE_VERSION);
        }

    }

    public static synchronized DatabaseClient getInstance(Context mCtx) {
        if (mInstance == null) {
            mInstance = new DatabaseClient(mCtx);
        }
        return mInstance;
    }

    public AppDatabase getAppDatabase() {
        if (appDatabase == null) {
            throw new IllegalStateException("Database not initialized");
        }
        return appDatabase;
    }


    // ----------------------- LOG -----------------------
    private static final String TAG = "DatabaseClient";
    private static void myLog(String str) { KanLogger.myLog(TAG, str); }
    private static void myLogD(String str) { KanLogger.myLogD(TAG, str); }
    private static void myLogI(String str) { KanLogger.myLogI(TAG, str); }
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
    private static void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, TAG, str); }
}
