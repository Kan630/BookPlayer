package com.driot.bookplayer.helpers;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.myLog;

import androidx.annotation.Nullable;

import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.utils.Tonio;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Finds the real file name behind a web link, which is often not the link's last path segment:
 * Gutenberg's ".../ebooks/11.epub3.images" redirects to ".../pg11-images-3.epub", and a LibriVox
 * zip on archive.org redirects to "zip_dir.php?..." and only names the file ("raven.zip") in the
 * Content-Disposition of a GET. Blocking network calls - never on the main thread.
 */
public final class WebFileNameHelper {

    private static final int MAX_REDIRECTS = 5;
    private static final int TIMEOUT_MS = 15_000;

    private WebFileNameHelper() {
    }

    /**
     * Follows redirects, then prefers Content-Disposition, else the final url's last segment.
     * Throws on network errors (including a cleartext-blocked http url, see
     * NetworkHelper.isCleartextNotPermitted()).
     */
    public static String resolve(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            Headers h;
            try {
                h = fetchHeaders(url, "HEAD");
            } catch (IOException e) {
                if (NetworkHelper.isCleartextNotPermitted(e))
                    throw e;
                // archive.org's zip_dir.php drops HEAD requests ("unexpected end of stream")
                h = fetchHeaders(url, "GET");
            }
            if (h.code >= 300 && h.code < 400 && h.location != null && !h.location.isEmpty()) {
                // archive.org puts raw spaces in its Location header
                url = new URL(url, h.location.replace(" ", "%20"));
                continue;
            }
            String cd = h.contentDisposition;
            if (cd == null && "HEAD".equals(h.method))
                cd = contentDispositionFromGet(url); // some servers only send it on GET
            String name = fromContentDisposition(cd);
            if (name != null && !name.isEmpty()) {
                myLog("WebFileNameHelper: [" + name + "] from Content-Disposition of " + url);
                return name;
            }
            name = Tonio.getFileNameFromUrl(url.toString());
            myLog("WebFileNameHelper: [" + name + "] from url " + url);
            return name;
        }
        return Tonio.getFileNameFromUrl(url.toString());
    }

    private static class Headers {
        String method;
        int code;
        String location;
        String contentDisposition;
    }

    /** GET is ranged to one byte: we only want the headers. */
    private static Headers fetchHeaders(URL url, String method) throws IOException {
        HttpURLConnection c = open(url, method);
        try {
            if ("GET".equals(method))
                c.setRequestProperty("Range", "bytes=0-0");
            Headers h = new Headers();
            h.method = method;
            h.code = c.getResponseCode();
            h.location = c.getHeaderField("Location");
            h.contentDisposition = h.code < 400 ? c.getHeaderField("Content-Disposition") : null;
            return h;
        } finally {
            c.disconnect();
        }
    }

    @Nullable
    private static String contentDispositionFromGet(URL url) {
        try {
            return fetchHeaders(url, "GET").contentDisposition;
        } catch (IOException e) {
            return null;
        }
    }

    private static HttpURLConnection open(URL url, String method) throws IOException {
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setRequestProperty("User-Agent", Var.USER_AGENT_BOOKPLAYER);
        c.setRequestMethod(method);
        c.setInstanceFollowRedirects(false); // we walk them ourselves, to see every Location
        return c;
    }

    /**
     * Extracts filename from Content-Disposition header.
     * Handles both:
     *   Content-Disposition: attachment; filename="pg13951-images-3.epub"
     *   Content-Disposition: attachment; filename*=UTF-8''pg13951-images-3.epub
     */
    @Nullable
    static String fromContentDisposition(@Nullable String contentDisposition) {
        if (contentDisposition == null || contentDisposition.isEmpty()) return null;

        // Try filename*=UTF-8''<name> (RFC 5987, takes priority)
        int starIdx = contentDisposition.indexOf("filename*=");
        if (starIdx >= 0) {
            String val = contentDisposition.substring(starIdx + 10).trim();
            // Strip encoding prefix like UTF-8''
            int quoteIdx = val.indexOf("''");
            if (quoteIdx >= 0) val = val.substring(quoteIdx + 2);
            val = val.split(";")[0].trim();
            try {
                return java.net.URLDecoder.decode(val, "UTF-8");
            } catch (Exception ignored) {}
            return val;
        }

        // Try filename="<name>" or filename=<name>
        int idx = contentDisposition.indexOf("filename=");
        if (idx < 0) return null;

        String val = contentDisposition.substring(idx + 9).trim();
        if (val.startsWith("\"")) {
            int end = val.indexOf('"', 1);
            return end > 0 ? val.substring(1, end) : null;
        }
        // unquoted: take until ; or end
        return val.split(";")[0].trim();
    }
}
