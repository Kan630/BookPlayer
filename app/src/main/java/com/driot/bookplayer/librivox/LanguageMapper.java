package com.driot.bookplayer.librivox;

import com.driot.bookplayer.R;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Maps a Librivox language name (lang_en) to:
 *  - three-letter code (useful for Librivox API / internal)
 *  - two-letter code (ISO 639-1 when available)
 *  - flag resource id (R.drawable.*) to show in spinners
 *
 * Keys are matched case-insensitively on lang_en (trimmed).
 *
 * If an entry is unknown, getMapping returns a Mapping with empty codes and R.drawable.flag_globe.
 *
 * Edit entries if you want to change the chosen flag or the codes.
 */
public final class LanguageMapper {

    private LanguageMapper() {}

    public static final class Mapping {
        public final String threeLetterCode; // e.g. "eng"
        public final String twoLetterCode;   // e.g. "en"
        public final int flagRes;            // R.drawable.flag_*

        public Mapping(String threeLetterCode, String twoLetterCode, int flagRes) {
            this.threeLetterCode = threeLetterCode == null ? "" : threeLetterCode;
            this.twoLetterCode = twoLetterCode == null ? "" : twoLetterCode;
            this.flagRes = flagRes;
        }
    }

    private static final Mapping FALLBACK = new Mapping("", "", R.drawable.flag_globe);

