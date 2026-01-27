// GutenbergBookshelf.java
package com.driot.bookplayer.ebooks.gutendex;

public class GutenbergBookshelf {
    public String name;
    public Integer count; // approximate count (can be null if unknown)

    public GutenbergBookshelf(String name, Integer count) {
        this.name = name;
        this.count = count;
    }
}
