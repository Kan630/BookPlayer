package com.driot.bookplayer.objects;

import androidx.annotation.NonNull;

import com.driot.bookplayer.db.Podcast;

public class PodcastEpisode {

    // Direct JSON :
    public String title;
    public String description;
    public String enclosureUrl; // download URL
    public String datePublishedPretty;
    public String datePublished;
    public long id;
    public Integer duration; // in seconds
    public long enclosureLength; // in bytes

    // manually SET  (don't forget) !!
    public Podcast podcast;

}
