package com.driot.bookplayer.db;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 28/10/20
 */

import static com.driot.tonylib.KanLogger.myLog;

import android.content.Context;

import androidx.room.Room;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

public class DatabaseClient {

    public static final String DATABASE_NAME = "BookPlayer";

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            myLog("Migration -> executing step 1 => 2");
            database.execSQL("ALTER TABLE ZikFile ADD COLUMN zeorder REAL NOT NULL default 0");
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            myLog("Migration -> executing step 2 => 3");
            database.execSQL("ALTER TABLE ZikFile ADD COLUMN displayName TEXT default '---'");
            database.execSQL("UPDATE ZikFile SET displayName = Name");        }
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
                //-------------------------------------------------------

                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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
}
