package com.driot.bookplayer.imports;

import android.net.Uri;
import androidx.annotation.NonNull;

public class BookCandidate {
    public Uri uri;
    public String name;
    public String type; // Folder, ZIP, M4B, EPUB.
    public String path; // For display

    public BookCandidate(Uri uri, String name, String type, String path) {
        this.uri = uri;
        this.name = name;
        this.type = type;
        this.path = path;
    }

    @NonNull
    @Override
    public String toString() {
        return "[" + type + "] " + name;
    }
}
