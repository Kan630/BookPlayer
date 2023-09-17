package com.driot.bookplayer.db;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 28/10/20
 */
import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

@Database(entities = {
                        Folder.class,
                        ZikFile.class
                     }
                     , version = 3
                    // on se fait les migrations a la main, en les ecrivant...... mais bon...
                    //, autoMigrations = {@AutoMigration(from = 1, to = 2),@AutoMigration(from = 2, to = 3)}
        )

// v2 for field zeOrder ?????
// v3 for field displayName -- 17 sept 2023

@TypeConverters({Converters.class})

public abstract class AppDatabase extends RoomDatabase {
    public abstract FolderDao FolderDao();
    public abstract ZikFileDao ZikFileDao();
}