    private static final Map<String, Mapping> MAP;
    static {
        Map<String, Mapping> m = new HashMap<>();

        // High-confidence mappings (common languages)
        m.put("english", new Mapping("eng", "en", R.drawable.flag_uk));
        m.put("french", new Mapping("fre", "fr", R.drawable.flag_fr));
        m.put("spanish", new Mapping("spa", "es", R.drawable.flag_es));
        m.put("german", new Mapping("deu", "de", R.drawable.flag_de));
        m.put("italian", new Mapping("ita", "it", R.drawable.flag_it));
        m.put("portuguese", new Mapping("por", "pt", R.drawable.flag_pt));
        m.put("dutch", new Mapping("nld", "nl", R.drawable.flag_nl));
        m.put("russian", new Mapping("rus", "ru", R.drawable.flag_ru));
        m.put("chinese", new Mapping("zho", "zh", R.drawable.flag_cn));
        m.put("japanese", new Mapping("jpn", "ja", R.drawable.flag_jp));
        m.put("korean", new Mapping("kor", "ko", R.drawable.flag_kr));
        m.put("arabic", new Mapping("ara", "ar", R.drawable.flag_sa));
        m.put("hindi", new Mapping("hin", "hi", R.drawable.flag_in));
        m.put("bengali", new Mapping("ben", "bn", R.drawable.flag_in));
        m.put("turkish", new Mapping("tur", "tr", R.drawable.flag_tr));
        m.put("swedish", new Mapping("swe", "sv", R.drawable.flag_se));
        m.put("norwegian", new Mapping("nor", "no", R.drawable.flag_no));
        m.put("danish", new Mapping("dan", "da", R.drawable.flag_dk));
        m.put("finnish", new Mapping("fin", "fi", R.drawable.flag_fi));
        m.put("polish", new Mapping("pol", "pl", R.drawable.flag_pl));
        m.put("czech", new Mapping("ces", "cs", R.drawable.flag_cz));
        m.put("greek", new Mapping("ell", "el", R.drawable.flag_gr));
        m.put("greek, ancient", new Mapping("grc", "", R.drawable.flag_gr));
        m.put("hebrew", new Mapping("heb", "he", R.drawable.flag_il));
        m.put("yiddish", new Mapping("yid", "yi", R.drawable.flag_il));
        m.put("romanian", new Mapping("ron", "ro", R.drawable.flag_ro));
        m.put("hungarian", new Mapping("hun", "hu", R.drawable.flag_hu));
        m.put("ukrainian", new Mapping("ukr", "uk", R.drawable.flag_ua));
        m.put("vietnamese", new Mapping("vie", "vi", R.drawable.flag_vn));
        m.put("indonesian", new Mapping("ind", "id", R.drawable.flag_id));
        m.put("malay", new Mapping("msa", "ms", R.drawable.flag_my));
        m.put("tagalog", new Mapping("tgl", "tl", R.drawable.flag_ph));
        m.put("tamil", new Mapping("tam", "ta", R.drawable.flag_in));
        m.put("telugu", new Mapping("tel", "te", R.drawable.flag_in));
        m.put("marathi", new Mapping("mar", "mr", R.drawable.flag_in));
        m.put("urdu", new Mapping("urd", "ur", R.drawable.flag_pk));
        m.put("persian/farsi", new Mapping("fas", "fa", R.drawable.flag_ir));
        m.put("farsi", new Mapping("fas", "fa", R.drawable.flag_ir));
        m.put("persian", new Mapping("fas", "fa", R.drawable.flag_ir));
        m.put("frisian", new Mapping("fry", "fy", R.drawable.flag_de));
        m.put("frysk", new Mapping("fry", "fy", R.drawable.flag_de));

        // Variants and close matches present in JSON
        m.put("ancient greek", new Mapping("grc", "", R.drawable.flag_gr));
        m.put("latin", new Mapping("lat", "la", R.drawable.flag_spqr));
        m.put("esperanto", new Mapping("epo", "", R.drawable.flag_esperanto));
        m.put("catalan", new Mapping("cat", "ca", R.drawable.flag_catalan));
        m.put("galician", new Mapping("glg", "gl", R.drawable.flag_galician));
        m.put("basque", new Mapping("eus", "eu", R.drawable.flag_basque));
        m.put("gaelic, scottish", new Mapping("gla", "gd", R.drawable.flag_scotland));
        m.put("gaelic", new Mapping("gla", "gd", R.drawable.flag_scotland));
        m.put("irish", new Mapping("gle", "ga", R.drawable.flag_ie));
        m.put("welsh", new Mapping("cym", "cy", R.drawable.flag_wales));
        m.put("breton", new Mapping("bre", "br", R.drawable.flag_breton));
        m.put("slovenian", new Mapping("slv", "sl", R.drawable.flag_si));
        m.put("slovak", new Mapping("slk", "sk", R.drawable.flag_sk));
        m.put("bulgarian", new Mapping("bul", "bg", R.drawable.flag_bg));
        m.put("croatian", new Mapping("hrv", "hr", R.drawable.flag_hr));
        m.put("serbian", new Mapping("srp", "sr", R.drawable.flag_rs));
        m.put("macedonian", new Mapping("mkd", "mk", R.drawable.flag_mk));
        m.put("estonian", new Mapping("est", "et", R.drawable.flag_ee));
        m.put("latvian", new Mapping("lav", "lv", R.drawable.flag_lv));
        m.put("lithuanian", new Mapping("lit", "lt", R.drawable.flag_lt));
        m.put("luxembourgish", new Mapping("ltz", "lb", R.drawable.flag_lu));
        m.put("polish", new Mapping("pol", "pl", R.drawable.flag_pl));
        m.put("norwegian nynorsk", new Mapping("nno", "nn", R.drawable.flag_no));
        m.put("nynorsk", new Mapping("nno", "nn", R.drawable.flag_no));
        m.put("dutch", new Mapping("nld", "nl", R.drawable.flag_nl));
        m.put("icelandic", new Mapping("isl", "is", R.drawable.flag_is));
        m.put("afrikaans", new Mapping("afr", "af", R.drawable.flag_za));

        // Additional single-country heuristics
        m.put("javanese", new Mapping("jav", "", R.drawable.flag_id));
        m.put("sundanese", new Mapping("sun", "", R.drawable.flag_id));
        m.put("minangkabau", new Mapping("min", "", R.drawable.flag_id));
        m.put("acehnese", new Mapping("ace", "", R.drawable.flag_id));
        m.put("buginese", new Mapping("bug", "", R.drawable.flag_id));
        m.put("balinese", new Mapping("ban", "", R.drawable.flag_id));
        m.put("tagalog", new Mapping("tgl", "tl", R.drawable.flag_ph));

        // Some languages mapped to "globe" or approximate flag when no clear single nation exists
        m.put("multilingual", new Mapping("mul", "", R.drawable.flag_globe));
        m.put("cantonese chinese", new Mapping("yue", "", R.drawable.flag_cn));
        m.put("church slavonic", new Mapping("chu", "", R.drawable.no_flag));
        m.put("old english", new Mapping("ang", "", R.drawable.flag_uk));
        m.put("middle english", new Mapping("enm", "", R.drawable.flag_uk));
        m.put("old norse", new Mapping("non", "", R.drawable.flag_no));
        m.put("old javanese", new Mapping("jav", "", R.drawable.flag_id));
        m.put("old sundanese", new Mapping("", "", R.drawable.flag_id));
        m.put("old tupi", new Mapping("", "", R.drawable.no_flag));
        m.put("mayan languages", new Mapping("", "", R.drawable.no_flag));
        m.put("iroquoian", new Mapping("", "", R.drawable.no_flag));
        m.put("north american indian (undetermined dialect)", new Mapping("", "", R.drawable.no_flag));

        // Languages with limited presence -> globe/fallback, but codes when known
        m.put("sanskrit", new Mapping("san", "sa", R.drawable.flag_in));
        m.put("sindhi", new Mapping("snd", "sd", R.drawable.no_flag));
        m.put("braj", new Mapping("bra", "", R.drawable.no_flag));
        m.put("assamese", new Mapping("asm", "as", R.drawable.flag_in));
        m.put("oriya", new Mapping("ori", "or", R.drawable.flag_in));
        m.put("korean", new Mapping("kor", "ko", R.drawable.flag_kr));
        m.put("kurdish", new Mapping("kur", "ku", R.drawable.no_flag));
        m.put("sindhi", new Mapping("snd", "sd", R.drawable.no_flag));
        m.put("maori", new Mapping("mri", "mi", R.drawable.flag_nz));

        m.put("dholuo/luo", new Mapping("luo", "", R.drawable.flag_ke));

        // Many small or uncommon languages default to globe: add them explicitly to allow later edits
        String[] noFlagLangs = new String[]{
                "aleut","braj","buginese","gamilaraay","garo","gascon/occitan",
                "faroese","friulano","kapampangan","khasi","kurdish","minangkabau",
                "nahuatl","neapolitan-calabrian","palatine german","rajasthani","volapük","walloon",
                "western frisian","occitan","neapolitan-calabrian","low german","ileoko",
                "iroquoian","mayan languages"
        };
        for (String gl : noFlagLangs) {
            m.put(gl, new Mapping("", "", R.drawable.no_flag));
        }

        // --- Ensure keys are lower-case and trimmed for robust matching ---
        Map<String, Mapping> normalized = new HashMap<>();
        for (Map.Entry<String, Mapping> e : m.entrySet()) {
            normalized.put(e.getKey().trim().toLowerCase(Locale.ROOT), e.getValue());
        }
        MAP = Collections.unmodifiableMap(normalized);
    }

