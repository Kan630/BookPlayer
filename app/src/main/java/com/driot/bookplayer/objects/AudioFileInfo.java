package com.driot.bookplayer.objects;

import android.media.MediaMetadataRetriever;

import java.util.Comparator;

public class AudioFileInfo {
    private final String displayPath;
    private final long duration;
    private final String contentUri;

    public AudioFileInfo(String displayPath, long duration, String contentUri) {
        this.displayPath = displayPath;
        this.duration = duration;
        this.contentUri = contentUri;
    }

    public String getDisplayPath() {
        return displayPath;
    }

    public long getDuration() {
        return duration;
    }

    public String getContentUri() { return contentUri; }

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

        // 1) Numbered chapters first (only if we confidently detected an index)
        if (k1.hasIndex != k2.hasIndex) return k1.hasIndex ? -1 : 1;

        // 2) When both have an index, sort numerically
        if (k1.hasIndex && k1.index != k2.index) return Integer.compare(k1.index, k2.index);

        // 3) Otherwise, fall back to your existing natural comparator
        int nat = ALPHANUMERIC_COMPARATOR.compare(a1, a2);
        if (nat != 0) return nat;

        // 4) Final stable tiebreaker
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
            String norm = normalize(base);                   // lowercase, accents removed, separators -> spaces
            Integer idx = detectChapterIndex(norm, base);    // null when not confident
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

    // Confident chapter detection, in a STRICT order to avoid breaking classic sorting.
    private static Integer detectChapterIndex(String norm, String rawBase) {
        // A) Context-aware: “chapter/chapitre/chap/ch” + token(s)
        Integer byContext = detectAfterChapterKeyword(norm);
        if (byContext != null) return byContext;

        // B) Leading Arabic number (e.g., "002 intro", "01 - prologue")
        Integer byLeadingDigits = detectLeadingDigits(norm);
        if (byLeadingDigits != null) return byLeadingDigits;

        // C) File is ONLY a Roman numeral (e.g., "xiv")
        Integer byRomanOnly = detectRomanOnly(norm);
        if (byRomanOnly != null) return byRomanOnly;

        return null; // no confident index found
    }

    private static final java.util.Set<String> CHAP_KEYWORDS = new java.util.HashSet<>();
    static {
        CHAP_KEYWORDS.add("chapter");
        CHAP_KEYWORDS.add("chapitre");
        CHAP_KEYWORDS.add("chap");
        CHAP_KEYWORDS.add("ch");
    }

    private static Integer detectAfterChapterKeyword(String norm) {
        // tokenize
        String[] toks = norm.split("\\s+");
        for (int i = 0; i < toks.length; i++) {
            if (CHAP_KEYWORDS.contains(toks[i])) {
                // Look ahead 1–3 tokens to parse numbers safely
                for (int len = 1; len <= 3 && i + len < toks.length; len++) {
                    String candidate = joinTokens(toks, i + 1, i + len);
                    Integer v = parseNumberCandidate(candidate);
                    if (v != null && v > 0) return v;
                }
            }
        }
        return null;
    }

