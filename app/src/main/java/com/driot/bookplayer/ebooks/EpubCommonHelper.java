package com.driot.bookplayer.ebooks;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.annotation.Nullable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

/**
 * Common utilities for EPUB parsing shared between EpubGutenbergHelper and EpubLowLevelHelper.
 * Contains ZIP reading, OPF parsing, cover extraction, and path utilities.
 */
public final class EpubCommonHelper {

    private EpubCommonHelper() {
    }

    // ===== ZIP Reading =====

    /**
     * Reads an EPUB file (ZIP archive) and returns a map of entry names to their byte content.
     */
    public static Map<String, byte[]> readZip(Uri uri, Context ctx) throws Exception {
        Map<String, byte[]> map = new LinkedHashMap<>();
        try (InputStream in = new BufferedInputStream(ctx.getContentResolver().openInputStream(uri));
                java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(in)) {
            byte[] buf = new byte[8192];
            java.util.zip.ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (!e.isDirectory()) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream(
                            (int) Math.max(0, e.getSize()));
                    int n;
                    while ((n = zin.read(buf)) != -1)
                        bos.write(buf, 0, n);
                    map.put(e.getName(), bos.toByteArray());
                }
                zin.closeEntry();
            }
        }
        myLogD("EPUB ZIP entries: " + map.size());
        return map;
    }

    // ===== Container.xml & OPF Path =====

    /**
     * Finds the OPF file path from container.xml.
     */
    public static String findOpfPath(byte[] containerXml) throws Exception {
        XmlPullParser x = newPull(containerXml);
        int t;
        while ((t = x.next()) != XmlPullParser.END_DOCUMENT) {
            if (t == XmlPullParser.START_TAG && "rootfile".equals(x.getName())) {
                String p = attr(x, "full-path");
                if (p != null)
                    return p;
            }
        }
        return null;
    }

    // ===== XML Parsing Utilities =====

    /**
     * Creates a new XmlPullParser for parsing XML bytes.
     */
    public static XmlPullParser newPull(byte[] bytes) throws Exception {
        XmlPullParserFactory f = XmlPullParserFactory.newInstance();
        f.setNamespaceAware(true);
        XmlPullParser x = f.newPullParser();
        x.setInput(new ByteArrayInputStream(bytes), null);
        return x;
    }

    /**
     * Gets an attribute value from an XmlPullParser, trying multiple methods.
     */
    public static String attr(XmlPullParser x, String name) {
        String v = x.getAttributeValue(null, name);
        if (v == null)
            v = x.getAttributeValue("", name);
        if (v == null && x.getAttributeCount() > 0) {
            for (int i = 0; i < x.getAttributeCount(); i++) {
                if (name.equals(x.getAttributeName(i)))
                    return x.getAttributeValue(i);
            }
        }
        return v;
    }

    /**
     * Extracts text content from an XML element.
     */
    public static String text(XmlPullParser x) throws Exception {
        StringBuilder sb = new StringBuilder();
        int t;
        while ((t = x.next()) != XmlPullParser.END_DOCUMENT) {
            if (t == XmlPullParser.TEXT)
                sb.append(x.getText());
            else if (t == XmlPullParser.END_TAG)
                break;
        }
        return sb.toString();
    }

    // ===== Path Utilities =====

    /**
     * Gets the base path (directory) of an OPF file path.
     */
    public static String opfBase(String opfPath) {
        if (opfPath == null)
            return "";
        int i = opfPath.lastIndexOf('/');
        return i >= 0 ? opfPath.substring(0, i + 1) : "";
    }

    /**
     * Resolves a relative href against a base path.
     */
    public static String resolve(String base, String href) {
        if (href == null)
            return null;
        if (href.startsWith("/"))
            return href.substring(1);
        if (base == null || base.isEmpty())
            return normalizePath(href);
        return normalizePath(base + href);
    }

    /**
     * Normalizes a path by resolving . and .. components.
     */
    public static String normalizePath(String p) {
        if (p == null)
            return null;
        String[] parts = p.split("/");
        Deque<String> stack = new ArrayDeque<>();
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part))
                continue;
            if ("..".equals(part)) {
                if (!stack.isEmpty())
                    stack.removeLast();
            } else {
                stack.addLast(part);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String s : stack) {
            if (sb.length() > 0)
                sb.append('/');
            sb.append(s);
        }
        return sb.toString();
    }

    // ===== Cover Extraction =====

    /**
     * Base interface for OpfInfo to allow cover extraction without knowing the exact type.
     */
    public interface OpfInfoForCover {
        String getOpfPath();
        String getCoverId();
        Map<String, String> getManifestHref();
        Map<String, String> getManifestType();
    }

    /**
     * Extracts the cover image bitmap from an EPUB.
     * Works with any OpfInfo implementation that implements OpfInfoForCover.
     */
    @Nullable
    public static Bitmap extractCoverBitmap(Map<String, byte[]> zip, OpfInfoForCover opf) {
        String basePath = opfBase(opf.getOpfPath());
        String coverHref = null;

        if (opf.getCoverId() != null) {
            coverHref = opf.getManifestHref().get(opf.getCoverId());
        }
        if (coverHref == null) {
            // fallback: first image in manifest
            for (Map.Entry<String, String> e : opf.getManifestType().entrySet()) {
                String mt = e.getValue();
                if (mt != null && mt.startsWith("image/")) {
                    coverHref = opf.getManifestHref().get(e.getKey());
                    if (coverHref != null)
                        break;
                }
            }
        }
        if (coverHref == null)
            return null;

        String resolved = normalizePath(resolve(basePath, coverHref));
        byte[] imgBytes = zip.get(resolved);
        if (imgBytes == null) {
            // Case-insensitive fallback
            for (String k : zip.keySet()) {
                if (k.equalsIgnoreCase(resolved)) {
                    imgBytes = zip.get(k);
                    break;
                }
            }
        }
        if (imgBytes == null)
            return null;
        return BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.length);
    }

    // ===== Encoding Detection =====

    /**
     * Converts byte array to string, detecting encoding from XML declaration if present.
     */
    public static String bytesToStringWithXmlGuess(byte[] data) {
        if (data == null)
            return "";

        String sniff = new String(data, 0, Math.min(data.length, 256), Charset.forName("ISO-8859-1"));
        String enc = null;
        int i = sniff.indexOf("encoding=");
        if (i >= 0) {
            int q1 = sniff.indexOf('"', i);
            int q2 = sniff.indexOf('"', q1 + 1);
            int a1 = sniff.indexOf('\'', i);
            int a2 = sniff.indexOf('\'', a1 + 1);
            if (q1 > 0 && q2 > q1)
                enc = sniff.substring(q1 + 1, q2);
            else if (a1 > 0 && a2 > a1)
                enc = sniff.substring(a1 + 1, a2);
        }
        Charset cs;
        try {
            cs = (enc != null) ? Charset.forName(enc) : Charset.forName("UTF-8");
        } catch (Exception e) {
            cs = Charset.forName("UTF-8");
        }
        return new String(data, cs);
    }
}
