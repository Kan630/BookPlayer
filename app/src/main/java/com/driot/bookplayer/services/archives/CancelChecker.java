package com.driot.bookplayer.services.archives;

@FunctionalInterface
public interface CancelChecker {
    /** Return true if the job should stop ASAP. */
    boolean isCancelled();
}