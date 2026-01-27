// GutenbergBookshelfItem.java
package com.driot.bookplayer.ebooks.gutendex;

public class GutenbergBookshelfItem {
    public final String name;
    public final Integer count; // nullable

    public GutenbergBookshelfItem(String name, Integer count) {
        this.name = name;
        this.count = count;
    }
}
