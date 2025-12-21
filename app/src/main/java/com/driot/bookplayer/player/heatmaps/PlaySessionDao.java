package com.driot.bookplayer.player.heatmaps;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Dao
public interface PlaySessionDao {

    @Insert
    void insertAll(List<PlaySession> sessions);

    @Query("SELECT * FROM PlaySession WHERE zikFileId = :zikFileId")
    List<PlaySession> getAllForFile(long zikFileId);

    // Helper method to convert sessions to buckets
    static List<PlayTickBucket> getBucketCounts(List<PlaySession> sessions, long bucketSizeMs) {
        Map<Long, Long> bucketMap = new HashMap<>();

        for (PlaySession session : sessions) {
            long startBucket = session.positionStart / bucketSizeMs;
            long endBucket = session.positionEnd / bucketSizeMs;

            for (long bucket = startBucket; bucket <= endBucket; bucket++) {
                // Calculate how much of this bucket is covered by the session
                long bucketStart = bucket * bucketSizeMs;
                long bucketEnd = (bucket + 1) * bucketSizeMs;

                long overlapStart = Math.max(session.positionStart, bucketStart);
                long overlapEnd = Math.min(session.positionEnd, bucketEnd);
                long overlapMs = overlapEnd - overlapStart;

                if (overlapMs > 0) {
                    long ticks = overlapMs / 1000; // Convert ms to seconds
                    bucketMap.merge(bucket, ticks, Long::sum);
                }
            }
        }

        List<PlayTickBucket> result = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : bucketMap.entrySet()) {
            PlayTickBucket bucket = new PlayTickBucket();
            bucket.bucket = entry.getKey();
            bucket.ticks = entry.getValue();
            result.add(bucket);
        }

        return result;
    }

}

