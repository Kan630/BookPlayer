package com.driot.bookplayer.tts;

public final class TxtCleaner {
    private TxtCleaner() {}

    public static String clean(String raw) {
        if (raw == null) return "";
        // Normalize line endings
        String s = raw.replace("\r\n", "\n").replace("\r", "\n");
        // Remove control chars except \n and \t
        s = s.replaceAll("[\\p{Cntrl}&&[^\n\t]]", "");
        // Collapse >2 blank lines to just one
        s = s.replaceAll("\n{3,}", "\n\n");
        // Trim huge leading/trailing whitespace
        return s.trim();
    }
}
