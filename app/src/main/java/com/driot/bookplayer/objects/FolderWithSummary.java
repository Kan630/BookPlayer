package com.driot.bookplayer.objects;

import androidx.annotation.Nullable;

import java.io.File;

// UI model

public class FolderWithSummary {
    public final File file;
    public final double percentDone;
    public final String sourceLocation;
    public final String playType;
    public final String image;
    public final long folderSizeInBytes;
    /** Folder.name from DB when associated; null when no Folder exists for this path. */
    @Nullable
    public final String folderName;
    /** Folder.id from DB when associated; <= 0 when no Folder exists for this path. */
    public final long idFolder;

    public FolderWithSummary(File file, double percentDone, String sourceLocation, String playType, long folderSizeInBytes, String image, @Nullable String folderName, long idFolder) {
        this.file = file;
        this.percentDone = percentDone;
        this.sourceLocation = sourceLocation;
        this.playType = playType;
        this.image = image;
        this.folderSizeInBytes = folderSizeInBytes;
        this.folderName = folderName;
        this.idFolder = idFolder;
    }
}
