package com.driot.bookplayer.ebooks.gutendex;

import androidx.annotation.Nullable;

import com.driot.bookplayer.global.Option;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GutendexMapper {

    private static final String GUTENBERG_CACHE_PREFIX = "https://www.gutenberg.org/cache/epub/";
    private static final String GUTENBERG_EBOOKS_PREFIX = "https://www.gutenberg.org/ebooks/";

    @Nullable
    public static String findBestEpubUrl(GutendexBook book) {
        if (book == null || book.formats == null) return null;
        // Return the raw gutenberg.org URL — rewriting happens at download time
        for (Map.Entry<String, String> e : book.formats.entrySet()) {
            String key = e.getKey();
            if (key != null && key.startsWith("application/epub+zip")) {
                return e.getValue();
            }
        }
        return null;
    }

    /**
     * Rewrites a raw gutenberg.org URL to use a specific mirror base.
     * If mirrorBase is the direct URL, the original URL is returned unchanged.
     */
    @Nullable
    public static String rewriteWithMirror(String url, int bookId, String mirrorBase) {
        if (url == null || mirrorBase == null || mirrorBase.isEmpty()) return url;

        if (url.startsWith(GUTENBERG_CACHE_PREFIX)) {
            return url.replace(GUTENBERG_CACHE_PREFIX, mirrorBase);
        }

        if (url.startsWith(GUTENBERG_EBOOKS_PREFIX)) {
            if (url.endsWith(".epub3.images")) {
                return mirrorBase + bookId + "/pg" + bookId + "-images-3.epub";
            } else if (url.endsWith(".epub.images")) {
                return mirrorBase + bookId + "/pg" + bookId + "-images.epub";
            } else if (url.endsWith(".epub.noimages")) {
                return mirrorBase + bookId + "/pg" + bookId + ".epub";
            }
        }

        if (url.contains("gutenberg.org")) {
            myLogD("unhandled gutenberg pattern : " + url);
        }
        return url;
    }

    /**
     * Builds an ordered list of candidate download URLs for the given raw URL:
     * 1. Currently selected mirror (first)
     * 2. All other mirrors
     * 3. Raw URL as last resort
     */
    public static List<String> buildDownloadCandidates(String rawUrl, int bookId) {
        List<String> candidates = new ArrayList<>();
        if (rawUrl == null || rawUrl.isEmpty()) return candidates;

        String selected = Option.getGutenbergMirrorUrl();

        // Selected mirror first
        String first = rewriteWithMirror(rawUrl, bookId, selected);
        if (first != null) candidates.add(first);

        // All other mirrors
        for (String mirrorBase : Option.GUTENBERG_MIRROR_URLS) {
            if (!mirrorBase.equals(selected)) {
                String candidate = rewriteWithMirror(rawUrl, bookId, mirrorBase);
                if (candidate != null && !candidates.contains(candidate)) {
                    candidates.add(candidate);
                }
            }
        }

        // Raw URL as final fallback (in case none of the mirrors match the pattern)
        if (!candidates.contains(rawUrl)) {
            candidates.add(rawUrl);
        }

        return candidates;
    }

    @Nullable
    public static String findCoverUrl(GutendexBook book) {
        if (book == null || book.formats == null) return null;

        String jpeg = book.formats.get("image/jpeg");
        if (jpeg != null && !jpeg.isEmpty()) return jpeg;

        // Fallback: any image/*
        for (Map.Entry<String, String> e : book.formats.entrySet()) {
            String key = e.getKey();
            if (key != null && key.startsWith("image/")) {
                return e.getValue();
            }
        }
        return null;
    }

    public static String buildAuthorLine(GutendexBook book) {
        if (book == null || book.authors == null || book.authors.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < book.authors.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(book.authors.get(i).name);
        }
        return sb.toString();
    }
}
