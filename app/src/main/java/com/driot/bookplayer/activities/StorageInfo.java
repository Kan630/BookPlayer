package com.driot.bookplayer.activities;

/**
 * Data class to hold storage information for display
 */
public class StorageInfo {
    public final long totalStorageBytes;
    public final long usedByOthersBytes;
    public final long usedByBookPlayerBytes;
    public final long expectedAddedMemoryBytes; // For MassImportActivity
    public final long linkedAudiosBytes; // Linked audios (files outside BookPlayer reserved space)
    public final long appStorageBytes; // BookPlayer app storage (app + db + logs + images, excluding audio)
    public final String displayText;

    public StorageInfo(long totalStorageBytes, long usedByOthersBytes, long usedByBookPlayerBytes, long expectedAddedMemoryBytes, long linkedAudiosBytes, long appStorageBytes, String displayText) {
        this.totalStorageBytes = totalStorageBytes;
        this.usedByOthersBytes = usedByOthersBytes;
        this.usedByBookPlayerBytes = usedByBookPlayerBytes;
        this.expectedAddedMemoryBytes = expectedAddedMemoryBytes;
        this.linkedAudiosBytes = linkedAudiosBytes;
        this.appStorageBytes = appStorageBytes;
        this.displayText = displayText;
    }

    public StorageInfo(long totalStorageBytes, long usedByOthersBytes, long usedByBookPlayerBytes, long expectedAddedMemoryBytes, long linkedAudiosBytes, String displayText) {
        this(totalStorageBytes, usedByOthersBytes, usedByBookPlayerBytes, expectedAddedMemoryBytes, linkedAudiosBytes, 0, displayText);
    }

    public StorageInfo(long totalStorageBytes, long usedByOthersBytes, long usedByBookPlayerBytes, long expectedAddedMemoryBytes, String displayText) {
        this(totalStorageBytes, usedByOthersBytes, usedByBookPlayerBytes, expectedAddedMemoryBytes, 0, 0, displayText);
    }

    public StorageInfo(long totalStorageBytes, long usedByOthersBytes, long usedByBookPlayerBytes, String displayText) {
        this(totalStorageBytes, usedByOthersBytes, usedByBookPlayerBytes, 0, 0, 0, displayText);
    }
}
