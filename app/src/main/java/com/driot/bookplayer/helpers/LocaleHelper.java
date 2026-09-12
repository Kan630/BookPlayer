package com.driot.bookplayer.helpers;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.LocaleList;
import android.text.TextUtils;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.utils.log.KanLogger;

import java.util.Locale;

public class LocaleHelper {

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
     * Wrap a context with the app's saved locale so that getResources(), getString(), etc.
     * use the user-chosen language. Use this in Activity.attachBaseContext so the language
     * works on all devices (including Oppo and Samsung Android 9-12 where setApplicationLocales
     * alone often does not). Reads APP_LANGUAGE from the same SharedPreferences as Option
     * (safe to call before Option.init()).
     *
     * Confirmed via device log on a real Oppo (ColorOS): createConfigurationContext() alone -
     * even when rebuilding a brand-new Resources instance from scratch - correctly updates the
     * returned context's Configuration metadata, but getString() on that same context still
     * returned the English default. The OEM's resource-resolution cache is keyed to the
     * original shared Resources instance and ignores any new/isolated one. The fix below
     * (createConfigurationContext + mutating the shared Resources in place via the deprecated
     * updateConfiguration, plus writing the locale through both the modern LocaleList API and
     * the legacy Configuration.locale field) is the exact technique used by the
     * battle-tested MultiLanguages library (https://github.com/getActivity/MultiLanguages),
     * built specifically to work around this class of Xiaomi/Huawei/Oppo/Vivo bug.
     */
    public static Context wrapContextWithAppLocale(Context base) {
        if (base == null)
            return base;

        String tag = Option.getAppLanguage(base);
        Locale target = (TextUtils.isEmpty(tag) || Option.DEFAULT_LANGUAGE.equals(tag))
                ? getSystemLocale()
                : Locale.forLanguageTag(tag);

        // Some OEM resource-resolution paths (and any date/number formatting that reads
        // Locale.getDefault() directly instead of the per-context Configuration) key off the
        // process-wide default locale - keep it in sync every time.
        Locale.setDefault(target);

        Resources baseResources = base.getResources();
        Configuration config = new Configuration(baseResources.getConfiguration());
        setLocale(config, target);

        Context wrapped = base.createConfigurationContext(config);
        // Mutate the shared Resources instance in place too - see class doc above for why.
        baseResources.updateConfiguration(config, baseResources.getDisplayMetrics());

        KanLogger.myLogD("LocaleHelper",
                "wrapContextWithAppLocale: tag=[" + tag + "] target=[" + target
                        + "] wrapped.locale=[" + wrapped.getResources().getConfiguration().getLocales().get(0)
                        + "] sample string=[" + wrapped.getString(R.string.nav_settings) + "]");
        return wrapped;
    }

    private static void setLocale(Configuration config, Locale locale) {
        config.setLocales(new LocaleList(locale));
        // Some OEM builds only honor the legacy single-locale field even on modern API levels
        // (matches what we saw on this Oppo) - set both for maximum compatibility.
        if (!locale.equals(config.getLocales().get(0))) {
            config.locale = locale;
        }
        config.setLayoutDirection(locale);
    }

    private static Locale getSystemLocale() {
        return Resources.getSystem().getConfiguration().getLocales().get(0);
    }

    /**
     * Get the app's configured locale from the given context.
     * Use this instead of Locale.getDefault() when formatting user-facing text
     * (numbers, dates, etc.) to ensure the text matches the app's selected language, not the
     * system locale.
     */
    public static Locale getLocale(Context context) {
        if (context == null) {
            return Locale.getDefault(); // fallback only
        }
        return context.getResources().getConfiguration().getLocales().get(0);
    }
}
