package com.driot.bookplayer.db;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 28/10/2020  -
 *
 * modified 05/2024
 */
import static com.driot.bookplayer.db.DatabaseClient.DATABASE_NAME;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
@Database(entities = {
                        Folder.class,
                        ZikFile.class
                     }
                     , version = 4
                    // on se fait les migrations a la main, en les ecrivant...... mais bon...
                    //, autoMigrations = {@AutoMigration(from = 1, to = 2),@AutoMigration(from = 2, to = 3)}
        )

// v2 for field zeOrder ?????
// v3 for field displayName -- 17 sept 2023

@TypeConverters({Converters.class})

public abstract class AppDatabase extends RoomDatabase {
    public abstract FolderDao FolderDao();
    public abstract ZikFileDao ZikFileDao();



    private static volatile AppDatabase INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;
    static final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(NUMBER_OF_THREADS);


    public static AppDatabase getDatabase(final Context context) {
        return DatabaseClient.getInstance(context).getAppDatabase();
    }
    /*
    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, DATABASE_NAME)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

 */
}

