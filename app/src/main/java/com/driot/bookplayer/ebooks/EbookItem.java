package com.driot.bookplayer.ebooks;

public class EbookItem {

    public int gutendexId;
    public String title;
    public String authors;
    public String language;      // first language code, e.g. "en"
    public int downloadCount;
    public String coverUrl;
    public String epubUrl;

    // For future integration (once you store imported ebooks in DB)
    public boolean isImported;

    public boolean isImported() {
        return isImported;
    }
}
