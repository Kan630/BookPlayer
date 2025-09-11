package com.driot.bookplayer.tts;

import android.content.Context;
import android.net.Uri;
import android.text.Spanned;

import androidx.core.text.HtmlCompat;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

public final class EpubChapterExtractor {
    private EpubChapterExtractor() {}

    public static final class Result {
        public final String bookTitle;
        public final List<File> chapterFiles;
        public Result(String title, List<File> files) { this.bookTitle = title; this.chapterFiles = files; }
    }

    public static Result extractChaptersToFiles(Context ctx, Uri epubUri) throws Exception {
        Map<String, byte[]> zip = readZip(epubUri, ctx);

        byte[] container = zip.get("META-INF/container.xml");
        if (container == null) throw new IllegalStateException("container.xml not found");

        String opfPath = findOpfPath(container);
        if (opfPath == null) throw new IllegalStateException("content.opf not found");
        byte[] opfBytes = zip.get(opfPath);
        if (opfBytes == null) throw new IllegalStateException("OPF missing at " + opfPath);

        Opf opf = parseOpf(opfBytes);
        String basePath = opfBase(opfPath);

        String title = opf.title != null && !opf.title.trim().isEmpty() ? opf.title.trim() : "untitled";
        File outDir = new File(ctx.getExternalFilesDir(null), "epub_" + safe(title));
        if (!outDir.exists() && !outDir.mkdirs()) throw new IllegalStateException("Cannot create " + outDir);

        List<File> out = new ArrayList<>();
        int idx = 0;
        for (String itemId : opf.spine) {
            String href = opf.manifestHref.get(itemId);
            if (href == null) continue;
            String media = opf.manifestType.get(itemId);
            if (media == null) media = "";
            if (!(media.contains("xhtml") || media.contains("html"))) continue;

            String path = resolve(basePath, href);
            byte[] x = zip.get(path);
            if (x == null) continue;

            String xhtml = bytesToStringWithXmlGuess(x);
            String chapterTitle = "";//extractTitleTag(xhtml);
            if (chapterTitle == null || chapterTitle.trim().isEmpty()) chapterTitle = basenameNoExt(href);

            String plain = xhtmlToPlain(xhtml);
            plain = clean(plain);

            String fname = String.format(Locale.US, "%03d_%s.txt", ++idx, safeSlug(chapterTitle));
            File f = new File(outDir, fname);
            try (FileOutputStream fos = new FileOutputStream(f)) {
                byte[] data = plain.getBytes(Charset.forName("UTF-8"));
                fos.write(data);
            }
            out.add(f);
        }
        return new Result(title, out);
    }