    /**
     * Return the mapping for the provided langEn (case-insensitive, trimmed).
     * If unknown, returns a fallback mapping (empty codes + globe flag).
     *
     * @param langEn the lang_en value from the JSON (e.g. "Arabic")
     * @return Mapping (never null)
     */
    public static Mapping getMapping(String langEn) {
        if (langEn == null) return FALLBACK;
        String key = langEn.trim().toLowerCase(Locale.ROOT);
        Mapping m = MAP.get(key);
        return m == null ? FALLBACK : m;
    }

    /**
     * Returns the English language name from a three-letter code (ISO 639-3),
     * e.g. "eng" -> "English". Returns null if not found.
     */
    public static String getNameFromThreeLetter(String code3) {
        if (code3 == null || code3.isEmpty()) return null;
        String wanted = code3.trim().toLowerCase(Locale.ROOT);

        for (Map.Entry<String, Mapping> e : MAP.entrySet()) {
            if (wanted.equals(e.getValue().threeLetterCode)) {
                return e.getKey();  // english name as stored in MAP keys
            }
        }
        return null;
    }

    /**
     * Returns the English language name from a three-letter code (ISO 639-3),
     * e.g. "eng" -> "English". Returns 2 letters code if not found.
     */
    public static String getNameFromTwoLetters(String code2) {
        if (code2 == null || code2.isEmpty()) return null;
        String wanted = code2.trim().toLowerCase(Locale.ROOT);

        for (Map.Entry<String, Mapping> e : MAP.entrySet()) {
            if (wanted.equals(e.getValue().twoLetterCode)) {
                return e.getKey();  // english name as stored in MAP keys
            }
        }
        return code2;
    }
}
