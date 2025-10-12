package com.driot.bookplayer.imports;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ImportJobDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(ImportJob job);

    @Update
    void update(ImportJob job);

    @Query("SELECT * FROM ImportJob WHERE importId = :id LIMIT 1")
    ImportJob get(String id);

    // One active job (QUEUED/RUNNING), most recent first
    @Query("SELECT * FROM ImportJob WHERE status IN (:s1, :s2, :s3) ORDER BY createdAt DESC LIMIT 1")
    LiveData<ImportJob> observeCurrentActive(String s1, String s2, String s3);

    // All active jobs if you later want a list/RecyclerView
    @Query("SELECT * FROM ImportJob WHERE status IN (:s1, :s2, :s3) ORDER BY createdAt DESC")
    LiveData<List<ImportJob>> observeAllActive(String s1, String s2, String s3);

    @Query("UPDATE ImportJob SET progressText=:txt, progressPercent=:pct, updatedAt=:ts WHERE importId=:id")
    void updateProgress(String id, String txt, int pct, long ts);

    @Query("UPDATE ImportJob SET progressText=:txt, updatedAt=:ts WHERE importId=:id")
    void updateProgressText(String id, String txt, long ts);

    @Query("UPDATE ImportJob SET status=:status, currentOperation=:op, updatedAt=:ts WHERE importId=:id")
    void updateStatus(String id, String status, String op, long ts);

    @Query("UPDATE ImportJob SET warningText=:warn, updatedAt=:ts WHERE importId=:id")
    void updateWarning(String id, String warn, long ts);

    @Query("UPDATE ImportJob SET errorTextDev=:errorTextDev, errorTextUser=:errorTextUser, status='" + ImportJob.S_FAILED + "', updatedAt=:ts WHERE importId=:id")
    void fail(String id, String errorTextDev, String errorTextUser, long ts);

    @Query("UPDATE ImportJob SET status='" + ImportJob.S_CANCELLED + "', updatedAt=:ts WHERE importId=:id")
    void cancel(String id, long ts);

    @Query("UPDATE ImportJob SET status='" + ImportJob.S_SUCCEEDED + "'" +
            ", updatedAt=:ts WHERE importId=:id")
    void finish(String id, long ts);

    @Query("UPDATE ImportJob SET progressText=:why, isLoadingPaused = 1, status='" + ImportJob.S_PAUSED + "', updatedAt=:ts WHERE importId=:id")
    void downloadPause(String id, String why, long ts);

    @Query("UPDATE ImportJob SET progressText=:progressText, isLoadingPaused = 0,  status='" + ImportJob.S_RUNNING + "', updatedAt=:ts WHERE importId=:id")
    void downloadResuming(String id, String progressText, long ts);

    @Query("UPDATE ImportJob SET status='" + ImportJob.S_RUNNING + "'" +
            ", currentOperation=:taskName" +
            ", dynamicType = 'File'" +
            ", dynamicUri=:downloadedFileFullPath" +
            ", dynamicSourceFilePath=:downloadedFileFullPath" +
            ", downloadedFilePath=:downloadedFileFullPath" +
            ", downloadedFileReady = 1" +
            ", isLoadingPaused = 0" +
            ", progressText=:progressText" +
            ", updatedAt=:ts WHERE importId=:id")
    void downloadComplete(String id
            , String taskName
            , String downloadedFileFullPath
            , String progressText
            , long ts);

    @Query("UPDATE ImportJob SET status='" + ImportJob.S_RUNNING + "'" +
            ", currentOperation=:taskName" +
            ", dynamicType = 'Folder'" +
            ", dynamicUri=:destinationFolderPath" +
            ", dynamicSourceFilePath=:destinationFolderPath" +
            ", dynamicDestinationFolderPath=:destinationFolderPath" +
            ", playType=:playType" +
            ", updatedAt=:ts WHERE importId=:id")
    void taskComplete(String id, String taskName, String destinationFolderPath, String playType, long ts);

    @Query("SELECT * FROM ImportJob WHERE status IN (:s1, :s2, :s3) ORDER BY createdAt DESC LIMIT 1")
    ImportJob getMostRecentActive(String s1, String s2, String s3);

    @Query("SELECT COUNT(*) FROM ImportJob WHERE status IN (:s1, :s2, :s3)")
    int countActive(String s1, String s2, String s3);

    @Query("SELECT COUNT(*) FROM ImportJob WHERE status IN (:s1, :s2, :s3)")
    LiveData<Integer> observeActiveCount(String s1, String s2, String s3);

    // ImportJobDao.java
    @Query("""
SELECT * FROM ImportJob
ORDER BY 
  CASE 
    WHEN status IN (:sRun, :sQue, :sPause) THEN 0  -- active first
    ELSE 1                                        -- then terminal
  END,
  updatedAt DESC
LIMIT 1
""")
    LiveData<ImportJob> observeCurrentOrLast(String sRun, String sQue, String sPause);
}
