package com.driot.bookplayer.helpers;

import android.content.Context;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;

import com.driot.bookplayer.R;
import com.driot.bookplayer.adapter.LanguageSpinnerAdapter;
import com.driot.bookplayer.objects.LanguageItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;

public class LanguageHelper {

    public static final List<LanguageItem> LIBRIVOX_LANGUAGES;
    public static final List<LanguageItem> PODCAST_LANGUAGES;

    // --- Dynamic TTS languages cache ---
    private static volatile List<LanguageItem> TTS_LANG_CACHE = null;
    private static final Object TTS_LOCK = new Object();

    public interface OnTtsLanguagesReady {
        void onReady(List<LanguageItem> items, int preselectIndex);
    }

    static {
        List<LanguageItem> baseList  = new ArrayList<>();
        baseList.add(new LanguageItem("eng", "en", "English", R.drawable.flag_uk));
        baseList.add(new LanguageItem("deu", "de", "Deutsch", R.drawable.flag_de));
        baseList.add(new LanguageItem("spa", "es", "Español", R.drawable.flag_es));
        baseList.add(new LanguageItem("fre", "fr", "Français", R.drawable.flag_fr));
        baseList.add(new LanguageItem("por", "pt", "Português", R.drawable.flag_pt));
        baseList.add(new LanguageItem("ita", "it", "Italiano", R.drawable.flag_it));
        baseList.add(new LanguageItem("rus", "ru", "Русский", R.drawable.flag_ru));
        baseList.add(new LanguageItem("zho", "zh", "中文", R.drawable.flag_cn));
        baseList.add(new LanguageItem("ara", "ar", "العربية", R.drawable.flag_sa));
        baseList.add(new LanguageItem("jpn", "ja", "日本語", R.drawable.flag_jp));
        baseList.add(new LanguageItem("hin", "hi", "हिन्दी", R.drawable.flag_in));
        baseList.add(new LanguageItem("ell", "el", "Ελληνικά", R.drawable.flag_gr));
        baseList.add(new LanguageItem("heb", "he", "עברית", R.drawable.flag_il));
        baseList.add(new LanguageItem("swe", "sv", "Svenska", R.drawable.flag_se));
        baseList.add(new LanguageItem("pol", "pl", "Polski", R.drawable.flag_pl));
        baseList.add(new LanguageItem("nld", "nl", "Nederlands", R.drawable.flag_nl));

        List<LanguageItem> librivoxList = new ArrayList<>(baseList );
        baseList.add(new LanguageItem("mul", "", "Multiple", R.drawable.flag_globe));
        LIBRIVOX_LANGUAGES = librivoxList ;

        List<LanguageItem> podcastList = new ArrayList<>(baseList );
        podcastList.add(new LanguageItem("tur", "tr", "Türkçe", R.drawable.flag_tr));
        podcastList.add(new LanguageItem("kor", "ko", "한국어", R.drawable.flag_kr));
        podcastList.add(new LanguageItem("ind", "id", "Bahasa Indonesia", R.drawable.flag_id));
        podcastList.add(new LanguageItem("tha", "th", "ไทย", R.drawable.flag_th));
        podcastList.add(new LanguageItem("vie", "vi", "Tiếng Việt", R.drawable.flag_vn));
        podcastList.add(new LanguageItem("ron", "ro", "Română", R.drawable.flag_ro));
        podcastList.add(new LanguageItem("ukr", "uk", "Українська", R.drawable.flag_ua));
        podcastList.add(new LanguageItem("ces", "cs", "Čeština", R.drawable.flag_cz));
        PODCAST_LANGUAGES = podcastList;
    }



    public static List<LanguageItem> getLibrivoxLanguages() {
        return LIBRIVOX_LANGUAGES;
    }

    public static List<LanguageItem> getPodcastLanguages() {
        return PODCAST_LANGUAGES;
    }

    public interface OnLanguageSelected {
        void onLanguageSelected(LanguageItem lang);
    }

