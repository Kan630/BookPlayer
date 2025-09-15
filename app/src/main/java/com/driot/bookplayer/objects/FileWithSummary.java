package com.driot.bookplayer.objects;

import java.io.File;

// UI model

public class FileWithSummary {
    public final File file;
    public final double percentDone;
    public final String sourceLocation;
    public final String playType;
    public final long fileSizeMB;

    public FileWithSummary(File file, double percentDone, String sourceLocation, String playType, long fileSizeMB) {
        this.file = file;
        this.percentDone = percentDone;
        this.sourceLocation = sourceLocation;
        this.playType = playType;
        this.fileSizeMB = fileSizeMB;
    }
}
