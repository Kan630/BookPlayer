package com.driot.bookplayer.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.driot.bookplayer.db.PlayTick;
import com.driot.bookplayer.objects.PlayTickBucket;

import java.util.List;

@Dao
public interface PlayTickDao {

    @Insert
    long insert(PlayTick tick);

    @Query("SELECT * FROM PlayTick WHERE id = :id LIMIT 1")
    PlayTick getById(long id);

    @Query("SELECT * FROM PlayTick WHERE zikFileId = :zikFileId ORDER BY timestamp DESC")
    List<PlayTick> getAllForZikFile(long zikFileId);

    @Query("DELETE FROM PlayTick WHERE timestamp < :beforeTimestamp")
    int deleteOlderThan(long beforeTimestamp);

    @Query("DELETE FROM PlayTick")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM PlayTick")
    long count();


    @Query("SELECT (position / :bucketSizeMs) AS bucket, COUNT(*) AS ticks " +
            "FROM PlayTick " +
            "WHERE zikFileId = :zikFileId " +
            "GROUP BY bucket")
    List<PlayTickBucket> getBucketCounts(long zikFileId, long bucketSizeMs);

}
