package com.driot.bookplayer.db;

import java.util.List;

public class ItemMetadata {
    public Metadata metadata;
    public List<FileEntry> files;

    public static class Metadata {
        public String title;
        public String creator;
        public String date;
        // add more fields as needed
    }

    public static class FileEntry {
        public String name;
        public String format;
        public long size;  // file size in bytes
        public String length; // duration in seconds, string format
        // add more if you want (bitrate, md5, etc)
    }
}
