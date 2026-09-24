package com.driot.bookplayer.importexport;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.BackupManager;
import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.helpers.StorageInfoCacheHelper;
import com.driot.bookplayer.podcasts.PodcastHelper;
import com.driot.bookplayer.radio.RadioHelper;
import com.driot.bookplayer.services.archives.PathSafe;
import com.driot.bookplayer.utils.log.KanLogger;
import com.google.gson.Gson;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * "Full Backup" - unlike the regular JSON export/OS-backup-safety-net (which deliberately never
 * include local audio/cover files, see data_extraction_rules.xml), this packs everything - the
 * actual audio files, covers, and a full metadata export - into a single ZIP file written via
 * ACTION_CREATE_DOCUMENT, the same one-shot single-file API the classic JSON backup already uses.
 *
 * This was originally built around ACTION_OPEN_DOCUMENT_TREE (a persistent grant over a whole
 * folder, letting the destination mirror the real folder structure) - confirmed on a real newer
 * Android device, that API is blocked by the OS at the bare root of a storage volume (SD card,
 * USB): the system's own picker refuses the selection before the app is even called back, which
 * looked like "the app can't do this" with no way to route around it in code. A single-file
 * ACTION_CREATE_DOCUMENT has none of that restriction (confirmed by the classic backup working
 * fine at that same root) and needs no standing permission grant at all - hence one zip instead
 * of a mirrored folder tree. Bonus: a single file can also go through the share sheet (Quick
 * Share, email, any app), not just SAF destinations.
 */
public class FullBackupHelper {

    private static final String TAG = "FullBackupHelper";
    private static final String[] BACKUP_SUBFOLDERS = {
            Var.FOLDER_UNZIPPED, Var.FOLDER_DOWNLOAD, Var.FOLDER_LINKED_DEFAULT,
            Var.FOLDER_IMAGE, Var.FOLDER_CACHED_IMAGE
    };
    // BACKUP_SUBFOLDERS minus FOLDER_UNZIPPED - used where the size of the actual audiobooks is
    // read from StorageInfoCacheHelper's cache instead (see computeEstimate), since these
    // remaining kinds have no equivalent cache but are typically much smaller/faster to scan
    // live anyway.
    private static final String[] OTHER_BACKUP_SUBFOLDERS = {
            Var.FOLDER_DOWNLOAD, Var.FOLDER_LINKED_DEFAULT, Var.FOLDER_IMAGE, Var.FOLDER_CACHED_IMAGE
    };
    // Written first, before the (potentially many-GB) audio/cover entries, specifically so a
    // restore preview only has to read this one small entry instead of streaming past
    // everything else to reach backup.json (which is still written, right after this, for the
    // actual restore later - this manifest only ever feeds the preview screen).
    private static final String MANIFEST_ENTRY_NAME = "backup_manifest.json";
    private static final String BACKUP_JSON_ENTRY_NAME = "backup.json";
    // Partial backup, individually-selected books' actual audio - namespaced by folder id so
    // two books with identically-named tracks can't collide, and so restore knows which Folder
    // record (from backup.json, parsed first) each entry belongs to.
    private static final String BOOK_FILES_ENTRY_PREFIX = "book_files/";
    // Each BACKUP_SUBFOLDERS kind can physically exist in two places - internal storage or a
    // removable SD card's app-reserved area, chosen per book at import time via the "use SD
    // card" setting (see StorageHelper.getPreferredBaseDir) - so FULL backup has to check both
    // rather than assume everything lives under getFilesDir(). Recorded in the entry name so
    // restore can put each one back in the same kind of place (see resolveBackupSubfolderDest).
    private static final String INTERNAL_PREFIX = "internal/";
    private static final String SDCARD_PREFIX = "sdcard/";

    /** SUCCESS/PARTIAL_FAILURE/FAILED mirror the old boolean return (true only for SUCCESS);
     *  CANCELLED is new - the user hit Cancel/back mid-operation. Checked cooperatively via an
     *  AtomicBoolean passed in by the caller (pass null to make an operation uncancellable). */
    public enum Result {
        SUCCESS, PARTIAL_FAILURE, FAILED, CANCELLED
    }

    private static void checkCancelled(AtomicBoolean cancelled) {
        if (cancelled != null && cancelled.get()) {
            throw new CancellationException("Full backup/restore cancelled by user");
        }
    }

    public static class Estimate {
        public long totalBytes;
        public long totalAudioDurationMs;
    }

    public static Estimate computeEstimate(Context context) {
        Estimate e = new Estimate();

        // "unzipped" (the actual audiobooks) dominates this total and is the slow part to
        // measure fresh - reuse StorageInfoCacheHelper's pre-computed per-book cache (same one
        // CleanMemoryFragment's ViewModel reads, refreshed in the background) instead of a live
        // recursive scan here.
        for (long size : StorageInfoCacheHelper.getCachedFolderSizes(true).values()) {
            e.totalBytes += size;
        }
        if (StorageHelper.isExternalSDCardAvailable(context)) {
            for (long size : StorageInfoCacheHelper.getCachedFolderSizes(false).values()) {
                e.totalBytes += size;
            }
        }

        // The rest (download/linked/images/cached_images) are typically far smaller, so a live
        // scan here is fine - no equivalent cache exists for them.
        // BUG FIXED: this used to only check new File(context.getFilesDir(), sub) for every
        // BACKUP_SUBFOLDERS kind including "unzipped" - books imported with "use SD card" on
        // live under a completely different physical directory (see
        // StorageHelper.getPreferredBaseDir) and were silently invisible to this estimate (and,
        // before this fix, to runFullBackup() itself - see there for the matching fix).
        for (String sub : OTHER_BACKUP_SUBFOLDERS) {
            File internalDir = StorageHelper.getFolder(context, sub, false);
            if (internalDir.exists()) {
                e.totalBytes += StorageHelper.getFolderSize(internalDir);
            }
        }
        if (StorageHelper.isExternalSDCardAvailable(context)) {
            for (String sub : OTHER_BACKUP_SUBFOLDERS) {
                File sdDir = StorageHelper.getFolder(context, sub, true);
                if (sdDir.exists()) {
                    e.totalBytes += StorageHelper.getFolderSize(sdDir);
                }
            }
        }
        File dbFile = context.getDatabasePath(DatabaseClient.DATABASE_NAME);
        if (dbFile.exists()) {
            e.totalBytes += dbFile.length();
        }

        List<Folder> folders = AppDatabase.getDatabase(context).folderDao().getAll();
        for (Folder f : folders) {
            e.totalAudioDurationMs += (long) f.getDuration();
        }
        return e;
    }

