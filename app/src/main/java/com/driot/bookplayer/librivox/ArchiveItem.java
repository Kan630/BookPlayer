package com.driot.bookplayer.librivox;

import androidx.annotation.Keep;

@Keep
public class ArchiveItem {
    public String identifier;
    public String title;
    public String date;
    public float avg_rating;
    public int num_reviews;

    public boolean is_favorite;
    public Long idFolder;       // null if not imported

    public String author;

    public boolean isImported() { return idFolder != null && idFolder > 0; }
}
