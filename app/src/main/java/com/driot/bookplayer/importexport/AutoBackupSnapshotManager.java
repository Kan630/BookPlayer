package com.driot.bookplayer.importexport;

import android.content.Context;

import com.driot.bookplayer.db.BackupManager;
import com.driot.bookplayer.utils.log.KanLogger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;

/**
 * Writes the same JSON produced by the user-facing manual backup (see ImportExportActivity /
 * BackupManager) to a small file under getFilesDir(), with every category included
 * unconditionally. This file is NOT one of the folders excluded in
 * res/xml/data_extraction_rules.xml / backup_rules.xml, so it rides along with Android's own
 * Auto Backup / device-transfer for free - it's the small, disaster-recovery safety net for
 * users who never made a manual backup. See MainActivity's empty-library check for the
 * restore side.
 */
public class AutoBackupSnapshotManager {

    private static final String TAG = "AutoBackupSnapshotManager";
    private static final String SNAPSHOT_DIR = "auto_backup";
    private static final String SNAPSHOT_FILE = "snapshot.json";

    public static File getSnapshotFile(Context context) {
        return new File(new File(context.getFilesDir(), SNAPSHOT_DIR), SNAPSHOT_FILE);
    }

    public static boolean hasSnapshot(Context context) {
        return getSnapshotFile(context).exists();
    }

    public static void writeSnapshot(Context context) {
        try {
            File file = getSnapshotFile(context);
            File dir = file.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
            BackupManager backupManager = new BackupManager(context);
            String json = backupManager.exportToJson(true, true, true, true, true, true);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

            // This snapshot is ~3 MB and the periodic task comes round every 15 minutes: writing
            // it unconditionally cost about 100 MB of flash writes a day on a real phone, for a
            // file whose content usually had not changed at all. Write only what is new.
            if (isSameAsOnDisk(file, bytes)) {
                KanLogger.myLogD(TAG, "writeSnapshot: unchanged (" + bytes.length
                        + " bytes), keeping the file as it is");
                return;
            }

            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(bytes);
            }
            KanLogger.myLogD(TAG, "writeSnapshot: wrote " + json.length() + " chars to " + file.getAbsolutePath());
        } catch (Exception e) {
            KanLogger.myLogEE(e, TAG, "writeSnapshot failed");
        }
    }

    /** Same length and same bytes as the snapshot already on disk? Reading 3 MB back is far
     * cheaper than rewriting it, and the length check alone settles most calls. */
    private static boolean isSameAsOnDisk(File file, byte[] fresh) {
        if (!file.exists() || file.length() != fresh.length) {
            return false;
        }
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int offset = 0;
            int read;
            while ((read = in.read(buf)) > 0) {
                for (int i = 0; i < read; i++) {
                    if (offset + i >= fresh.length || buf[i] != fresh[offset + i]) {
                        return false;
                    }
                }
                offset += read;
            }
            return offset == fresh.length;
        } catch (Exception e) {
            return false; // unreadable: just write it
        }
    }

    public static String readSnapshot(Context context) {
        File file = getSnapshotFile(context);
        if (!file.exists())
            return null;
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (Exception e) {
            KanLogger.myLogEE(e, TAG, "readSnapshot failed");
            return null;
        }
        return sb.toString();
    }
}
