package com.driot.bookplayer.imports;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.driot.bookplayer.global.Var;

import java.util.List;

@Dao
public interface ImportJobDao {

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        void upsert(ImportJob job);

        @Update
        void update(ImportJob job);

        // Cancel all active (RUNNING / QUEUED / PAUSED)
        @Query("UPDATE ImportJob SET status = '" + Var.IMPORT_STATUS_CANCELLED + "'" +
                        ", updatedAt = :ts " +
                        "WHERE status IN ('" + Var.IMPORT_STATUS_RUNNING + "', '" +
                        Var.IMPORT_STATUS_QUEUED + "', '" +
                        Var.IMPORT_STATUS_PAUSED + "')")
        void cancelAll(long ts);

        @Query("SELECT * FROM ImportJob WHERE importId = :id LIMIT 1")
        ImportJob get(String id);

        // One active job (QUEUED/RUNNING/PAUSED), most recent first
        @Query("SELECT * FROM ImportJob " +
                        "WHERE status IN ('" + Var.IMPORT_STATUS_RUNNING + "', '" +
                        Var.IMPORT_STATUS_QUEUED + "', '" +
                        Var.IMPORT_STATUS_PAUSED + "') " +
                        "ORDER BY createdAt DESC " +
                        "LIMIT 1")
        LiveData<ImportJob> observeCurrentActive();

        // All active jobs (QUEUED/RUNNING/PAUSED)
        @Query("SELECT * FROM ImportJob " +
                        "WHERE status IN ('" + Var.IMPORT_STATUS_RUNNING + "', '" +
                        Var.IMPORT_STATUS_QUEUED + "', '" +
                        Var.IMPORT_STATUS_PAUSED + "') " +
                        "ORDER BY createdAt DESC")
        LiveData<List<ImportJob>> observeAllActive();

        @Query("UPDATE ImportJob SET status = '" + Var.IMPORT_STATUS_RUNNING + "'" +
                        ", showToUser = 1" +
                        ", progressText = :txt" +
                        ", progressPercent = :pct" +
                        ", updatedAt = :ts " +
                        "WHERE importId = :id")
        void updateProgress(String id, String txt, int pct, long ts);

        @Query("UPDATE ImportJob SET status = '" + Var.IMPORT_STATUS_RUNNING + "'" +
                        ", showToUser = 1" +
                        ", progressText = :txt" +
                        ", updatedAt = :ts " +
                        "WHERE importId = :id")
        void updateProgressText(String id, String txt, long ts);

        @Query("UPDATE ImportJob " +
                        "SET warningText = COALESCE(warningText || '\n', '') || :warn, " +
                        "updatedAt = :ts " +
                        "WHERE importId = :id")
        void appendWarning(String id, String warn, long ts);

        @Query("UPDATE ImportJob SET status = '" + Var.IMPORT_STATUS_FAILED + "'" +
                        ", showToUser = 1" +
                        ", errorTextDev = :errorTextDev" +
                        ", errorTextUser = :errorTextUser" +
                        ", warningText = ''" +
                        ", updatedAt = :ts " +
                        "WHERE importId = :id")
        void fail(String id, String errorTextDev, String errorTextUser, long ts);

        @Query("UPDATE ImportJob SET status = '" + Var.IMPORT_STATUS_CANCELLED + "'" +
                        ", showToUser = 0" +
                        ", updatedAt = :ts " +
                        "WHERE importId = :id")
        void cancel(String id, long ts);

        @Query("UPDATE ImportJob SET status = '" + Var.IMPORT_STATUS_SUCCEEDED + "'" +
                        ", showToUser = 1" +
                        ", progressText = :progressText" +
                        ", progressPercent = 100" +
                        ", updatedAt = :ts " +
                        "WHERE importId = :id")
        void success(String id, String progressText, long ts);

        @Query("UPDATE ImportJob SET warningText = COALESCE(warningText || '\n', '') || :why" +
                        ", showToUser = 1" +
                        ", isLoadingPaused = 1" +
                        ", status = '" + Var.IMPORT_STATUS_PAUSED + "'" +
                        ", updatedAt = :ts " +
                        "WHERE importId = :id")
        void downloadPause(String id, String why, long ts);

        @Query("UPDATE ImportJob SET progressText = :progressText" +
                        ", showToUser = 1" +
                        ", isLoadingPaused = 0" +
                        ", status = '" + Var.IMPORT_STATUS_RUNNING + "'" +
                        ", updatedAt = :ts " +
                        "WHERE importId = :id")
        void downloadResuming(String id, String progressText, long ts);

