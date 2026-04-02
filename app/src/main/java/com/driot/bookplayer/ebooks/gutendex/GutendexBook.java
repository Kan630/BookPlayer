// GutendexBook.java
package com.driot.bookplayer.ebooks.gutendex;

import androidx.annotation.Keep;

import java.util.List;
import java.util.Map;

@Keep
public class GutendexBook {

    public int id;
    public String title;
    public List<GutendexPerson> authors;
    public List<GutendexPerson> translators;
    public List<String> subjects;
    public List<String> bookshelves;
    public List<String> languages;
    public boolean copyright;
    public String media_type;
    public int download_count;
    public List<String> summaries; // may be null if not provided by server

    // key = MIME type, value = URL
    public Map<String, String> formats;

    @Override
    public String toString() {
        return "GutendexBook{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", authors=" + authors +
                ", translators=" + translators +
                ", subjects=" + subjects +
                ", bookshelves=" + bookshelves +
                ", languages=" + languages +
                ", copyright=" + copyright +
                ", media_type='" + media_type + '\'' +
                ", download_count=" + download_count +
                ", formats=" + formats +
                '}';
    }
}
