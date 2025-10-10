// com.driot.bookplayer.objects.CoverResult.java
package com.driot.bookplayer.objects;

public class CoverResult {
    public final String title;     // optional
    public final String imageUrl;  // direct image or large thumbnail
    public final String source;    // "OpenLibrary", "GoogleBooks"

    public CoverResult(String title, String imageUrl, String source) {
        this.title = title;
        this.imageUrl = imageUrl;
        this.source = source;
    }
}
