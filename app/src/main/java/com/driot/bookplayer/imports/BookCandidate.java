package com.driot.bookplayer.imports;

import android.net.Uri;
import androidx.annotation.NonNull;

public class BookCandidate {
    public Uri uri;
    public String name;
    public String type; // Folder, ZIP, M4B, EPUB.
    public String path; // For display
    public long size;
    public int tracksCount;
    public String originalHash; // Computed during scanning
    public String existingBookName; // Name of book if hash already exists in DB (null if not imported)

    public BookCandidate(Uri uri, String name, String type, String path, long size, String originalHash,
            String existingBookName, int tracksCount) {
        this.uri = uri;
        this.name = name;
        this.type = type;
        this.path = path;
        this.size = size;
        this.originalHash = originalHash;
        this.existingBookName = existingBookName;
        this.tracksCount = tracksCount;
    }

    public boolean isAlreadyImported() {
        return existingBookName != null && !existingBookName.isEmpty();
    }

    @NonNull
    @Override
    public String toString() {
        return "[" + type + "] " + name;
    }
}
