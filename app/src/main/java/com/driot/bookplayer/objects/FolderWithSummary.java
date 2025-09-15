package com.driot.bookplayer.objects;

import java.io.File;

// UI model

public class FolderWithSummary {
    public final File file;
    public final double percentDone;
    public final String sourceLocation;
    public final String playType;
    public final String image;
    public final long folderSizeInBytes;

    public FolderWithSummary(File file, double percentDone, String sourceLocation, String playType, long folderSizeInBytes, String image) {
        this.file = file;
        this.percentDone = percentDone;
        this.sourceLocation = sourceLocation;
        this.playType = playType;
        this.image = image;
        this.folderSizeInBytes = folderSizeInBytes;
    }
}
