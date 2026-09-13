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
 * picked file, exactly like before this heuristic existed.
 */
public final class SiblingBookDetector {

    private SiblingBookDetector() {
    }

    public static final class Result {
        // Resolved real filesystem path of the opened file itself - never null when a Result is
        // returned. Safe to link (not just copy) exactly like the folder-import path below already
        // does, unlike the original "Open With" content:// Uri, which is typically a one-shot
        // grant with no persistable permission.
        public final File pickedFile;
        @Nullable
        public final File parentDir; // null if not resolvable, or not actually a real directory
        public final int siblingTrackCount; // includes the picked file itself; 0 if parentDir is null

        Result(File pickedFile, @Nullable File parentDir, int siblingTrackCount) {
            this.pickedFile = pickedFile;
            this.parentDir = parentDir;
            this.siblingTrackCount = siblingTrackCount;
        }
    }

    /**
     * Resolves the picked content:// Uri down to a real filesystem File, without touching its
     * parent directory - cheap enough to call unconditionally whenever the caller needs a stable
     * file:// path to link instead of copy (see ImportBookSingleActivity.checkSiblingBookThenProceed),
     * independent of whether the sibling-scan below is also wanted. Does file I/O - call this off
     * the main thread.
     */
    @Nullable
    public static File resolvePickedFile(Context context, Uri pickedUri) {
        myLogD("resolvePickedFile() start for [" + pickedUri + "]");
        String path = FileHelper.processUri(context, pickedUri);
        if (path == null || path.isEmpty()) {
            FileHelper.NameAndSize nameAndSize = FileHelper.queryDisplayNameAndSize(context, pickedUri);
            myLogD("processUri empty - trying MediaStore by name/size: name=[" + nameAndSize.name
                    + "] size=[" + nameAndSize.size + "]");
            path = FileHelper.resolveRealPathViaMediaStore(context, nameAndSize.name, nameAndSize.size);
        }
        if (path == null || path.isEmpty()) {
            myLogW("resolvePickedFile() - could not resolve a real path for [" + pickedUri + "] - giving up");
            return null;
        }
        myLogD("resolvePickedFile() resolved real path = [" + path + "]");
        return new File(path);
    }

    /** Does file I/O - call this off the main thread. */
    @Nullable
    public static Result detect(Context context, Uri pickedUri) {
        File pickedFile = resolvePickedFile(context, pickedUri);
        if (pickedFile == null) {
            return null;
        }
        return detectSiblingsOf(pickedFile);
    }

    /**
     * Same sibling scan as {@link #detect}, but for a File the caller already resolved via
     * {@link #resolvePickedFile} - avoids re-resolving the Uri when both are needed. Does file
     * I/O - call this off the main thread.
     */
    public static Result detectSiblingsOf(File pickedFile) {
        File parentDir = pickedFile.getParentFile();
        if (parentDir == null || !parentDir.isDirectory()) {
            myLogW("detectSiblingsOf() - parentDir null/not a directory for [" + pickedFile
                    + "] (parentDir=" + parentDir + ")");
            return new Result(pickedFile, null, 0);
        }

        File[] siblings = parentDir.listFiles();
        if (siblings == null) {
            myLogW("detectSiblingsOf() - listFiles() returned null for [" + parentDir + "] (permission issue?)");
            return new Result(pickedFile, null, 0);
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
        myLogD("detectSiblingsOf() - found " + siblings.length + " entries, " + trackCount + " audio/video tracks in ["
                + parentDir + "]");

        // Only the picked file itself found - not a multi-track folder, but pickedFile is still
        // returned so the caller can use it for the single-file import.
        if (trackCount < 2)
            return new Result(pickedFile, null, 0);

        return new Result(pickedFile, parentDir, trackCount);
    }
}
