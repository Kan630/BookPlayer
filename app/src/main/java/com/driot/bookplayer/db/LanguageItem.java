package com.driot.bookplayer.db;

import androidx.annotation.NonNull;

public class LanguageItem {
    public String threeLetterCode;
    public String displayName;
    public int flagResId;

    public LanguageItem(String threeLetterCode, String displayName, int flagResId) {
        this.threeLetterCode = threeLetterCode;
        this.displayName = displayName;
        this.flagResId = flagResId;
    }

    @NonNull
    @Override
    public String toString() {
        return threeLetterCode;
    }
}