        @Query("UPDATE ImportJob SET status = '" + Var.IMPORT_STATUS_RUNNING + "'" +
                        ", showToUser = 1" +
                        ", currentOperation = :currentOperation" +
                        ", dynamicType = 'File'" +
                        ", dynamicUri = :downloadedFileFullPath" +
                        ", dynamicSourceFilePath = :downloadedFileFullPath" +
                        ", downloadedFilePath = :downloadedFileFullPath" +
                        ", downloadedFileReady = 1" +
                        ", isLoadingPaused = 0" +
                        ", isPauseAvailable = 0" +
                        ", warningText = ''" +
                        ", errorTextUser = ''" +
                        ", progressText = :progressText" +
                        ", updatedAt = :ts " +
                        "WHERE importId = :id")
        void downloadComplete(String id,
                        String currentOperation,
                        String downloadedFileFullPath,
                        String progressText,
                        long ts);

        @Query("UPDATE ImportJob SET status = '" + Var.IMPORT_STATUS_RUNNING + "'" +
                        ", showToUser = 1" +
                        ", currentOperation = :currentOperation" +
                        ", progressText = :progressText" +
                        ", dynamicType = 'Folder'" +
                        ", dynamicUri = :destinationFolderPath" +
                        ", dynamicSourceFilePath = :destinationFolderPath" +
                        ", dynamicDestinationFolderPath = :destinationFolderPath" +
                        ", playType = :playType" +
                        ", updatedAt = :ts " +
                        "WHERE importId = :id")
        void taskComplete(String id,
                        String currentOperation,
                        String destinationFolderPath,
                        String playType,
                        String progressText,
                        long ts);

        @Query("UPDATE ImportJob SET status = '" + Var.IMPORT_STATUS_RUNNING + "'" +
                        ", showToUser = 1" +
                        ", currentOperation = :currentOperation" +
                        ", progressText = :progressText" +
                        ", updatedAt = :ts " +
                        "WHERE importId = :id")
        void taskStart(String id, String currentOperation, String progressText, long ts);

        // Most recent active job (RUNNING/QUEUED/PAUSED)
        @Query("SELECT * FROM ImportJob " +
                        "WHERE status IN ('" + Var.IMPORT_STATUS_RUNNING + "', '" +
                        Var.IMPORT_STATUS_QUEUED + "', '" +
                        Var.IMPORT_STATUS_PAUSED + "') " +
                        "ORDER BY createdAt DESC " +
                        "LIMIT 1")
        ImportJob getMostRecentActive();

        // Count active jobs
        @Query("SELECT COUNT(*) FROM ImportJob " +
                        "WHERE status IN ('" + Var.IMPORT_STATUS_RUNNING + "', '" +
                        Var.IMPORT_STATUS_QUEUED + "', '" +
                        Var.IMPORT_STATUS_PAUSED + "')")
        int countActive();

        @Query("SELECT * FROM ImportJob " +
                        "WHERE status IN ('" + Var.IMPORT_STATUS_RUNNING + "', '" +
                        Var.IMPORT_STATUS_QUEUED + "', '" +
                        Var.IMPORT_STATUS_PAUSED + "')")
        List<ImportJob> getActive();

        @Query("SELECT COUNT(*) FROM ImportJob " +
                        "WHERE status IN ('" + Var.IMPORT_STATUS_RUNNING + "', '" +
                        Var.IMPORT_STATUS_QUEUED + "', '" +
                        Var.IMPORT_STATUS_PAUSED + "')")
        LiveData<Integer> observeActiveCount();

        // Current active OR last finished job
        @Query("SELECT * FROM ImportJob " +
                        "ORDER BY " +
                        "  CASE " +
                        "    WHEN status IN ('" + Var.IMPORT_STATUS_RUNNING + "', '" +
                        Var.IMPORT_STATUS_QUEUED + "', '" +
                        Var.IMPORT_STATUS_PAUSED + "') THEN 0 " +
                        "    ELSE 1 " +
                        "  END, " +
                        "  updatedAt DESC " +
                        "LIMIT 1")
        LiveData<ImportJob> observeCurrentOrLast();

        // The unique job (latest)
        @Query("SELECT * FROM ImportJob " +
                        "ORDER BY updatedAt DESC " +
                        "LIMIT 1")
        LiveData<ImportJob> observeUniqueJob();

        @Query("SELECT * FROM ImportJob " +
                        "ORDER BY updatedAt DESC " +
                        "LIMIT 1")
        ImportJob getUniqueJob();

        @Query("UPDATE ImportJob SET showToUser = :showToUser" +
                        ", updatedAt = :ts " +
                        "WHERE importId = :id")
        void setShowToUser(String id, boolean showToUser, long ts);

        @Query("SELECT EXISTS(SELECT 1 FROM ImportJob " +
                        "WHERE importId = :id " +
                        "AND warningText IS NOT NULL " +
                        "AND TRIM(warningText) != '')")
        boolean hasWarnings(String id);

        // Get all jobs (for batch position calculation)
        @Query("SELECT * FROM ImportJob ORDER BY createdAt ASC")
        List<ImportJob> getAll();
}
