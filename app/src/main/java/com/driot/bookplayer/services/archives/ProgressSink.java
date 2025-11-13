package com.driot.bookplayer.services.archives;

@FunctionalInterface
public interface ProgressSink {
    /** cur goes from 0..total (total may be 0 if unknown). */
    void onProgress(int cur, int total, String currentName);
}