    public interface ProgressListener {
        void onProgress(long copiedBytes, long totalBytes, String currentFileName);
    }

    /** Streams every backup-relevant local folder plus a full metadata export into a single zip
     * at destFileUri. Keeps going on individual file failures rather than aborting the whole
     * backup. Audio/images are already compressed, so the zip uses no further compression - it's
     * purely a container here, not a size-reduction step.
     * <p>
     * {@code cancelled} is polled between files (and periodically within a large file) - pass
     * null for an uncancellable run. On cancellation the partially-written destFileUri is
     * deleted rather than left behind as a broken/incomplete zip. */
    public static Result runFullBackup(Context context, Uri destFileUri, AtomicBoolean cancelled,
            ProgressListener listener) {
        Estimate estimate = computeEstimate(context);
        long[] copiedSoFar = { 0 };
        boolean allOk = true;

        try (OutputStream rawOut = context.getContentResolver().openOutputStream(destFileUri);
                ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(rawOut))) {
            if (rawOut == null) {
                return Result.FAILED;
            }
            zos.setLevel(Deflater.NO_COMPRESSION);

            // Metadata first (manifest + full JSON): both are tiny next to the audio folders
            // below, and writing them first means a restore preview never has to scan past any
            // audio to find them.
            try {
                checkCancelled(cancelled);
                BackupManager backupManager = new BackupManager(context);
                String json = backupManager.exportToJson(true, true, true, true, true, true);
                BackupManager.BackupData data = backupManager.inspectJson(json);

                RestorePreview manifest = new RestorePreview();
                if (data != null) {
                    populatePreviewFromBackupData(manifest, data);
                }
                byte[] manifestBytes = new Gson().toJson(manifest).getBytes(StandardCharsets.UTF_8);
                zos.putNextEntry(new ZipEntry(MANIFEST_ENTRY_NAME));
                zos.write(manifestBytes);
                zos.closeEntry();
                copiedSoFar[0] += manifestBytes.length;
                if (listener != null) {
                    listener.onProgress(copiedSoFar[0], estimate.totalBytes, MANIFEST_ENTRY_NAME);
                }

                byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
                zos.putNextEntry(new ZipEntry(BACKUP_JSON_ENTRY_NAME));
                zos.write(jsonBytes);
                zos.closeEntry();
                copiedSoFar[0] += jsonBytes.length;
                if (listener != null) {
                    listener.onProgress(copiedSoFar[0], estimate.totalBytes, BACKUP_JSON_ENTRY_NAME);
                }
            } catch (CancellationException e) {
                throw e;
            } catch (Exception e) {
                KanLogger.myLogEE(e, TAG, "writing manifest/backup.json into zip failed");
                allOk = false;
            }

            // BUG FIXED: this used to only zip new File(context.getFilesDir(), sub), silently
            // dropping every book that lived on a removable SD card instead (see computeEstimate
            // for the same fix and its explanation). Internal/SD each get their own entry prefix
            // so runFullRestore() can put each file back in the same kind of place.
            for (String sub : BACKUP_SUBFOLDERS) {
                File internalDir = StorageHelper.getFolder(context, sub, false);
                if (!internalDir.exists())
                    continue;
                if (!zipDirRecursive(internalDir, INTERNAL_PREFIX + sub + "/", zos, estimate.totalBytes, copiedSoFar,
                        cancelled, listener)) {
                    allOk = false;
                }
            }
            if (StorageHelper.isExternalSDCardAvailable(context)) {
                for (String sub : BACKUP_SUBFOLDERS) {
                    File sdDir = StorageHelper.getFolder(context, sub, true);
                    if (!sdDir.exists())
                        continue;
                    if (!zipDirRecursive(sdDir, SDCARD_PREFIX + sub + "/", zos, estimate.totalBytes, copiedSoFar,
                            cancelled, listener)) {
                        allOk = false;
                    }
                }
            }
        } catch (CancellationException e) {
            KanLogger.myLogI(TAG, "runFullBackup cancelled by user - deleting partial destination file");
            deleteQuietly(context, destFileUri);
            return Result.CANCELLED;
        } catch (Exception e) {
            KanLogger.myLogEE(e, TAG, "runFullBackup (zip) failed");
            return Result.FAILED;
        }

