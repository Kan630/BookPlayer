package com.driot.bookplayer.helpers;

import android.content.Context;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;

import com.driot.bookplayer.R;
import com.driot.bookplayer.adapter.LanguageSpinnerAdapter;
import com.driot.bookplayer.objects.LanguageItem;

import java.util.ArrayList;
import java.util.List;

public class LanguageHelper {

    public static final List<LanguageItem> LIBRIVOX_LANGUAGES;
    public static final List<LanguageItem> PODCAST_LANGUAGES;
    public static final List<LanguageItem> RADIO_LANGUAGES;
    public static final List<LanguageItem> APP_LANGUAGES;
    static {
        List<LanguageItem> list = new ArrayList<>();
        // order matches your pref_languages_entries/values
        list.add(new LanguageItem("",   "system", "System default", R.drawable.flag_globe));
        list.add(new LanguageItem("eng","en",     "English",        R.drawable.flag_uk));
        list.add(new LanguageItem("fre","fr",     "Français",       R.drawable.flag_fr));
        list.add(new LanguageItem("spa","es",     "Español",        R.drawable.flag_es));
        list.add(new LanguageItem("deu","de",     "Deutsch",        R.drawable.flag_de));
        list.add(new LanguageItem("ita","it",     "Italiano",       R.drawable.flag_it));
        list.add(new LanguageItem("por","pt",     "Português",      R.drawable.flag_pt));
        list.add(new LanguageItem("hin","hi",     "हिन्दी",            R.drawable.flag_in));
        list.add(new LanguageItem("ara","ar",     "العربية",        R.drawable.flag_sa));
        list.add(new LanguageItem("rus","ru",     "Русский",        R.drawable.flag_ru));
        list.add(new LanguageItem("zho","zh",     "中文",            R.drawable.flag_cn));
        APP_LANGUAGES = list;
    }

    static {
        List<LanguageItem> allFirstList = new ArrayList<>();
        allFirstList.add(new LanguageItem("", "", "All", R.drawable.flag_globe));

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

        List<LanguageItem> radioList = new ArrayList<>(allFirstList);
        radioList.addAll(podcastList);
        RADIO_LANGUAGES = radioList;
    }

    public static List<LanguageItem> getLibrivoxLanguages() {
        return LIBRIVOX_LANGUAGES;
    }

    public static List<LanguageItem> getPodcastLanguages() {
        return PODCAST_LANGUAGES;
    }

    public static List<LanguageItem> getRadioLanguages() {
        return RADIO_LANGUAGES;
    }

    public static List<LanguageItem> getAppLanguages() { return APP_LANGUAGES; }

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
     * Returns a representative ISO 3166-1 alpha-2 country code
     * for a given ISO 639-1 language code.
     * Returns null if no mapping exists.
     */
    public static String getCountryForLanguage(String languageCode) {
        if (languageCode == null) return null;
        switch (languageCode.toLowerCase()) {
            case "en": return "US";  // English
            case "fr": return "FR";  // French
            case "es": return "ES";  // Spanish
            case "de": return "DE";  // German
            case "it": return "IT";  // Italian
            case "pt": return "PT";  // Portuguese
            case "ru": return "RU";  // Russian
            case "zh": return "CN";  // Chinese
            case "ja": return "JP";  // Japanese
            case "ko": return "KR";  // Korean
            case "ar": return "SA";  // Arabic
            case "hi": return "IN";  // Hindi
            case "bn": return "BD";  // Bengali
            case "pa": return "PK";  // Punjabi
            case "ur": return "PK";  // Urdu
            case "vi": return "VN";  // Vietnamese
            case "th": return "TH";  // Thai
            case "tr": return "TR";  // Turkish
            case "nl": return "NL";  // Dutch
            case "sv": return "SE";  // Swedish
            case "no": return "NO";  // Norwegian
            case "da": return "DK";  // Danish
            case "fi": return "FI";  // Finnish
            case "he": return "IL";  // Hebrew
            case "el": return "GR";  // Greek
            case "pl": return "PL";  // Polish
            case "cs": return "CZ";  // Czech
            case "hu": return "HU";  // Hungarian
            case "ro": return "RO";  // Romanian
            case "sk": return "SK";  // Slovak
            case "sl": return "SI";  // Slovene
            case "bg": return "BG";  // Bulgarian
            case "sr": return "RS";  // Serbian
            case "hr": return "HR";  // Croatian
            case "id": return "ID";  // Indonesian
            case "ms": return "MY";  // Malay
            case "tl": return "PH";  // Tagalog/Filipino
            case "sw": return "KE";  // Swahili
            case "fa": return "IR";  // Persian/Farsi
            case "uk": return "UA";  // Ukrainian
            case "mr": return "IN";  // Marathi
            case "ta": return "IN";  // Tamil
            case "te": return "IN";  // Telugu
            case "ml": return "IN";  // Malayalam
            case "kn": return "IN";  // Kannada
            // ... add more languages here
            default: return null;    // no mapping available
        }
    }

}
