package com.driot.bookplayer.player.heatmaps;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

@Dao
public interface PlaySessionDao {

    @Insert
    void insertAll(List<PlaySession> sessions);

    @Query("SELECT * FROM PlaySession WHERE zikFileId = :zikFileId")
    List<PlaySession> getAllForFile(long zikFileId);

    // Helper method to convert sessions to buckets
    static List<PlayTickBucket> getBucketCounts(List<PlaySession> sessions, long bucketSizeMs) {
        Map<Long, Long> bucketMap = new HashMap<>();

        if (PlayTickHeatMapHelper.LOG_DEBUG_PLAYTICK) myLogD("sessions size: " + sessions.size());
        for (PlaySession session : sessions) {
            long sessionStartBucket = session.positionStart / bucketSizeMs;
            long sessionEndBucket = session.positionEnd / bucketSizeMs;
            if (PlayTickHeatMapHelper.LOG_DEBUG_PLAYTICK) myLogD("sessionStartBucket: " + sessionStartBucket + " - sessionEndBucket: " + sessionEndBucket + " - bucketSizeMs: " + bucketSizeMs);

            for (long bucketPos = sessionStartBucket; bucketPos <= sessionEndBucket; bucketPos++) {
                // Calculate how much of this bucket is covered by the session
                long bucketStart = bucketPos * bucketSizeMs;
                long bucketEnd = (bucketPos + 1) * bucketSizeMs;

                long overlapStart = Math.max(session.positionStart, bucketStart);
                long overlapEnd = Math.min(session.positionEnd, bucketEnd);
                long overlapMs = Math.max(overlapEnd - overlapStart, 1000);

                long ticks = 0;
                ticks = Math.round((float) overlapMs / 1000); // Convert ms to seconds //TODO => 1000 is SleepTimer periodicity ?
                bucketMap.merge(bucketPos, ticks, Long::sum);
                if (PlayTickHeatMapHelper.LOG_DEBUG_PLAYTICK) myLogD("bucketPos: " + bucketPos + " - bucket [ start: " + bucketStart + " - end: " + bucketEnd + "] - overlap [ start: " + overlapStart + " - end: " + overlapEnd + "] - overlapMs: " + overlapMs + " - ticks: " + ticks + " - bucketMap: " + bucketMap.toString());
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

