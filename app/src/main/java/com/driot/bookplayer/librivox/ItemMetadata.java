package com.driot.bookplayer.librivox;

import androidx.annotation.Keep;

import com.google.gson.annotations.JsonAdapter;

import java.util.List;

@Keep
public class ItemMetadata {
    public Metadata metadata;
    public List<FileEntry> files;

    @Keep
    public static class Metadata {
        public String title;
        // Same archive.org string-or-array quirk as ArchiveItem.creator - see FlexibleStringAdapter.
        @JsonAdapter(FlexibleStringAdapter.class)
        public String creator;
        public String date;
        public String description;
        public String identifier;
        public String runtime;
        public String language;
        /*
        public String subject;
        public String licenseurl;
        public String collection;
        public String mediatype;
        public String language;
        public String publicdate;
         */
        // Add more if needed, depending on what you want to display
    }

    @Keep
    public static class FileEntry {
        public String name;
        public String format;
        public String size;  // file size in bytes
        public String length; // duration in seconds, string format
    }
}
