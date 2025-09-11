package com.driot.bookplayer.tts;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class EpubCoverHelper {
    private EpubCoverHelper() {}

    public static Bitmap extractCover(Context ctx, Uri epubUri) {
        try (InputStream base = ctx.getContentResolver().openInputStream(epubUri);
             ZipInputStream zin = new ZipInputStream(new BufferedInputStream(base))) {

            Map<String, byte[]> entries = readZipEntries(zin);
            byte[] container = entries.get("META-INF/container.xml");
            if (container == null) return null;

            String opfPath = findOpfPath(container);
            if (opfPath == null) return null;

            byte[] opfData = entries.get(opfPath);
            if (opfData == null) return null;

            OpfInfo info = parseOpf(opfData);
            if (info == null) return null;

            String basePath = opfPath.contains("/") ? opfPath.substring(0, opfPath.lastIndexOf('/') + 1) : "";
            String coverHref = null;

            if (info.coverId != null) coverHref = info.manifest.get(info.coverId);
            if (coverHref == null) coverHref = firstImageHref(info);

            if (coverHref == null) return null;

            String resolved = resolveHref(basePath, coverHref);
            byte[] imgBytes = entries.get(resolved);
            if (imgBytes == null) {
                // try case-insensitive match if needed
                for (String k : entries.keySet()) {
                    if (k.equalsIgnoreCase(resolved)) { imgBytes = entries.get(k); break; }
                }
            }
            if (imgBytes == null) return null;

            return BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.length);
        } catch (Exception ignore) {
            return null;
        }
    }

    private static Map<String, byte[]> readZipEntries(ZipInputStream zin) throws Exception {
        Map<String, byte[]> map = new LinkedHashMap<>();
        ZipEntry e;
        byte[] buf = new byte[8192];
        while ((e = zin.getNextEntry()) != null) {
            if (e.isDirectory()) continue;
            ByteArrayOutputStream baos = new ByteArrayOutputStream((int)Math.max(0, e.getSize()));
            int n;
            while ((n = zin.read(buf)) != -1) baos.write(buf, 0, n);
            map.put(e.getName(), baos.toByteArray());
            zin.closeEntry();
        }
        return map;
    }

    private static String findOpfPath(byte[] containerXml) throws Exception {
        XmlPullParserFactory f = XmlPullParserFactory.newInstance();
        XmlPullParser xpp = f.newPullParser();
        xpp.setInput(new java.io.ByteArrayInputStream(containerXml), null);
        int type;
        while ((type = xpp.next()) != XmlPullParser.END_DOCUMENT) {
            if (type == XmlPullParser.START_TAG && "rootfile".equals(xpp.getName())) {
                String fullPath = xpp.getAttributeValue(null, "full-path");
                if (fullPath == null) fullPath = xpp.getAttributeValue("", "full-path");
                if (fullPath != null) return fullPath;
            }
        }
        return null;
    }

    private static class OpfInfo {
        String coverId;
        Map<String, String> manifest = new LinkedHashMap<>();     // id -> href
        Map<String, String> mediaTypes = new LinkedHashMap<>();   // id -> media-type
    }

    private static OpfInfo parseOpf(byte[] opfXml) throws Exception {
        OpfInfo info = new OpfInfo();
        XmlPullParserFactory f = XmlPullParserFactory.newInstance();
        XmlPullParser xpp = f.newPullParser();
        xpp.setInput(new java.io.ByteArrayInputStream(opfXml), null);

        String currentTag = null;
        int type;
        while ((type = xpp.next()) != XmlPullParser.END_DOCUMENT) {
            if (type == XmlPullParser.START_TAG) {
                currentTag = xpp.getName();
                if ("item".equals(currentTag)) {
                    String id = xpp.getAttributeValue(null, "id");
                    String href = xpp.getAttributeValue(null, "href");
                    String media = xpp.getAttributeValue(null, "media-type");
                    if (id != null && href != null) {
                        info.manifest.put(id, href);
                        if (media != null) info.mediaTypes.put(id, media);
                    }
                } else if ("meta".equals(currentTag)) {
                    String name = xpp.getAttributeValue(null, "name");
                    if (name == null) name = xpp.getAttributeValue(null, "property"); // some EPUB3 use <meta property="...">
                    if ("cover".equalsIgnoreCase(name)) {
                        String content = xpp.getAttributeValue(null, "content");
                        if (content != null && !content.isEmpty()) info.coverId = content;
                    }
                }
            }
        }
        return info;
    }

    private static String firstImageHref(OpfInfo info) {
        for (Map.Entry<String, String> e : info.mediaTypes.entrySet()) {
            String mt = e.getValue();
            if (mt != null && mt.startsWith("image/")) {
                String href = info.manifest.get(e.getKey());
                if (href != null) return href;
            }
        }
        return null;
    }

    private static String resolveHref(String basePath, String href) {
        if (href == null) return null;
        if (href.startsWith("/")) return href.substring(1);
        if (basePath == null || basePath.isEmpty()) return normalize(href);
        return normalize(basePath + href);
    }

    private static String normalize(String p) {
        // normalize "./" and "../" segments simply
        String[] parts = p.split("/");
        java.util.Deque<String> stack = new java.util.ArrayDeque<>();
        for (String part : parts) {
            if (part.equals("") || part.equals(".")) continue;
            if (part.equals("..")) { if (!stack.isEmpty()) stack.removeLast(); }
            else stack.addLast(part);
        }
        StringBuilder sb = new StringBuilder();
        for (String s : stack) {
            if (sb.length() > 0) sb.append('/');
            sb.append(s);
        }
        return sb.toString();
    }
}

