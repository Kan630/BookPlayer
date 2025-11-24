// GutendexBook.java
package com.driot.bookplayer.ebooks.gutendex;

import java.util.List;
import java.util.Map;

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

    // key = MIME type, value = URL
    public Map<String, String> formats;
}
