package com.driot.bookplayer.ebooks.gutendex;

import androidx.annotation.Nullable;

import com.driot.bookplayer.global.Option;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import java.util.Map;

public class GutendexMapper {

    @Nullable
    public static String findBestEpubUrl(GutendexBook book) {
        if (book == null || book.formats == null) return null;

        // Prefer any key starting with application/epub+zip
        for (Map.Entry<String, String> e : book.formats.entrySet()) {
            String key = e.getKey();
            if (key != null && key.startsWith("application/epub+zip")) {
                return rewriteUrlForMirror(e.getValue(), book.id);
            }
        }
        return null;
    }

    @Nullable
    public static String rewriteUrlForMirror(String url, int bookId) {
        boolean useMirror = Option.getGutenbergUseMirror();
        if (url == null || !useMirror) {
            return url;
        }

        String mirrorBase = Option.getGutenbergMirrorUrl();
        if (mirrorBase == null || mirrorBase.isEmpty()) {
            myLogE("mirrorBase is null");
            return url;
        }

        // Pattern 1: Standard cache pattern
        // https://www.gutenberg.org/cache/epub/ -> https://mirror.cs.odu.edu/gutenberg-epub/
        String cachePattern = "https://www.gutenberg.org/cache/epub/";
        if (url.startsWith(cachePattern)) {
            String newUrl = url.replace(cachePattern, mirrorBase);
            //myLog("using mirror : " + url + " => " + newUrl);
            return newUrl;
        }

        // Pattern 2: Ebooks redirector pattern (common in Gutendex)
        // https://www.gutenberg.org/ebooks/ID.epub3.images -> mirror/ID/pgID-images-3.epub
        String ebooksPattern = "https://www.gutenberg.org/ebooks/";
        if (url.startsWith(ebooksPattern)) {
            String newUrl = null;
            if (url.endsWith(".epub3.images")) {
                newUrl = mirrorBase + bookId + "/pg" + bookId + "-images-3.epub";
            } else if (url.endsWith(".epub.images")) {
                newUrl = mirrorBase + bookId + "/pg" + bookId + "-images.epub";
            } else if (url.endsWith(".epub.noimages")) {
                newUrl = mirrorBase + bookId + "/pg" + bookId + ".epub";
            }

            if (newUrl != null) {
                //myLog("using mirror (ebooks redirector) : " + url + " => " + newUrl);
                return newUrl;
            }
        }

        // If we reach here and it's still a gutenberg.org URL, it's an unhandled pattern
        if (url.contains("gutenberg.org")) {
            myLogD("unhandled gutenberg pattern : " + url);
        }
        return url;
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
