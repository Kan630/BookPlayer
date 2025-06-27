package com.driot.bookplayer.db;

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

        public long getSizeAsLong() {
            try {
                return Long.parseLong(size);
            } catch (Exception e) {
                return -1;
            }
        }

        public String getReadableSizeInMB() {
            try {
                long bytes = Long.parseLong(size);
                double mb = bytes / (1024.0 * 1024.0);
                return String.format(Locale.US, "%.1f MB", mb);
            } catch (Exception e) {
                return "Unknown size";
            }
        }
        public String getReadableDuration() {
            try {
                double seconds = Double.parseDouble(length);
                int h = (int) (seconds / 3600);
                int m = (int) ((seconds % 3600) / 60);
                int s = (int) (seconds % 60);
                if (h > 0) {
                    return String.format(Locale.US,"%d:%02d:%02d", h, m, s);
                } else {
                    return String.format(Locale.US,"%02d:%02d", m, s);
                }
            } catch (Exception e) {
                return "Unknown";
            }
        }

    }
}
