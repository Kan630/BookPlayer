package com.driot.bookplayer.imports;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;

import androidx.annotation.Nullable;

import com.driot.bookplayer.helpers.FileHelper;
import com.driot.bookplayer.helpers.SupportedFilesHelper;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

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
 *
 * The suggestion only fires for a folder that looks like a clean, dedicated single-book folder:
 * no subfolders next to the picked file, no ZIP/M4B/archive siblings (each already a complete
 * book on its own - their presence means several distinct books share this folder, not chapters
 * of one), and not a well-known shared/generic folder (Download, Music, a storage root, etc. -
 * see {@link #isGenericSharedFolder}) where unrelated single files commonly pile up.
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
        String path;
        try {
            // FileHelper.processUri() is known to throw for some provider-specific document id
            // formats it doesn't recognize (e.g. a Samsung Downloads-provider id shaped like
            // "msf:1234567" instead of a plain numeric row id, which blows up
            // Long.valueOf(id) with a NumberFormatException) - every other caller in this
            // codebase already wraps it for exactly that reason (see
            // FileHelper.getRealPathFromURI, OpenWithHelper.handle); this one hadn't been.
            path = FileHelper.processUri(context, pickedUri);
        } catch (Exception e) {
            myLogEE(e, "resolvePickedFile: FileHelper.processUri failed for " + pickedUri);
            path = null;
        }
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

        if (isGenericSharedFolder(parentDir)) {
            // A well-known shared/generic folder (or a storage volume's own root) commonly holds
            // many unrelated single files dropped there by different apps - "2+ audio files in
            // the same folder" stops being a meaningful "these are chapters of one book" signal
            // there, unlike a dedicated per-book folder. Matched by name only, so a real per-book
            // folder *inside* one of these (e.g. Download/My Audiobook/) is unaffected - only
            // files sitting loose directly in the generic folder itself are skipped.
            myLogD("detectSiblingsOf() - parentDir [" + parentDir + "] is a generic/shared folder - "
                    + "skipping the whole-book suggestion");
            return new Result(pickedFile, null, 0);
        }

        File[] siblings = parentDir.listFiles();
        if (siblings == null) {
            myLogW("detectSiblingsOf() - listFiles() returned null for [" + parentDir + "] (permission issue?)");
            return new Result(pickedFile, null, 0);
        }

        int trackCount = 0;
        for (File f : siblings) {
            if (f.isDirectory()) {
                // A subfolder sitting right next to the picked file means this directory isn't a
                // clean single-book folder (chapters don't usually have their own sub-folders) -
                // more likely a parent directory holding several distinct books/albums side by
                // side. Bail rather than risk merging unrelated content.
                myLogD("detectSiblingsOf() - found subfolder [" + f.getName() + "] in [" + parentDir
                        + "] - skipping the whole-book suggestion");
                return new Result(pickedFile, null, 0);
            }
            String specialType = SupportedFilesHelper.getSpecialType(f.getName());
            if (SupportedFilesHelper.isBundleSpecial(specialType) || SupportedFilesHelper.isM4bSpecial(specialType)) {
                // A ZIP/M4B/archive sibling is itself a complete, self-contained book - its
                // presence means the folder holds multiple distinct books, not chapters of one,
                // even if some of those siblings happen to be plain audio files too.
                myLogD("detectSiblingsOf() - found a self-contained book [" + f.getName() + "] in ["
                        + parentDir + "] - skipping the whole-book suggestion");
                return new Result(pickedFile, null, 0);
            }
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

    // Android's own standard public directories, matched by name (case-insensitively) wherever
    // they happen to live (primary storage, an SD card, etc.) - these are exactly the folders
    // various apps (browsers, messengers, podcast/download managers...) dump single unrelated
    // files into by default, as opposed to a folder the user or an app deliberately organized as
    // one-book-per-folder.
    private static final Set<String> GENERIC_FOLDER_NAMES = new HashSet<>(Arrays.asList(
            Environment.DIRECTORY_DOWNLOADS.toLowerCase(Locale.ROOT),
            "download", // singular - the actual on-disk folder name on many devices
            Environment.DIRECTORY_MUSIC.toLowerCase(Locale.ROOT),
            Environment.DIRECTORY_PODCASTS.toLowerCase(Locale.ROOT),
            Environment.DIRECTORY_RINGTONES.toLowerCase(Locale.ROOT),
            Environment.DIRECTORY_NOTIFICATIONS.toLowerCase(Locale.ROOT),
            Environment.DIRECTORY_ALARMS.toLowerCase(Locale.ROOT),
            Environment.DIRECTORY_DCIM.toLowerCase(Locale.ROOT),
            Environment.DIRECTORY_MOVIES.toLowerCase(Locale.ROOT),
            Environment.DIRECTORY_PICTURES.toLowerCase(Locale.ROOT),
            Environment.DIRECTORY_DOCUMENTS.toLowerCase(Locale.ROOT)));

    private static boolean isGenericSharedFolder(File dir) {
        String name = dir.getName();
        if (name != null && GENERIC_FOLDER_NAMES.contains(name.toLowerCase(Locale.ROOT))) {
            return true;
        }
        // A storage volume's own root (internal storage) - same "many unrelated files" reasoning.
        try {
            if (dir.getCanonicalPath().equals(Environment.getExternalStorageDirectory().getCanonicalPath())) {
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
