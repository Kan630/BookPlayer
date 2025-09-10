package com.driot.bookplayer.tts;

import android.content.Context;
import android.net.Uri;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;

public final class TxtReader {
    private TxtReader() {}

    public interface Callback {
        void onLoaded(String text);
        void onError(Exception e);
    }

    public static void loadAsync(Context ctx, Uri uri, Callback cb) {
        new Thread(() -> {
            try (InputStream raw = ctx.getContentResolver().openInputStream(uri)) {
                if (raw == null) { cb.onError(new IOException("null input stream")); return; }

                // Wrap to avoid mark/reset; we can unread bytes after BOM sniff.
                try (PushbackInputStream in = new PushbackInputStream(raw, 4)) {
                    String charset = sniffCharset(in);
                    String text = readAll(in, charset);

                    // Fallback: some legacy .txt may be Latin-1
                    if (text.isEmpty()) {
                        try (InputStream raw2 = ctx.getContentResolver().openInputStream(uri);
                             PushbackInputStream in2 = new PushbackInputStream(raw2, 4)) {
                            sniffCharset(in2); // discard BOM if present
                            text = readAll(in2, "ISO-8859-1");
                        }
                    }

                    cb.onLoaded(clean(text));
                }
            } catch (Exception e) {
                cb.onError(e);
            }
        }, "TxtReader").start();
    }

    private static String sniffCharset(PushbackInputStream in) throws IOException {
        byte[] bom = new byte[4];
        int n = in.read(bom, 0, 4);
        int unread = n;
        String charset;

        if (n >= 3 && (bom[0] & 0xFF) == 0xEF && (bom[1] & 0xFF) == 0xBB && (bom[2] & 0xFF) == 0xBF) {
            charset = "UTF-8";
            unread = n - 3; // consume UTF-8 BOM
        } else if (n >= 2 && (bom[0] & 0xFF) == 0xFE && (bom[1] & 0xFF) == 0xFF) {
            charset = "UTF-16BE";
            unread = n - 2;
        } else if (n >= 2 && (bom[0] & 0xFF) == 0xFF && (bom[1] & 0xFF) == 0xFE) {
            charset = "UTF-16LE";
            unread = n - 2;
        } else {
            charset = "UTF-8"; // default
            unread = n;        // push everything back
        }
        if (unread > 0) in.unread(bom, n - unread, unread);
        return charset;
    }

    private static String readAll(InputStream in, String charset) throws IOException {
        try (InputStreamReader isr = new InputStreamReader(in, charset);
             BufferedReader br = new BufferedReader(isr, 64 * 1024)) {
            StringBuilder sb = new StringBuilder(256 * 1024);
            char[] buf = new char[8192];
            int read;
            while ((read = br.read(buf)) != -1) sb.append(buf, 0, read);
            return sb.toString();
        }
    }

    private static String clean(String raw) {
        if (raw == null) return "";
        String s = raw.replace("\r\n", "\n").replace("\r", "\n");
        s = s.replaceAll("[\\p{Cntrl}&&[^\n\t]]", "");
        s = s.replaceAll("\n{3,}", "\n\n");
        return s.trim();
    }
}
