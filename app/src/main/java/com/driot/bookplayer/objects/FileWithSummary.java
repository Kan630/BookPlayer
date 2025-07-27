package com.driot.bookplayer.objects;

import java.io.File;

public class FileWithSummary {
    public final File file;
    public final double percentDone;
    public final String sourceLocation;

    public final long fileSizeMB;

    public FileWithSummary(File file, double percentDone, String sourceLocation, long fileSizeMB) {
        this.file = file;
        this.percentDone = percentDone;
        this.sourceLocation = sourceLocation;
        this.fileSizeMB = fileSizeMB;
    }
}
