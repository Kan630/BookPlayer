package com.driot.bookplayer.podcasts;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

@Keep
public class PodcastEpisode {

    // Direct JSON :
    public long id;
    public String guid;
    public String title;
    public String description;
    public String image;
    public String enclosureUrl; // download URL
    public String datePublishedPretty;
    public String datePublished;
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
                "\nguid=" + guid +
                "\nduration=" + duration +
                "\nenclosureLength=" + enclosureLength +
                "\nimage=" + image +
                "\nenclosureUrl=" + enclosureUrl +
                "\n------------------------------------";
    }
}