        return allOk ? Result.SUCCESS : Result.PARTIAL_FAILURE;
    }

    private static void deleteQuietly(Context context, Uri uri) {
        try {
            context.getContentResolver().delete(uri, null, null);
        } catch (Exception e) {
            KanLogger.myLogEE(e, TAG, "could not delete cancelled backup's partial file: " + uri);
        }
    }

    // -----------------------------------------------------------------------------------------
    // Partial backup: pick which of the 6 metadata categories to include, plus - independently -
    // opt specific books' actual audio files in one by one. Unlike runFullBackup (which zips the
    // whole BACKUP_SUBFOLDERS wholesale), this only ever touches the files for books explicitly
    // selected, so it needs to resolve each candidate book to its own on-disk directory rather
    // than a folder-kind-level bucket.
    // -----------------------------------------------------------------------------------------

    public static class BackupSelection {
        public boolean includePreferences = true;
        public boolean includeRadios = true;
        public boolean includePodcasts = true;
        public boolean includeLibrivox = true;
        public boolean includeBookProgress = true;
        public boolean includePodcastHistory = true;
        // Empty by default - "include book files" starts unchecked, and even once checked the
        // per-book list itself starts with nothing ticked (see BackupActivity).
        public final java.util.Set<Long> includedBookFileFolderIds = new java.util.HashSet<>();
    }

    /** One book eligible to have its actual audio bundled into a partial backup - see
     *  listBookFileCandidates() below for what makes a book eligible. */
    public static class BookFileCandidate {
        public long folderId;
        public String name;
        public long sizeBytes;
        public long durationMs;
        // Same copy/link icon (and colors) ModifyFolderActivity shows next to a book's own
        // storage-location line - Folder.getCopyOrLinkIconRes()/isReservedLocation().
        public int copyOrLinkIconRes;
        public boolean reservedLocation;
        // File-resolvable (copy, internal or SD reserved) books get this set, and the zip writer
        // uses the fast whole-directory path (zipDirRecursive). Null for a link/SAF book - those
        // are zipped one track at a time instead, via zikFiles below (see runPartialBackup).
        File dir;
        List<com.driot.bookplayer.db.ZikFile> zikFiles;
    }

    /** Every book with at least one track is eligible now, regardless of where its files live -
     *  copy (internal/SD reserved) or link (shared storage or an external SAF grant): "we backup
     *  everything" per how this was actually meant, not just what happens to be a plain
     *  java.io.File. Sizing is a straight sum of each ZikFile's own cached size field (already
     *  populated by StorageInfoCacheHelper at app startup) - a pure DB read, no filesystem access
     *  at all, so it's both correct for every location type and the fastest option rather than
     *  scanning or even looking up a folder-size cache that only ever covered "copy" books
     *  anyway. */
    public static List<BookFileCandidate> listBookFileCandidates(Context context) {
        List<BookFileCandidate> out = new java.util.ArrayList<>();
        AppDatabase db = AppDatabase.getDatabase(context);
        List<Folder> folders = db.folderDao().getAll();
        for (Folder f : folders) {
            if (f.getPath() == null) {
                continue;
            }
            List<com.driot.bookplayer.db.ZikFile> zikFiles = db.zikFileDao().getZikFiles(f.getId());
            if (zikFiles.isEmpty()) {
                continue; // nothing to back up for this book
            }
            long sizeBytes = 0;
            for (com.driot.bookplayer.db.ZikFile zf : zikFiles) {
                sizeBytes += (long) zf.getSize();
            }

            BookFileCandidate c = new BookFileCandidate();
            c.folderId = f.getId();
            c.name = f.getName();
            c.dir = resolveInternalFolderDir(Uri.parse(f.getPath())); // null for link/SAF books
            c.zikFiles = zikFiles;
            c.sizeBytes = sizeBytes;
            c.durationMs = (long) f.getDuration();
            c.copyOrLinkIconRes = f.getCopyOrLinkIconRes(context);
            c.reservedLocation = f.isReservedLocation(context);
            out.add(c);
        }
        return out;
    }

    /** Only a file:// URI or a raw absolute path resolves to something this app can read/write
     *  directly with java.io.File - anything else (content:// SAF) is a linked book, out of
     *  scope for per-book file inclusion. Mirrors the scheme handling in
     *  UriHelper.getDocumentFileFromAnyUri, kept independent here since that one also handles
     *  SAF trees/documents this method deliberately never needs to. */
    private static File resolveInternalFolderDir(Uri uri) {
        String scheme = uri.getScheme();
        if ("file".equalsIgnoreCase(scheme)) {
            String path = uri.getPath();
            return path != null ? new File(path) : null;
        }
        if (scheme == null) {
            String s = uri.toString();
            if (!s.isEmpty() && s.startsWith("/")) {
                return new File(s);
            }
        }
        return null;
    }

    public static class PartialEstimate {
        public long totalBytes;
        public long totalAudioDurationMs;
    }

    /** Real (not guessed) JSON size for the current selection, so toggling a checkbox shows an
     *  honest number - metadata export is fast even for a large library, so recomputing it per
     *  toggle is fine. Duration reflects every book's recorded progress when Book Progress is
     *  included, independent of which (if any) individual books also have their files ticked -
     *  it answers "how much listening progress will I get back", which is unaffected by whether
     *  the audio itself rides along. */
    public static PartialEstimate computePartialEstimate(Context context, BackupSelection selection) {
        PartialEstimate e = new PartialEstimate();

        try {
            BackupManager backupManager = new BackupManager(context);
            String json = backupManager.exportToJson(selection.includePreferences, selection.includeRadios,
                    selection.includePodcasts, selection.includeLibrivox, selection.includeBookProgress,
                    selection.includePodcastHistory);
            e.totalBytes += json.getBytes(StandardCharsets.UTF_8).length;
        } catch (Exception ex) {
            KanLogger.myLogEE(ex, TAG, "computePartialEstimate: exportToJson failed");
        }

        // Both size AND duration only ever reflect the individually-selected books - showing a
        // duration for books whose audio isn't actually included would be misleading (there's
        // nothing dynamic about it otherwise: with no book selected, it'd always show the same
        // "total library duration" number regardless of what's actually going into the backup).
        if (!selection.includedBookFileFolderIds.isEmpty()) {
            for (BookFileCandidate c : listBookFileCandidates(context)) {
                if (selection.includedBookFileFolderIds.contains(c.folderId)) {
                    e.totalBytes += c.sizeBytes;
                    e.totalAudioDurationMs += c.durationMs;
                }
            }
        }

        return e;
    }

    /** Writes a metadata-selective backup, with the actual audio bundled in only for the
     *  individually-opted-in books - everything else about the zip (manifest first, then
     *  backup.json, same cancellation/progress handling) matches runFullBackup so the same
     *  restore path (see runFullRestore) reads either kind transparently. */
    public static Result runPartialBackup(Context context, Uri destFileUri, BackupSelection selection,
            AtomicBoolean cancelled, ProgressListener listener) {
        PartialEstimate estimate = computePartialEstimate(context, selection);
        long[] copiedSoFar = { 0 };
        boolean allOk = true;

        try (OutputStream rawOut = context.getContentResolver().openOutputStream(destFileUri);
                ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(rawOut))) {
            if (rawOut == null) {
                return Result.FAILED;
            }
            zos.setLevel(Deflater.NO_COMPRESSION);

            try {
                checkCancelled(cancelled);
                BackupManager backupManager = new BackupManager(context);
                String json = backupManager.exportToJson(selection.includePreferences, selection.includeRadios,
                        selection.includePodcasts, selection.includeLibrivox, selection.includeBookProgress,
                        selection.includePodcastHistory);
                BackupManager.BackupData data = backupManager.inspectJson(json);

                RestorePreview manifest = new RestorePreview();
                if (data != null) {
                    populatePreviewFromBackupData(manifest, data);
                }
                manifest.bookFilesIncludedCount = selection.includedBookFileFolderIds.size();

                byte[] manifestBytes = new Gson().toJson(manifest).getBytes(StandardCharsets.UTF_8);
                zos.putNextEntry(new ZipEntry(MANIFEST_ENTRY_NAME));
                zos.write(manifestBytes);
                zos.closeEntry();
                copiedSoFar[0] += manifestBytes.length;
                if (listener != null) {
                    listener.onProgress(copiedSoFar[0], estimate.totalBytes, MANIFEST_ENTRY_NAME);
                }

                byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
                zos.putNextEntry(new ZipEntry(BACKUP_JSON_ENTRY_NAME));
                zos.write(jsonBytes);
                zos.closeEntry();
                copiedSoFar[0] += jsonBytes.length;
                if (listener != null) {
                    listener.onProgress(copiedSoFar[0], estimate.totalBytes, BACKUP_JSON_ENTRY_NAME);
                }
            } catch (CancellationException e) {
                throw e;
            } catch (Exception e) {
                KanLogger.myLogEE(e, TAG, "writing manifest/backup.json into partial zip failed");
                allOk = false;
            }

            if (!selection.includedBookFileFolderIds.isEmpty()) {
                for (BookFileCandidate c : listBookFileCandidates(context)) {
                    if (!selection.includedBookFileFolderIds.contains(c.folderId)) {
                        continue;
                    }
                    checkCancelled(cancelled);
                    String prefix = BOOK_FILES_ENTRY_PREFIX + c.folderId + "/";
                    if (c.dir != null && c.dir.exists()) {
                        // Copy book: a real directory, zip it whole (fast path).
                        if (!zipDirRecursive(c.dir, prefix, zos, estimate.totalBytes, copiedSoFar, cancelled,
                                listener)) {
                            allOk = false;
                        }
                    } else if (c.zikFiles != null) {
                        // Link/SAF book: no single directory to hand to zipDirRecursive - stream
                        // each track through its own resolved content:// (or file://) Uri
                        // instead. "We back up everything" regardless of where a book's files
                        // actually live - see listBookFileCandidates.
                        if (!zipBookTracksByUri(context, c, prefix, zos, estimate.totalBytes, copiedSoFar, cancelled,
                                listener)) {
                            allOk = false;
                        }
                    }
                    // else: book was deleted/moved since the list was built - skip, not fatal.
                }
            }
        } catch (CancellationException e) {
            KanLogger.myLogI(TAG, "runPartialBackup cancelled by user - deleting partial destination file");
            deleteQuietly(context, destFileUri);
            return Result.CANCELLED;
        } catch (Exception e) {
            KanLogger.myLogEE(e, TAG, "runPartialBackup (zip) failed");
            return Result.FAILED;
        }

        return allOk ? Result.SUCCESS : Result.PARTIAL_FAILURE;
    }

    /** What's actually inside a Full Backup zip, for the "review before you restore" screen -
     *  mirrors computeEstimate()'s shape (size + audio duration) plus the same category counts
     *  the classic restore screen shows as checkboxes, so the user sees what they're about to
     *  overwrite their library with before confirming. {@code valid} is false when the picked
     *  file has no backup.json entry (not a Full Backup zip at all). */
    public static class RestorePreview {
        public boolean valid;
        public long timestamp;
        public long zipTotalBytes;
        public long totalAudioDurationMs;
        public int bookCount;
        public int zikFileCount;
        public int librivoxSourceCount;
        public int radioCount;
        public int podcastCount;
        public int podcastHistoryCount;
        public boolean hasPreferences;
        // How many books' actual audio was individually opted into this backup (partial mode
        // only - 0 for a metadata-only partial backup, and for FULL this is left at 0 too since
        // FULL zips every book's files wholesale rather than enumerating them one by one - see
        // runFullBackup vs runPartialBackup).
        public int bookFilesIncludedCount;
    }

    private static void populatePreviewFromBackupData(RestorePreview preview, BackupManager.BackupData data) {
        preview.valid = true;
        preview.timestamp = data.timestamp;
        preview.bookCount = data.folders != null ? data.folders.size() : 0;
        preview.zikFileCount = data.zikFiles != null ? data.zikFiles.size() : 0;
        preview.librivoxSourceCount = data.bookSources != null ? data.bookSources.size() : 0;
        preview.hasPreferences = data.preferences != null && !data.preferences.isEmpty();
        if (data.folders != null) {
            for (Folder f : data.folders) {
                preview.totalAudioDurationMs += (long) f.getDuration();
            }
        }
        // Radio/podcast fields only exist on the full flavor's BackupData subclass - these
        // helpers are the same per-flavor pattern already used for the classic restore screen's
        // hasRadios/hasPodcasts checks (real counts on full, always 0 on pure).
        preview.radioCount = RadioHelper.backupDataRadioCount(data);
        preview.podcastCount = PodcastHelper.backupDataPodcastCount(data);
        preview.podcastHistoryCount = PodcastHelper.backupDataEpisodeHistoryCount(data);
    }

    /** Fast path: the manifest is written first (see runFullBackup), so it's normally the very
     *  first zip entry - read just that one entry and we're done, regardless of how many GB of
     *  audio follow it. Falls back to the old slow full-scan-to-backup.json behavior for a zip
     *  made before the manifest existed, or if the first entry isn't the manifest for any other
     *  reason. Call off the main thread either way - the fallback path still reads the whole
     *  file. */
    public static RestorePreview peekRestorePreview(Context context, Uri srcZipUri) {
        RestorePreview preview = new RestorePreview();
        preview.zipTotalBytes = estimateZipSize(context, srcZipUri);

        try (InputStream rawIn = context.getContentResolver().openInputStream(srcZipUri);
                ZipInputStream zis = new ZipInputStream(new BufferedInputStream(rawIn))) {
            if (rawIn == null) {
                return preview;
            }
            ZipEntry first = zis.getNextEntry();
            if (first != null && !first.isDirectory() && MANIFEST_ENTRY_NAME.equals(first.getName())) {
                String manifestJson = readEntryAsString(zis);
                RestorePreview fromManifest = new Gson().fromJson(manifestJson, RestorePreview.class);
                if (fromManifest != null) {
                    fromManifest.valid = true;
                    fromManifest.zipTotalBytes = preview.zipTotalBytes;
                    return fromManifest;
                }
            }
        } catch (Exception e) {
            KanLogger.myLogEE(e, TAG, "peekRestorePreview (fast path) failed - falling back to full scan");
        }

        return peekRestorePreviewSlow(context, srcZipUri, preview);
    }

    /** Old behavior, kept only as a fallback: streams through the whole zip discarding the
     *  (potentially huge) audio/cover entries without writing them anywhere, until it reaches
     *  backup.json. */
    private static RestorePreview peekRestorePreviewSlow(Context context, Uri srcZipUri, RestorePreview preview) {
        String json = null;
        try (InputStream rawIn = context.getContentResolver().openInputStream(srcZipUri);
                ZipInputStream zis = new ZipInputStream(new BufferedInputStream(rawIn))) {
            if (rawIn == null) {
                return preview;
            }
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory() && BACKUP_JSON_ENTRY_NAME.equals(entry.getName())) {
                    json = readEntryAsString(zis);
                    zis.closeEntry();
                    break;
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            KanLogger.myLogEE(e, TAG, "peekRestorePreviewSlow (zip scan) failed");
            return preview;
        }

        if (json == null) {
            return preview; // no backup.json entry either - not a Full Backup zip
        }

        try {
            BackupManager backupManager = new BackupManager(context);
            BackupManager.BackupData data = backupManager.inspectJson(json);
            if (data != null) {
                populatePreviewFromBackupData(preview, data);
            }
        } catch (Exception e) {
            KanLogger.myLogEE(e, TAG, "peekRestorePreviewSlow (parse) failed");
        }

        return preview;
    }

    /**
     * The other half of runFullBackup(): reads a Full Backup zip back in - extracting the
     * audio/cover/cache folders into place under getFilesDir() and importing the embedded
     * backup.json - so this actually restores a library, not just archives it. Handles a mixed
     * copy/link library uniformly: copied books get their bytes back from the zip's file
     * entries; linked books just need their DB records back, which backup.json covers the same
     * way the classic JSON restore does (their actual audio lives outside app storage and was
     * never touched by the uninstall in the first place - though Android does revoke this app's
     * persisted SAF folder-access grant on uninstall, so a linked source may need re-granting
     * through its picker afterward; this method has no way to do that for you).
     * <p>
     * Deliberately restores every backup.json section (prefs/radios/podcasts/librivox/
     * bookProgress/podcastHistory) rather than offering the classic restore's per-section
     * checkboxes - a Full Backup is meant to be a single "get everything back" unit.
     * Streamed single-pass (no separate "peek the date first" step): the file entries can be
     * many GB, so reading the zip twice just to preview it before confirming isn't worth doubling
     * the I/O for what a static confirmation message already covers.
     * <p>
     * {@code cancelled} is polled between entries (and periodically within a large file) - pass
     * null for an uncancellable run. Cancellation is only honored during the file-extraction
     * phase; once that's done and importFromJson (a single DB write, not chunked/interruptible)
     * starts, it always runs to completion rather than risk leaving a half-imported database.
     * That means a cancel mid-restore leaves the DB/prefs completely untouched - safe - but some
     * audio/cover files may already have been overwritten by the time cancellation is noticed;
     * there's no transactional rollback for those, so a cancelled restore's files should be
     * treated as a partial mix of old and new, not simply "nothing happened". */
    public static Result runFullRestore(Context context, Uri srcZipUri, AtomicBoolean cancelled,
            ProgressListener listener) {
        long totalBytes = estimateZipSize(context, srcZipUri);
        long[] copiedSoFar = { 0 };
        boolean allOk = true;
        String backupJson = null;
        // Populated as soon as backup.json is seen (always written before any book_files/*
        // entry, see runPartialBackup) so a partial backup's individually-selected books can be
        // extracted straight to the exact path their restored Folder record will point at.
        java.util.Map<Long, File> folderIdToDir = new java.util.HashMap<>();
        // Folders extracted to a NEW reserved-link location because their original (SAF/shared)
        // path wasn't writable - these need their DB path (and their ZikFiles') rewritten once
        // importFromJson has (re)inserted them, see the fixup after it below.
        java.util.Set<Long> relocatedFolderIds = new java.util.HashSet<>();

        try (InputStream rawIn = context.getContentResolver().openInputStream(srcZipUri);
                ZipInputStream zis = new ZipInputStream(new BufferedInputStream(rawIn))) {
            if (rawIn == null) {
                return Result.FAILED;
            }

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                checkCancelled(cancelled); // between entries
                String name = entry.getName();
                if (!entry.isDirectory()) {
                    if (BACKUP_JSON_ENTRY_NAME.equals(name)) {
                        backupJson = readEntryAsString(zis);
                        copiedSoFar[0] += backupJson.length();
                        if (listener != null) {
                            listener.onProgress(copiedSoFar[0], totalBytes, BACKUP_JSON_ENTRY_NAME);
                        }
                        FolderIdToDirResult resolved = buildFolderIdToDirMap(context, backupJson);
                        folderIdToDir.putAll(resolved.dirs);
                        relocatedFolderIds.addAll(resolved.relocatedFolderIds);
                    } else if (name.startsWith(BOOK_FILES_ENTRY_PREFIX)) {
                        if (!extractBookFileEntry(zis, name, folderIdToDir, totalBytes, copiedSoFar, cancelled,
                                listener)) {
                            allOk = false;
                        }
                    } else {
                        ResolvedSubfolderDest dest = resolveBackupSubfolderDest(context, name);
                        if (dest != null) {
                            if (!extractEntry(dest.baseDir, zis, dest.relativePath, totalBytes, copiedSoFar, cancelled,
                                    listener)) {
                                allOk = false;
                            }
                        }
                        // Anything else (the manifest, or an unrecognized entry from a foreign/
                        // future zip) is skipped rather than failing the whole restore over it.
                    }
                }
                zis.closeEntry();
            }
        } catch (CancellationException e) {
            KanLogger.myLogI(TAG, "runFullRestore cancelled by user during file extraction - DB/prefs untouched");
            return Result.CANCELLED;
        } catch (Exception e) {
            KanLogger.myLogEE(e, TAG, "runFullRestore (unzip) failed");
            return Result.FAILED;
        }

        if (backupJson == null) {
            KanLogger.myLogE(TAG, "runFullRestore: no backup.json entry found - not a Full Backup zip");
            return Result.FAILED;
        }

        // Past this point cancellation is no longer honored - see javadoc above.
        try {
            BackupManager backupManager = new BackupManager(context);
            backupManager.importFromJson(backupJson, true, true, true, true, true, true);
        } catch (Exception e) {
            KanLogger.myLogEE(e, TAG, "runFullRestore: importFromJson failed");
            allOk = false;
        }

        // Folder/ZikFile rows are reinserted with their ORIGINAL ids intact (see
        // BaseBackupManager.importBaseData's own comment on this), which is exactly what makes
        // this safe: relocatedFolderIds was built before import from the same ids the zip's
        // book_files/ entries and this restore both use throughout.
        if (!relocatedFolderIds.isEmpty()) {
            fixUpRelocatedFolderPaths(context, folderIdToDir, relocatedFolderIds);
        }

        return allOk ? Result.SUCCESS : Result.PARTIAL_FAILURE;
    }

    /** For every link/SAF book whose files got relocated into this app's own reserved storage
     *  during extraction (see buildFolderIdToDirMap), points its now-restored Folder row - and
     *  each of its ZikFile rows - at the new location instead of the stale original one, so the
     *  book is immediately playable again rather than needing a manual re-add. Best-effort per
     *  folder: one folder's DB update failing doesn't stop the others. */
    private static void fixUpRelocatedFolderPaths(Context context, java.util.Map<Long, File> folderIdToDir,
            java.util.Set<Long> relocatedFolderIds) {
        AppDatabase db = AppDatabase.getDatabase(context);
        for (Long folderId : relocatedFolderIds) {
            try {
                File newDir = folderIdToDir.get(folderId);
                if (newDir == null || !newDir.exists()) {
                    continue; // nothing actually landed here (e.g. every track failed to resolve)
                }
                Folder folder = db.folderDao().getById(folderId);
                if (folder == null) {
                    continue; // this section wasn't included in the restore
                }
                folder.setPath(Uri.fromFile(newDir).toString());
                db.folderDao().update(folder);

                for (com.driot.bookplayer.db.ZikFile zf : db.zikFileDao().getZikFiles(folderId)) {
                    File restoredTrack = new File(newDir, zf.getName());
                    if (restoredTrack.exists()) {
                        zf.setPath(Uri.fromFile(restoredTrack).toString());
                        db.zikFileDao().update(zf);
                    }
                    // else: this particular track wasn't part of the backup (or failed to
                    // extract) - left pointing at its old, now-unreachable path, same as the
                    // classic JSON-only restore already leaves every link/SAF book today.
                }
            } catch (Exception e) {
                KanLogger.myLogEE(e, TAG, "fixUpRelocatedFolderPaths failed for folder " + folderId);
            }
        }
    }

    private static class ResolvedSubfolderDest {
        final File baseDir;
        final String relativePath;

        ResolvedSubfolderDest(File baseDir, String relativePath) {
            this.baseDir = baseDir;
            this.relativePath = relativePath;
        }
    }

    /** Recognizes an "internal/..." or "sdcard/..." BACKUP_SUBFOLDERS entry (see runFullBackup)
     *  and resolves where it should land: the same KIND of location it came from, via
     *  StorageHelper.getPreferredBaseDir() - which already falls back to internal storage on its
     *  own if the entry says "sdcard" but this device has no SD card (or none available) right
     *  now, so a restore onto a different device never gets stuck on a missing card. Returns
     *  null for anything that isn't one of these entries (e.g. the manifest, or a foreign zip's
     *  entry) - not book_files/... entries, which are resolved separately via backup.json's own
     *  Folder paths instead of this generic kind-based mapping. */
    private static ResolvedSubfolderDest resolveBackupSubfolderDest(Context context, String zipEntryName) {
        boolean sdcard;
        String rest;
        if (zipEntryName.startsWith(INTERNAL_PREFIX)) {
            sdcard = false;
            rest = zipEntryName.substring(INTERNAL_PREFIX.length());
        } else if (zipEntryName.startsWith(SDCARD_PREFIX)) {
            sdcard = true;
            rest = zipEntryName.substring(SDCARD_PREFIX.length());
        } else {
            return null;
        }
        for (String sub : BACKUP_SUBFOLDERS) {
            if (rest.equals(sub) || rest.startsWith(sub + "/")) {
                return new ResolvedSubfolderDest(StorageHelper.getPreferredBaseDir(context, sdcard), rest);
            }
        }
        return null;
    }

    private static String readEntryAsString(ZipInputStream zis) throws java.io.IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[64 * 1024];
        int len;
        while ((len = zis.read(buf)) > 0) {
            baos.write(buf, 0, len);
        }
        return baos.toString(StandardCharsets.UTF_8.name());
    }

    private static boolean extractEntry(File baseDir, ZipInputStream zis, String entryName, long totalBytes,
            long[] copiedSoFar, AtomicBoolean cancelled, ProgressListener listener) {
        try {
            File dest = PathSafe.safeResolve(baseDir, entryName);
            File parent = dest.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new java.io.IOException("mkdirs failed for " + parent);
            }
            try (OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[64 * 1024];
                int len;
                while ((len = zis.read(buf)) > 0) {
                    checkCancelled(cancelled); // within a large file too, not just between entries
                    out.write(buf, 0, len);
                    copiedSoFar[0] += len;
                    if (listener != null) {
                        listener.onProgress(copiedSoFar[0], totalBytes, dest.getName());
                    }
                }
            }
            return true;
        } catch (CancellationException e) {
            throw e;
        } catch (Exception e) {
            KanLogger.myLogEE(e, TAG, "extracting zip entry failed: " + entryName);
            return false;
        }
    }

    /** Parses backup.json far enough to know, for every Folder it contains, exactly which
     *  on-disk directory a restored book_files/&lt;folderId&gt;/... entry belongs in. Two cases:
     *  <ul>
     *  <li>The Folder's own saved path already resolves to a real writable directory (a "copy"
     *  book, internal or SD reserved) - files land right back where they were, matching what the
     *  restored Folder record's own path will still say, no further bookkeeping needed.</li>
     *  <li>It doesn't (a "link"/SAF book - the original external location's permission grant
     *  doesn't survive an uninstall/reinstall regardless, so writing back there isn't even
     *  possible) - files instead land in a fresh per-folder directory under this app's own
     *  reserved "link" storage, tracked in relocatedFolderIds so the caller can rewrite that
     *  Folder's (and its ZikFiles') path afterward - see runFullRestore's post-import fixup.</li>
     *  </ul>
     *  Swallows its own errors (returns an empty result) rather than failing the whole restore -
     *  a partial backup with no book_files entries at all (or a FULL backup, which never has
     *  any) never needed this in the first place. */
    private static class FolderIdToDirResult {
        final java.util.Map<Long, File> dirs = new java.util.HashMap<>();
        final java.util.Set<Long> relocatedFolderIds = new java.util.HashSet<>();
    }

    private static FolderIdToDirResult buildFolderIdToDirMap(Context context, String backupJson) {
        FolderIdToDirResult result = new FolderIdToDirResult();
        try {
            BackupManager backupManager = new BackupManager(context);
            BackupManager.BackupData data = backupManager.inspectJson(backupJson);
            if (data != null && data.folders != null) {
                for (Folder f : data.folders) {
                    if (f.getPath() == null) {
                        continue;
                    }
                    File dir = resolveInternalFolderDir(Uri.parse(f.getPath()));
                    if (dir != null) {
                        result.dirs.put(f.getId(), dir);
                        continue;
                    }
                    File linkedBase = StorageHelper.getDefaultLinkedFolder(context, false);
                    if (linkedBase != null) {
                        result.dirs.put(f.getId(), new File(linkedBase, String.valueOf(f.getId())));
                        result.relocatedFolderIds.add(f.getId());
                    }
                    // else: no writable fallback available either (shouldn't normally happen,
                    // external-files-dir is always present) - this folder's book_files entries,
                    // if any, will be skipped by extractBookFileEntry same as before this fix.
                }
            }
        } catch (Exception e) {
            KanLogger.myLogEE(e, TAG, "buildFolderIdToDirMap failed - book_files entries (if any) will be skipped");
        }
        return result;
    }

    private static boolean extractBookFileEntry(ZipInputStream zis, String entryName,
            java.util.Map<Long, File> folderIdToDir, long totalBytes, long[] copiedSoFar, AtomicBoolean cancelled,
            ProgressListener listener) {
        try {
            String rest = entryName.substring(BOOK_FILES_ENTRY_PREFIX.length()); // "<folderId>/<relPath...>"
            int slash = rest.indexOf('/');
            if (slash < 0) {
                return false;
            }
            long folderId;
            try {
                folderId = Long.parseLong(rest.substring(0, slash));
            } catch (NumberFormatException nfe) {
                return false;
            }
            File baseDir = folderIdToDir.get(folderId);
            if (baseDir == null) {
                // backup.json didn't carry a Folder for this id (or its path wasn't an internal
                // one) - not fatal, just nothing to restore this entry against.
                KanLogger.myLogW(TAG, "runFullRestore: no destination for " + entryName + " - skipping");
                return true;
            }

            File dest = PathSafe.safeResolve(baseDir, rest.substring(slash + 1));
            File parent = dest.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new java.io.IOException("mkdirs failed for " + parent);
            }
            try (OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[64 * 1024];
                int len;
                while ((len = zis.read(buf)) > 0) {
                    checkCancelled(cancelled);
                    out.write(buf, 0, len);
                    copiedSoFar[0] += len;
                    if (listener != null) {
                        listener.onProgress(copiedSoFar[0], totalBytes, dest.getName());
                    }
                }
            }
            return true;
        } catch (CancellationException e) {
            throw e;
        } catch (Exception e) {
            KanLogger.myLogEE(e, TAG, "extracting book_files entry failed: " + entryName);
            return false;
        }
    }

    /** The zip was written with NO_COMPRESSION, so its own size is a good stand-in for the total
     *  bytes we're about to write back out - close enough for a progress bar/ETA without a
     *  separate full pass just to sum entry sizes. */
    private static long estimateZipSize(Context context, Uri zipUri) {
        try (ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(zipUri, "r")) {
            return pfd != null ? pfd.getStatSize() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static boolean zipDirRecursive(File dir, String entryPrefix, ZipOutputStream zos, long totalBytes,
            long[] copiedSoFar, AtomicBoolean cancelled, ProgressListener listener) {
        File[] children = dir.listFiles();
        if (children == null)
            return true;
        boolean ok = true;
        for (File child : children) {
            checkCancelled(cancelled); // between files - propagates out of this method on purpose
            String entryName = entryPrefix + child.getName();
            if (child.isDirectory()) {
                if (!zipDirRecursive(child, entryName + "/", zos, totalBytes, copiedSoFar, cancelled, listener)) {
                    ok = false;
                }
            } else {
                try (InputStream in = new FileInputStream(child)) {
                    zos.putNextEntry(new ZipEntry(entryName));
                    byte[] buf = new byte[64 * 1024];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        checkCancelled(cancelled); // within a large file too, not just between files
                        zos.write(buf, 0, len);
                        copiedSoFar[0] += len;
                        if (listener != null) {
                            listener.onProgress(copiedSoFar[0], totalBytes, child.getName());
                        }
                    }
                    zos.closeEntry();
                } catch (CancellationException e) {
                    throw e;
                } catch (Exception e) {
                    KanLogger.myLogEE(e, TAG, "zip entry failed for " + child.getName());
                    ok = false;
                }
            }
        }
        return ok;
    }

    /** The link/SAF counterpart to zipDirRecursive() - there's no single directory to hand it
     *  for these books, so each track is resolved to its own playable Uri (same helper the
     *  player itself uses, handles both content:// and file://) and streamed individually. Entry
     *  names use each ZikFile's own filename, matching what the book-sharing feature already
     *  sends peers under the same assumption that it's a safe, real filename (see
     *  NearbyConnectionsHelper.performPreparation). */
    private static boolean zipBookTracksByUri(Context context, BookFileCandidate candidate, String entryPrefix,
            ZipOutputStream zos, long totalBytes, long[] copiedSoFar, AtomicBoolean cancelled,
            ProgressListener listener) {
        boolean ok = true;
        for (com.driot.bookplayer.db.ZikFile zf : candidate.zikFiles) {
            checkCancelled(cancelled);
            Uri uri = com.driot.bookplayer.helpers.UriHelper.resolvePlayableUri(context, zf);
            if (uri == null) {
                KanLogger.myLogW(TAG, "zipBookTracksByUri: could not resolve " + zf.getName() + " for folder "
                        + candidate.folderId + " - skipping this track");
                ok = false;
                continue;
            }
            String entryName = entryPrefix + zf.getName();
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in == null) {
                    throw new java.io.IOException("openInputStream returned null for " + uri);
                }
                zos.putNextEntry(new ZipEntry(entryName));
                byte[] buf = new byte[64 * 1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    checkCancelled(cancelled);
                    zos.write(buf, 0, len);
                    copiedSoFar[0] += len;
                    if (listener != null) {
                        listener.onProgress(copiedSoFar[0], totalBytes, zf.getName());
                    }
                }
                zos.closeEntry();
            } catch (CancellationException e) {
                throw e;
            } catch (Exception e) {
                KanLogger.myLogEE(e, TAG, "zip entry failed for " + zf.getName());
                ok = false;
            }
        }
        return ok;
    }
}
