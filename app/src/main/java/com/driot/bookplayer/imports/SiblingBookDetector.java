package com.driot.bookplayer.imports;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.driot.bookplayer.helpers.SupportedFilesHelper;
import com.driot.bookplayer.helpers.UriHelper;

import java.io.File;

/**
 * Detects whether a single file opened via "Open with" sits alongside sibling audio/video files
 * that together look like the separate chapter files of one book, so the caller can offer to
 * import the whole folder instead of just the picked file.
 *
 * Only works when the picked content:// Uri's real filesystem path can be recovered (see
 * {@link UriHelper#getPathFromUri}) - Android's Storage Access Framework has no API to go from a
 * single-document Uri back to its parent directory (DocumentFile.fromSingleUri() hardcodes a null
 * parent, and its listFiles() throws), so when that recovery fails there is simply nothing to
 * detect and this returns null - the caller falls back to importing just the picked file, exactly
 * like before this heuristic existed.
 */
public final class SiblingBookDetector {

    private SiblingBookDetector() {
    }

    public static final class Result {
        public final File parentDir;
        public final int siblingTrackCount; // includes the picked file itself

        Result(File parentDir, int siblingTrackCount) {
            this.parentDir = parentDir;
            this.siblingTrackCount = siblingTrackCount;
        }
    }

    /** Does file I/O - call this off the main thread. */
    @Nullable
    public static Result detect(Context context, Uri pickedUri) {
        String path = UriHelper.getPathFromUri(context, pickedUri);
        if (path == null)
            return null;

        File pickedFile = new File(path);
        File parentDir = pickedFile.getParentFile();
        if (parentDir == null || !parentDir.isDirectory())
            return null;

        File[] siblings = parentDir.listFiles();
        if (siblings == null)
            return null;

        int trackCount = 0;
        for (File f : siblings) {
            if (f.isDirectory())
                continue;
            String type = SupportedFilesHelper.getType(f.getName());
            if (SupportedFilesHelper.FILE_TYPE_AUDIO.equals(type)
                    || SupportedFilesHelper.FILE_TYPE_VIDEO.equals(type)) {
                trackCount++;
            }
        }

        // Only the picked file itself found - not a multi-track folder.
        if (trackCount < 2)
            return null;

        return new Result(parentDir, trackCount);
    }
}
