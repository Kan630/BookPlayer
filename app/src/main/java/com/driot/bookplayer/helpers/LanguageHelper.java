package com.driot.bookplayer.helpers;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;

import com.driot.bookplayer.R;
import com.driot.bookplayer.adapter.LanguageSpinnerAdapter;
import com.driot.bookplayer.objects.LanguageItem;
import com.driot.bookplayer.objects.VoiceItem;
import com.driot.bookplayer.utils.KanLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;

public class LanguageHelper {

    public static final List<LanguageItem> LIBRIVOX_LANGUAGES;
    public static final List<LanguageItem> PODCAST_LANGUAGES;

    // --- Dynamic TTS languages cache ---
    private static volatile List<LanguageItem> TTS_LANG_CACHE = null;
    private static volatile List<VoiceItem> TTS_VOICE_CACHE = null;
    private static final Object TTS_LOCK = new Object();



    public interface OnTtsVoicesReady {
        /** voices sorted best-first; preselect is index of currently-saved voice name (or 0). */
        void onReady(java.util.List<VoiceItem> voices, int preselect);
    }

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
        librivoxList.add(new LanguageItem("mul", "", "Multiple", R.drawable.flag_globe));
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
            int idx = LanguageHelper.computePreselectIndexForLanguage(TTS_LANG_CACHE, savedCodeOrSystem);
            cb.onReady(TTS_LANG_CACHE, idx);
            return;
        }

        synchronized (TTS_LOCK) {
            if (TTS_LANG_CACHE != null) {
                int idx = LanguageHelper.computePreselectIndexForLanguage(TTS_LANG_CACHE, savedCodeOrSystem);
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
                    String deviceLabel = ctx.getString(R.string.default_) + " (" + java.util.Locale.getDefault().getDisplayName() + ")";
                    list.add(new LanguageItem("sys", "system", deviceLabel, 0));

                    // Build best-per-language map
                    java.util.Map<String, java.util.Locale> bestByLang2 = new java.util.LinkedHashMap<>();

                    // Prefer voices (more granular) if available
                    try {
                        java.util.Set<android.speech.tts.Voice> voices = tts.getVoices();
                        if (voices != null && !voices.isEmpty()) {
                            myLogD(voices.size() + " voices found.");
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
                                myLogD(locales.size() + " locales found.");
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
                int idx = LanguageHelper.computePreselectIndexForLanguage(TTS_LANG_CACHE, savedCodeOrSystem);
                cb.onReady(TTS_LANG_CACHE, idx);
            });
        }
    }
    public static void loadAvailableTtsVoices(Context ctx, String savedCodeOrSystem, OnTtsVoicesReady cb) {
        if (TTS_VOICE_CACHE != null) {
            int idx = computePreselectIndexForVoice(TTS_VOICE_CACHE, savedCodeOrSystem);
            cb.onReady(TTS_VOICE_CACHE, idx);
            return;
        }

        synchronized (TTS_LOCK) {
            if (TTS_VOICE_CACHE != null) {
                int idx = computePreselectIndexForVoice(TTS_VOICE_CACHE, savedCodeOrSystem);
                cb.onReady(TTS_VOICE_CACHE, idx);
                return;
            }

            // Keep a reference so we can call methods + shutdown in onInit
            final TextToSpeech[] holder = new TextToSpeech[1];
            holder[0] = new TextToSpeech(ctx.getApplicationContext(), status -> {
                List<VoiceItem> list = new ArrayList<>();

                try {
                    TextToSpeech tts = holder[0];

                    // Always include "Device voice"
                    //String deviceLabel = ctx.getString(R.string.default_) + " (" + java.util.Locale.getDefault().getDisplayName() + ")";
                    //list.add(new VoiceItem("sys", "system", deviceLabel, 0));

                    // Build best-per-voice map
                    java.util.Map<String, java.util.Locale> bestByLang2 = new java.util.LinkedHashMap<>();

                    // Prefer voices (more granular) if available
                    try {
                        java.util.Set<android.speech.tts.Voice> voices = tts.getVoices();
                        if (voices != null && !voices.isEmpty()) {
                            myLogD(voices.size() + " voices found.");
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

                    // Build VoiceItem list
                    for (java.util.Map.Entry<String, java.util.Locale> e : bestByLang2.entrySet()) {
                        String lang2 = e.getKey();
                        java.util.Locale loc = e.getValue();
                        String lang3 = safeLang3(loc);
                        String display = "toto"; //loc.getDisplayVoice(loc);
                        int flagRes = resolveFlagRes(ctx, loc);
                        //list.add(new VoiceItem(lang3, lang2, display, flagRes));
                    }

                } catch (Exception ignored) {
                    // keep only "system" if anything goes wrong
                    if (list.isEmpty()) {
                        String deviceLabel = "Device voice (" + java.util.Locale.getDefault().getDisplayName() + ")";
                        //list.add(new VoiceItem("sys", "system", deviceLabel, R.drawable.flag_globe));
                    }
                } finally {
                    try { holder[0].shutdown(); } catch (Throwable ignored) {}
                }

                TTS_VOICE_CACHE = list;
                int idx = computePreselectIndexForVoice(TTS_VOICE_CACHE, savedCodeOrSystem);
                cb.onReady(TTS_VOICE_CACHE, idx);
            });
        }
    }
    public static void setupTtsVoiceSpinner(
            Context context,
            Spinner spinner,
            String savedCodeOrSystem,
            OnLanguageSelected callback
    ) {
        // Quick placeholder
        /*
        List<VoiceItem> placeholder = new ArrayList<>();
        String deviceLabel = "Device language (" + java.util.Locale.getDefault().getDisplayName() + ")";
        placeholder.add(new VoiceItem("sys", "system", deviceLabel, R.drawable.flag_globe));
        LanguageSpinnerAdapter adapter = new LanguageSpinnerAdapter(context, placeholder);
        spinner.setAdapter(adapter);
        spinner.setSelection(0);

         */

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

    private static int computePreselectIndexForLanguage(List<LanguageItem> items, String saved) {
        if (saved == null) saved = "system";
        for (int i = 0; i < items.size(); i++) {
            if (saved.equalsIgnoreCase(items.get(i).twoLetterCode)) return i;
        }
        return 0; // default to "system"
    }

    private static int computePreselectIndexForVoice(List<VoiceItem> items, String saved) {
        if (saved == null) saved = "system";
        for (int i = 0; i < items.size(); i++) {
            if (saved.equalsIgnoreCase(items.get(i).codeVoice)) return i;
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
        return flagResByCode(ctx, fallbackCountry);
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

    public static java.util.Locale localeFromTwoLetter(@androidx.annotation.Nullable String codeOrSystem) {
        // Fallback to device default for null/empty/"system"/"und"
        if (codeOrSystem == null) return java.util.Locale.getDefault();
        String c = codeOrSystem.trim().toLowerCase(java.util.Locale.ROOT);
        if (c.isEmpty() || "system".equals(c) || "und".equals(c)) {
            return java.util.Locale.getDefault();
        }

        // If user passed a full language tag (e.g., "en-US", "pt_BR"), use it.
        if (c.indexOf('-') >= 0 || c.indexOf('_') >= 0) {
            try {
                java.util.Locale viaTag = java.util.Locale.forLanguageTag(c.replace('_', '-'));
                if (viaTag != null && !viaTag.getLanguage().isEmpty()) return viaTag;
            } catch (Throwable ignored) {}
        }

        // Common languages → stable singletons (slightly nicer than new Locale("xx"))
        switch (c) {
            case "en": return java.util.Locale.ENGLISH;
            case "fr": return java.util.Locale.FRENCH;
            case "de": return java.util.Locale.GERMAN;
            case "it": return java.util.Locale.ITALIAN;
            case "ja": return java.util.Locale.JAPANESE;
            case "ko": return java.util.Locale.KOREAN;
            case "zh": return java.util.Locale.SIMPLIFIED_CHINESE; // default to simplified
            case "es": return new java.util.Locale("es");
            case "pt": return new java.util.Locale("pt");
            case "ru": return new java.util.Locale("ru");
            case "ar": return new java.util.Locale("ar");
            case "nl": return new java.util.Locale("nl");
            case "sv": return new java.util.Locale("sv");
            case "pl": return new java.util.Locale("pl");
            default:   return new java.util.Locale(c); // best-effort for any other 2-letter code
        }
    }


    // === LOGGING ===
    // ----------------------- LOG -----------------------
    private static final String TAG = "LanguageHelper";
    private static void myLog(String str) { KanLogger.myLog(TAG, str); }
    private static void myLogD(String str) { KanLogger.myLogD(TAG, str); }
    private static void myLogI(String str) { KanLogger.myLogI(TAG, str); }
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
    private static void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, TAG, str); }
    private static void myToastEE(Throwable t, String str) { KanLogger.myToastEE(t, TAG, str); }


// >>> add inside LanguageHelper <<<
    /** Async: query engine voices and return those matching the language of 'locale'. */
/*
    public static void loadVoicesForLocale(Context ctx,
                                           java.util.Locale locale,
                                           @androidx.annotation.Nullable String savedVoiceName,
                                           OnTtsVoicesReady cb) {
        final TextToSpeech[] holder = new TextToSpeech[1];
        holder[0] = new TextToSpeech(ctx.getApplicationContext(), status -> {
            java.util.List<VoiceItem> out = new java.util.ArrayList<>();
            try {
                if (status != TextToSpeech.SUCCESS) {
                    myLogE("loadVoicesForLocale: TTS init failed: " + status);
                    cb.onReady(out, 0);
                    return;
                }
                TextToSpeech tts = holder[0];
                java.util.Set<android.speech.tts.Voice> voices = tts.getVoices();
                if (voices == null || voices.isEmpty()) {
                    myLogW("loadVoicesForLocale: engine reported no voices");
                    cb.onReady(out, 0);
                    return;
                }

                String want = locale.getLanguage(); // match by language, accept any region
                for (android.speech.tts.Voice v : voices) {
                    if (v.getLocale() == null) continue;
                    if (!want.equals(v.getLocale().getLanguage())) continue;
                    out.add(new VoiceItem(v));
                }

                // Sort best-first (embedded first, higher quality, lower latency; then name)
                java.util.Collections.sort(out, (a, b) -> {
                    int sa = scoreVoice(a);
                    int sb = scoreVoice(b);
                    if (sa != sb) return Integer.compare(sb, sa);
                    // tie-breakers
                    int q = Integer.compare(b.quality, a.quality);
                    if (q != 0) return q;
                    int l = Integer.compare(a.latency, b.latency);
                    if (l != 0) return l;
                    return a.name.compareToIgnoreCase(b.name);
                });

                int preselect = preselectVoiceIndex(out, savedVoiceName);
                cb.onReady(out, preselect);
            } catch (Throwable t) {
                myLogEE(t, "loadVoicesForLocale failed");
                cb.onReady(out, 0);
            } finally {
                try { holder[0].shutdown(); } catch (Throwable ignored) {}
            }
        });
    }

 */

    private static int scoreVoice(VoiceItem v) {
        int score = 0;
        if (v.embedded) score += 200;                     // prefer offline/installed
        if (!v.requiresNetwork) score += 50;              // prefer non-network if ambiguous
        score += (10 * v.quality);                        // higher quality
        score += (10 * (5 - Math.min(5, v.latency)));     // lower latency
        return score;
    }

    private static int preselectVoiceIndex(java.util.List<VoiceItem> list, @androidx.annotation.Nullable String voiceName) {
        if (voiceName == null || voiceName.isEmpty()) return 0;
        for (int i = 0; i < list.size(); i++) {
            if (voiceName.equals(list.get(i).name)) return i;
        }
        return 0;
    }

// >>> add inside LanguageHelper <<<

    public interface OnVoiceSelected {
        void onVoiceSelected(VoiceItem voice);
    }

    /**
     * Build/update a voice spinner for a chosen locale.
     * - Shows label from VoiceItem#toString()
     * - Preselects savedVoiceName if present
     */
    public static void setupVoiceSpinnerForLocale(Context ctx,
                                                  Spinner spinner,
                                                  java.util.Locale locale,
                                                  @androidx.annotation.Nullable String savedVoiceName,
                                                  OnVoiceSelected cb) {
        // show a tiny placeholder immediately
        java.util.List<VoiceItem> placeholder = new java.util.ArrayList<>();
        android.widget.ArrayAdapter<VoiceItem> phAdapter =
                new android.widget.ArrayAdapter<>(ctx, android.R.layout.simple_spinner_item, placeholder);
        phAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(phAdapter);
/*
        loadVoicesForLocale(ctx, locale, savedVoiceName, (voices, preselect) -> {
            android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            h.post(() -> {
                android.widget.ArrayAdapter<VoiceItem> adapter =
                        new android.widget.ArrayAdapter<>(ctx, android.R.layout.simple_spinner_item, voices);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinner.setAdapter(adapter);
                if (preselect >= 0 && preselect < voices.size()) spinner.setSelection(preselect);

                spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        VoiceItem v = (VoiceItem) parent.getItemAtPosition(position);
                        cb.onVoiceSelected(v);
                    }
                    @Override public void onNothingSelected(AdapterView<?> parent) {}
                });
            });
        });

 */
    }

// >>> add inside LanguageHelper <<<

    /** Dump all engine voices with attributes (locale, quality, latency, features, network/embedded). */
    public static void logAllVoices(Context ctx) {
        final TextToSpeech[] holder = new TextToSpeech[1];
        holder[0] = new TextToSpeech(ctx.getApplicationContext(), status -> {
            try {
                if (status != TextToSpeech.SUCCESS) {
                    myLogE("logAllVoices: TTS init failed: " + status);
                    return;
                }
                TextToSpeech tts = holder[0];
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) {
                    myLogW("logAllVoices: API<21, no voices API.");
                    return;
                }
                java.util.Set<android.speech.tts.Voice> set = tts.getVoices();
                myLogI("---- VOICES CATALOG (engine=" + tts.getDefaultEngine() + ", size=" + (set == null ? 0 : set.size()) + ") ----");
                if (set != null) {
                    for (android.speech.tts.Voice v : set) {
                        myLogI(describeVoice(v));
                    }
                }
                myLogI("---- END VOICES ----");
            } catch (Throwable t) {
                myLogEE(t, "logAllVoices failed");
            } finally {
                try { holder[0].shutdown(); } catch (Throwable ignored) {}
            }
        });
    }

    private static String describeVoice(android.speech.tts.Voice v) {
        if (v == null) return "Voice{null}";
        java.util.Locale loc = v.getLocale();
        java.util.Set<String> feat = v.getFeatures();
        boolean embedded = (feat != null && feat.contains("embeddedTts"));
        boolean network  = v.isNetworkConnectionRequired() || (feat != null && feat.contains("networkTts"));
        String state = embedded ? "EMBEDDED" : (network ? "NETWORK_ONLY" : "UNKNOWN");
        return "Voice{name=" + v.getName() +
                ", locale=" + (loc == null ? "null" : loc.toLanguageTag()) +
                ", quality=" + v.getQuality() +
                ", latency=" + v.getLatency() +
                ", state=" + state +
                ", features=" + (feat == null ? "[]" : feat.toString()) +
                "}";
    }


}
