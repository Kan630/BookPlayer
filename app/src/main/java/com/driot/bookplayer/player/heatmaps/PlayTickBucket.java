package com.driot.bookplayer.player.heatmaps;

import com.driot.bookplayer.utils.Tonio;

public class PlayTickBucket {
    public long bucket;  // 0..(nbBuckets-1)
    public long ticks;   // how many 1-second ticks in that bucket

    @Override
    public String toString() {
        return Tonio.lpad(bucket,2) + Tonio.lpad(ticks,2);   // for logging
    }
}