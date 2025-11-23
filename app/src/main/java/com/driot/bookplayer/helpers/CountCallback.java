package com.driot.bookplayer.helpers;

public interface CountCallback {
    /**
     * Called every time a new file is discovered.
     *
     * @param fileCount total number of files found so far
     * @param currentPath  path or name of the current file (may be null)
     */
    void onCountUpdated(int fileCount, String currentPath, int folderCount);

    default void onFinished(int fileCount, int folderCount) {}

    /**
     * Return true to stop the scan as soon as possible.
     */
    boolean isCancelled();
}
