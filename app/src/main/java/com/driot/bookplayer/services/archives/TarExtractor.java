// com/driot/bookplayer/services/archives/TarExtractor.java
package com.driot.bookplayer.services.archives;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;

import java.io.*;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

public final class TarExtractor implements ArchiveExtractor {

    @Override
    public void extract(File archive, File dest, ProgressSink progress, CancelChecker cancel) throws Exception {
        int total = countEntries(archive);

        int cur = 0;
        byte[] buf = new byte[8192];

        try (InputStream fis = new BufferedInputStream(new FileInputStream(archive));
             InputStream cis = maybeWrapCompressor(fis);
             TarArchiveInputStream tis = new TarArchiveInputStream(cis)) {

            TarArchiveEntry e;
            while ((e = tis.getNextTarEntry()) != null) {
                if (cancel != null && cancel.isCancelled()) throw new InterruptedIOException("user_cancel");
                if (e.isDirectory()) continue;

                String name = e.getName();
                myLog(String.valueOf(cur + 1) + " - Tar entry : " + name);

                File out = PathSafe.safeResolve(dest, name);
                File parent = out.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IOException("Failed to create " + parent);
                }

                try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                    int n;
                    while ((n = tis.read(buf)) != -1) {
                        if (cancel != null && cancel.isCancelled()) throw new InterruptedIOException("user_cancel");
                        os.write(buf, 0, n);
                    }
                }

                cur++;
                if (progress != null) progress.onProgress(cur, total, name);
            }
        }
    }

    private int countEntries(File archive) {
        int total = 0;
        try (InputStream fis = new BufferedInputStream(new FileInputStream(archive));
             InputStream cis = maybeWrapCompressor(fis);
             TarArchiveInputStream tis = new TarArchiveInputStream(cis)) {
            TarArchiveEntry e;
            while ((e = tis.getNextTarEntry()) != null) {
                if (!e.isDirectory()) total++;
            }
        } catch (Throwable ignore) { return 0; }
        return total;
    }

    private static InputStream maybeWrapCompressor(InputStream in) throws CompressorException {
        // auto-detect gzip/bzip2/xz by magic bytes
        CompressorStreamFactory f = new CompressorStreamFactory(true);
        try {
            CompressorInputStream cis = f.createCompressorInputStream(in);
            return cis; // compressed tar
        } catch (CompressorException notCompressed) {
            // plain .tar (no compression)
            return in;
        }
    }
}
