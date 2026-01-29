package com.driot.bookplayer.db;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import static com.driot.bookplayer.utils.log.KanLogger.myLogI;

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
            myLogI("Migration -> executing step 5 => 6");
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
            myLogI("Migration -> executing step 6 => 7");
            db.execSQL("ALTER TABLE Folder ADD COLUMN lFirstAccess INTEGER");
            db.execSQL("ALTER TABLE Folder ADD COLUMN lLastAccess INTEGER NOT NULL DEFAULT 0");

            db.execSQL("UPDATE Folder SET lFirstAccess = firstaccess WHERE firstaccess IS NOT NULL");
            db.execSQL("UPDATE Folder SET lLastAccess = lastaccess WHERE lastaccess IS NOT NULL");
            db.execSQL("UPDATE Folder SET lLastAccess = lastaccessTime WHERE lastaccess IS NULL AND lastaccessTime IS NOT NULL");
        }
    };

    static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            myLogI("Migration -> executing step 7 => 8");

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

    static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            myLogI("Migration -> executing step 8 => 9");
            db.execSQL(
                    "CREATE TABLE IF NOT EXISTS Episode (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "idPodcast INTEGER NOT NULL, " +
                            "date_add INTEGER NOT NULL, " +
                            "idZikFile INTEGER, " +
                            "date_import INTEGER, " +
                            "date_delete INTEGER, " +
                            "lastAccess INTEGER, " +
                            "FOREIGN KEY(idPodcast) REFERENCES Podcast(id) ON DELETE CASCADE, " +
                            "FOREIGN KEY(idZikFile) REFERENCES ZikFile(id) ON DELETE SET NULL" +
                            ")"
            );
            db.execSQL("ALTER TABLE Podcast ADD COLUMN autoDelete INTEGER NOT NULL DEFAULT 0");
        }
    };

    static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            myLogI("Migration -> executing step 9 => 10"); //2025-08-05
            db.execSQL("CREATE INDEX IF NOT EXISTS index_Episode_idPodcast ON Episode(idPodcast)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_Episode_idZikFile ON Episode(idZikFile)");
        }
    };

    static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            myLogI("Migration -> executing step 10 => 11"); // 2025-08-06

            // Add new columns
            db.execSQL("ALTER TABLE Episode ADD COLUMN idEpisode INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE Episode ADD COLUMN title TEXT");
            db.execSQL("ALTER TABLE Episode ADD COLUMN description TEXT");
            db.execSQL("ALTER TABLE Episode ADD COLUMN duration INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE Episode ADD COLUMN image TEXT");
            db.execSQL("ALTER TABLE Episode ADD COLUMN guid TEXT");
            db.execSQL("ALTER TABLE Episode ADD COLUMN podcastGuid TEXT");
            db.execSQL("ALTER TABLE Episode ADD COLUMN enclosureUrl TEXT");
            db.execSQL("ALTER TABLE Episode ADD COLUMN datePublished TEXT");

            db.execSQL("UPDATE Episode SET idEpisode = rowid WHERE idEpisode = 0"); //shitty thing so I can update on my personal phone

            db.execSQL("DROP INDEX IF EXISTS index_Episode_idZikFile");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_Episode_idPodcast ON Episode(idPodcast)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_Episode_idZikFile ON Episode(idZikFile)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_Episode_idEpisode ON Episode(idEpisode)");
        }
    };
    static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            myLogI("Migration -> executing step 11 => 12"); // 2025-09-09

            db.execSQL("ALTER TABLE Podcast ADD COLUMN lastCheck INTEGER NOT NULL DEFAULT 0");
        }
    };
    static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            myLogI("Migration -> executing step 12 => 13"); // 2025-09-09

            db.execSQL("ALTER TABLE Podcast ADD COLUMN sort_newest_top INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE Episode ADD COLUMN enclosureLength INTEGER NOT NULL DEFAULT 0");
        }
    };
    static final Migration MIGRATION_13_14 = new Migration(13, 14) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            myLogI("Migration -> executing step 13 => 14"); // 2025-09-15

            db.execSQL("ALTER TABLE Folder ADD COLUMN playType TEXT");
        }
    };
    static final Migration MIGRATION_14_15 = new Migration(14, 15) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            myLogI("Migration -> executing step 14 => 15"); // 2025-10-05

            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_BookSource_repoType_repoName_repoId ON BookSource(repoType, repoName, repoId)");

            db.execSQL("ALTER TABLE BookSource ADD COLUMN book_title TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE BookSource ADD COLUMN source_url TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE BookSource ADD COLUMN source_size INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE BookSource ADD COLUMN is_favorite INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE BookSource ADD COLUMN date_add INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE BookSource ADD COLUMN date_maj INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE BookSource ADD COLUMN last_checked INTEGER");
        }
    };
    static final Migration MIGRATION_15_16 = new Migration(15, 16) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            myLogI("Migration -> executing step 15 => 16"); // 2025-10-11/14

            // Create ImportJob table (booleans -> INTEGER 0/1; primitives NOT NULL with defaults)
            db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ImportJob` (" +
                            " `importId` TEXT NOT NULL," +
                            " `originalUri` TEXT," +
                            " `originalType` TEXT," +
                            " `dynamicUri` TEXT," +
                            " `dynamicType` TEXT," +
                            " `title` TEXT," +
                            " `futureFolderName` TEXT," +
                            " `futureFolderPath` TEXT," +

                            " `optionSplit` INTEGER NOT NULL DEFAULT 0," +
                            " `optionCopy` INTEGER NOT NULL DEFAULT 0," +
                            " `optionDelete` INTEGER NOT NULL DEFAULT 0," +

                            " `originalFile` TEXT," +
                            " `originalHash` TEXT," +
                            " `sourceLocation` TEXT," +
                            " `fileExtension` TEXT," +
                            " `mimeType` TEXT," +

                            " `imagePath` TEXT," +
                            " `progressText` TEXT," +
                            " `progressPercent` INTEGER NOT NULL DEFAULT 0," +

                            " `isLoadingPaused` INTEGER NOT NULL DEFAULT 0," +
                            " `isPauseAvailable` INTEGER NOT NULL DEFAULT 0," +
                            " `currentOperation` TEXT," +

                            " `downloadFileUrl` TEXT," +
                            " `downloadDestinationFolder` TEXT," +
                            " `downloadRetryCount` INTEGER NOT NULL DEFAULT 0," +
                            " `downloadStartTime` INTEGER NOT NULL DEFAULT 0," +
                            " `downloadDuration` INTEGER NOT NULL DEFAULT 0," +

                            " `downloadedFilePath` TEXT," +
                            " `downloadedFileReady` INTEGER NOT NULL DEFAULT 0," +

                            " `dynamicDestinationFolderPath` TEXT," +
                            " `dynamicSourceFilePath` TEXT," +

                            " `doDownload` INTEGER NOT NULL DEFAULT 0," +
                            " `doCopy` INTEGER NOT NULL DEFAULT 0," +
                            " `doSplitM4b` INTEGER NOT NULL DEFAULT 0," +
                            " `doSplitEbook` INTEGER NOT NULL DEFAULT 0," +
                            " `doUnzip` INTEGER NOT NULL DEFAULT 0," +

                            " `playType` TEXT," +
                            " `downloadWorkId` TEXT," +
                            " `uniqueChainName` TEXT," +

                            " `status` TEXT," +
                            " `createdAt` INTEGER NOT NULL DEFAULT 0," +
                            " `updatedAt` INTEGER NOT NULL DEFAULT 0," +
                            " `warningText` TEXT," +
                            " `errorTextDev` TEXT," +
                            " `errorTextUser` TEXT," +

                            " `showToUser` INTEGER NOT NULL DEFAULT 0," +

                            " PRIMARY KEY(`importId`)" +
                            ")"
            );

            // Indices declared in @Entity(indices=...)
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ImportJob_futureFolderPath` ON `ImportJob`(`futureFolderPath`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ImportJob_status` ON `ImportJob`(`status`)");

            db.execSQL("ALTER TABLE ZikFile ADD COLUMN metadataJson TEXT NOT NULL DEFAULT '{}'");
        }
    };

    static final Migration MIGRATION_16_17 = new Migration(16, 17) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            myLogI("Migration -> executing step 16 => 17"); // 2025-11-19

            db.execSQL("ALTER TABLE ImportJob ADD COLUMN addToExistingFolderId INTEGER NOT NULL DEFAULT 0");
        };
    };

    static final Migration MIGRATION_17_18 = new Migration(17, 18) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            myLogI("Migration -> executing step 17 => 18"); // 2025-11-21
            db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `PlayTick` (" +
                            " `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            " `timestamp` INTEGER NOT NULL," +
                            " `zikFileId` INTEGER NOT NULL," +
                            " `position` INTEGER NOT NULL DEFAULT 0," +
                            " FOREIGN KEY(zikFileId) REFERENCES ZikFile(id) ON DELETE CASCADE" +
                            ")"
            );
        };
    };

    static final Migration MIGRATION_18_19 = new Migration(18, 19) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            myLogI("Migration -> executing step 18 => 19"); // 2025-11-22
            db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `RadioStation` (" +
                            " `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            " `stationuuid` TEXT NOT NULL, " +
                            " `name` TEXT, " +
                            " `url` TEXT, " +
                            " `url_resolved` TEXT, " +
                            " `codec` TEXT, " +
                            " `bitrate` INTEGER NOT NULL DEFAULT 0, " +
                            " `hls` INTEGER NOT NULL DEFAULT 0, " +
                            " `favicon` TEXT, " +
                            " `country` TEXT, " +
                            " `countrycode` TEXT, " +
                            " `language` TEXT, " +
                            " `tags` TEXT, " +
                            " `clickcount` INTEGER NOT NULL DEFAULT 0, " +
                            " `lastcheckok` INTEGER NOT NULL DEFAULT 0, " +
                            " `display_order` INTEGER NOT NULL DEFAULT 0, " +
                            " `isFavorite` INTEGER NOT NULL DEFAULT 0, " +
                            " `date_last_played` INTEGER," +
                            " `date_added` INTEGER NOT NULL DEFAULT 0," +
                            " `date_maj` INTEGER NOT NULL DEFAULT 0" +
                            ")"
            );
        }
    };

    static final Migration MIGRATION_19_20 = new Migration(19, 20) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            myLogI("Migration -> executing step 19 => 20"); // 2025-12-11
            db.execSQL("ALTER TABLE `RadioStation` ADD COLUMN `state` TEXT");
            db.execSQL("ALTER TABLE `RadioStation` ADD COLUMN `iso_3166_2` TEXT");
            db.execSQL("ALTER TABLE `RadioStation` ADD COLUMN `votes` TEXT");
            db.execSQL("ALTER TABLE `RadioStation` ADD COLUMN `homepage` TEXT");
        }
    };

    static final Migration MIGRATION_20_21 = new Migration(20, 21) { //2025-12-21
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            myLogI("Migration -> executing step 20 => 21 (PlayTick sessions)");

            // 1) Add missing indices to existing PlayTick table
            db.execSQL("CREATE INDEX IF NOT EXISTS index_PlayTick_zikFileId ON PlayTick(zikFileId)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_PlayTick_zikFileId_timestamp ON PlayTick(zikFileId, timestamp)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_PlayTick_zikFileId_position ON PlayTick(zikFileId, position)");

            db.execSQL(
                    "CREATE TABLE PlaySession (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "zikFileId INTEGER NOT NULL, " +
                            "timestampStart INTEGER NOT NULL, " +
                            "timestampEnd INTEGER NOT NULL, " +
                            "positionStart INTEGER NOT NULL, " +
                            "positionEnd INTEGER NOT NULL, " +
                            "FOREIGN KEY(zikFileId) REFERENCES ZikFile(id) ON DELETE CASCADE" +
                            ")"
            );

            // 3) Indices (important for heatmaps)
            db.execSQL("CREATE INDEX index_PlaySession_zikFileId ON PlaySession(zikFileId)");
            db.execSQL("CREATE INDEX index_PlaySession_zikFileId_positionStart ON PlaySession(zikFileId, positionStart)");

            // 4) Handle legacy data: For ZikFiles with position > 0 but no PlayTick data,
            //    create a session from 0 to current position (assumes they listened from start)
            db.execSQL(
                    "INSERT INTO PlaySession (zikFileId, timestampStart, timestampEnd, positionStart, positionEnd) " +
                            "SELECT z.id, 0, z.position, 0, z.position " +
                            "FROM ZikFile z " +
                            "WHERE z.position > 0 " +
                            "AND NOT EXISTS (SELECT 1 FROM PlayTick pt WHERE pt.zikFileId = z.id)"
            );
        }
    };

    static final Migration MIGRATION_21_22 = new Migration(21, 22) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            myLogI("Migration -> executing step 21 => 22"); // 2026-01-29
            db.execSQL("ALTER TABLE ImportJob ADD COLUMN batchIndex INTEGER NOT NULL DEFAULT -1");
            db.execSQL("ALTER TABLE ImportJob ADD COLUMN batchTotal INTEGER NOT NULL DEFAULT -1");
        }
    };


}
