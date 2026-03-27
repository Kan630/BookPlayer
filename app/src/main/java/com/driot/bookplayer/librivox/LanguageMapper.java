package com.driot.bookplayer.librivox;

import com.driot.bookplayer.R;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps a language name to:
 *  - three-letter code (ISO 639-3, useful for Librivox API / internal)
 *  - two-letter code  (ISO 639-1 when available; also used as grouping key for radio cards)
 *  - flag resource id (R.drawable.*)
 *
 * Both canonical language names ("english", "russian") and alternate/alias names
 * (typos, non-English labels, regional variants) live in the same MAP, so there is
 * only one place to edit.
 *
 * Keys are matched after: trim + lowercase + collapse-whitespace (see normaliseKey).
 *
 * If a name is unknown, getMapping() returns a fallback with empty codes + flag_globe.
 */
public final class LanguageMapper {

    private LanguageMapper() {}

    public static final class Mapping {
        public final String threeLetterCode; // iso 639-3 e.g. "eng"
        public final String twoLetterCode;   // iso 639-1, e.g. "en";
        public final int flagRes;            // R.drawable.flag_*

        public Mapping(String threeLetterCode, String twoLetterCode, int flagRes) {
            this.threeLetterCode = threeLetterCode == null ? "" : threeLetterCode;
            this.twoLetterCode   = twoLetterCode   == null ? "" : twoLetterCode;
            this.flagRes         = flagRes;
        }
    }

    private static final Mapping FALLBACK = new Mapping("", "", R.drawable.flag_globe);

