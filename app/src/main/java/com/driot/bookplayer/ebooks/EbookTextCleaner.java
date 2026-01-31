package com.driot.bookplayer.ebooks;

import com.driot.bookplayer.global.Option;

/**
 * Removes in-text reference markers (e.g. [1], [8]) and footnote/endnote blocks from ebook text
 * so that TTS and reading flow are not interrupted by "figure 8" or footnote explanations.
 */
public final class EbookTextCleaner {

    private EbookTextCleaner() {}

    /**
     * If the "remove references" option is enabled, strips reference markers and footnote blocks
     * from the given text. Otherwise returns the text unchanged.
     */
    public static String removeReferencesIfEnabled(String text) {
        if (text == null || text.isEmpty())
            return text;
        if (!Option.getEbookRemoveReferences())
            return text;
        return removeReferences(text);
    }

    /**
     * Removes (1) footnote/endnote blocks (lines or blocks like "[8] : explanation..." typically at
     * end of page/chapter), and (2) in-text citation markers like [1], [8], [12].
     */
    public static String removeReferences(String text) {
        if (text == null || text.isEmpty())
            return text;

        // 1) Remove footnote/endnote blocks: lines (or multi-line blocks) that start with "[digits] :" or "[digits] "
        //    and run until the next such line or end of string. (?ms) = multiline + DOTALL so . matches newlines.
        String footnoteBlockPattern = "(?ms)^\\s*\\[\\d+\\]\\s*[::]?\\s*.*?(?=^\\s*\\[\\d+\\]\\s*[::]?\\s*|\\z)";
        String step1 = text.replaceAll(footnoteBlockPattern, "");

        // 2) Remove in-text reference markers like [1], [8], [123]
        String step2 = step1.replaceAll("\\[\\d+\\]", "");

        // 3) Collapse leftover multiple blank lines and trim
        return step2.replaceAll("\\n{3,}", "\n\n").trim();
    }
}