    private static String joinTokens(String[] arr, int start, int end) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i <= end; i++) {
            if (sb.length() > 0) sb.append(' ');
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
                if (n > 0 && n <= Integer.MAX_VALUE) return (int) n;
            } catch (NumberFormatException ignored) { }
        }
        return null;
    }

    private static Integer detectRomanOnly(String norm) {
        // Entire (normalized) name is a single roman token like "xiv"
        if (!norm.matches("^[ivxlcdm]+$")) return null;
        int val = parseRomanStrict(norm);
        return (val > 0) ? val : null;
    }

    private static Integer parseNumberCandidate(String raw) {
        String s = raw.trim();

        // 1) Arabic digits
        if (s.matches("\\d+")) {
            try {
                long n = Long.parseLong(s);
                if (n > 0 && n <= Integer.MAX_VALUE) return (int) n;
            } catch (NumberFormatException ignored) { }
            return null;
        }

        // Normalize hyphens to spaces for word parsing
        s = s.replace('-', ' ').replaceAll("\\s+", " ").trim();

        // 2) Roman (STRICT; only accept canonical)
        if (s.matches("[ivxlcdm]+")) {
            int val = parseRomanStrict(s);
            if (val > 0) return val;
        }

        // 3) English words (cardinal or ordinal) e.g., "one", "first", "twenty one", "twenty-first"
        Integer en = parseEnglishNumberOrOrdinal(s);
        if (en != null) return en;

        // 4) French ordinals/cardinals near chapter (basic coverage)
        Integer fr = parseFrenchNumberOrOrdinal(s);
        if (fr != null) return fr;

        return null;
    }

    // ---------- Roman parsing (STRICT canonical) ----------
    private static int parseRomanStrict(String s) {
        int n = romanToInt(s);
        if (n <= 0) return -1;
        String canonical = intToRoman(n);
        return canonical.equalsIgnoreCase(s) ? n : -1; // accept only canonical
    }

    private static int romanToInt(String s) {
        int sum = 0, prev = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            int v = romanVal(s.charAt(i));
            if (v < prev) sum -= v; else sum += v;
            prev = v;
        }
        return sum;
    }

    private static int romanVal(char c) {
        switch (c) {
            case 'i': return 1;
            case 'v': return 5;
            case 'x': return 10;
            case 'l': return 50;
            case 'c': return 100;
            case 'd': return 500;
            case 'm': return 1000;
            default:  return 0;
        }
    }

    private static String intToRoman(int num) {
        // Uppercase canonical
        int[]    vals = {1000,900,500,400,100,90, 50,40,10,9, 5,4,1};
        String[] nums = {"M","CM","D","CD","C","XC","L","XL","X","IX","V","IV","I"};
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
    private static final java.util.Map<String,Integer> EN_UNITS = new java.util.HashMap<>();
    private static final java.util.Map<String,Integer> EN_ORD_UNITS = new java.util.HashMap<>();
    private static final java.util.Map<String,Integer> EN_TENS = new java.util.HashMap<>();
    private static final java.util.Map<String,Integer> EN_ORD_TENS = new java.util.HashMap<>();
    static {
        // units
        EN_UNITS.put("one",1); EN_UNITS.put("two",2); EN_UNITS.put("three",3); EN_UNITS.put("four",4);
        EN_UNITS.put("five",5); EN_UNITS.put("six",6); EN_UNITS.put("seven",7); EN_UNITS.put("eight",8);
        EN_UNITS.put("nine",9); EN_UNITS.put("ten",10); EN_UNITS.put("eleven",11); EN_UNITS.put("twelve",12);
        EN_UNITS.put("thirteen",13); EN_UNITS.put("fourteen",14); EN_UNITS.put("fifteen",15); EN_UNITS.put("sixteen",16);
        EN_UNITS.put("seventeen",17); EN_UNITS.put("eighteen",18); EN_UNITS.put("nineteen",19);

        // ordinals (irregulars)
        EN_ORD_UNITS.put("first",1); EN_ORD_UNITS.put("second",2); EN_ORD_UNITS.put("third",3); EN_ORD_UNITS.put("fourth",4);
        EN_ORD_UNITS.put("fifth",5); EN_ORD_UNITS.put("sixth",6); EN_ORD_UNITS.put("seventh",7); EN_ORD_UNITS.put("eighth",8);
        EN_ORD_UNITS.put("ninth",9); EN_ORD_UNITS.put("tenth",10); EN_ORD_UNITS.put("eleventh",11); EN_ORD_UNITS.put("twelfth",12);
        EN_ORD_UNITS.put("thirteenth",13); EN_ORD_UNITS.put("fourteenth",14); EN_ORD_UNITS.put("fifteenth",15);
        EN_ORD_UNITS.put("sixteenth",16); EN_ORD_UNITS.put("seventeenth",17); EN_ORD_UNITS.put("eighteenth",18); EN_ORD_UNITS.put("nineteenth",19);

        // tens
        EN_TENS.put("twenty",20); EN_TENS.put("thirty",30); EN_TENS.put("forty",40); EN_TENS.put("fifty",50);
        EN_TENS.put("sixty",60); EN_TENS.put("seventy",70); EN_TENS.put("eighty",80); EN_TENS.put("ninety",90);

        // ordinal tens
        EN_ORD_TENS.put("twentieth",20); EN_ORD_TENS.put("thirtieth",30); EN_ORD_TENS.put("fortieth",40); EN_ORD_TENS.put("fiftieth",50);
        EN_ORD_TENS.put("sixtieth",60); EN_ORD_TENS.put("seventieth",70); EN_ORD_TENS.put("eightieth",80); EN_ORD_TENS.put("ninetieth",90);
    }

    private static Integer parseEnglishNumberOrOrdinal(String s) {
        String[] t = s.split("\\s+");
        if (t.length == 1) {
            String w = t[0];
            if (EN_UNITS.containsKey(w)) return EN_UNITS.get(w);
            if (EN_ORD_UNITS.containsKey(w)) return EN_ORD_UNITS.get(w);
            if (EN_TENS.containsKey(w)) return EN_TENS.get(w);
            if (EN_ORD_TENS.containsKey(w)) return EN_ORD_TENS.get(w);
            return null;
        }
        if (t.length == 2) {
            Integer tens = EN_TENS.get(t[0]);
            if (tens != null) {
                Integer unit = EN_UNITS.get(t[1]);
                if (unit != null) return tens + unit;
                Integer ordUnit = EN_ORD_UNITS.get(t[1]); // twenty first
                if (ordUnit != null) return tens + ordUnit;
            }
        }
        return null; // keep it strict to avoid mis-detections
    }

    // ---------- French numbers (basic set for chapters) ----------
    private static final java.util.Map<String,Integer> FR_ORD = new java.util.HashMap<>();
    private static final java.util.Map<String,Integer> FR_CARD = new java.util.HashMap<>();
    static {
        // cardinals commonly used after "chapitre"
        FR_CARD.put("un",1); FR_CARD.put("deux",2); FR_CARD.put("trois",3); FR_CARD.put("quatre",4);
        FR_CARD.put("cinq",5); FR_CARD.put("six",6); FR_CARD.put("sept",7); FR_CARD.put("huit",8); FR_CARD.put("neuf",9);
        FR_CARD.put("dix",10); FR_CARD.put("onze",11); FR_CARD.put("douze",12); FR_CARD.put("treize",13); FR_CARD.put("quatorze",14);
        FR_CARD.put("quinze",15); FR_CARD.put("seize",16); FR_CARD.put("dix sept",17); FR_CARD.put("dix huit",18); FR_CARD.put("dix neuf",19);
        FR_CARD.put("vingt",20);

        // ordinals (accents removed)
        FR_ORD.put("premier",1);
        FR_ORD.put("second",2); FR_ORD.put("deuxieme",2);
        FR_ORD.put("troisieme",3); FR_ORD.put("quatrieme",4); FR_ORD.put("cinquieme",5);
        FR_ORD.put("sixieme",6); FR_ORD.put("septieme",7); FR_ORD.put("huitieme",8); FR_ORD.put("neuvieme",9);
        FR_ORD.put("dixieme",10); FR_ORD.put("onzeieme",11); FR_ORD.put("douzieme",12); FR_ORD.put("treizieme",13);
        FR_ORD.put("quatorzieme",14); FR_ORD.put("quinzieme",15); FR_ORD.put("seizieme",16);
        FR_ORD.put("dix septieme",17); FR_ORD.put("dix huitieme",18); FR_ORD.put("dix neuvieme",19);
        FR_ORD.put("vingtieme",20);
    }

    private static Integer parseFrenchNumberOrOrdinal(String s) {
        // hyphens already normalized to spaces
        if (FR_ORD.containsKey(s)) return FR_ORD.get(s);
        if (FR_CARD.containsKey(s)) return FR_CARD.get(s);
        return null;
    }

}
