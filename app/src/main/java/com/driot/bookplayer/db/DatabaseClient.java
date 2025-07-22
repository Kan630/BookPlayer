package com.driot.bookplayer.db;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 28/10/20
 */

import android.content.Context;

import androidx.room.Room;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.driot.bookplayer.utils.KanLogger;

public class DatabaseClient {

    public static final String DATABASE_NAME = "BookPlayer";

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            myLogI("Migration -> executing step 1 => 2");
            database.execSQL("ALTER TABLE ZikFile ADD COLUMN zeorder REAL NOT NULL default 0");
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            myLogI("Migration -> executing step 2 => 3");
            database.execSQL("ALTER TABLE ZikFile ADD COLUMN displayName TEXT default '---'");
            database.execSQL("UPDATE ZikFile SET displayName = Name");
        }
    };

    static final Migration MIGRATION_3_4 = new Migration(3, 4) { // v102 - 2025-07-04
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            myLogI("Migration -> executing step 3 => 4");
            database.execSQL("ALTER TABLE Folder ADD COLUMN originalFile TEXT default '---'");
            database.execSQL("ALTER TABLE Folder ADD COLUMN originalHash TEXT default '---'");
            database.execSQL("ALTER TABLE Folder ADD COLUMN originalType TEXT default '---'");
            database.execSQL("ALTER TABLE Folder ADD COLUMN sourceLocation TEXT default '---'");
            database.execSQL("ALTER TABLE Folder ADD COLUMN listeningDuration INTEGER NOT NULL default 0");
            database.execSQL("ALTER TABLE Folder ADD COLUMN listeningPlayCount INTEGER NOT NULL default 0");
        }
    };

    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            myLogI("Migration -> executing step 4 => 5");
            database.execSQL("CREATE TABLE IF NOT EXISTS Podcast (" +
                    "feedId INTEGER PRIMARY KEY NOT NULL, " +
                    "title TEXT NOT NULL, " +
                    "image TEXT, " +
                    "language TEXT, " +
                    "isFavorite INTEGER NOT NULL DEFAULT 0, " +
                    "autoDownload INTEGER NOT NULL DEFAULT 0, " +
                    "idFolder INTEGER, " +

                    "FOREIGN KEY(idFolder) REFERENCES Folder(id) ON DELETE SET NULL)"
        );

            // Add index on foreign key
            database.execSQL("CREATE INDEX index_Podcast_idFolder ON Podcast(idFolder)");
        }
    };

    private static DatabaseClient mInstance;

    //our app database object
    private final AppDatabase appDatabase;

    private DatabaseClient(Context mCtx) {

        //mCtx.deleteDatabase(DATABASE_NAME);

        //creating the app database with Room database builder
        //MyToDos is the name of the database
        appDatabase = Room.databaseBuilder(mCtx, AppDatabase.class,DATABASE_NAME )

                //-------------------------------------------------------
                //.fallbackToDestructiveMigration()  // <--- ATTENTION !!
                //                              modif version BDD => truncate all tables !!
                //       => better, just uncomment the deleteDatabase at the top of this method
                //-------------------------------------------------------

                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build();

    }

    public static synchronized DatabaseClient getInstance(Context mCtx) {
        if (mInstance == null) {
            mInstance = new DatabaseClient(mCtx);
        }
        return mInstance;
    }

    public AppDatabase getAppDatabase() {
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
