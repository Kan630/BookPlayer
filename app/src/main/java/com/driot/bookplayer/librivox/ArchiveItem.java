package com.driot.bookplayer.librivox;

import androidx.annotation.Keep;

@Keep
public class ArchiveItem {
    public String identifier;
    public String title;
    public String date;
    public float avg_rating;
    public int num_reviews;
    public String creator;

    public boolean is_favorite;
    public Long idFolder;       // null if not imported

    public String author;

    /** Remote cover image URL (e.g. archive.org item image). */
    public String imageRemote;
    /** Total size in bytes from source (e.g. archive.org). 0 if unknown. */
    public long source_size;

    public boolean isImported() { return idFolder != null && idFolder > 0; }
}
