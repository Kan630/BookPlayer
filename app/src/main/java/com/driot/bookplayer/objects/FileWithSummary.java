package com.driot.bookplayer.objects;

import java.io.File;

public class FileWithSummary {
    public final File file;
    public final double percentDone;
    public final String sourceLocation;
    public final String originalFile;
    public final long fileSizeMB;

    public FileWithSummary(File file, double percentDone, String sourceLocation, String originalFile, long fileSizeMB) {
        this.file = file;
        this.percentDone = percentDone;
        this.sourceLocation = sourceLocation;
        this.originalFile = originalFile;
        this.fileSizeMB = fileSizeMB;
    }
}
