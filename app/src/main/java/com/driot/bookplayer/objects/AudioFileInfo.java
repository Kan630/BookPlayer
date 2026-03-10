package com.driot.bookplayer.objects;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class AudioFileInfo implements Parcelable {
    private final String displayPath;
    private final long duration;
    private final long size;
    private final String contentUri;
    private final Map<String, String> meta;

    // Simple language hint for parsing number words after "chapter" keywords.
    private enum Language {
        UNKNOWN,
        EN,
        FR,
        ES,
        DE,
        IT,
        PT,
        ES_PT // shared "capitulo" keyword (es/pt)
    }

    private static final java.util.Set<String> PREFACE_SINGLE = new java.util.HashSet<>();
    static {
        // normalized, accent-stripped, lowercase versions
        PREFACE_SINGLE.add("preface"); // "préface", "preface"
        PREFACE_SINGLE.add("prologue"); // "prologue", "prologue"
        PREFACE_SINGLE.add("prolog");
        PREFACE_SINGLE.add("introduction");
        PREFACE_SINGLE.add("intro");
        PREFACE_SINGLE.add("prefazione"); // it
        PREFACE_SINGLE.add("prefacio"); // es/pt
        PREFACE_SINGLE.add("prologo"); // "prólogo"
    }

    public AudioFileInfo(String displayPath,
            long duration,
            long size, String contentUri,
            @Nullable Map<String, String> meta) {
        this.displayPath = displayPath;
        this.duration = duration;
        this.size = size;
        this.contentUri = contentUri;
        // defensive copy to avoid external mutation
        this.meta = (meta == null) ? new HashMap<>() : new HashMap<>(meta);
    }

    public String getDisplayPath() {
        return displayPath;
    }

    public long getDuration() {
        return duration;
    }

    public long getSize() {
        return size;
    }

    public String getContentUri() {
        return contentUri;
    }

    public Map<String, String> getMeta() {
        return meta;
    }

    protected AudioFileInfo(Parcel in) {
        displayPath = in.readString();
        duration = in.readLong();
        size = in.readLong();
        contentUri = in.readString();
        int sizeMeta = in.readInt();
        meta = new HashMap<>(sizeMeta);
        for (int i = 0; i < sizeMeta; i++) {
            meta.put(in.readString(), in.readString());
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(displayPath);
        dest.writeLong(duration);
        dest.writeLong(size);
        dest.writeString(contentUri);
        dest.writeInt(meta.size());
        for (Map.Entry<String, String> entry : meta.entrySet()) {
            dest.writeString(entry.getKey());
            dest.writeString(entry.getValue());
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<AudioFileInfo> CREATOR = new Creator<AudioFileInfo>() {
        @Override
        public AudioFileInfo createFromParcel(Parcel in) {
            return new AudioFileInfo(in);
        }

        @Override
        public AudioFileInfo[] newArray(int size) {
            return new AudioFileInfo[size];
        }
    };

    /** Convenience builder from AudioInfo (uses AudioInfo.metadata directly). */
    public static AudioFileInfo fromProbe(AudioInfo ai, @Nullable String displayPathOverride) {
        String shown = (displayPathOverride != null && !displayPathOverride.isEmpty())
                ? displayPathOverride
                : ai.displayName;

        // copy whatever the prober collected (title/artist/album/genre/year, etc.)
        Map<String, String> m = (ai.metadata == null) ? new HashMap<>() : new HashMap<>(ai.metadata);

        return new AudioFileInfo(
                shown,
                ai.durationMs,
                ai.size,
                ai.uri.toString(),
                m);
    }

    public static final Comparator<AudioFileInfo> ALPHANUMERIC_COMPARATOR = new Comparator<AudioFileInfo>() {
        @Override
        public int compare(AudioFileInfo a1, AudioFileInfo a2) {
            String s1 = a1.getDisplayPath();
            String s2 = a2.getDisplayPath();

            String[] arr1 = s1.split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");
            String[] arr2 = s2.split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");

            int i = 0;
            while (i < arr1.length && i < arr2.length) {
                if (arr1[i].equals(arr2[i])) {
                    i++;
                    continue;
                }

                if (isNumeric(arr1[i]) && isNumeric(arr2[i])) {
                    try {
                        long num1 = Long.parseLong(arr1[i]);
                        long num2 = Long.parseLong(arr2[i]);
                        return Long.compare(num1, num2);
                    } catch (NumberFormatException e) {
                        // fallback to string comparison
                    }
                }

                return arr1[i].compareTo(arr2[i]);
            }

            return Integer.compare(arr1.length, arr2.length);
        }

        private boolean isNumeric(String s) {
            return s.matches("\\d+");
        }
    };

    // ---------- SMART, CAUTIOUS CHAPTER SORT ----------
    public static final Comparator<AudioFileInfo> SMART_CHAPTER_COMPARATOR = (a1, a2) -> {
        SortKey k1 = SortKey.of(a1.getDisplayPath());
        SortKey k2 = SortKey.of(a2.getDisplayPath());

        // 1) Preface / introduction etc. get index 0 and thus come first
        if (k1.index == 0 && k2.index != 0)
            return -1;
        if (k2.index == 0 && k1.index != 0)
            return 1;

        // 2) Numbered chapters next (only if we confidently detected an index)
        if (k1.hasIndex != k2.hasIndex)
            return k1.hasIndex ? -1 : 1;

        // 3) When both have an index, sort numerically
        if (k1.hasIndex && k1.index != k2.index)
            return Integer.compare(k1.index, k2.index);

        // 4) Otherwise, fall back to your existing natural comparator
        int nat = ALPHANUMERIC_COMPARATOR.compare(a1, a2);
        if (nat != 0)
            return nat;

        // 5) Final stable tiebreaker
        return k1.normName.compareTo(k2.normName);
    };

    // ---------- Helpers ----------
    private static final class SortKey {
        final boolean hasIndex;
        final int index;
        final String normName;

        private SortKey(boolean hasIndex, int index, String normName) {
            this.hasIndex = hasIndex;
            this.index = index;
            this.normName = normName;
        }

        static SortKey of(String raw) {
            String base = stripExtension(raw);
            String norm = normalize(base); // lowercase, accents removed, separators -> spaces
            Integer idx = detectChapterIndex(norm, base); // null when not confident
            return new SortKey(idx != null, idx != null ? idx : Integer.MAX_VALUE, norm);
        }
    }

    private static String stripExtension(String s) {
        int i = s.lastIndexOf('.');
        return (i >= 0) ? s.substring(0, i) : s;
    }

    private static String normalize(String s) {
        String lower = s.toLowerCase(java.util.Locale.ROOT);
        String noAccents = java.text.Normalizer.normalize(lower, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return noAccents.replaceAll("[\\s._-]+", " ").trim();
    }

    // Confident chapter detection, in a STRICT order to avoid breaking classic
    // sorting.
    private static Integer detectChapterIndex(String norm, String rawBase) {
        // 0) Preface / introduction etc. → index 0
        // This makes "PRÉFACE" come right before "Chapter I".
        if (isPrefaceLike(norm)) {
            return 0;
        }

        // A) Context-aware: “chapter/chapitre/capitulo/kapitel/capitolo” + token(s)
        Integer byContext = detectAfterChapterKeyword(norm);
        if (byContext != null)
            return byContext;

        // B) Leading Arabic number (e.g., "002 intro", "01 - prologue")
        Integer byLeadingDigits = detectLeadingDigits(norm);
        if (byLeadingDigits != null)
            return byLeadingDigits;

        // C) File is ONLY a Roman numeral (e.g., "xiv")
        Integer byRomanOnly = detectRomanOnly(norm);
        if (byRomanOnly != null)
            return byRomanOnly;

        return null; // no confident index found
    }

    // Map chapter keywords -> language hint
    private static final java.util.Map<String, Language> CHAP_KEYWORDS_LANG = new java.util.HashMap<>();
    static {
        // English / French legacy
        CHAP_KEYWORDS_LANG.put("chapter", Language.EN);
        CHAP_KEYWORDS_LANG.put("chapitre", Language.FR);
        CHAP_KEYWORDS_LANG.put("chap", Language.UNKNOWN); // could be FR or EN
        CHAP_KEYWORDS_LANG.put("ch", Language.UNKNOWN); // short, ambiguous

        // Spanish / Portuguese: "capítulo" (normalized -> "capitulo")
        CHAP_KEYWORDS_LANG.put("capitulo", Language.ES_PT);

        // Italian
        CHAP_KEYWORDS_LANG.put("capitolo", Language.IT);

        // German
        CHAP_KEYWORDS_LANG.put("kapitel", Language.DE);
    }

    private static Integer detectAfterChapterKeyword(String norm) {
        // tokenize
        String[] toks = norm.split("\\s+");
        for (int i = 0; i < toks.length; i++) {
            Language lang = CHAP_KEYWORDS_LANG.get(toks[i]);
            if (lang != null) {
                // Look ahead 1–3 tokens to parse numbers safely
                for (int len = 1; len <= 3 && i + len < toks.length; len++) {
                    String candidate = joinTokens(toks, i + 1, i + len);
                    Integer v = parseNumberCandidate(candidate, lang);
                    if (v != null && v > 0)
                        return v;
                }
            }
        }
        return null;
    }

    private static String joinTokens(String[] arr, int start, int end) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i <= end; i++) {
            if (sb.length() > 0)
                sb.append(' ');
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    private static Integer detectLeadingDigits(String norm) {
        // Accept only if the very first token is digits (e.g., "002", "12")
        // so we don't re-interpret random numbers in the middle of filenames.
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d+)\\b").matcher(norm);
        if (m.find()) {
            try {
                long n = Long.parseLong(m.group(1));
                if (n > 0 && n <= Integer.MAX_VALUE)
                    return (int) n;
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private static Integer detectRomanOnly(String norm) {
        // Entire (normalized) name is a single roman token like "xiv"
        if (!norm.matches("^[ivxlcdm]+$"))
            return null;
        int val = parseRomanStrict(norm);
        return (val > 0) ? val : null;
    }

    // Language-aware number/ordinal parsing after a chapter keyword
    private static Integer parseNumberCandidate(String raw, Language lang) {
        if (lang == null)
            lang = Language.UNKNOWN;
        String s = raw.trim();
        if (s.isEmpty())
            return null;

        // 1) Arabic digits (language-independent)
        if (s.matches("\\d+")) {
            try {
                long n = Long.parseLong(s);
                if (n > 0 && n <= Integer.MAX_VALUE)
                    return (int) n;
            } catch (NumberFormatException ignored) {
            }
            return null;
        }

        // Normalize hyphens to spaces for word parsing
        s = s.replace('-', ' ').replaceAll("\\s+", " ").trim();
        if (s.isEmpty())
            return null;

        // 2) Roman numerals (STRICT; only accept canonical) - language-independent
        if (s.matches("[ivxlcdm]+")) {
            int val = parseRomanStrict(s);
            if (val > 0)
                return val;
        }

        // 3) Language-specific words
        switch (lang) {
            case EN: {
                Integer en = parseEnglishNumberOrOrdinal(s);
                if (en != null)
                    return en;
                break;
            }
            case FR: {
                Integer fr = parseFrenchNumberOrOrdinal(s);
                if (fr != null)
                    return fr;
                break;
            }
            case ES: {
                Integer es = parseSpanishNumberOrOrdinal(s);
                if (es != null)
                    return es;
                break;
            }
            case IT: {
                Integer it = parseItalianNumberOrOrdinal(s);
                if (it != null)
                    return it;
                break;
            }
            case PT: {
                Integer pt = parsePortugueseNumberOrOrdinal(s);
                if (pt != null)
                    return pt;
                break;
            }
            case ES_PT: {
                // shared "capitulo" keyword: try Spanish, then Portuguese
                Integer es = parseSpanishNumberOrOrdinal(s);
                if (es != null)
                    return es;
                Integer pt = parsePortugueseNumberOrOrdinal(s);
                if (pt != null)
                    return pt;
                break;
            }
            case DE: {
                Integer de = parseGermanNumberOrOrdinal(s);
                if (de != null)
                    return de;
                break;
            }
            case UNKNOWN: {
                // Backward-compatible fallback (old behavior: EN then FR)
                Integer en = parseEnglishNumberOrOrdinal(s);
                if (en != null)
                    return en;
                Integer fr = parseFrenchNumberOrOrdinal(s);
                if (fr != null)
                    return fr;
                break;
            }
        }

        return null;
    }

    // ---------- Roman parsing (STRICT canonical) ----------
    private static int parseRomanStrict(String s) {
        int n = romanToInt(s);
        if (n <= 0)
            return -1;
        String canonical = intToRoman(n);
        return canonical.equalsIgnoreCase(s) ? n : -1; // accept only canonical
    }

    private static int romanToInt(String s) {
        int sum = 0, prev = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            int v = romanVal(s.charAt(i));
            if (v < prev)
                sum -= v;
            else
                sum += v;
            prev = v;
        }
        return sum;
    }

    private static int romanVal(char c) {
        switch (c) {
            case 'i':
                return 1;
            case 'v':
                return 5;
            case 'x':
                return 10;
            case 'l':
                return 50;
            case 'c':
                return 100;
            case 'd':
                return 500;
            case 'm':
                return 1000;
            default:
                return 0;
        }
    }

    private static String intToRoman(int num) {
        // Uppercase canonical
        int[] vals = { 1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1 };
        String[] nums = { "M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I" };
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vals.length; i++) {
            while (num >= vals[i]) {
                num -= vals[i];
                sb.append(nums[i]);
            }
        }
        return sb.toString();
    }

    // ---------- English number words (1..99, cardinal & ordinal) ----------
    private static final java.util.Map<String, Integer> EN_UNITS = new java.util.HashMap<>();
    private static final java.util.Map<String, Integer> EN_ORD_UNITS = new java.util.HashMap<>();
    private static final java.util.Map<String, Integer> EN_TENS = new java.util.HashMap<>();
    private static final java.util.Map<String, Integer> EN_ORD_TENS = new java.util.HashMap<>();
    static {
        // units
        EN_UNITS.put("one", 1);
        EN_UNITS.put("two", 2);
        EN_UNITS.put("three", 3);
        EN_UNITS.put("four", 4);
        EN_UNITS.put("five", 5);
        EN_UNITS.put("six", 6);
        EN_UNITS.put("seven", 7);
        EN_UNITS.put("eight", 8);
        EN_UNITS.put("nine", 9);
        EN_UNITS.put("ten", 10);
        EN_UNITS.put("eleven", 11);
        EN_UNITS.put("twelve", 12);
        EN_UNITS.put("thirteen", 13);
        EN_UNITS.put("fourteen", 14);
        EN_UNITS.put("fifteen", 15);
        EN_UNITS.put("sixteen", 16);
        EN_UNITS.put("seventeen", 17);
        EN_UNITS.put("eighteen", 18);
        EN_UNITS.put("nineteen", 19);

        // ordinals (irregulars)
        EN_ORD_UNITS.put("first", 1);
        EN_ORD_UNITS.put("second", 2);
        EN_ORD_UNITS.put("third", 3);
        EN_ORD_UNITS.put("fourth", 4);
        EN_ORD_UNITS.put("fifth", 5);
        EN_ORD_UNITS.put("sixth", 6);
        EN_ORD_UNITS.put("seventh", 7);
        EN_ORD_UNITS.put("eighth", 8);
        EN_ORD_UNITS.put("ninth", 9);
        EN_ORD_UNITS.put("tenth", 10);
        EN_ORD_UNITS.put("eleventh", 11);
        EN_ORD_UNITS.put("twelfth", 12);
        EN_ORD_UNITS.put("thirteenth", 13);
        EN_ORD_UNITS.put("fourteenth", 14);
        EN_ORD_UNITS.put("fifteenth", 15);
        EN_ORD_UNITS.put("sixteenth", 16);
        EN_ORD_UNITS.put("seventeenth", 17);
        EN_ORD_UNITS.put("eighteenth", 18);
        EN_ORD_UNITS.put("nineteenth", 19);

        // tens
        EN_TENS.put("twenty", 20);
        EN_TENS.put("thirty", 30);
        EN_TENS.put("forty", 40);
        EN_TENS.put("fifty", 50);
        EN_TENS.put("sixty", 60);
        EN_TENS.put("seventy", 70);
        EN_TENS.put("eighty", 80);
        EN_TENS.put("ninety", 90);

        // ordinal tens
        EN_ORD_TENS.put("twentieth", 20);
        EN_ORD_TENS.put("thirtieth", 30);
        EN_ORD_TENS.put("fortieth", 40);
        EN_ORD_TENS.put("fiftieth", 50);
        EN_ORD_TENS.put("sixtieth", 60);
        EN_ORD_TENS.put("seventieth", 70);
        EN_ORD_TENS.put("eightieth", 80);
        EN_ORD_TENS.put("ninetieth", 90);
    }

    private static Integer parseEnglishNumberOrOrdinal(String s) {
        String[] t = s.split("\\s+");
        if (t.length == 1) {
            String w = t[0];
            if (EN_UNITS.containsKey(w))
                return EN_UNITS.get(w);
            if (EN_ORD_UNITS.containsKey(w))
                return EN_ORD_UNITS.get(w);
            if (EN_TENS.containsKey(w))
                return EN_TENS.get(w);
            if (EN_ORD_TENS.containsKey(w))
                return EN_ORD_TENS.get(w);
            return null;
        }
        if (t.length == 2) {
            Integer tens = EN_TENS.get(t[0]);
            if (tens != null) {
                Integer unit = EN_UNITS.get(t[1]);
                if (unit != null)
                    return tens + unit;
                Integer ordUnit = EN_ORD_UNITS.get(t[1]); // twenty first
                if (ordUnit != null)
                    return tens + ordUnit;
            }
        }
        return null; // keep it strict to avoid mis-detections
    }

    // ---------- French numbers (basic set for chapters) ----------
    private static final java.util.Map<String, Integer> FR_ORD = new java.util.HashMap<>();
    private static final java.util.Map<String, Integer> FR_CARD = new java.util.HashMap<>();
    static {
        // cardinals commonly used after "chapitre"
        FR_CARD.put("un", 1);
        FR_CARD.put("deux", 2);
        FR_CARD.put("trois", 3);
        FR_CARD.put("quatre", 4);
        FR_CARD.put("cinq", 5);
        FR_CARD.put("six", 6);
        FR_CARD.put("sept", 7);
        FR_CARD.put("huit", 8);
        FR_CARD.put("neuf", 9);
        FR_CARD.put("dix", 10);
        FR_CARD.put("onze", 11);
        FR_CARD.put("douze", 12);
        FR_CARD.put("treize", 13);
        FR_CARD.put("quatorze", 14);
        FR_CARD.put("quinze", 15);
        FR_CARD.put("seize", 16);
        FR_CARD.put("dix sept", 17);
        FR_CARD.put("dix huit", 18);
        FR_CARD.put("dix neuf", 19);
        FR_CARD.put("vingt", 20);

        // ordinals (accents removed)
        FR_ORD.put("premier", 1);
        FR_ORD.put("premiere", 1);
        FR_ORD.put("second", 2);
        FR_ORD.put("seconde", 2);
        FR_ORD.put("deuxieme", 2);
        FR_ORD.put("troisieme", 3);
        FR_ORD.put("quatrieme", 4);
        FR_ORD.put("cinquieme", 5);
        FR_ORD.put("sixieme", 6);
        FR_ORD.put("septieme", 7);
        FR_ORD.put("huitieme", 8);
        FR_ORD.put("neuvieme", 9);
        FR_ORD.put("dixieme", 10);
        FR_ORD.put("onzeieme", 11);
        FR_ORD.put("douzieme", 12);
        FR_ORD.put("treizieme", 13);
        FR_ORD.put("quatorzieme", 14);
        FR_ORD.put("quinzieme", 15);
        FR_ORD.put("seizieme", 16);
        FR_ORD.put("dix septieme", 17);
        FR_ORD.put("dix huitieme", 18);
        FR_ORD.put("dix neuvieme", 19);
        FR_ORD.put("vingtieme", 20);
    }

    private static Integer parseFrenchNumberOrOrdinal(String s) {
        // hyphens already normalized to spaces
        if (FR_ORD.containsKey(s))
            return FR_ORD.get(s);
        if (FR_CARD.containsKey(s))
            return FR_CARD.get(s);
        return null;
    }

    // ---------- Spanish (basic 1..20, cardinals & ordinals) ----------
    private static final java.util.Map<String, Integer> ES_CARD = new java.util.HashMap<>();
    private static final java.util.Map<String, Integer> ES_ORD = new java.util.HashMap<>();
    static {
        // cardinals
        ES_CARD.put("uno", 1);
        ES_CARD.put("una", 1);
        ES_CARD.put("dos", 2);
        ES_CARD.put("tres", 3);
        ES_CARD.put("cuatro", 4);
        ES_CARD.put("cinco", 5);
        ES_CARD.put("seis", 6);
        ES_CARD.put("siete", 7);
        ES_CARD.put("ocho", 8);
        ES_CARD.put("nueve", 9);
        ES_CARD.put("diez", 10);
        ES_CARD.put("once", 11);
        ES_CARD.put("doce", 12);
        ES_CARD.put("trece", 13);
        ES_CARD.put("catorce", 14);
        ES_CARD.put("quince", 15);
        ES_CARD.put("dieciseis", 16);
        ES_CARD.put("diecisiete", 17);
        ES_CARD.put("dieciocho", 18);
        ES_CARD.put("diecinueve", 19);
        ES_CARD.put("veinte", 20);
        ES_CARD.put("veintiuno", 21); // just in case

        // ordinals (accents already stripped)
        ES_ORD.put("primero", 1);
        ES_ORD.put("primera", 1);
        ES_ORD.put("segundo", 2);
        ES_ORD.put("segunda", 2);
        ES_ORD.put("tercero", 3);
        ES_ORD.put("tercera", 3);
        ES_ORD.put("cuarto", 4);
        ES_ORD.put("cuarta", 4);
        ES_ORD.put("quinto", 5);
        ES_ORD.put("quinta", 5);
        ES_ORD.put("sexto", 6);
        ES_ORD.put("sexta", 6);
        ES_ORD.put("septimo", 7);
        ES_ORD.put("septima", 7);
        ES_ORD.put("octavo", 8);
        ES_ORD.put("octava", 8);
        ES_ORD.put("noveno", 9);
        ES_ORD.put("novena", 9);
        ES_ORD.put("decimo", 10);
        ES_ORD.put("decima", 10);
    }

    private static Integer parseSpanishNumberOrOrdinal(String s) {
        String[] t = s.split("\\s+");
        if (t.length == 1) {
            String w = t[0];
            Integer v = ES_CARD.get(w);
            if (v != null)
                return v;
            v = ES_ORD.get(w);
            if (v != null)
                return v;
        }
        return null;
    }

    // ---------- Italian (basic 1..20, cardinals & ordinals) ----------
    private static final java.util.Map<String, Integer> IT_CARD = new java.util.HashMap<>();
    private static final java.util.Map<String, Integer> IT_ORD = new java.util.HashMap<>();
    static {
        IT_CARD.put("uno", 1);
        IT_CARD.put("una", 1);
        IT_CARD.put("due", 2);
        IT_CARD.put("tre", 3);
        IT_CARD.put("quattro", 4);
        IT_CARD.put("cinque", 5);
        IT_CARD.put("sei", 6);
        IT_CARD.put("sette", 7);
        IT_CARD.put("otto", 8);
        IT_CARD.put("nove", 9);
        IT_CARD.put("dieci", 10);
        IT_CARD.put("undici", 11);
        IT_CARD.put("dodici", 12);
        IT_CARD.put("tredici", 13);
        IT_CARD.put("quattordici", 14);
        IT_CARD.put("quindici", 15);
        IT_CARD.put("sedici", 16);
        IT_CARD.put("diciassette", 17);
        IT_CARD.put("diciotto", 18);
        IT_CARD.put("diciannove", 19);
        IT_CARD.put("venti", 20);

        IT_ORD.put("primo", 1);
        IT_ORD.put("prima", 1);
        IT_ORD.put("secondo", 2);
        IT_ORD.put("seconda", 2);
        IT_ORD.put("terzo", 3);
        IT_ORD.put("terza", 3);
        IT_ORD.put("quarto", 4);
        IT_ORD.put("quarta", 4);
        IT_ORD.put("quinto", 5);
        IT_ORD.put("quinta", 5);
        IT_ORD.put("sesto", 6);
        IT_ORD.put("sesta", 6);
        IT_ORD.put("settimo", 7);
        IT_ORD.put("settima", 7);
        IT_ORD.put("ottavo", 8);
        IT_ORD.put("ottava", 8);
        IT_ORD.put("nono", 9);
        IT_ORD.put("nona", 9);
        IT_ORD.put("decimo", 10);
        IT_ORD.put("decima", 10);
    }

    private static Integer parseItalianNumberOrOrdinal(String s) {
        String[] t = s.split("\\s+");
        if (t.length == 1) {
            String w = t[0];
            Integer v = IT_CARD.get(w);
            if (v != null)
                return v;
            v = IT_ORD.get(w);
            if (v != null)
                return v;
        }
        return null;
    }

    // ---------- Portuguese (basic 1..20, cardinals & ordinals) ----------
    private static final java.util.Map<String, Integer> PT_CARD = new java.util.HashMap<>();
    private static final java.util.Map<String, Integer> PT_ORD = new java.util.HashMap<>();
    static {
        PT_CARD.put("um", 1);
        PT_CARD.put("uma", 1);
        PT_CARD.put("dois", 2);
        PT_CARD.put("duas", 2);
        PT_CARD.put("tres", 3);
        PT_CARD.put("quatro", 4);
        PT_CARD.put("cinco", 5);
        PT_CARD.put("seis", 6);
        PT_CARD.put("sete", 7);
        PT_CARD.put("oito", 8);
        PT_CARD.put("nove", 9);
        PT_CARD.put("dez", 10);
        PT_CARD.put("onze", 11);
        PT_CARD.put("doze", 12);
        PT_CARD.put("treze", 13);
        PT_CARD.put("quatorze", 14);
        PT_CARD.put("catorze", 14); // both forms
        PT_CARD.put("quinze", 15);
        PT_CARD.put("dezesseis", 16);
        PT_CARD.put("dezasseis", 16);
        PT_CARD.put("dezessete", 17);
        PT_CARD.put("dezassete", 17);
        PT_CARD.put("dezoito", 18);
        PT_CARD.put("dezenove", 19);
        PT_CARD.put("dezanove", 19);
        PT_CARD.put("vinte", 20);

        PT_ORD.put("primeiro", 1);
        PT_ORD.put("primeira", 1);
        PT_ORD.put("segundo", 2);
        PT_ORD.put("segunda", 2);
        PT_ORD.put("terceiro", 3);
        PT_ORD.put("terceira", 3);
        PT_ORD.put("quarto", 4);
        PT_ORD.put("quarta", 4);
        PT_ORD.put("quinto", 5);
        PT_ORD.put("quinta", 5);
        PT_ORD.put("sexto", 6);
        PT_ORD.put("sexta", 6);
        PT_ORD.put("setimo", 7);
        PT_ORD.put("setima", 7);
        PT_ORD.put("oitavo", 8);
        PT_ORD.put("oitava", 8);
        PT_ORD.put("nono", 9);
        PT_ORD.put("nona", 9);
        PT_ORD.put("decimo", 10);
        PT_ORD.put("decima", 10);
    }

    private static Integer parsePortugueseNumberOrOrdinal(String s) {
        String[] t = s.split("\\s+");
        if (t.length == 1) {
            String w = t[0];
            Integer v = PT_CARD.get(w);
            if (v != null)
                return v;
            v = PT_ORD.get(w);
            if (v != null)
                return v;
        }
        return null;
    }

    // ---------- German (basic 1..20, cardinals + simple ordinal patterns)
    // ----------
    private static final java.util.Map<String, Integer> DE_CARD = new java.util.HashMap<>();
    static {
        // normalized (accents stripped: "fünf" -> "funf", "zwölf" -> "zwolf")
        DE_CARD.put("eins", 1);
        DE_CARD.put("ein", 1);
        DE_CARD.put("eine", 1);
        DE_CARD.put("zwei", 2);
        DE_CARD.put("drei", 3);
        DE_CARD.put("vier", 4);
        DE_CARD.put("funf", 5);
        DE_CARD.put("fuenf", 5);
        DE_CARD.put("sechs", 6);
        DE_CARD.put("sieben", 7);
        DE_CARD.put("acht", 8);
        DE_CARD.put("neun", 9);
        DE_CARD.put("zehn", 10);
        DE_CARD.put("elf", 11);
        DE_CARD.put("zwolf", 12);
        DE_CARD.put("zwoelf", 12);
        DE_CARD.put("dreizehn", 13);
        DE_CARD.put("vierzehn", 14);
        DE_CARD.put("funfzehn", 15);
        DE_CARD.put("fuenfzehn", 15);
        DE_CARD.put("sechzehn", 16);
        DE_CARD.put("siebzehn", 17);
        DE_CARD.put("achtzehn", 18);
        DE_CARD.put("neunzehn", 19);
        DE_CARD.put("zwanzig", 20);
    }

    private static Integer parseGermanNumberOrOrdinal(String s) {
        String[] t = s.split("\\s+");
        if (t.length != 1)
            return null;
        String w = t[0];

        // direct cardinal
        Integer card = DE_CARD.get(w);
        if (card != null)
            return card;

        // simple ordinal endings: erste, ersten, erster, erstes...
        // pattern: base ("eins", "zwei", "drei", ...) + "te"/"ste" (+ n/r/s)
        if (w.length() > 3) {
            // strip common ordinal suffix variants
            String base = w;
            base = base.replaceFirst("(sten|sten$)", "");
            base = base.replaceFirst("(ste|ter|tes|ten|te)$", "");
            card = DE_CARD.get(base);
            if (card != null)
                return card;
        }

        return null;
    }

    /**
     * Return true if this looks like a preface/introduction type track,
     * e.g. "PRÉFACE", "01 preface", "Prologue", "Avant-propos", etc.
     * The input is the normalized name (lowercase, accents stripped, separators ->
     * spaces).
     */
    private static boolean isPrefaceLike(String norm) {
        if (norm == null)
            return false;
        String n = norm.trim();
        if (n.isEmpty())
            return false;

        // Drop leading digits: "01 preface", "1 - preface", etc.
        n = n.replaceFirst("^\\d+\\s+", "");

        // Also strip a few generic words that might appear before:
        // e.g. "track 01 preface", "disc 1 preface"
        n = n.replaceFirst("^(track|trk|disc|disque)\\s+\\d+\\s*", "").trim();

        if (n.isEmpty())
            return false;

        String[] toks = n.split("\\s+");
        if (toks.length == 0)
            return false;

        String first = toks[0];

        // Single-word cases: "preface", "prologue", "intro", ...
        if (PREFACE_SINGLE.contains(first))
            return true;

        // "avant propos", "avant-propos" -> normalized to "avant propos"
        if (toks.length >= 2 && "avant".equals(toks[0]) && "propos".equals(toks[1])) {
            return true;
        }

        // Safety: also accept when preface word appears at the beginning of the title
        // like "preface to second edition"
        for (String kw : PREFACE_SINGLE) {
            if (n.startsWith(kw + " ") || n.equals(kw)) {
                return true;
            }
        }
        if (n.startsWith("avant propos"))
            return true;

        return false;
    }

    @Override
    public String toString() {
        return "AudioFileInfo{" +
                "displayPath='" + displayPath + '\'' +
                ", duration=" + duration +
                ", size=" + size +
                ", contentUri='" + contentUri + '\'' +
                ", meta=" + meta +
                '}';
    }
}
