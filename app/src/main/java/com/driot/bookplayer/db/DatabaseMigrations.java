package com.driot.bookplayer.db;

import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import static com.driot.bookplayer.utils.KanLogger.myLogI;

public class DatabaseMigrations {

    //EXPECTED = Class Object   ;    MIGRATION = FOUND (2nd part in log message) = state of DB ?



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
                    "id INTEGER PRIMARY KEY NOT NULL, " +
                    "feedId INTEGER NOT NULL, " +
                    "source TEXT, " +
                    "title TEXT NOT NULL, " +
                    "image TEXT, " +
                    "imageOriginalUrl TEXT, " +
                    "description TEXT, " +
                    "language TEXT, " +
                    "isFavorite INTEGER NOT NULL, " +
                    "autoDownload INTEGER NOT NULL, " +
                    "idFolder INTEGER, " +
                    "date_added INTEGER NOT NULL, " +

                    "FOREIGN KEY(idFolder) REFERENCES Folder(id) ON DELETE SET NULL)"
            );

            // Add index on foreign key
            database.execSQL("CREATE UNIQUE INDEX index_Podcast_feedId ON Podcast(feedId)");
            database.execSQL("CREATE UNIQUE INDEX index_Podcast_idFolder ON Podcast(idFolder)");

            database.execSQL("ALTER TABLE Folder ADD COLUMN nbZikFile INTEGER NOT NULL default 0");
            database.execSQL("ALTER TABLE Folder ADD COLUMN date_added INTEGER NOT NULL default 0");
            database.execSQL("ALTER TABLE Folder ADD COLUMN date_last_zikfile_added INTEGER NOT NULL default 0");
            database.execSQL("ALTER TABLE Folder ADD COLUMN image TEXT");

            database.execSQL("ALTER TABLE ZikFile ADD COLUMN date_added INTEGER NOT NULL default 0");
        }
    };

    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS BookSource (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "imageLocal TEXT, " +
                    "imageRemote TEXT, " +
                    "repoType TEXT, " +
                    "repoName TEXT, " +
                    "repoId TEXT, " +
                    "idFolder INTEGER, " +
                    "FOREIGN KEY(idFolder) REFERENCES Folder(id) ON DELETE SET NULL)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_BookSource_idFolder ON BookSource(idFolder)");
        }
    };

    static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            // Add columns if not already done
            db.execSQL("ALTER TABLE Folder ADD COLUMN lFirstAccess INTEGER");
            db.execSQL("ALTER TABLE Folder ADD COLUMN lLastAccess INTEGER NOT NULL DEFAULT 0");

            // Set lFirstAccess from firstaccess, if not null
            db.execSQL("UPDATE Folder SET lFirstAccess = firstaccess WHERE firstaccess IS NOT NULL");

            // Set lLastAccess from lastaccess if not null
            db.execSQL("UPDATE Folder SET lLastAccess = lastaccess WHERE lastaccess IS NOT NULL");

            // If lastaccess is null, fallback to lastaccessTime
            db.execSQL("UPDATE Folder SET lLastAccess = lastaccessTime WHERE lastaccess IS NULL AND lastaccessTime IS NOT NULL");
        }
    };

    static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {

            //db.execSQL("ALTER TABLE Folder DROP COLUMN firstaccess");    // looks like SQLlite does not support DROP COLUMN !!
            //db.execSQL("ALTER TABLE Folder DROP COLUMN lastaccess");
            //db.execSQL("ALTER TABLE Folder DROP COLUMN lastaccessTime");

            db.execSQL("ALTER TABLE ZikFile ADD COLUMN lFirstAccess INTEGER");
            db.execSQL("ALTER TABLE ZikFile ADD COLUMN lLastAccess INTEGER");

            db.execSQL("UPDATE ZikFile SET lFirstAccess = firstaccess WHERE firstaccess IS NOT NULL");
            db.execSQL("UPDATE ZikFile SET lLastAccess = lastaccess WHERE lastaccess IS NOT NULL");
            db.execSQL("UPDATE ZikFile SET lLastAccess = lastaccessTime WHERE lastaccess IS NULL AND lastaccessTime IS NOT NULL");

            //db.execSQL("ALTER TABLE ZikFile DROP COLUMN firstaccess");
            //db.execSQL("ALTER TABLE ZikFile DROP COLUMN lastaccess");
            //db.execSQL("ALTER TABLE ZikFile DROP COLUMN lastaccessTime");
        }
    };



}
