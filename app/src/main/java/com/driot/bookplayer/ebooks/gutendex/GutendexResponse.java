// GutendexResponse.java
package com.driot.bookplayer.ebooks.gutendex;

import androidx.annotation.Keep;

import java.util.List;

@Keep
public class GutendexResponse {
    public int count;
    public String next;      // URL to next page or null
    public String previous;  // URL to previous page or null
    public List<GutendexBook> results;
}
