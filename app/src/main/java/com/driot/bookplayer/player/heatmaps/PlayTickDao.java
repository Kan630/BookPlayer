package com.driot.bookplayer.player.heatmaps;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface PlayTickDao {

    @Insert
    long insert(PlayTick tick);


    @Query("""
        SELECT * FROM PlayTick
        WHERE zikFileId = :zikFileId
        ORDER BY timestamp ASC
    """)
    List<PlayTick> getAllForFile(long zikFileId);

    @Query("""
        DELETE FROM PlayTick
        WHERE zikFileId = :zikFileId
          AND timestamp <= :maxTimestamp
    """)
    void deleteUpTo(long zikFileId, long maxTimestamp);

    @Query("""
        DELETE FROM PlayTick
        WHERE zikFileId = :zikFileId
          AND timestamp >= :minTimestamp
          AND timestamp <= :maxTimestamp
    """)
    void deleteRange(long zikFileId, long minTimestamp, long maxTimestamp);

        @Query("""
            SELECT (position / :bucketSizeMs) AS bucket,
                   COUNT(*) AS ticks
            FROM PlayTick
            WHERE zikFileId = :zikFileId
            GROUP BY bucket
        """)
        List<PlayTickBucket> getBucketCounts(long zikFileId, long bucketSizeMs);
}