    /** Lowercase + trim + collapse runs of whitespace to a single space. */
    private static String normaliseKey(String s) {
        return s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static final Map<String, Mapping> MAP;
    static {
        Map<String, Mapping> m = new HashMap<>();

        // ── Canonical entries ─────────────────────────────────────────────────

        m.put("english",    new Mapping("eng", "en", R.drawable.flag_uk));
        m.put("french",     new Mapping("fre", "fr", R.drawable.flag_fr));
        m.put("spanish",    new Mapping("spa", "es", R.drawable.flag_es));
        m.put("german",     new Mapping("deu", "de", R.drawable.flag_de));
        m.put("italian",    new Mapping("ita", "it", R.drawable.flag_it));
        m.put("portuguese", new Mapping("por", "pt", R.drawable.flag_pt));
        m.put("dutch",      new Mapping("nld", "nl", R.drawable.flag_nl));
        m.put("russian",    new Mapping("rus", "ru", R.drawable.flag_ru));
        m.put("arabic",     new Mapping("ara", "ar", R.drawable.flag_sa));
        m.put("hindi",      new Mapping("hin", "hi", R.drawable.flag_in));
        m.put("bengali",    new Mapping("ben", "bn", R.drawable.flag_in));
        m.put("turkish",    new Mapping("tur", "tr", R.drawable.flag_tr));
        m.put("polish",     new Mapping("pol", "pl", R.drawable.flag_pl));
        m.put("czech",      new Mapping("ces", "cs", R.drawable.flag_cz));
        m.put("greek",      new Mapping("ell", "el", R.drawable.flag_gr));
        m.put("hebrew",     new Mapping("heb", "he", R.drawable.flag_il));
        m.put("yiddish",    new Mapping("yid", "yi", R.drawable.flag_il));
        m.put("romanian",   new Mapping("ron", "ro", R.drawable.flag_ro));
        m.put("hungarian",  new Mapping("hun", "hu", R.drawable.flag_hu));
        m.put("ukrainian",  new Mapping("ukr", "uk", R.drawable.flag_ua));
        m.put("bosnian",    new Mapping("",    "ba", R.drawable.flag_ba));
        m.put("albanian",   new Mapping("sqi", "sq", R.drawable.flag_al));

        m.put("slovenian",    new Mapping("slv", "sl", R.drawable.flag_si));
        m.put("slovak",       new Mapping("slk", "sk", R.drawable.flag_sk));
        m.put("bulgarian",    new Mapping("bul", "bg", R.drawable.flag_bg));
        m.put("croatian",     new Mapping("hrv", "hr", R.drawable.flag_hr));
        m.put("serbian",      new Mapping("srp", "sr", R.drawable.flag_rs));
        m.put("macedonian",   new Mapping("mkd", "mk", R.drawable.flag_mk));
        m.put("estonian",     new Mapping("est", "et", R.drawable.flag_ee));
        m.put("latvian",      new Mapping("lav", "lv", R.drawable.flag_lv));
        m.put("lithuanian",   new Mapping("lit", "lt", R.drawable.flag_lt));
        m.put("luxembourgish",new Mapping("ltz", "lb", R.drawable.flag_lu));
        m.put("français - lëtzebuergesch", new Mapping("", "lu", R.drawable.flag_lu));


        m.put("swedish",          new Mapping("swe", "sv",  R.drawable.flag_se));
        m.put("norwegian",        new Mapping("nor", "no",  R.drawable.flag_no));
        m.put("danish",           new Mapping("dan", "da",  R.drawable.flag_dk));
        m.put("finnish",          new Mapping("fin", "fi",  R.drawable.flag_fi));
        m.put("icelandic",        new Mapping("isl", "is",  R.drawable.flag_is));
        m.put("norwegian nynorsk",new Mapping("nno", "nn",  R.drawable.flag_no));
        m.put("nynorsk",          new Mapping("nno", "nn",  R.drawable.flag_no));
        m.put("norsk",            new Mapping("nno", "nn",  R.drawable.flag_no));
        m.put("old norse",        new Mapping("non", "",    R.drawable.flag_no));
        m.put("faroese",          new Mapping("",    "",    R.drawable.flag_fo));

        m.put("walloon",          new Mapping("",    "wa",  R.drawable.flag_be));

        m.put("indonesian",      new Mapping("ind", "id", R.drawable.flag_id));
        m.put("javanese",        new Mapping("jav", "",   R.drawable.flag_id));
        m.put("sundanese",       new Mapping("sun", "",   R.drawable.flag_id));
        m.put("minangkabau",     new Mapping("min", "",   R.drawable.flag_id));
        m.put("acehnese",        new Mapping("ace", "",   R.drawable.flag_id));
        m.put("buginese",        new Mapping("bug", "",   R.drawable.flag_id));
        m.put("balinese",        new Mapping("ban", "",   R.drawable.flag_id));
        m.put("bahasa indonesia",new Mapping("idn", "id", R.drawable.flag_id));
        m.put("old javanese",    new Mapping("jav", "",   R.drawable.flag_id));
        m.put("old sundanese",   new Mapping("",    "",   R.drawable.flag_id));

        // "tl" groups tagalog + filipino (same language)
        m.put("tagalog",     new Mapping("tgl", "tl", R.drawable.flag_ph));
        m.put("filipino",    new Mapping("",    "tl", R.drawable.flag_ph));
        m.put("kapampangan", new Mapping("",    "",   R.drawable.flag_ph));
        m.put("ileoko",      new Mapping("",    "",   R.drawable.flag_ph));

        m.put("cantonese chinese",new Mapping("yue", "",   R.drawable.flag_cn));
        m.put("cantonese",        new Mapping("yue", "",   R.drawable.flag_cn));
        m.put("mandarin",         new Mapping("cmn", "",   R.drawable.flag_cn));
        m.put("chinese",          new Mapping("zho", "zh", R.drawable.flag_cn));
        m.put("hokkien",          new Mapping("",    "",   R.drawable.flag_tw));
        m.put("japanese",         new Mapping("jpn", "ja", R.drawable.flag_jp));
        m.put("korean",           new Mapping("kor", "ko", R.drawable.flag_kr));
        m.put("vietnamese",       new Mapping("vie", "vi", R.drawable.flag_vn));
        m.put("malay",            new Mapping("msa", "ms", R.drawable.flag_my));

        m.put("maori",      new Mapping("mri", "mi", R.drawable.flag_nz));
        m.put("gamilaraay", new Mapping("",    "",   R.drawable.flag_au));

        m.put("nahuatl",    new Mapping("", "", R.drawable.flag_mx));
        m.put("maya",       new Mapping("", "", R.drawable.flag_mx));

        m.put("azerbaijani",new Mapping("aze", "az", R.drawable.flag_az));
        m.put("kazakh",     new Mapping("kaz", "kk", R.drawable.flag_kz));
        m.put("georgian",   new Mapping("",    "ge", R.drawable.flag_ge));
        m.put("kurdish",    new Mapping("kur", "ku", R.drawable.flag_iq));

        m.put("persian/farsi", new Mapping("fas", "fa", R.drawable.flag_ir));
        m.put("farsi",         new Mapping("fas", "fa", R.drawable.flag_ir));
        m.put("persian",       new Mapping("fas", "fa", R.drawable.flag_ir));
        m.put("sindhi",        new Mapping("snd", "sd", R.drawable.flag_pk));
        m.put("urdu",          new Mapping("urd", "ur", R.drawable.flag_pk));

        m.put("sanskrit",   new Mapping("san", "sa", R.drawable.flag_in));
        m.put("punjabi",    new Mapping("pan", "",   R.drawable.flag_in));
        m.put("rajasthani", new Mapping("",    "",   R.drawable.flag_in));
        m.put("tamil",      new Mapping("tam", "ta", R.drawable.flag_in));
        m.put("telugu",     new Mapping("tel", "te", R.drawable.flag_in));
        m.put("marathi",    new Mapping("mar", "mr", R.drawable.flag_in));
        m.put("assamese",   new Mapping("asm", "as", R.drawable.flag_in));
        m.put("oriya",      new Mapping("ori", "or", R.drawable.flag_in));
        m.put("braj",       new Mapping("bra", "",   R.drawable.flag_in));
        m.put("garo",       new Mapping("",    "",   R.drawable.flag_in));
        m.put("khasi",      new Mapping("",    "",   R.drawable.flag_in));
        m.put("bangla",     new Mapping("",    "",   R.drawable.flag_bd));
        m.put("nepali",     new Mapping("",    "",   R.drawable.flag_np));

        m.put("sinhala",    new Mapping("sin", "si", R.drawable.flag_lk));

        m.put("creole",         new Mapping("", "", R.drawable.flag_ht));
        m.put("haitian creole", new Mapping("", "", R.drawable.flag_ht));

        m.put("afrikaans",  new Mapping("afr", "af", R.drawable.flag_za));
        m.put("dholuo/luo", new Mapping("luo", "",   R.drawable.flag_ke));
        m.put("luganda",    new Mapping("uga", "ug", R.drawable.flag_ug));
        m.put("amharic",    new Mapping("",    "",   R.drawable.flag_et));

        m.put("latin",          new Mapping("lat", "la", R.drawable.flag_spqr));
        m.put("old english",    new Mapping("ang", "",   R.drawable.flag_uk));
        m.put("middle english", new Mapping("enm", "",   R.drawable.flag_uk));
        m.put("frisian",        new Mapping("fry", "fy", R.drawable.flag_de));
        m.put("frysk",          new Mapping("fry", "fy", R.drawable.flag_de));
        m.put("mayan languages",new Mapping("",    "",   R.drawable.flag_mx));
        m.put("greek, ancient", new Mapping("grc", "",   R.drawable.flag_gr));
        m.put("ancient greek",  new Mapping("grc", "",   R.drawable.flag_gr));
        m.put("church slavonic",new Mapping("chu", "",   R.drawable.no_flag));
        m.put("old tupi",       new Mapping("",    "",   R.drawable.no_flag));
        m.put("iroquoian",      new Mapping("",    "",   R.drawable.no_flag));
        m.put("north american indian (undetermined dialect)", new Mapping("", "", R.drawable.no_flag));

        // ── Radio-browser additions (from flag audit) ─────────────────────────

        m.put("swiss german",   new Mapping("gsw", "",    R.drawable.flag_ch));
        m.put("belarusian",     new Mapping("bel", "be",  R.drawable.flag_by));
        m.put("low german",     new Mapping("nds", "",    R.drawable.flag_de));
        m.put("mongolian",      new Mapping("mon", "mn",  R.drawable.flag_mn));
        m.put("bambara",        new Mapping("bam", "bm",  R.drawable.flag_ml));
        m.put("tatar",          new Mapping("tat", "tt",  R.drawable.flag_ru));
        m.put("bashkir",        new Mapping("bak", "ba",  R.drawable.flag_ru));
        m.put("hausa",          new Mapping("hau", "ha",  R.drawable.flag_ng));
        m.put("uzbek",          new Mapping("uzb", "uz",  R.drawable.flag_uz));
        m.put("hakka",          new Mapping("hak", "",    R.drawable.flag_cn));
        m.put("odia",           new Mapping("ori", "or",  R.drawable.flag_in));
        m.put("bhojpuri",       new Mapping("",    "",    R.drawable.flag_in));
        m.put("uyghur",         new Mapping("uig", "ug",  R.drawable.flag_cn));
        m.put("montenegrin",    new Mapping("cnr", "",    R.drawable.flag_me));
        m.put("moldovian",      new Mapping("mol", "",    R.drawable.flag_md));
        m.put("tunisian",       new Mapping("",    "",    R.drawable.flag_tn));
        m.put("lusoga",         new Mapping("",    "",    R.drawable.flag_ug));
        m.put("cebuano",        new Mapping("ceb", "",    R.drawable.flag_ph));
        m.put("ilocano",        new Mapping("ilo", "",    R.drawable.flag_ph));
        m.put("isizulu",        new Mapping("zul", "zu",  R.drawable.flag_za));
        m.put("sepedi",         new Mapping("",    "",    R.drawable.flag_za));
        m.put("xitsonga",       new Mapping("tso", "ts",  R.drawable.flag_za));
        m.put("kiswahili",      new Mapping("swa", "sw",  R.drawable.flag_tz));
        m.put("kyrgyz",         new Mapping("kir", "ky",  R.drawable.flag_kg));
        m.put("papiamentu",     new Mapping("pap", "",    R.drawable.flag_cw));
        m.put("sorbian",        new Mapping("hsb", "",    R.drawable.flag_de));
        m.put("willemstad",     new Mapping("pap", "",    R.drawable.flag_cw));

        // ── Alias entries ─────────────────────────────────────────────────────
        // twoLetterCode must match the canonical entry so mergeByIso groups them.
        // flagRes is the direct drawable — no intermediate "flag code" string needed.

        // Russian
        m.put("язык: русский", new Mapping("", "ru", R.drawable.flag_ru));
        m.put("язык: ру",      new Mapping("", "ru", R.drawable.flag_ru));
        m.put("русский",       new Mapping("", "ru", R.drawable.flag_ru));
        m.put("rus",           new Mapping("", "ru", R.drawable.flag_ru));
        m.put("ру",            new Mapping("", "ru", R.drawable.flag_ru));

        // English
        m.put("american english", new Mapping("", "en", R.drawable.flag_us));
        m.put("british english",  new Mapping("", "en", R.drawable.flag_uk));
        m.put("english uk",       new Mapping("", "en", R.drawable.flag_uk));
        m.put("english/",         new Mapping("", "en", R.drawable.flag_uk));
        m.put("engilsh",          new Mapping("", "en", R.drawable.flag_uk));
        m.put("engilsh uk",       new Mapping("", "en", R.drawable.flag_uk));
        m.put("englsih",          new Mapping("", "en", R.drawable.flag_uk));
        m.put("английский",       new Mapping("", "en", R.drawable.flag_uk));
        m.put("ingles español",   new Mapping("", "en", R.drawable.flag_uk));

        // Spanish (generic — groups with "spanish")
        m.put("español internacional",  new Mapping("", "es", R.drawable.flag_es));
        m.put("castellano. español",    new Mapping("", "es", R.drawable.flag_es));
        m.put("castellano",             new Mapping("", "es", R.drawable.flag_es));
        m.put("castilian",              new Mapping("", "es", R.drawable.flag_es));
        m.put("espanish",               new Mapping("", "es", R.drawable.flag_es));
        m.put("espaňol",                new Mapping("", "es", R.drawable.flag_es));
        m.put("spain",                  new Mapping("", "es", R.drawable.flag_es));
        m.put("#spanish",               new Mapping("", "es", R.drawable.flag_es));
        m.put("español - latinoamerica", new Mapping("", "", R.drawable.flag_es));

        // Spanish regional (each gets its own code to stay separate from each other and from "es")
        m.put("español mexico",          new Mapping("", "", R.drawable.flag_mx));
        m.put("español colombia",       new Mapping("", "", R.drawable.flag_co));
        m.put("español argentina",       new Mapping("", "", R.drawable.flag_ar));
        m.put("español chile",           new Mapping("", "", R.drawable.flag_cl));
        m.put("español peruano",         new Mapping("", "", R.drawable.flag_pe));
        m.put("español ecuador",         new Mapping("", "", R.drawable.flag_ec));
        m.put("español paraguay",        new Mapping("", "", R.drawable.flag_py));
        m.put("asuncíon",                new Mapping("", "", R.drawable.flag_py));

        // Brazilian Portuguese aliases (group under "ptbr")
        m.put("brazilian portuguese",  new Mapping("", "", R.drawable.flag_br));
        m.put("português brasil",      new Mapping("", "", R.drawable.flag_br));
        m.put("português (brasil)",    new Mapping("", "", R.drawable.flag_br));
        m.put("portugues do brasil",   new Mapping("", "", R.drawable.flag_br));
        m.put("portugues brasil",      new Mapping("", "", R.drawable.flag_br));
        m.put("português (br)",        new Mapping("", "", R.drawable.flag_br));
        m.put("portugues do braasil",  new Mapping("", "", R.drawable.flag_br));
        m.put("brasil",                new Mapping("", "", R.drawable.flag_br));
        m.put("pt-br",                 new Mapping("", "", R.drawable.flag_br));

        // Romanian
        m.put("romania", new Mapping("", "ro", R.drawable.flag_ro));
        m.put("româna",  new Mapping("", "ro", R.drawable.flag_ro));
        m.put("românä",  new Mapping("", "ro", R.drawable.flag_ro));

        // German
        m.put("deu",         new Mapping("", "de", R.drawable.flag_de));
        m.put("gernan",      new Mapping("", "de", R.drawable.flag_de));
        m.put("deutch",      new Mapping("", "de", R.drawable.flag_de));
        m.put("norddeutsch", new Mapping("", "de", R.drawable.flag_de));

        // Swiss German alias (groups with "swiss german" → "gsw" via threeLetterCode)
        m.put("schweizerdeutsch", new Mapping("gsw", "", R.drawable.flag_ch));

        // Turkish
        m.put("türkisch", new Mapping("", "tr", R.drawable.flag_tr));
        m.put("turkçe",   new Mapping("", "tr", R.drawable.flag_tr));
        m.put("tr",       new Mapping("", "tr", R.drawable.flag_tr));

        // Arabic
        m.put("العربية", new Mapping("", "ar", R.drawable.flag_sa));
        m.put("عربي",    new Mapping("", "ar", R.drawable.flag_sa));
        m.put("arabic.", new Mapping("", "ar", R.drawable.flag_sa));

        // French
        m.put("francaise", new Mapping("", "fr", R.drawable.flag_fr));
        m.put("français",  new Mapping("", "fr", R.drawable.flag_fr));
        m.put("franch",    new Mapping("", "fr", R.drawable.flag_fr));

        // Ukrainian typo
        m.put("ukranian", new Mapping("", "uk", R.drawable.flag_ua));

        // Country names used as language labels
        m.put("estonia",    new Mapping("", "et",  R.drawable.flag_ee));  // → estonian
        m.put("nederland",  new Mapping("", "nl",  R.drawable.flag_nl));  // → dutch
        m.put("china",      new Mapping("", "zh",  R.drawable.flag_cn));  // → chinese
        m.put("montenegro", new Mapping("cnr", "", R.drawable.flag_me));  // → montenegrin

        m.put("shqip",     new Mapping("sqi", "sq", R.drawable.flag_al));       // Albanian
        m.put("česky",     new Mapping("ces", "cs", R.drawable.flag_cz));       // Czech
        m.put("slovenski", new Mapping("slv", "sl", R.drawable.flag_si));       // Slovenian

        // Special non-country / regional flag
        m.put("multilingual",     new Mapping("mul", "",    R.drawable.flag_globe));
        m.put("galego",           new Mapping("glg", "gl",  R.drawable.flag_galician));
        m.put("galician",         new Mapping("glg", "gl",  R.drawable.flag_galician));
        m.put("gaelic, scottish", new Mapping("gla", "gd",  R.drawable.flag_scotland));
        m.put("gaelic",           new Mapping("gla", "gd",  R.drawable.flag_scotland));
        m.put("flammish",         new Mapping("",    "",    R.drawable.flag_flanders));
        m.put("flemish",          new Mapping("",    "",    R.drawable.flag_flanders));
        m.put("kurdi",            new Mapping("kur", "ku",  R.drawable.flag_kurd));
        m.put("romani",           new Mapping("rom", "",    R.drawable.flag_romani));
        m.put("breton",           new Mapping("bre", "br",  R.drawable.flag_breton));
        m.put("euskera",          new Mapping("eus", "eu",  R.drawable.flag_basque));
        m.put("catalan",          new Mapping("cat", "ca",  R.drawable.flag_catalan));
        m.put("basque",           new Mapping("eus", "eu",  R.drawable.flag_basque));
        m.put("deutsch fränkisch",new Mapping("frk", "",    R.drawable.flag_franken));
        m.put("irish",            new Mapping("gle", "ga",  R.drawable.flag_ie));
        m.put("welsh",            new Mapping("cym", "cy",  R.drawable.flag_wales));
        m.put("esperanto",        new Mapping("epo", "",    R.drawable.flag_esperanto));
        m.put("tibetan",          new Mapping("bod", "bo",  R.drawable.flag_tibet));
        m.put("occitan",          new Mapping("oci", "oc",  R.drawable.flag_occitan));


        // ── Normalise all keys ────────────────────────────────────────────────
        Map<String, Mapping> normalized = new HashMap<>();
        for (Map.Entry<String, Mapping> e : m.entrySet()) {
            normalized.put(normaliseKey(e.getKey()), e.getValue());
        }
        MAP = Collections.unmodifiableMap(normalized);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the Mapping for the given language name (case-insensitive, trimmed,
     * whitespace-collapsed). Returns the fallback mapping (globe flag, empty codes)
     * if the name is unknown.
     */
    public static Mapping getMapping(String langEn) {
        if (langEn == null) return FALLBACK;
        Mapping m = MAP.get(normaliseKey(langEn));
        return m == null ? FALLBACK : m;
    }

    /**
     * Resolves a language name from the radio-browser API to a grouping code.
     *
     * Returns twoLetterCode (ISO 639-1) when available; falls back to threeLetterCode
     * (ISO 639-3) for languages that have no ISO 639-1 code (e.g. "swiss german" → "gsw",
     * "deutsch fränkisch" → "frk", "montenegrin" → "cnr").
     * Regional varieties use distinct codes so they are NOT merged with the parent
     * (e.g. "brazilian portuguese" → "ptbr", not "pt").
     *
     * @return grouping code, or {@code null} if unknown
     */
    public static String resolveIso639(String name) {
        if (name == null || name.isEmpty()) return null;
        Mapping m = MAP.get(normaliseKey(name));
        if (m == null) return null;
        if (!m.twoLetterCode.isEmpty()) return m.twoLetterCode;
        if (!m.threeLetterCode.isEmpty()) return m.threeLetterCode;
        return null;
    }

    /**
     * Returns all MAP keys (normalised) whose grouping code equals {@code code}.
     * The grouping code is twoLetterCode when non-empty, otherwise threeLetterCode
     * (mirrors the logic of {@link #resolveIso639}).
     * Used to build the complete list of API variant queries when a language card
     * is clicked. The canonical name is included; callers should filter it out.
     *
     * Example: {@code getAliasNamesForCode("ptbr")} →
     *   ["brazilian portuguese", "português brasil", "português (brasil)", ...]
     */
    public static List<String> getAliasNamesForCode(String code) {
        if (code == null || code.isEmpty()) return new java.util.ArrayList<>();
        List<String> result = new java.util.ArrayList<>();
        for (Map.Entry<String, Mapping> e : MAP.entrySet()) {
            Mapping m = e.getValue();
            String primary = !m.twoLetterCode.isEmpty() ? m.twoLetterCode : m.threeLetterCode;
            if (code.equals(primary)) result.add(e.getKey());
        }
        return result;
    }

    /**
     * Returns the flag drawable for a name key (case-insensitive, trimmed).
     * Returns 0 if not found.
     */
    public static int getFlagFromName(String keyName) {
        if (keyName == null || keyName.isEmpty()) return 0;
        Mapping m = MAP.get(normaliseKey(keyName));
        return m != null ? m.flagRes : 0;
    }

    /**
     * Returns the English language name for a three-letter ISO 639-3 code.
     * Returns null if not found.
     */
    public static String getNameFromThreeLetter(String code3) {
        if (code3 == null || code3.isEmpty()) return null;
        String wanted = code3.trim().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Mapping> e : MAP.entrySet()) {
            if (wanted.equals(e.getValue().threeLetterCode)) return e.getKey();
        }
        return null;
    }

    /**
     * Returns the English language name for an ISO 639-1 two-letter code.
     * Prefers canonical entries (those with a non-empty threeLetterCode).
     * Returns the code itself if not found.
     */
    public static String getNameFromTwoLetters(String code2) {
        if (code2 == null || code2.isEmpty()) return null;
        String wanted = code2.trim().toLowerCase(Locale.ROOT);
        String anyResult = null;
        for (Map.Entry<String, Mapping> e : MAP.entrySet()) {
            if (wanted.equals(e.getValue().twoLetterCode)) {
                if (!e.getValue().threeLetterCode.isEmpty()) return e.getKey(); // canonical preferred
                if (anyResult == null) anyResult = e.getKey();
            }
        }
        return anyResult != null ? anyResult : code2;
    }
}
