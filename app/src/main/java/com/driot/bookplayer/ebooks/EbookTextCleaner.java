package com.driot.bookplayer.ebooks;

import com.driot.bookplayer.global.Option;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

/**
 * Removes in-text reference markers (e.g. [1], [8]) and footnote/endnote blocks
 * from ebook text
 * so that TTS and reading flow are not interrupted by "figure 8" or footnote
 * explanations.
 */
public final class EbookTextCleaner {

    private EbookTextCleaner() {
    }

    /**
     * If the "remove references" option is enabled, strips reference markers and
     * footnote blocks
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
     * Removes (1) footnote/endnote blocks (lines or blocks like "[8] :
     * explanation..." typically at
     * end of page/chapter), and (2) in-text citation markers like [1], [8], [12].
     */
    public static String removeReferences(String text) {
        String returnText;
        if (text == null || text.isEmpty())
            return text;

        // 1) Remove footnote/endnote blocks: lines (or multi-line blocks) that start
        // with "[digits] :" or "[Note digits] "
        // and run until the next such line or end of string. (?im) = case-insensitive +
        // multiline.
        // Pattern: Start of line, Optional whitespace, [, optional "Note / Footnote /
        // etc", digits, ], optional punctuation/spaces
        String footnoteBlockPattern = "(?im)^\\s*\\[(?:Note|Footnote|Endnote|Reference)?\\s*\\d+\\]\\s*[:.]?\\s*.*?(?=(?:^\\s*\\[(?:Note|Footnote|Endnote|Reference)?\\s*\\d+\\]\\s*[:.]?\\s*)|\\z)";
        String step1 = text.replaceAll(footnoteBlockPattern, "");

        // 2) Remove in-text reference markers like [1], [Note 8], [Footnote 123]
        String step2 = step1.replaceAll("(?i)\\[(?:Note|Footnote|Endnote|Reference)?\\s*\\d+\\]", "");

        // 3) Collapse leftover multiple blank lines and trim
        returnText = step2.replaceAll("\\n{3,}", "\n\n").trim();
        myLog("removing - refs size : " + text.length() + " => " + returnText.length());
        return returnText;
    }
}
