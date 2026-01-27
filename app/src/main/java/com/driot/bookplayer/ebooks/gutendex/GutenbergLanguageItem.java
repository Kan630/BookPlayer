// GutenbergLanguageItem.java
package com.driot.bookplayer.ebooks.gutendex;

import androidx.annotation.Keep;

import java.io.Serial;
import java.io.Serializable;

@Keep
public class GutenbergLanguageItem implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public final String name;        // lang_en
    public final String nativeName;  // lang_native_alphabet
    public final String code2;       // ISO 639-1 two-letter code
    public final String code3;       // ISO 639-2/3 three-letter code
    public final int flagRes;        // drawable resource ID
    public final String bookCount;   // book count indicator (e.g., "+50", "+0", or actual number)

    public GutenbergLanguageItem(String name, String nativeName, String code2, String code3, int flagRes, String bookCount) {
        this.name = name;
        this.nativeName = nativeName;
        this.code2 = code2;
        this.code3 = code3;
        this.flagRes = flagRes;
        this.bookCount = bookCount;
    }

    @Override
    public String toString() {
        return "GutenbergLanguageItem{" +
                "name='" + name + '\'' +
                ", nativeName='" + nativeName + '\'' +
                ", code2='" + code2 + '\'' +
                ", code3='" + code3 + '\'' +
                ", flagRes=" + flagRes +
                ", bookCount='" + bookCount + '\'' +
                '}';
    }
}
