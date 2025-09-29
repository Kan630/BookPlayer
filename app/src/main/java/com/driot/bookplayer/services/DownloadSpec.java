package com.driot.bookplayer.services;

public final class DownloadSpec {
    public final String workId;      // String form of UUID
    public final String url;
    public final String destFolder;
    public final String title;
    public final boolean isManual;

    public DownloadSpec(String workId, String url, String destFolder, String title, boolean isManual) {
        this.workId = workId; this.url = url; this.destFolder = destFolder;
        this.title = title; this.isManual = isManual;
    }

    public String uniqueName() {
        // Unique per (url + dest), so we can replace/restart cleanly
        String key = url + "|" + destFolder;
        return "dl_" + Integer.toHexString(key.hashCode());
    }
}
