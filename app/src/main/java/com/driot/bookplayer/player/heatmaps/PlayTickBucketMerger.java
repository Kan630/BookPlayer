package com.driot.bookplayer.player.heatmaps;

import java.util.*;

public final class PlayTickBucketMerger {

    private PlayTickBucketMerger() {}

    public static List<PlayTickBucket> merge(
            List<PlayTickBucket> sessions,
            List<PlayTickBucket> ticks
    ) {
        Map<Long, Long> map = new HashMap<>();

        if (sessions != null) {
            for (PlayTickBucket b : sessions) {
                map.put(b.bucket, b.ticks);
            }
        }

        if (ticks != null) {
            for (PlayTickBucket b : ticks) {
                map.merge(b.bucket, b.ticks, Long::sum);
            }
        }

        List<PlayTickBucket> result = new ArrayList<>();
        for (Map.Entry<Long, Long> e : map.entrySet()) {
            PlayTickBucket b = new PlayTickBucket();
            b.bucket = e.getKey();
            b.ticks = e.getValue();
            result.add(b);
        }

        result.sort(Comparator.comparingLong(o -> o.bucket));
        return result;
    }
}
