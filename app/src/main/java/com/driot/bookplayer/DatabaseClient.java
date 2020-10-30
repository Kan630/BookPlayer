package com.driot.bookplayer;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 28/10/20
 */

import android.content.Context;

import androidx.room.Room;

public class DatabaseClient {

    public static final String DATABASE_NAME = "BookPlayer";

    private Context mCtx;
    private static com.driot.bookplayer.DatabaseClient mInstance;

    //our app database object
    private AppDatabase appDatabase;

    private DatabaseClient(Context mCtx) {
        this.mCtx = mCtx;

        //mCtx.deleteDatabase(DATABASE_NAME);

        //creating the app database with Room database builder
        //MyToDos is the name of the database
        appDatabase = Room.databaseBuilder(mCtx, AppDatabase.class,DATABASE_NAME )

                //-------------------------------------------------------
                .fallbackToDestructiveMigration()  // <--- ATTENTION !!
                //                              modif version BDD => truncate all tables !!
                //-------------------------------------------------------

                .build();

    }

    public static synchronized com.driot.bookplayer.DatabaseClient getInstance(Context mCtx) {
        if (mInstance == null) {
            mInstance = new com.driot.bookplayer.DatabaseClient(mCtx);
        }
        return mInstance;
    }

    public AppDatabase getAppDatabase() {
        return appDatabase;
    }
}
