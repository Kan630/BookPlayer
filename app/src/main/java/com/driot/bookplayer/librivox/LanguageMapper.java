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
        m.put("brazilian portuguese", new Mapping("", "", R.drawable.flag_br)); // flag only; code resolved via ALIAS
        m.put("dutch", new Mapping("nld", "nl", R.drawable.flag_nl));
        m.put("russian", new Mapping("rus", "ru", R.drawable.flag_ru));
        m.put("arabic", new Mapping("ara", "ar", R.drawable.flag_sa));
        m.put("hindi", new Mapping("hin", "hi", R.drawable.flag_in));
        m.put("bengali", new Mapping("ben", "bn", R.drawable.flag_in));
        m.put("turkish", new Mapping("tur", "tr", R.drawable.flag_tr));
        m.put("polish", new Mapping("pol", "pl", R.drawable.flag_pl));
        m.put("czech", new Mapping("ces", "cs", R.drawable.flag_cz));
        m.put("greek", new Mapping("ell", "el", R.drawable.flag_gr));
        m.put("hebrew", new Mapping("heb", "he", R.drawable.flag_il));
        m.put("yiddish", new Mapping("yid", "yi", R.drawable.flag_il));
        m.put("romanian", new Mapping("ron", "ro", R.drawable.flag_ro));
        m.put("hungarian", new Mapping("hun", "hu", R.drawable.flag_hu));
        m.put("ukrainian", new Mapping("ukr", "uk", R.drawable.flag_ua));
        m.put("bosnian", new Mapping("", "ba", R.drawable.flag_ba));
        m.put("albanian", new Mapping("sqi", "sq", R.drawable.flag_al));

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

        m.put("swedish", new Mapping("swe", "sv", R.drawable.flag_se));
        m.put("norwegian", new Mapping("nor", "no", R.drawable.flag_no));
        m.put("danish", new Mapping("dan", "da", R.drawable.flag_dk));
        m.put("finnish", new Mapping("fin", "fi", R.drawable.flag_fi));
        m.put("icelandic", new Mapping("isl", "is", R.drawable.flag_is));
        m.put("norwegian nynorsk", new Mapping("nno", "nn", R.drawable.flag_no));
        m.put("nynorsk", new Mapping("nno", "nn", R.drawable.flag_no));
        m.put("norsk", new Mapping("nno", "nn", R.drawable.flag_no));
        m.put("old norse", new Mapping("non", "", R.drawable.flag_no));
        m.put("faroese", new Mapping("", "", R.drawable.flag_fo));

        m.put("walloon", new Mapping("", "ba", R.drawable.flag_be));
        m.put("catalan", new Mapping("cat", "ca", R.drawable.flag_catalan));
        m.put("galician", new Mapping("glg", "gl", R.drawable.flag_galician));
        m.put("basque", new Mapping("eus", "eu", R.drawable.flag_basque));
        m.put("gaelic, scottish", new Mapping("gla", "gd", R.drawable.flag_scotland));
        m.put("gaelic", new Mapping("gla", "gd", R.drawable.flag_scotland));
        m.put("irish", new Mapping("gle", "ga", R.drawable.flag_ie));
        m.put("welsh", new Mapping("cym", "cy", R.drawable.flag_wales));
        m.put("breton", new Mapping("bre", "br", R.drawable.flag_breton));
        m.put("deutsch fränkisch", new Mapping("frk", "", R.drawable.flag_franken));
        m.put("flemish", new Mapping("", "", R.drawable.flag_flanders));

        m.put("indonesian", new Mapping("ind", "id", R.drawable.flag_id));
        m.put("javanese", new Mapping("jav", "", R.drawable.flag_id));
        m.put("sundanese", new Mapping("sun", "", R.drawable.flag_id));
        m.put("minangkabau", new Mapping("min", "", R.drawable.flag_id));
        m.put("acehnese", new Mapping("ace", "", R.drawable.flag_id));
        m.put("buginese", new Mapping("bug", "", R.drawable.flag_id));
        m.put("balinese", new Mapping("ban", "", R.drawable.flag_id));
        m.put("bahasa indonesia", new Mapping("idn", "id", R.drawable.flag_id));
        m.put("old javanese", new Mapping("jav", "", R.drawable.flag_id));
        m.put("old sundanese", new Mapping("", "", R.drawable.flag_id));

        m.put("tagalog", new Mapping("tgl", "tl", R.drawable.flag_ph));
        m.put("filipino", new Mapping("", "", R.drawable.flag_ph));
        m.put("kapampangan", new Mapping("", "", R.drawable.flag_ph));
        m.put("ileoko", new Mapping("", "", R.drawable.flag_ph));



        m.put("multilingual", new Mapping("mul", "", R.drawable.flag_globe));
        m.put("cantonese chinese", new Mapping("yue", "", R.drawable.flag_cn));
        m.put("cantonese", new Mapping("yue", "", R.drawable.flag_cn));
        m.put("mandarin", new Mapping("cmn", "", R.drawable.flag_cn));
        m.put("chinese", new Mapping("zho", "zh", R.drawable.flag_cn));
        m.put("hokkien", new Mapping("", "", R.drawable.flag_tw));
        m.put("japanese", new Mapping("jpn", "ja", R.drawable.flag_jp));
        m.put("korean", new Mapping("kor", "ko", R.drawable.flag_kr));
        m.put("vietnamese", new Mapping("vie", "vi", R.drawable.flag_vn));
        m.put("malay", new Mapping("msa", "ms", R.drawable.flag_my));

        m.put("maori", new Mapping("mri", "mi", R.drawable.flag_nz));
        m.put("gamilaraay", new Mapping("", "", R.drawable.flag_au));

        m.put("nahuatl", new Mapping("", "", R.drawable.flag_mx));
        m.put("maya", new Mapping("", "", R.drawable.flag_mx));

        m.put("azerbaijani", new Mapping("aze", "az", R.drawable.flag_az));
        m.put("kazakh", new Mapping("kaz", "kz", R.drawable.flag_kz));
        m.put("georgian", new Mapping("", "ge", R.drawable.flag_ge));
        m.put("kurdish", new Mapping("kur", "ku", R.drawable.flag_iq));

        m.put("persian/farsi", new Mapping("fas", "fa", R.drawable.flag_ir));
        m.put("farsi", new Mapping("fas", "fa", R.drawable.flag_ir));
        m.put("persian", new Mapping("fas", "fa", R.drawable.flag_ir));
        m.put("sindhi", new Mapping("snd", "sd", R.drawable.flag_pk));
        m.put("urdu", new Mapping("urd", "ur", R.drawable.flag_pk));

        m.put("sanskrit", new Mapping("san", "sa", R.drawable.flag_in));
        m.put("punjabi", new Mapping("pan", "", R.drawable.flag_in));
        m.put("rajasthani", new Mapping("", "", R.drawable.flag_in));
        m.put("tamil", new Mapping("tam", "ta", R.drawable.flag_in));
        m.put("telugu", new Mapping("tel", "te", R.drawable.flag_in));
        m.put("marathi", new Mapping("mar", "mr", R.drawable.flag_in));
        m.put("assamese", new Mapping("asm", "as", R.drawable.flag_in));
        m.put("oriya", new Mapping("ori", "or", R.drawable.flag_in));
        m.put("braj", new Mapping("bra", "", R.drawable.flag_in));
        m.put("garo", new Mapping("", "", R.drawable.flag_in));
        m.put("khasi", new Mapping("", "", R.drawable.flag_in));
        m.put("bangla", new Mapping("", "", R.drawable.flag_bd));
        m.put("nepali", new Mapping("", "", R.drawable.flag_np));

        m.put("sinhala", new Mapping("sin", "si", R.drawable.flag_lk));

        m.put("creole", new Mapping("", "", R.drawable.flag_ht));
        m.put("haitian creole", new Mapping("", "", R.drawable.flag_ht));

        m.put("afrikaans", new Mapping("afr", "af", R.drawable.flag_za));
        m.put("dholuo/luo", new Mapping("luo", "", R.drawable.flag_ke));
        m.put("luganda", new Mapping("uga", "ug", R.drawable.flag_ug));
        m.put("amharic", new Mapping("", "", R.drawable.flag_et));

        m.put("latin", new Mapping("lat", "la", R.drawable.flag_spqr));
        m.put("esperanto", new Mapping("epo", "", R.drawable.flag_esperanto));
        m.put("old english", new Mapping("ang", "", R.drawable.flag_uk));
        m.put("middle english", new Mapping("enm", "", R.drawable.flag_uk));
        m.put("frisian", new Mapping("fry", "fy", R.drawable.flag_de));
        m.put("frysk", new Mapping("fry", "fy", R.drawable.flag_de));
        m.put("mayan languages", new Mapping("", "", R.drawable.flag_mx));
        m.put("greek, ancient", new Mapping("grc", "", R.drawable.flag_gr));
        m.put("ancient greek", new Mapping("grc", "", R.drawable.flag_gr));
        m.put("church slavonic", new Mapping("chu", "", R.drawable.no_flag));
        m.put("old tupi", new Mapping("", "", R.drawable.no_flag));
        m.put("iroquoian", new Mapping("", "", R.drawable.no_flag));
        m.put("north american indian (undetermined dialect)", new Mapping("", "", R.drawable.no_flag));

        // --- Additional entries resolved from radio-browser flag audit ---
        // Languages that already had a valid iso_639 from the API but no flag in this MAP
        m.put("swiss german",    new Mapping("gsw", "gsw", R.drawable.flag_ch));
        m.put("belarusian",      new Mapping("bel", "be",  R.drawable.flag_by));
        m.put("low german",      new Mapping("nds", "nds", R.drawable.flag_de));
        m.put("mongolian",       new Mapping("mon", "mn",  R.drawable.flag_mn));
        m.put("tibetan",         new Mapping("bod", "bo",  R.drawable.flag_tibet));
        m.put("bambara",         new Mapping("bam", "bm",  R.drawable.flag_ml));
        m.put("tatar",           new Mapping("tat", "tt",  R.drawable.flag_ru));
        m.put("bashkir",         new Mapping("bak", "ba",  R.drawable.flag_ru));
        m.put("hausa",           new Mapping("hau", "ha",  R.drawable.flag_ng));
        m.put("uzbek",           new Mapping("uzb", "uz",  R.drawable.flag_uz));
        m.put("occitan",         new Mapping("oci", "oc",  R.drawable.flag_occitan));
        m.put("hakka",           new Mapping("hak", "",    R.drawable.flag_cn));
        // Languages with null iso_639 from the API that have a clear flag
        m.put("odia",            new Mapping("ori", "or",  R.drawable.flag_in));
        m.put("bhojpuri",        new Mapping("",    "",    R.drawable.flag_in));
        m.put("uyghur",          new Mapping("uig", "ug",  R.drawable.flag_cn));
        m.put("montenegrin",     new Mapping("",    "",    R.drawable.flag_me));
        m.put("moldovian",       new Mapping("",    "",    R.drawable.flag_md));
        m.put("tunisian",        new Mapping("",    "",    R.drawable.flag_tn));
        m.put("lusoga",          new Mapping("",    "",    R.drawable.flag_ug));
        m.put("cebuano",         new Mapping("ceb", "",    R.drawable.flag_ph));
        m.put("ilocano",         new Mapping("ilo", "",    R.drawable.flag_ph));
        m.put("isizulu",         new Mapping("zul", "zu",  R.drawable.flag_za));
        m.put("sepedi",          new Mapping("",    "",    R.drawable.flag_za));
        m.put("xitsonga",        new Mapping("tso", "ts",  R.drawable.flag_za));
        m.put("kiswahili",       new Mapping("swa", "sw",  R.drawable.flag_tz));
        m.put("kyrgyz",          new Mapping("kir", "ky",  R.drawable.flag_kg));
        m.put("papiamentu",      new Mapping("pap", "",    R.drawable.flag_cw));
        m.put("flammish",        new Mapping("",    "",    R.drawable.flag_flanders));
        m.put("sorbian",         new Mapping("hsb", "",    R.drawable.flag_de));
        m.put("romani",          new Mapping("rom", "",    R.drawable.flag_romani));
        // manual additions
        m.put("français - lëtzebuergesch", new Mapping("", "lu",    R.drawable.flag_lu));



        /*
        String[] noFlagLangs = new String[]{
                "gascon/occitan",
                "friulano"
                "",","neapolitan-calabrian","palatine german","volapük","",
                "western frisian","occitan","neapolitan-calabrian","low german","",
                "iroquoian","mayan languages"
        };
        for (String gl : noFlagLangs) {
            m.put(gl, new Mapping("", "", R.drawable.no_flag));
        }

         */

        // --- Ensure keys are lower-case and trimmed for robust matching ---
        Map<String, Mapping> normalized = new HashMap<>();
        for (Map.Entry<String, Mapping> e : m.entrySet()) {
            normalized.put(e.getKey().trim().toLowerCase(Locale.ROOT), e.getValue());
        }
        MAP = Collections.unmodifiableMap(normalized);
    }

    // -------------------------------------------------------------------------
    // Alias map: messy/non-English/regional/typo names → grouping code
    // Used to resolve "язык: русский" → "ru", all "português brasil" variants
    // → "pt-BR" (NOT "pt"), "deutsch fränkisch" → "frk" (NOT "de"), etc.
    // Keys are stored lowercased + single-space-normalised (same normalisation
    // applied at lookup time).
    // -------------------------------------------------------------------------
    private static final Map<String, String> ALIAS;
    static {
        Map<String, String> a = new HashMap<>();

        a.put("язык: русский", "ru");
        a.put("язык: ру", "ru");
        a.put("русский", "ru");
        a.put("rus", "ru");
        a.put("ру", "ru");

        // English variants / typos
        a.put("american english", "en");
        a.put("british english", "en");
        a.put("english uk", "en");
        a.put("engilsh", "en");
        a.put("english/", "en");
        a.put("engilsh uk", "en");
        a.put("englsih", "en");
        a.put("английский", "en");

        // Spanish variants / typos
        a.put("español internacional", "es");
        a.put("español colombia", "es");
        a.put("castellano. español", "es");
        a.put("castellano", "es");
        a.put("espanish", "es");
        a.put("espaňol", "es");
        a.put("spain", "es");
        a.put("#spanish", "es");

        //code 2 letters checked ok
        a.put("español mexico", "mx");
        a.put("español - latinoamerica", "mx");
        a.put("español argentina", "ar");
        a.put("español chile", "cl");
        a.put("español peruano", "pe");
        a.put("español ecuador", "ec");
        a.put("español paraguay", "py");

        a.put("brazilian portuguese", "br");
        a.put("português brasil", "br");
        a.put("português (brasil)", "br");
        a.put("portugues do brasil", "br");
        a.put("portugues brasil", "br");
        a.put("português (br)", "br");
        a.put("portugues do braasil", "br");
        a.put("brasil", "br");
        a.put("pt-br", "br");

        // Romanian (sometimes the country name "romania" is used)
        a.put("romania", "ro");
        a.put("româna", "ro");
        a.put("românä", "ro");
        a.put("moldovian", "mol"); // Moldovan → flag_md via MAP

        // German variants / typos
        a.put("deu", "de");
        a.put("gernan", "de");
        a.put("deutch", "de");
        a.put("norddeutsch", "de");
        a.put("schweizerdeutsch", "gsw");

        // Turkish variants
        a.put("türkisch", "tr");
        a.put("turkçe", "tr");
        a.put("tr", "tr");

        // Arabic variants
        a.put("العربية", "ar");
        a.put("عربي", "ar");
        a.put("arabic.", "ar");

        // French
        a.put("francaise", "fr");
        a.put("français", "fr");
        a.put("franch", "fr");

        // Ukrainian typo
        a.put("ukranian", "uk");

        // Frankish — kept separate from plain German ("de").
        // "frk" is the ISO 639-3 code; the flag is flag_franken (already in MAP).
        a.put("deutsch fränkisch", "frk");

        // Country names used as language labels
        a.put("estonia", "et");
        a.put("nederland", "nl");
        a.put("china", "zh");
        a.put("montenegro", "cnr"); // Montenegrin → flag_me via MAP

        // Other language name variants / non-Latin scripts
        a.put("galego", "gl");    // Galician (in Galician)
        a.put("kurdi", "ku");     // Kurdish (in Kurdish)
        a.put("shqip", "sq");     // Albanian (in Albanian)
        a.put("česky", "cs");     // Czech (in Czech)
        a.put("slovenski", "sl"); // Slovenian (in Slovenian)

        // Normalize all keys: lowercase + collapse internal whitespace
        Map<String, String> normalized = new HashMap<>();
        for (Map.Entry<String, String> e : a.entrySet()) {
            normalized.put(normaliseKey(e.getKey()), e.getValue());
        }
        ALIAS = Collections.unmodifiableMap(normalized);
    }

    /** Lowercase + collapse runs of whitespace to a single space + trim. */
    private static String normaliseKey(String s) {
        return s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /**
     * Resolves a language name (from the radio-browser API) to a grouping code,
     * even when the API didn't supply one.
     *
     * Standard ISO 639-1 codes are used for typos / non-English names of the same
     * language (e.g. "язык: русский" → "ru", "engilsh" → "en").
     *
     * Distinct regional varieties get their own code so they are NOT merged with
     * the parent language (e.g. "brazilian portuguese" → "pt-BR", not "pt";
     * "deutsch fränkisch" → "frk", not "de").
     *
     * Lookup order:
     *  1. ALIAS map  (typos, non-English labels, regional variants)
     *  2. Main MAP   (LanguageMapper entries that have a twoLetterCode)
     *
     * @param name the raw language name (e.g. "язык: русский", "português brasil")
     * @return grouping code (e.g. "ru", "pt-BR"), or {@code null} if unresolvable
     */
    public static String resolveIso639(String name) {
        if (name == null || name.isEmpty()) return null;
        String key = normaliseKey(name);
        // 1. Alias map (handles typos, non-English, regional)
        String code = ALIAS.get(key);
        if (code != null) return code;
        // 2. Main MAP fallback (e.g. "bahasa indonesia" → "id")
        Mapping m = MAP.get(key);
        if (m != null && !m.twoLetterCode.isEmpty()) return m.twoLetterCode;
        return null;
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


    public static int getFlagFromName(String keyName) {
        if (keyName == null || keyName.isEmpty()) return 0;
        String wanted = keyName.trim().toLowerCase(Locale.ROOT);

        for (Map.Entry<String, Mapping> e : MAP.entrySet()) {
            if (wanted.equals(e.getKey())) {
                return e.getValue().flagRes;  // english name as stored in MAP keys
            }
        }
        return 0;
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
