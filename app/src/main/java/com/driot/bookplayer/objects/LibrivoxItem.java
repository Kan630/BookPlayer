package com.driot.bookplayer.objects;

public class LibrivoxItem {
    public String identifier;
    public String title;
    public String date;
    public float avg_rating;
    public int num_reviews;

    public boolean is_favorite;
    public Long idFolder;       // null if not imported

    public boolean isImported() { return idFolder != null && idFolder > 0; }
}
