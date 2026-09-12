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
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            }
            KanLogger.myLogD(TAG, "writeSnapshot: wrote " + json.length() + " chars to " + file.getAbsolutePath());
        } catch (Exception e) {
            KanLogger.myLogEE(e, TAG, "writeSnapshot failed");
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
