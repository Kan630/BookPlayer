package com.driot.bookplayer.helpers;

// LocaleHelper.java
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import android.text.TextUtils;

public class LocaleHelper {
    public static void applyAppLocale(String tagOrSystem) {
        if (TextUtils.isEmpty(tagOrSystem) || "system".equals(tagOrSystem)) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tagOrSystem));
        }
    }
}
