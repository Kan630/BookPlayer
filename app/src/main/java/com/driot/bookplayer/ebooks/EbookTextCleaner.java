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
        if (!Option.getEbookRemoveReferencesFootnotes())
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

        // 1) Remove footnote/endnote lines: every line that starts with "[digits]" or
        // "[Note digits]" (optionally followed by ":" or "."), through end of line.
        // Matched line by line on purpose: a lazy multi-line block pattern only ever matched the
        // LAST footnote of a list (dot does not cross newlines, so it never reached the next
        // marker), leaving all earlier footnotes in the text to be read aloud by TTS.
        String footnoteLinePattern = "(?im)^[ \\t]*\\[(?:Note|Footnote|Endnote|Reference)?\\s*\\d+\\][ \\t]*[:.]?[ \\t]*.*$\\n?";
        String step1 = text.replaceAll(footnoteLinePattern, "");

        // 2) Remove in-text reference markers like [1], [Note 8], [Footnote 123]
        String step2 = step1.replaceAll("(?i)\\[(?:Note|Footnote|Endnote|Reference)?\\s*\\d+\\]", "");

        // 3) Collapse leftover multiple blank lines and trim
        returnText = step2.replaceAll("\\n{3,}", "\n\n").trim();
        myLog("removing - refs size : " + text.length() + " => " + returnText.length());
        return returnText;
    }
}
