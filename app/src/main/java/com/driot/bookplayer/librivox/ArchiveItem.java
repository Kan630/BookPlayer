package com.driot.bookplayer.librivox;

import androidx.annotation.Keep;

import com.google.gson.annotations.JsonAdapter;

@Keep
public class ArchiveItem {
    public String identifier;
    public String title;
    // archive.org returns this as a plain string OR a JSON array (multiple creators) -
    // see FlexibleStringAdapter for why a custom adapter is needed here.
    @JsonAdapter(FlexibleStringAdapter.class)
    public String creator;
    public String author;
    public String date;
    public float avg_rating;
    public int num_reviews;

    public boolean is_favorite;
    public Long idFolder;       // null if not imported


    /** Remote cover image URL (e.g. archive.org item image). */
    public String imageRemote;
    /** Total size in bytes from source (e.g. archive.org). 0 if unknown. */
    public long source_size;

    public boolean isImported() { return idFolder != null && idFolder > 0; }
}