    // -------- ZIP --------
    private static Map<String, byte[]> readZip(Uri uri, Context ctx) throws Exception {
        Map<String, byte[]> map = new LinkedHashMap<>();
        try (InputStream in = new BufferedInputStream(ctx.getContentResolver().openInputStream(uri));
             ZipInputStream zin = new ZipInputStream(in)) {
            byte[] buf = new byte[8192];
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (!e.isDirectory()) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream((int)Math.max(0, e.getSize()));
                    int n;
                    while ((n = zin.read(buf)) != -1) bos.write(buf, 0, n);
                    map.put(e.getName(), bos.toByteArray());
                }
                zin.closeEntry();
            }
        }
        return map;
    }

    // -------- CONTAINER / OPF --------
    private static String findOpfPath(byte[] containerXml) throws Exception {
        XmlPullParser x = newPull(containerXml);
        int t;
        while ((t = x.next()) != XmlPullParser.END_DOCUMENT) {
            if (t == XmlPullParser.START_TAG && "rootfile".equals(x.getName())) {
                String p = attr(x, "full-path");
                if (p != null) return p;
            }
        }
        return null;
    }

    private static final class Opf {
        String title;
        final Map<String,String> manifestHref = new LinkedHashMap<>(); // id -> href
        final Map<String,String> manifestType = new LinkedHashMap<>(); // id -> media-type
        final List<String> spine = new ArrayList<>();                   // ordered ids
    }

    private static Opf parseOpf(byte[] opfXml) throws Exception {
        Opf o = new Opf();
        XmlPullParser x = newPull(opfXml);
        String cur = null;
        int t;
        while ((t = x.next()) != XmlPullParser.END_DOCUMENT) {
            if (t == XmlPullParser.START_TAG) {
                cur = x.getName();
                if ("dc:title".equals(cur) || "title".equals(cur)) {
                    o.title = text(x);
                } else if ("item".equals(cur)) {
                    String id = attr(x,"id");
                    String href = attr(x,"href");
                    String mt = attr(x,"media-type");
                    if (id!=null && href!=null) {
                        o.manifestHref.put(id, href);
                        o.manifestType.put(id, mt!=null?mt:"");
                    }
                } else if ("itemref".equals(cur)) {
                    String idref = attr(x,"idref");
                    if (idref != null) o.spine.add(idref);
                }
            }
        }
        return o;
    }

    private static XmlPullParser newPull(byte[] bytes) throws Exception {
        XmlPullParserFactory f = XmlPullParserFactory.newInstance();
        f.setNamespaceAware(true);
        XmlPullParser x = f.newPullParser();
        x.setInput(new ByteArrayInputStream(bytes), null);
        return x;
    }

    private static String attr(XmlPullParser x, String name) {
        String v = x.getAttributeValue(null, name);
        if (v == null) v = x.getAttributeValue("", name);
        if (v == null && x.getAttributeCount() > 0) {
            for (int i=0;i<x.getAttributeCount();i++) {
                if (name.equals(x.getAttributeName(i))) return x.getAttributeValue(i);
            }
        }
        return v;
    }

    private static String text(XmlPullParser x) throws Exception {
        StringBuilder sb = new StringBuilder();
        int t;
        while ((t = x.next()) != XmlPullParser.END_DOCUMENT) {
            if (t == XmlPullParser.TEXT) sb.append(x.getText());
            else if (t == XmlPullParser.END_TAG) break;
        }
        return sb.toString();
    }

    private static String opfBase(String opfPath) {
        int i = opfPath.lastIndexOf('/');
        return i>=0 ? opfPath.substring(0, i+1) : "";
    }

    private static String resolve(String base, String href) {
        if (href == null) return null;
        if (href.startsWith("/")) return href.substring(1);
        if (base == null || base.isEmpty()) return normalize(href);
        return normalize(base + href);
    }

    private static String normalize(String p) {
        String[] parts = p.split("/");
        Deque<String> stack = new ArrayDeque<>();
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part)) continue;
            if ("..".equals(part)) { if (!stack.isEmpty()) stack.removeLast(); }
            else stack.addLast(part);
        }
        StringBuilder sb = new StringBuilder();
        for (String s : stack) { if (sb.length()>0) sb.append('/'); sb.append(s); }
        return sb.toString();
    }

    // -------- XHTML → TEXT --------
    private static String xhtmlToPlain(String xhtml) {
        // HtmlCompat handles common tags; we also normalize spaces and breaks.
        Spanned sp = HtmlCompat.fromHtml(xhtml, HtmlCompat.FROM_HTML_MODE_LEGACY);
        return sp.toString();
    }

    private static String clean(String s) {
        if (s == null) return "";
        String t = s.replace('\u00A0',' ');
        t = t.replace("\r\n","\n").replace("\r","\n");
        t = t.replaceAll("[\\t ]{2,}", " ");
        t = t.replaceAll("\n{3,}", "\n\n");
        return t.trim();
    }

    private static String bytesToStringWithXmlGuess(byte[] data) {
        // Try to guess encoding from XML declaration; else UTF-8.
        String sniff = new String(data, 0, Math.min(data.length, 256), Charset.forName("ISO-8859-1"));
        String enc = null;
        int i = sniff.indexOf("encoding=");
        if (i >= 0) {
            int q1 = sniff.indexOf('"', i), q2 = sniff.indexOf('"', q1+1);
            int a1 = sniff.indexOf('\'', i), a2 = sniff.indexOf('\'', a1+1);
            if (q1>0 && q2>q1) enc = sniff.substring(q1+1, q2);
            else if (a1>0 && a2>a1) enc = sniff.substring(a1+1, a2);
        }
        Charset cs;
        try { cs = enc!=null ? Charset.forName(enc) : Charset.forName("UTF-8"); }
        catch (Exception e) { cs = Charset.forName("UTF-8"); }
        return new String(data, cs);
    }

    private static String basenameNoExt(String href) {
        int slash = href.lastIndexOf('/');
        String name = slash>=0 ? href.substring(slash+1) : href;
        int dot = name.lastIndexOf('.');
        return dot>=0 ? name.substring(0,dot) : name;
    }

    private static String safe(String s) {
        String out = s.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        if (out.isEmpty()) out = "untitled";
        return out.length() > 60 ? out.substring(0,60) : out;
    }

    private static String safeSlug(String s) {
        String out = s.toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]+","-")
                .replaceAll("^-+|-+$","");
        if (out.isEmpty()) out = "chapter";
        return out.length()>40 ? out.substring(0,40) : out;
    }
}
