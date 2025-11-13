package com.driot.bookplayer.services.archives;

import com.driot.bookplayer.utils.log.LoggerHelper;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;

import java.io.*;

public final class SevenZExtractor extends LoggerHelper implements ArchiveExtractor {

    public SevenZExtractor() {
        super(SevenZExtractor.class);
    }

    @Override
    public void extract(File archive, File dest, ProgressSink progress, CancelChecker cancel) throws Exception {
        int total = 0;
        // First pass: count entries
        myLog("First pass...");
        try (SevenZFile z = new SevenZFile(archive)) {
            SevenZArchiveEntry e;
            while ((e = z.getNextEntry()) != null) {
                if (!e.isDirectory()) total++;
            }
        } catch (Throwable t) {
            myLogEE(t, "could not count 7z entries");
            total = 0;
        }
        myLog(total + " entries found");

        int cur = 0;
        byte[] buf = new byte[8192];

        myLog("Second pass...");
        // Second pass: extract
        try (SevenZFile z = new SevenZFile(archive)) {
            SevenZArchiveEntry e;
            while ((e = z.getNextEntry()) != null) {
                if (cancel.isCancelled()) throw new InterruptedIOException("user_cancel");
                if (e.isDirectory()) continue;

                String name = e.getName();
                myLog("reading " + name);
                File out = PathSafe.safeResolve(dest, name);
                File parent = out.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IOException("Failed to create " + parent);
                }

                try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                    int n;
                    while ((n = z.read(buf, 0, buf.length)) > 0) {
                        if (cancel.isCancelled()) throw new InterruptedIOException("user_cancel");
                        os.write(buf, 0, n);
                    }
                }

                cur++;
                if (progress != null) progress.onProgress(cur, total, name);
            }
        } catch (Throwable t) {
            myLogEE(t, "7z - second pass");
            throw new Exception(t);
        }
    }
}
