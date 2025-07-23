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

    @NonNull
    @Override
    public String toString() {
        return "-----------PodcastEpisode----------" +
                "\ntitle='" + title + '\'' +
                "\ndescription='" + description + '\'' +
                "\nenclosureUrl='" + enclosureUrl + '\'' +
                "\ndatePublishedPretty='" + datePublishedPretty + '\'' +
                "\ndatePublished='" + datePublished + '\'' +
                "\nid=" + id +
                "\nduration=" + duration +
                "\nenclosureLength=" + enclosureLength +
                "\n------------------------------------";
    }
}
