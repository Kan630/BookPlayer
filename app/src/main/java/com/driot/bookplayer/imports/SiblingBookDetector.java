package com.driot.bookplayer.imports;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.driot.bookplayer.helpers.FileHelper;
import com.driot.bookplayer.helpers.SupportedFilesHelper;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import java.io.File;

/**
 * Detects whether a single file opened via "Open with" sits alongside sibling audio/video files
 * that together look like the separate chapter files of one book, so the caller can offer to
 * import the whole folder instead of just the picked file.
 *
 * Only works when the picked content:// Uri's real filesystem path can be recovered - Android's
 * Storage Access Framework has no API to go from a single-document Uri back to its parent
 * directory (DocumentFile.fromSingleUri() hardcodes a null parent, and its listFiles() throws).
 * Tries, in order: {@link FileHelper#processUri} (file:// paths, DocumentsContract Uris, a
 * generic content _data column), then - since several vendor file managers hand out a content://
 * Uri whose own provider exposes neither (seen: Samsung's own "My Files" FileProvider) -
 * {@link FileHelper#resolveRealPathViaMediaStore} keyed on the Uri's DISPLAY_NAME/SIZE
 * (OpenableColumns, the one thing every provider still exposes). When even that fails there is
 * simply nothing to detect and this returns null - the caller falls back to importing just the
 * picked file, exactly like before this heuristic existed. Deliberately does NOT use
 * UriHelper.getPathFromUri() here - its own fallback silently copies the file to cache and
 * returns that unrelated temp path, which would make this scan the app's cache dir instead of
 * the real folder.
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
        myLogD("detect() start for [" + pickedUri + "]");
        String path = FileHelper.processUri(context, pickedUri);
        if (path == null || path.isEmpty()) {
            FileHelper.NameAndSize nameAndSize = FileHelper.queryDisplayNameAndSize(context, pickedUri);
            myLogD("processUri empty - trying MediaStore by name/size: name=[" + nameAndSize.name
                    + "] size=[" + nameAndSize.size + "]");
            path = FileHelper.resolveRealPathViaMediaStore(context, nameAndSize.name, nameAndSize.size);
        }
        if (path == null || path.isEmpty()) {
            myLogW("detect() - could not resolve a real path for [" + pickedUri + "] - giving up");
            return null;
        }
        myLogD("detect() resolved real path = [" + path + "]");

        File pickedFile = new File(path);
        File parentDir = pickedFile.getParentFile();
        if (parentDir == null || !parentDir.isDirectory()) {
            myLogW("detect() - parentDir null/not a directory for [" + path + "] (parentDir=" + parentDir + ")");
            return null;
        }

        File[] siblings = parentDir.listFiles();
        if (siblings == null) {
            myLogW("detect() - listFiles() returned null for [" + parentDir + "] (permission issue?)");
            return null;
        }

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
        myLogD("detect() - found " + siblings.length + " entries, " + trackCount + " audio/video tracks in ["
                + parentDir + "]");

        // Only the picked file itself found - not a multi-track folder.
        if (trackCount < 2)
            return null;

        return new Result(parentDir, trackCount);
    }
}
