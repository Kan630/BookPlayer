// com/driot/bookplayer/services/archives/PathSafe.java
package com.driot.bookplayer.services.archives;

import java.io.File;
import java.io.IOException;

// Deliberately public: shared with FullBackupHelper (com.driot.bookplayer.importexport), which
// needs the exact same zip-slip guard when extracting a Full Backup zip back onto disk.
public final class PathSafe {
    public static File safeResolve(File destDir, String entryName) throws IOException {
        // Normalize separators
        String clean = entryName.replace('\\','/');

        // Strip leading slashes
        while (clean.startsWith("/")) clean = clean.substring(1);

        File out = new File(destDir, clean);
        String destPath = destDir.getCanonicalPath();
        String outPath  = out.getCanonicalPath();
        if (!outPath.startsWith(destPath + File.separator) && !outPath.equals(destPath)) {
            throw new IOException("Blocked path traversal: " + entryName);
        }
        return out;
    }

    private PathSafe() {}
}
