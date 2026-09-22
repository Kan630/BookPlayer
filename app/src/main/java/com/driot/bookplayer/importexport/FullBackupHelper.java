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
        for (String sub : BACKUP_SUBFOLDERS) {
            File dir = new File(context.getFilesDir(), sub);
            if (dir.exists()) {
                e.totalBytes += StorageHelper.getFolderSize(dir);
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

            for (String sub : BACKUP_SUBFOLDERS) {
                File srcDir = new File(context.getFilesDir(), sub);
                if (!srcDir.exists())
                    continue;
                if (!zipDirRecursive(srcDir, sub + "/", zos, estimate.totalBytes, copiedSoFar, cancelled, listener)) {
                    allOk = false;
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

    /** One book eligible to have its actual audio bundled into a partial backup. Only books
     *  whose files live inside this app's own storage are eligible - a linked book's audio lives
     *  outside the app already (safe from uninstall on its own), and restoring it would mean
     *  writing back through a SAF grant that may not even still be valid after a reinstall (see
     *  runFullBackup's own doc comment on this same caveat for FULL mode's linked books). */
    public static class BookFileCandidate {
        public long folderId;
        public String name;
        public long sizeBytes;
        File dir; // resolved on-disk directory; not exposed outside this file
    }

    public static List<BookFileCandidate> listBookFileCandidates(Context context) {
        List<BookFileCandidate> out = new java.util.ArrayList<>();
        List<Folder> folders = AppDatabase.getDatabase(context).folderDao().getAll();
        for (Folder f : folders) {
            if (f.getPath() == null) {
                continue;
            }
            File dir = resolveInternalFolderDir(Uri.parse(f.getPath()));
            if (dir == null) {
                continue; // linked/SAF book - not eligible, see class doc above
            }
            BookFileCandidate c = new BookFileCandidate();
            c.folderId = f.getId();
            c.name = f.getName();
            c.dir = dir;
            c.sizeBytes = dir.exists() ? StorageHelper.getFolderSize(dir) : 0;
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

        if (selection.includeBookProgress) {
            List<Folder> folders = AppDatabase.getDatabase(context).folderDao().getAll();
            for (Folder f : folders) {
                e.totalAudioDurationMs += (long) f.getDuration();
            }
        }

        try {
            BackupManager backupManager = new BackupManager(context);
            String json = backupManager.exportToJson(selection.includePreferences, selection.includeRadios,
                    selection.includePodcasts, selection.includeLibrivox, selection.includeBookProgress,
                    selection.includePodcastHistory);
            e.totalBytes += json.getBytes(StandardCharsets.UTF_8).length;
        } catch (Exception ex) {
            KanLogger.myLogEE(ex, TAG, "computePartialEstimate: exportToJson failed");
        }

        if (!selection.includedBookFileFolderIds.isEmpty()) {
            for (BookFileCandidate c : listBookFileCandidates(context)) {
                if (selection.includedBookFileFolderIds.contains(c.folderId)) {
                    e.totalBytes += c.sizeBytes;
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
                    if (c.dir == null || !c.dir.exists()) {
                        continue; // book was deleted/moved since the list was built - skip, not fatal
                    }
                    String prefix = BOOK_FILES_ENTRY_PREFIX + c.folderId + "/";
                    if (!zipDirRecursive(c.dir, prefix, zos, estimate.totalBytes, copiedSoFar, cancelled, listener)) {
                        allOk = false;
                    }
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
                        folderIdToDir.putAll(buildFolderIdToDirMap(context, backupJson));
                    } else if (isUnderBackupSubfolder(name)) {
                        if (!extractEntry(context, zis, name, totalBytes, copiedSoFar, cancelled, listener)) {
                            allOk = false;
                        }
                    } else if (name.startsWith(BOOK_FILES_ENTRY_PREFIX)) {
                        if (!extractBookFileEntry(zis, name, folderIdToDir, totalBytes, copiedSoFar, cancelled,
                                listener)) {
                            allOk = false;
                        }
                    }
                    // Anything else (the manifest, or an unrecognized entry from a foreign/future
                    // zip) is skipped rather than failing the whole restore over it.
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

        return allOk ? Result.SUCCESS : Result.PARTIAL_FAILURE;
    }

    private static boolean isUnderBackupSubfolder(String zipEntryName) {
        for (String sub : BACKUP_SUBFOLDERS) {
            if (zipEntryName.equals(sub) || zipEntryName.startsWith(sub + "/")) {
                return true;
            }
        }
        return false;
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

    private static boolean extractEntry(Context context, ZipInputStream zis, String entryName, long totalBytes,
            long[] copiedSoFar, AtomicBoolean cancelled, ProgressListener listener) {
        try {
            File dest = PathSafe.safeResolve(context.getFilesDir(), entryName);
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
     *  on-disk directory a restored book_files/&lt;folderId&gt;/... entry belongs in - the same
     *  directory the restored Folder record's own path will point at, so the files and the DB
     *  row agree once both are back. Swallows its own errors (returns an empty map) rather than
     *  failing the whole restore - a partial backup with no book_files entries at all (or a FULL
     *  backup, which never has any) never needed this map in the first place. */
    private static java.util.Map<Long, File> buildFolderIdToDirMap(Context context, String backupJson) {
        java.util.Map<Long, File> map = new java.util.HashMap<>();
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
                        map.put(f.getId(), dir);
                    }
                }
            }
        } catch (Exception e) {
            KanLogger.myLogEE(e, TAG, "buildFolderIdToDirMap failed - book_files entries (if any) will be skipped");
        }
        return map;
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
}
