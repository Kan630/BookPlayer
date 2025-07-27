package com.driot.bookplayer.objects;

import java.io.File;

public class FileWithSummary {
    public final File file;
    public final double percentDone;
    public final String sourceLocation;

    public FileWithSummary(File file, double percentDone, String sourceLocation) {
        this.file = file;
        this.percentDone = percentDone;
        this.sourceLocation = sourceLocation;
    }
}
