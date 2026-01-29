package com.driot.bookplayer.activities;

/**
 * Data class to hold storage information for display
 */
public class StorageInfo {
    public final long totalStorageBytes;
    public final long usedByOthersBytes;
    public final long usedByBookPlayerBytes;
    public final long expectedAddedMemoryBytes; // For MassImportActivity
    public final String displayText;

    public StorageInfo(long totalStorageBytes, long usedByOthersBytes, long usedByBookPlayerBytes, long expectedAddedMemoryBytes, String displayText) {
        this.totalStorageBytes = totalStorageBytes;
        this.usedByOthersBytes = usedByOthersBytes;
        this.usedByBookPlayerBytes = usedByBookPlayerBytes;
        this.expectedAddedMemoryBytes = expectedAddedMemoryBytes;
        this.displayText = displayText;
    }

    public StorageInfo(long totalStorageBytes, long usedByOthersBytes, long usedByBookPlayerBytes, String displayText) {
        this(totalStorageBytes, usedByOthersBytes, usedByBookPlayerBytes, 0, displayText);
    }
}
