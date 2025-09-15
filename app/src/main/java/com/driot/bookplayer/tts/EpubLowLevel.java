package com.driot.bookplayer.tts;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.text.Spanned;

import androidx.core.text.HtmlCompat;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class EpubLowLevel {
    private EpubLowLevel() {}

    public static final class OpfInfo {
        public String opfPath;
        public String title;
        public String coverId;
        public final Map<String,String> manifestHref = new LinkedHashMap<>();
        public final Map<String,String> manifestType = new LinkedHashMap<>();
        public final List<String> spine = new ArrayList<>();
    }

    public static final class ExtractResult {
        public final String bookTitle;
        public final File outDir;
        public final List<File> chapterFiles;
        public final Bitmap coverBitmap;
        public ExtractResult(String t, File d, List<File> f, Bitmap c) { bookTitle=t; outDir=d; chapterFiles=f; coverBitmap=c; }
    }

    public static ExtractResult extractAll(Context ctx, Uri epubUri) throws Exception {
        Map<String, byte[]> zip = readZip(epubUri, ctx);
        byte[] container = zip.get("META-INF/container.xml");
        if (container == null) throw new IllegalStateException("container.xml not found");

        String opfPath = findOpfPath(container);
        if (opfPath == null) throw new IllegalStateException("content.opf not found");

        byte[] opfBytes = zip.get(opfPath);
        if (opfBytes == null) throw new IllegalStateException("OPF missing at " + opfPath);

        OpfInfo opf = parseOpf(opfBytes);
        opf.opfPath = opfPath;
        String basePath = opfBase(opfPath);

        String title = (opf.title != null && !opf.title.trim().isEmpty()) ? opf.title.trim() : "untitled";
        File outDir = new File(ctx.getExternalFilesDir(null), "epub_" + safe(title));
        if (!outDir.exists() && !outDir.mkdirs()) throw new IllegalStateException("Cannot create " + outDir);

        Bitmap cover = extractCoverBitmap(zip, opf);

        List<File> out = new ArrayList<>();
        int idx = 0;
        for (String itemId : opf.spine) {
            String href = opf.manifestHref.get(itemId);
            if (href == null) continue;
            String mt = opf.manifestType.get(itemId);
            if (mt == null) mt = "";
            if (!(mt.contains("xhtml") || mt.contains("html"))) continue;

            String path = resolve(basePath, href);
            byte[] x = zip.get(path);
            if (x == null) continue;

            String xhtml = bytesToStringWithXmlGuess(x);
            String chapterTitle = extractTitleTag(xhtml);
            if (chapterTitle == null || chapterTitle.trim().isEmpty()) chapterTitle = basenameNoExt(href);

            String plain = xhtmlToPlain(xhtml);
            plain = clean(plain);

            String fname = String.format(Locale.US, "%03d_%s.txt", ++idx, safeSlug(chapterTitle));
            File f = new File(outDir, fname);
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(plain.getBytes(Charset.forName("UTF-8")));
            }
            out.add(f);
        }

        return new ExtractResult(title, outDir, out, cover);
    }

    public static Bitmap extractCoverBitmap(Map<String, byte[]> zip, OpfInfo opf) {
        String basePath = opfBase(opf.opfPath);
        String coverHref = null;
        if (opf.coverId != null) coverHref = opf.manifestHref.get(opf.coverId);
        if (coverHref == null) {
            for (Map.Entry<String,String> e : opf.manifestType.entrySet()) {
                String mt = e.getValue();
                if (mt != null && mt.startsWith("image/")) {
                    coverHref = opf.manifestHref.get(e.getKey());
                    if (coverHref != null) break;
                }
            }
        }
        if (coverHref == null) return null;
        String resolved = resolve(basePath, coverHref);
        byte[] imgBytes = zip.get(resolved);
        if (imgBytes == null) {
            for (String k : zip.keySet()) {
                if (k.equalsIgnoreCase(resolved)) { imgBytes = zip.get(k); break; }
            }
        }
        if (imgBytes == null) return null;
        return BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.length);
    }

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

    private static OpfInfo parseOpf(byte[] opfXml) throws Exception {
        OpfInfo o = new OpfInfo();
        XmlPullParser x = newPull(opfXml);
        int t;
        boolean inMetadata = false;
        while ((t = x.next()) != XmlPullParser.END_DOCUMENT) {
            if (t == XmlPullParser.START_TAG) {
                String name = x.getName();
                if ("metadata".equalsIgnoreCase(name)) inMetadata = true;
                if (inMetadata && ("dc:title".equalsIgnoreCase(name) || "title".equalsIgnoreCase(name))) o.title = text(x);
                else if ("meta".equalsIgnoreCase(name)) {
                    String nm = attr(x,"name");
                    String prop = attr(x,"property");
                    if ("cover".equalsIgnoreCase(nm) || "cover".equalsIgnoreCase(prop)) {
                        String content = attr(x,"content");
                        if (content != null && !content.isEmpty()) o.coverId = content;
                    }
                } else if ("item".equalsIgnoreCase(name)) {
                    String id = attr(x,"id");
                    String href = attr(x,"href");
                    String mt = attr(x,"media-type");
                    if (id!=null && href!=null) { o.manifestHref.put(id, href); o.manifestType.put(id, mt!=null?mt:""); }
                } else if ("itemref".equalsIgnoreCase(name)) {
                    String idref = attr(x,"idref");
                    if (idref != null) o.spine.add(idref);
                }
            } else if (t == XmlPullParser.END_TAG && "metadata".equalsIgnoreCase(x.getName())) {
                inMetadata = false;
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

    private static String bytesToStringWithXmlGuess(byte[] data) {
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

    private static String xhtmlToPlain(String xhtml) {
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

    private static String extractTitleTag(String xhtml) {
        if (xhtml == null) return null;
        String lower = xhtml.toLowerCase(Locale.US);
        int i = lower.indexOf("<title>");
        if (i >= 0) {
            int j = lower.indexOf("</title>", i + 7);
            if (j > i) {
                String s = xhtml.substring(i + 7, j).replaceAll("\\s+", " ").trim();
                if (!s.isEmpty()) return s;
            }
        }
        int h = lower.indexOf("<h1");
        if (h >= 0) {
            int gt = lower.indexOf(">", h);
            if (gt > h) {
                int end = lower.indexOf("</h1>", gt);
                if (end > gt) {
                    String s = xhtml.substring(gt + 1, end).replaceAll("\\s+", " ").trim();
                    if (!s.isEmpty()) return s;
                }
            }
        }
        return null;
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
