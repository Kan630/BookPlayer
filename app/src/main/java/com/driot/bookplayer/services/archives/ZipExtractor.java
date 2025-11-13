// com/driot/bookplayer/services/archives/ZipExtractor.java
package com.driot.bookplayer.services.archives;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*; // for myLog, myLogD, myLogE, myLogEE

public final class ZipExtractor implements ArchiveExtractor {

    // --- Your original scoring list ---
    private static final Charset[] ZIP_CHARSET_CANDIDATES = new Charset[] {
            StandardCharsets.UTF_8,
            Charset.forName("CP437"),
            Charset.forName("windows-1252"),
            StandardCharsets.ISO_8859_1,
            Charset.forName("IBM850"),
            StandardCharsets.US_ASCII,
            StandardCharsets.UTF_16,
            StandardCharsets.UTF_16BE,
            StandardCharsets.UTF_16LE,
            Charset.defaultCharset()
    };

    @Override
    public void extract(File zipFile, File unzipFolder, ProgressSink progress, CancelChecker cancel) throws Exception {
        myLogD("unzipping in: " + unzipFolder);
        int nbZip;

        myLogD("---------------------------------------------------------");
        myLogD(unzipFolder.getName());
        myLogD("---------------------------------------------------------");

        // --- Count entries (best effort) ---
        try (ZipFile zf = new ZipFile(zipFile)) {
            nbZip = zf.size();
        } catch (Exception e) {
            myLogEE(e, "Could not count zip entries");
            nbZip = 10;
        }
        myLogD("Zip file has : " + nbZip + " entries");
        myLogD("---------------------------------------------------------");

        int numCurZip = 0;
        Charset charset = detectZipCharset(zipFile);
        if (charset == null) charset = StandardCharsets.UTF_8;
        myLogD("Chosen charset: " + charset);
        myLogD("---------------------------------------------------------");

        // --- Loop entries (streamed) ---
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)), charset)) {
            ZipEntry ze;
            byte[] buffer = new byte[8192];
            ze = zis.getNextEntry();

            while (ze != null) {
                if (cancel != null && cancel.isCancelled()) throw new InterruptedIOException("user_cancel");

                myLog(String.valueOf(numCurZip + 1) + " - Zip entry : " + ze.getName());

                if (ze.isDirectory()) {
                    myLog("ze.isDirectory... goto next record");
                    if (ze.getName().equals(unzipFolder.getName() + "/")) {
                        myLogE("ze.isDirectory and same name !!... ");
                    }
                } else {
                    String audioFileName = shortenAudioFileName(ze.getName(), unzipFolder.getName());

                    numCurZip++;
                    int progressPct = (int) ((double) numCurZip / nbZip * 100);
                    if (progress != null) progress.onProgress(numCurZip, nbZip, audioFileName);

                    File unzippedFile = PathSafe.safeResolve(unzipFolder, audioFileName);
                    if (unzippedFile.getParentFile() != null
                            && !unzippedFile.getParentFile().exists()
                            && !unzippedFile.getParentFile().mkdirs()) {
                        throw new IOException("Failed to create output dir: " + unzippedFile);
                    }

                    try (FileOutputStream fout = new FileOutputStream(unzippedFile)) {
                        int count;
                        while ((count = zis.read(buffer)) != -1) {
                            if (cancel != null && cancel.isCancelled()) throw new InterruptedIOException("user_cancel");
                            fout.write(buffer, 0, count);
                        }
                    }
                }

                // get next entry (with your defensive retry)
                ze = null;
                try {
                    ze = zis.getNextEntry();
                } catch (Exception e) {
                    myLogE("error getting next zip file entry : " + e.getMessage());
                    try {
                        ze = zis.getNextEntry();
                        if (ze == null) myLog("next next zip file entry is null");
                    } catch (Exception e2) {
                        myLogE("error getting next next zip file entry : " + e2.getMessage());
                    }
                }
            }
        }
    }

    // --- Your original charset detection (kept) ---
    private Charset detectZipCharset(File zipFile) {
        Charset best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (Charset cs : ZIP_CHARSET_CANDIDATES) {
            double score = scoreZipNames(zipFile, cs);
            if (score > bestScore) { bestScore = score; best = cs; }
        }
        if (best == null) {
            myLogEE(null, "No charset scored > -Inf, using default");
            return Charset.defaultCharset();
        }
        return best;
    }

    private double scoreZipNames(File zip, Charset cs) {
        int names = 0;
        int goodChars = 0;
        int badChars = 0;
        int suspicious = 0;

        try (ZipFile zf = new ZipFile(zip, cs)) {
            Enumeration<? extends ZipEntry> it = zf.entries();
            while (it.hasMoreElements()) {
                ZipEntry e = it.nextElement();
                String name = e.getName();
                names++;

                for (int i = 0; i < name.length(); i++) {
                    char c = name.charAt(i);
                    if (c == '\uFFFD') { badChars++; continue; } // replacement char
                    if (Character.isISOControl(c) && c != '/' && c != '\\') { badChars++; continue; }
                    if (c >= 0x2500 && c <= 0x257F) { suspicious++; continue; } // box-drawing etc.
                    if (Character.isLetterOrDigit(c) || " .-_()+[]{}'.,".indexOf(c) >= 0 || c=='/' || c=='\\') {
                        goodChars++;
                    } else {
                        suspicious++;
                    }
                }
            }
        } catch (Exception ex) {
            myLog("Charset " + cs + " failed during listing");
            return -1_000_000;
        }

        if (names == 0) return -1;
        return goodChars - (4.0 * badChars) - (0.5 * suspicious);
    }

    // --- Your shorten logic (kept) ---
    private String shortenAudioFileName(String audioFileName, String folderName) {
        String tmp = audioFileName;

        String folderNorm = normalizeName(folderName);
        String tmpNorm = normalizeName(tmp);

        if (tmpNorm.startsWith(folderNorm)) {
            tmp = tmp.substring(folderName.length());
        }

        if (tmp.startsWith("/") || tmp.startsWith("\\")) {
            tmp = tmp.substring(1);
        }

        tmpNorm = normalizeName(tmp);
        if (tmpNorm.startsWith(folderNorm)) {
            tmp = tmp.substring(folderName.length());
        }

        tmp = tmp.replace("\\", "_").replace("/", "_");

        while (tmp.startsWith("_") || tmp.startsWith(" ")) {
            tmp = tmp.substring(1);
        }

        if (tmp.length() < 5) {
            tmp = audioFileName;
        }

        if (!tmp.equals(audioFileName)) {
            myLogD("name shortened = [" + tmp + "] .   was [" + audioFileName + "]");
        }

        return tmp;
    }

    private String normalizeName(String s) {
        return s.toLowerCase(Locale.ROOT)
                .replace("_", " ")
                .replaceAll("[\\\\/]", " ")
                .trim();
    }
}
