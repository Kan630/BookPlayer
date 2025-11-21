package com.driot.bookplayer.objects;

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