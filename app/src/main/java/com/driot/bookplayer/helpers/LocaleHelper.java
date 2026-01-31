package com.driot.bookplayer.helpers;

import android.content.Context;
import android.content.res.Configuration;
import android.text.TextUtils;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.driot.bookplayer.global.Option;

import java.util.Locale;

public class LocaleHelper {

    private static final String PREF_KEY_APP_LANGUAGE = "APP_LANGUAGE";

    /**
     * Apply app locale via AppCompat (used when user changes language in settings).
     * On Android 13+ this uses system per-app language; on older versions behaviour
     * varies by OEM (Oppo, some Samsung, etc. may not apply or recreate).
     */
    public static void applyAppLocale(String tagOrSystem) {
        if (TextUtils.isEmpty(tagOrSystem) || Option.DEFAULT_LANGUAGE.equals(tagOrSystem)) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tagOrSystem));
        }
    }

    /**
     * Wrap a context with the app's saved locale so that getResources(),
     * getString(), etc.
     * use the user-chosen language. Use this in Application.attachBaseContext and
     * Activity.attachBaseContext so the language works on all devices (including
     * Oppo
     * and Samsung Android 9–12 where setApplicationLocales alone often does not).
     * Reads APP_LANGUAGE from the same SharedPreferences as Option (safe to call
     * before
     * Option.init()).
     */
    public static Context wrapContextWithAppLocale(Context base) {
        if (base == null)
            return base;
        String tag = base.getSharedPreferences(Option.SHARED_PREFERENCES_OPTIONS, Context.MODE_PRIVATE)
                .getString(PREF_KEY_APP_LANGUAGE, Option.DEFAULT_LANGUAGE);
        if (TextUtils.isEmpty(tag) || Option.DEFAULT_LANGUAGE.equals(tag)) {
            return base;
        }
        Locale locale = Locale.forLanguageTag(tag);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(locale);
        return base.createConfigurationContext(config);
    }

    /**
     * Get the app's configured locale from the given context.
     * Use this instead of Locale.getDefault() when formatting user-facing text
     * (numbers, dates, etc.)
     * to ensure the text matches the app's selected language, not the system
     * locale.
     */
    public static Locale getLocale(Context context) {
        if (context == null) {
            return Locale.getDefault(); // fallback only
        }
        return context.getResources().getConfiguration().getLocales().get(0);
    }
}