    public static void setupLanguageSpinner(
            Context context,
            Spinner spinner,
            String selectedLangCode,
            List<LanguageItem> items,
            OnLanguageSelected callback,
            boolean useThreeLetterCode
    ) {
        LanguageSpinnerAdapter adapter = new LanguageSpinnerAdapter(context, items);
        spinner.setAdapter(adapter);

        int selectedPosition = 0;
        for (int i = 0; i < items.size(); i++) {
            String code = useThreeLetterCode ? items.get(i).threeLetterCode : items.get(i).twoLetterCode;
            if (code.equals(selectedLangCode)) {
                selectedPosition = i;
                break;
            }
        }
        spinner.setSelection(selectedPosition);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                LanguageItem selected = (LanguageItem) parent.getItemAtPosition(position);
                callback.onLanguageSelected(selected);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    public static LanguageItem getLanguageForPodcastsByCode(String twoLetterCode) {
        for (LanguageItem item : PODCAST_LANGUAGES) {
            if (item.twoLetterCode.equalsIgnoreCase(twoLetterCode)) {
                return item;
            }
        }
        return null;
    }


    /**
     * Load all available TTS languages from the current engine (async), with a "Device language" first entry.
     * Result is cached for subsequent calls in the process.
     */
    public static void loadAvailableTtsLanguages(Context ctx, String savedCodeOrSystem, OnTtsLanguagesReady cb) {
        if (TTS_LANG_CACHE != null) {
            int idx = computePreselectIndex(TTS_LANG_CACHE, savedCodeOrSystem);
            cb.onReady(TTS_LANG_CACHE, idx);
            return;
        }

        synchronized (TTS_LOCK) {
            if (TTS_LANG_CACHE != null) {
                int idx = computePreselectIndex(TTS_LANG_CACHE, savedCodeOrSystem);
                cb.onReady(TTS_LANG_CACHE, idx);
                return;
            }

            // Keep a reference so we can call methods + shutdown in onInit
            final TextToSpeech[] holder = new TextToSpeech[1];
            holder[0] = new TextToSpeech(ctx.getApplicationContext(), status -> {
                List<LanguageItem> list = new ArrayList<>();

                try {
                    TextToSpeech tts = holder[0];

                    // Always include "Device language"
                    String deviceLabel = "Device language (" + java.util.Locale.getDefault().getDisplayName() + ")";
                    list.add(new LanguageItem("sys", "system", deviceLabel, R.drawable.flag_globe));

                    if (status == TextToSpeech.SUCCESS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        // Build best-per-language map
                        java.util.Map<String, java.util.Locale> bestByLang2 = new java.util.LinkedHashMap<>();

                        // Prefer voices (more granular) if available
                        try {
                            java.util.Set<android.speech.tts.Voice> voices = tts.getVoices();
                            if (voices != null && !voices.isEmpty()) {
                                for (android.speech.tts.Voice v : voices) {
                                    java.util.Locale loc = v.getLocale();
                                    if (loc == null) continue;
                                    String lang2 = safeLang2(loc);
                                    if (lang2.isEmpty()) continue;

                                    boolean prefer = (v.getQuality() <= android.speech.tts.Voice.QUALITY_HIGH)
                                            || !v.isNetworkConnectionRequired();
                                    java.util.Locale cur = bestByLang2.get(lang2);
                                    if (cur == null) bestByLang2.put(lang2, loc);
                                    else if (prefer && hasCountry(loc) && !hasCountry(cur)) bestByLang2.put(lang2, loc);
                                }
                            }
                        } catch (Throwable ignored) {}

                        // Fallback: available languages
                        if (bestByLang2.isEmpty()) {
                            try {
                                java.util.Set<java.util.Locale> locales = tts.getAvailableLanguages();
                                if (locales != null) {
                                    for (java.util.Locale loc : locales) {
                                        String lang2 = safeLang2(loc);
                                        if (lang2.isEmpty()) continue;
                                        if (!bestByLang2.containsKey(lang2)) bestByLang2.put(lang2, loc);
                                        else if (hasCountry(loc) && !hasCountry(bestByLang2.get(lang2))) bestByLang2.put(lang2, loc);
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }

                        // Build LanguageItem list
                        for (java.util.Map.Entry<String, java.util.Locale> e : bestByLang2.entrySet()) {
                            String lang2 = e.getKey();
                            java.util.Locale loc = e.getValue();
                            String lang3 = safeLang3(loc);
                            String display = loc.getDisplayLanguage(loc);
                            int flagRes = resolveFlagRes(ctx, loc);
                            list.add(new LanguageItem(lang3, lang2, display, flagRes));
                        }
                    }

                } catch (Exception ignored) {
                    // keep only "system" if anything goes wrong
                    if (list.isEmpty()) {
                        String deviceLabel = "Device language (" + java.util.Locale.getDefault().getDisplayName() + ")";
                        list.add(new LanguageItem("sys", "system", deviceLabel, R.drawable.flag_globe));
                    }
                } finally {
                    try { holder[0].shutdown(); } catch (Throwable ignored) {}
                }

                TTS_LANG_CACHE = list;
                int idx = computePreselectIndex(TTS_LANG_CACHE, savedCodeOrSystem);
                cb.onReady(TTS_LANG_CACHE, idx);
            });
        }
    }

    public static void setupTtsSettingsSpinnerDynamic(
            Context context,
            Spinner spinner,
            String savedCodeOrSystem,
            OnLanguageSelected callback
    ) {
        // Quick placeholder
        List<LanguageItem> placeholder = new ArrayList<>();
        String deviceLabel = "Device language (" + java.util.Locale.getDefault().getDisplayName() + ")";
        placeholder.add(new LanguageItem("sys", "system", deviceLabel, R.drawable.flag_globe));
        LanguageSpinnerAdapter adapter = new LanguageSpinnerAdapter(context, placeholder);
        spinner.setAdapter(adapter);
        spinner.setSelection(0);

        loadAvailableTtsLanguages(context, savedCodeOrSystem, (items, preselect) -> {
            // Ensure we update the spinner on the main thread
            android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            h.post(() -> {
                LanguageSpinnerAdapter realAdapter = new LanguageSpinnerAdapter(context, items);
                spinner.setAdapter(realAdapter);
                spinner.setSelection(preselect);

                spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        LanguageItem selected = (LanguageItem) parent.getItemAtPosition(position);
                        callback.onLanguageSelected(selected);
                    }
                    @Override public void onNothingSelected(AdapterView<?> parent) {}
                });
            });
        });
    }
    // --- helpers ---

    private static int computePreselectIndex(List<LanguageItem> items, String saved) {
        if (saved == null) saved = "system";
        for (int i = 0; i < items.size(); i++) {
            if (saved.equalsIgnoreCase(items.get(i).twoLetterCode)) return i;
        }
        return 0; // default to "system"
    }

    private static boolean hasCountry(Locale loc) {
        return loc != null && loc.getCountry() != null && !loc.getCountry().isEmpty();
    }

    private static String safeLang2(Locale loc) {
        try { return loc.getLanguage() == null ? "" : loc.getLanguage(); }
        catch (Throwable ignored) { return ""; }
    }

    private static String safeLang3(Locale loc) {
        try { return loc.getISO3Language(); }
        catch (MissingResourceException e) { return ""; }
    }

    /** Try country flag first; if missing, try a curated fallback per language; else globe. */
    private static int resolveFlagRes(Context ctx, Locale loc) {
        // First try exact country
        String country = (loc != null ? loc.getCountry() : null);
        if (country != null && !country.isEmpty()) {
            int res = flagResByCode(ctx, country);
            if (res != 0) return res;
        }
        // Fallback mapping by language → canonical country
        String lang = safeLang2(loc);
        String fallbackCountry = fallbackCountryForLanguage(lang);
        int res = flagResByCode(ctx, fallbackCountry);
        return res != 0 ? res : R.drawable.flag_globe;
    }

    private static int flagResByCode(Context ctx, String countryCode2) {
        if (countryCode2 == null || countryCode2.isEmpty()) return 0;
        String name = "flag_" + countryCode2.toLowerCase(java.util.Locale.US);
        return ctx.getResources().getIdentifier(name, "drawable", ctx.getPackageName());
    }

    private static String fallbackCountryForLanguage(String lang2) {
        if (lang2 == null) return null;
        switch (lang2) {
            case "en": return "uk";
            case "fr": return "fr";
            case "de": return "de";
            case "es": return "es";
            case "pt": return "pt";
            case "it": return "it";
            case "ru": return "ru";
            case "zh": return "cn";
            case "ar": return "sa";
            case "ja": return "jp";
            case "hi": return "in";
            case "el": return "gr";
            case "he": return "il";
            case "sv": return "se";
            case "pl": return "pl";
            case "nl": return "nl";
            case "tr": return "tr";
            case "ko": return "kr";
            case "id": return "id";
            case "th": return "th";
            case "vi": return "vn";
            case "ro": return "ro";
            case "uk": return "ua";
            case "cs": return "cz";
            default:   return null;
        }
    }

    public static String twoLetterFromLocale(java.util.Locale l) {
        return (l == null) ? "system" : (l.getLanguage() == null ? "system" : l.getLanguage().toLowerCase());
    }

}
