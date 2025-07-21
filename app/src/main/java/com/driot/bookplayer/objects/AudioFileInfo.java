package com.driot.bookplayer.objects;

public class AudioFileInfo {
    private String fileName;
    private long duration;

    public AudioFileInfo(String fileName, long duration) {
        this.fileName = fileName;
        this.duration = duration;
    }

    public String getFileName() {
        return fileName;
    }

    public long getDuration() {
        return duration;
    }

    // Optional: toString, equals, hashCode...
}
