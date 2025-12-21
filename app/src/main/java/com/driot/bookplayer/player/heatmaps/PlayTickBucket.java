package com.driot.bookplayer.player.heatmaps;

public class PlayTickBucket {
    public long bucket;  // 0..(nbBuckets-1)
    public long ticks;   // how many 1-second ticks in that bucket

    @Override
    public String toString() {
        return "{" +
                "bucket=" + bucket +
                ", ticks=" + ticks +
                '}';
    }
}