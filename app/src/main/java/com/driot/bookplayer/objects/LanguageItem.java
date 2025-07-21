package com.driot.bookplayer.objects;

import androidx.annotation.NonNull;

public class LanguageItem {
    public String threeLetterCode;
    public String twoLetterCode;
    public String displayName;
    public int flagResId;

    public LanguageItem(String threeLetterCode, String rss2LettersCode, String displayName, int flagResId) {
        this.threeLetterCode = threeLetterCode;
        this.twoLetterCode = rss2LettersCode;
        this.displayName = displayName;
        this.flagResId = flagResId;
    }

    @NonNull
    @Override
    public String toString() {
        return threeLetterCode;
    }

    public String getTwoLetterCode() {
        return twoLetterCode;
    }
}
