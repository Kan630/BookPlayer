package com.driot.bookplayer.importexport;

import android.content.Context;
import android.net.Uri;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.BackupManager;
import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.utils.log.KanLogger;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
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

            for (String sub : BACKUP_SUBFOLDERS) {
                File srcDir = new File(context.getFilesDir(), sub);
                if (!srcDir.exists())
                    continue;
                if (!zipDirRecursive(srcDir, sub + "/", zos, estimate.totalBytes, copiedSoFar, listener)) {
                    allOk = false;
                }
            }

            try {
                BackupManager backupManager = new BackupManager(context);
                String json = backupManager.exportToJson(true, true, true, true, true, true);
                byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
                zos.putNextEntry(new ZipEntry("backup.json"));
                zos.write(jsonBytes);
                zos.closeEntry();
                copiedSoFar[0] += jsonBytes.length;
                if (listener != null) {
                    listener.onProgress(copiedSoFar[0], estimate.totalBytes, "backup.json");
                }
            } catch (Exception e) {
                KanLogger.myLogEE(e, TAG, "writing backup.json into zip failed");
                allOk = false;
            }
        } catch (Exception e) {
            KanLogger.myLogEE(e, TAG, "runFullBackup (zip) failed");
            return false;
        }

        return allOk;
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
