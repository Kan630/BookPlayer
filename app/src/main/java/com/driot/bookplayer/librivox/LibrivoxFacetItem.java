// LibrivoxFacetItem.java
package com.driot.bookplayer.librivox;


public class LibrivoxFacetItem {
    public final String name;
    public final Integer count; // nullable, if you later have counts from API

    public LibrivoxFacetItem(String name, Integer count) {
        this.name = name;
        this.count = count;
    }
}
