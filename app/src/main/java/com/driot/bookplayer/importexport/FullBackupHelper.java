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
     * backup - returns false if at least one file failed (or the zip itself couldn't be
     * finalized). Audio/images are already compressed, so the zip uses no further compression -
     * it's purely a container here, not a size-reduction step. */
    public static boolean runFullBackup(Context context, Uri destFileUri, ProgressListener listener) {
        Estimate estimate = computeEstimate(context);
        long[] copiedSoFar = { 0 };
        boolean allOk = true;

        try (OutputStream rawOut = context.getContentResolver().openOutputStream(destFileUri);
                ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(rawOut))) {
            if (rawOut == null) {
                return false;
            }
            zos.setLevel(Deflater.NO_COMPRESSION);

            // Metadata first (manifest + full JSON): both are tiny next to the audio folders
            // below, and writing them first means a restore preview never has to scan past any
            // audio to find them.
            try {
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
            } catch (Exception e) {
                KanLogger.myLogEE(e, TAG, "writing manifest/backup.json into zip failed");
                allOk = false;
            }

            for (String sub : BACKUP_SUBFOLDERS) {
                File srcDir = new File(context.getFilesDir(), sub);
                if (!srcDir.exists())
                    continue;
                if (!zipDirRecursive(srcDir, sub + "/", zos, estimate.totalBytes, copiedSoFar, listener)) {
                    allOk = false;
                }
            }
        } catch (Exception e) {
            KanLogger.myLogEE(e, TAG, "runFullBackup (zip) failed");
            return false;
        }

        return allOk;
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
     */
    public static boolean runFullRestore(Context context, Uri srcZipUri, ProgressListener listener) {
        long totalBytes = estimateZipSize(context, srcZipUri);
        long[] copiedSoFar = { 0 };
        boolean allOk = true;
        String backupJson = null;

        try (InputStream rawIn = context.getContentResolver().openInputStream(srcZipUri);
                ZipInputStream zis = new ZipInputStream(new BufferedInputStream(rawIn))) {
            if (rawIn == null) {
                return false;
            }

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (!entry.isDirectory()) {
                    if (BACKUP_JSON_ENTRY_NAME.equals(name)) {
                        backupJson = readEntryAsString(zis);
                        copiedSoFar[0] += backupJson.length();
                        if (listener != null) {
                            listener.onProgress(copiedSoFar[0], totalBytes, BACKUP_JSON_ENTRY_NAME);
                        }
                    } else if (isUnderBackupSubfolder(name)) {
                        if (!extractEntry(context, zis, name, totalBytes, copiedSoFar, listener)) {
                            allOk = false;
                        }
                    }
                    // Anything else (the manifest, or an unrecognized entry from a foreign/future
                    // zip) is skipped rather than failing the whole restore over it.
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            KanLogger.myLogEE(e, TAG, "runFullRestore (unzip) failed");
            return false;
        }

        if (backupJson == null) {
            KanLogger.myLogE(TAG, "runFullRestore: no backup.json entry found - not a Full Backup zip");
            return false;
        }

        try {
            BackupManager backupManager = new BackupManager(context);
            backupManager.importFromJson(backupJson, true, true, true, true, true, true);
        } catch (Exception e) {
            KanLogger.myLogEE(e, TAG, "runFullRestore: importFromJson failed");
            allOk = false;
        }

        return allOk;
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
            long[] copiedSoFar, ProgressListener listener) {
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
                    out.write(buf, 0, len);
                    copiedSoFar[0] += len;
                    if (listener != null) {
                        listener.onProgress(copiedSoFar[0], totalBytes, dest.getName());
                    }
                }
            }
            return true;
        } catch (Exception e) {
            KanLogger.myLogEE(e, TAG, "extracting zip entry failed: " + entryName);
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
            long[] copiedSoFar, ProgressListener listener) {
        File[] children = dir.listFiles();
        if (children == null)
            return true;
        boolean ok = true;
        for (File child : children) {
            String entryName = entryPrefix + child.getName();
            if (child.isDirectory()) {
                if (!zipDirRecursive(child, entryName + "/", zos, totalBytes, copiedSoFar, listener)) {
                    ok = false;
                }
            } else {
                try (InputStream in = new FileInputStream(child)) {
                    zos.putNextEntry(new ZipEntry(entryName));
                    byte[] buf = new byte[64 * 1024];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        zos.write(buf, 0, len);
                        copiedSoFar[0] += len;
                        if (listener != null) {
                            listener.onProgress(copiedSoFar[0], totalBytes, child.getName());
                        }
                    }
                    zos.closeEntry();
                } catch (Exception e) {
                    KanLogger.myLogEE(e, TAG, "zip entry failed for " + child.getName());
                    ok = false;
                }
            }
        }
        return ok;
    }
}
