package com.driot.bookplayer.helpers;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.text.Spanned;
import android.webkit.MimeTypeMap;

import androidx.annotation.Nullable;
import androidx.core.text.HtmlCompat;

import com.driot.bookplayer.utils.KanLogger;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class TextExtractor {
    private TextExtractor() {}

    /** Toggle this to force-disable Jsoup (fallback will still work). */
    public static boolean USE_JSOUP = true;

    // ------------------- PUBLIC API -------------------
    public static String getPlainText(Context ctx, Uri uri, @Nullable String displayNameOrPath) {
        String ext = guessExt(ctx, uri, displayNameOrPath);
        String lower = ext == null ? "" : ext.toLowerCase(Locale.ROOT);

        try {
            switch (lower) {
                case "txt":
                    return readAllText(ctx, uri);

                case "html":
                case "htm":
                case "xhtml": {
                    String html = readAllText(ctx, uri);
                    return clean(htmlToPlain(html));
                }

                case "fb2": {
                    return clean(extractFb2(ctx, uri));
                }

                case "odt": {
                    return clean(extractOdt(ctx, uri));
                }

                default: {
                    // If MIME looks like HTML, treat as HTML
                    String mime = getMime(ctx, uri);
                    if (mime != null && (mime.contains("html") || mime.contains("xhtml"))) {
                        return clean(htmlToPlain(readAllText(ctx, uri)));
                    }
                    // Fallback: raw text (works fine if the file is actually plain)
                    return readAllText(ctx, uri);
                }
            }
        } catch (Exception e) {
            myLogEE(e, "getPlainText failed, returning best-effort raw text");
            try { return readAllText(ctx, uri); } catch (Exception ignored) {}
            return "";
        }
    }

    // ------------------- HTML → plain -------------------
    private static String htmlToPlain(String xhtml) {
        if (xhtml == null) return "";
        if (USE_JSOUP) {
            try {
                Class.forName("org.jsoup.Jsoup");
                return htmlToPlainWithJsoup(xhtml);
            } catch (Throwable ignored) { /* fall through */ }
        }
        return htmlToPlainCompat(xhtml);
    }

    // Jsoup path (best quality). Requires: implementation "org.jsoup:jsoup:1.17.2"
    private static String htmlToPlainWithJsoup(String xhtml) {
        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(xhtml);
        doc.outputSettings(new org.jsoup.nodes.Document.OutputSettings().prettyPrint(false));

        StringBuilder out = new StringBuilder(xhtml.length() / 2);

        org.jsoup.select.NodeTraversor.traverse(new org.jsoup.select.NodeVisitor() {
            private boolean atLineStart = true;

            @Override public void head(org.jsoup.nodes.Node node, int depth) {
                String name = node.nodeName();

                if (node instanceof org.jsoup.nodes.TextNode) {
                    String text = ((org.jsoup.nodes.TextNode) node).text();
                    if (!text.trim().isEmpty()) {
                        if (!atLineStart) out.append(' ');
                        out.append(text.trim());
                        atLineStart = false;
                    }
                    return;
                }

                if ("li".equalsIgnoreCase(name)) {
                    newline(out); out.append("• "); atLineStart = false;
                }
                if ("br".equalsIgnoreCase(name)) {
                    newline(out); atLineStart = true;
                }
                if (name.matches("h[1-6]")) {
                    ensureBlankLine(out);
                    atLineStart = true;
                }
            }

            @Override public void tail(org.jsoup.nodes.Node node, int depth) {
                String name = node.nodeName();
                if (isBlockForPlain(name)) ensureBlankLine(out);
            }

            private boolean isBlockForPlain(String name) {
                String n = name.toLowerCase(Locale.ROOT);
                return n.equals("p") || n.equals("div") || n.equals("section") || n.equals("article")
                        || n.equals("blockquote") || n.equals("ul") || n.equals("ol")
                        || n.equals("table") || n.equals("tr") || n.equals("thead") || n.equals("tbody")
                        || n.matches("h[1-6]");
            }

            private void newline(StringBuilder b) {
                if (b.length() == 0 || b.charAt(b.length()-1) == '\n') return;
                b.append('\n');
            }

            private void ensureBlankLine(StringBuilder b) {
                int len = b.length();
                if (len == 0) return;
                if (len >= 2 && b.charAt(len-1) == '\n' && b.charAt(len-2) == '\n') return;
                if (len >= 1 && b.charAt(len-1) != '\n') b.append('\n');
                b.append('\n');
            }
        }, doc.body() != null ? doc.body() : doc);

        // tidy trailing spaces before newlines, keep paragraph blank lines
        return out.toString().replaceAll("[ \\t]+\\n", "\n").trim();
    }

    // Lightweight fallback (no Jsoup)
    private static String htmlToPlainCompat(String xhtml) {
        String pre = xhtml
                .replaceAll("(?is)</(p|div|section|article|blockquote|h[1-6]|ul|ol|li|table|tr)>", "</$1><br/>")
                .replaceAll("(?is)<(h[1-6])\\b[^>]*>", "<br/><$1>")
                .replaceAll("(?is)<li\\b[^>]*>", "<br/><li>");
        Spanned sp = HtmlCompat.fromHtml(pre, HtmlCompat.FROM_HTML_MODE_LEGACY);
        String txt = sp.toString();
        return txt.replace("\r", "")
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .replaceAll("[ \\t]{2,}", " ")
                .trim();
    }

    // ------------------- FB2 -------------------
    private static String extractFb2(Context ctx, Uri uri) throws Exception {
        String xml = readAllText(ctx, uri);
        StringBuilder out = new StringBuilder(xml.length()/2);
        XmlPullParser x = XmlPullParserFactory.newInstance().newPullParser();
        x.setInput(new StringReader(xml));
        boolean inBody = false;
        int t;
        while ((t = x.next()) != XmlPullParser.END_DOCUMENT) {
            if (t == XmlPullParser.START_TAG) {
                String tag = x.getName().toLowerCase(Locale.ROOT);
                if ("body".equals(tag)) inBody = true;
                if (!inBody) continue;
                if ("title".equals(tag) || "section".equals(tag)) out.append("\n\n");
                if ("p".equals(tag)) out.append("\n\n");
            } else if (t == XmlPullParser.TEXT && inBody) {
                String txt = x.getText();
                if (txt != null && !txt.trim().isEmpty()) out.append(txt.trim()).append(' ');
            } else if (t == XmlPullParser.END_TAG && "body".equalsIgnoreCase(x.getName())) {
                inBody = false;
            }
        }
        return out.toString();
    }

    // ------------------- ODT -------------------
    private static String extractOdt(Context ctx, Uri uri) throws Exception {
        byte[] contentXml = readZipEntry(ctx, uri, "content.xml");
        if (contentXml == null) return "";
        String xml = new String(contentXml, StandardCharsets.UTF_8);

        StringBuilder out = new StringBuilder(xml.length()/2);
        XmlPullParser x = XmlPullParserFactory.newInstance().newPullParser();
        x.setInput(new StringReader(xml));
        int t; boolean inBlock = false;
        while ((t = x.next()) != XmlPullParser.END_DOCUMENT) {
            if (t == XmlPullParser.START_TAG) {
                String tag = x.getName();
                if ("text:h".equals(tag) || "text:p".equals(tag)) { inBlock = true; out.append("\n\n"); }
                else if ("text:line-break".equals(tag)) out.append("\n");
            } else if (t == XmlPullParser.TEXT && inBlock) {
                String txt = x.getText(); if (txt != null) out.append(txt);
            } else if (t == XmlPullParser.END_TAG) {
                if ("text:h".equals(x.getName()) || "text:p".equals(x.getName())) inBlock = false;
            }
        }
        return out.toString();
    }

    // ------------------- Utils -------------------
    private static String readAllText(Context ctx, Uri uri) throws Exception {
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) return "";
            ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.max(64 * 1024, 4096));
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
            return bos.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static byte[] readZipEntry(Context ctx, Uri uri, String wantedName) throws Exception {
        try (InputStream in0 = ctx.getContentResolver().openInputStream(uri);
             ZipInputStream zin = new ZipInputStream(new BufferedInputStream(in0))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (wantedName.equals(e.getName())) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream((int)Math.max(0, e.getSize()));
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = zin.read(buf)) != -1) bos.write(buf, 0, n);
                    return bos.toByteArray();
                }
                zin.closeEntry();
            }
        }
        return null;
    }

    private static String guessExt(Context ctx, Uri uri, @Nullable String displayNameOrPath) {
        if (displayNameOrPath != null) {
            int dot = displayNameOrPath.lastIndexOf('.');
            if (dot >= 0 && dot < displayNameOrPath.length() - 1) {
                return displayNameOrPath.substring(dot + 1);
            }
        }
        String mime = getMime(ctx, uri);
        if (mime != null) {
            String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
            if (ext != null && !ext.isEmpty()) return ext;
        }
        return null;
    }

    private static String getMime(Context ctx, Uri uri) {
        try {
            ContentResolver cr = ctx.getContentResolver();
            return cr.getType(uri);
        } catch (Exception ignored) { return null; }
    }

    /** Normalize whitespace and keep paragraph breaks. */
    private static String clean(String s) {
        if (s == null) return "";
        String t = s.replace('\u00A0', ' ')
                .replace("\r\n","\n").replace("\r","\n")
                .replaceAll("[\\t ]{2,}", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return t;
    }

    // --- logging ---
    private static final String TAG = "TextExtractor";
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, TAG, str); }
}
