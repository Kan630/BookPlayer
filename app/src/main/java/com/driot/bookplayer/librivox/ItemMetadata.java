package com.driot.bookplayer.librivox;

import java.util.List;
import java.util.Locale;

public class ItemMetadata {
    public Metadata metadata;
    public List<FileEntry> files;

    public static class Metadata {
        public String title;
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

    public static class FileEntry {
        public String name;
        public String format;
        public String size;  // file size in bytes
        public String length; // duration in seconds, string format
    }
}
