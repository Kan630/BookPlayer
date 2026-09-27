package com.driot.bookplayer.helpers;

import android.content.Context;

import com.driot.bookplayer.db.AppDatabase;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Removes what interrupted downloads leave in the book/podcast folders: "*.part" files
 * (PodcastDownloadEpisodeWorker, RedownloadBookWorker) and the directory of a podcast whose first
 * download never completed. Only ever deletes a stale .part or an empty directory, so a book can't
 * lose anything even if its Folder row looks unusual. Call off the main thread.
 */
public final class DownloadLeftoverSweeper {

    // Older than any download still running: those rewrite their .part continuously.
    private static final long STALE_MS = 24L * 60 * 60 * 1000;

    private DownloadLeftoverSweeper() {
    }

    public static void sweep(Context context) {
        Set<File> roots = new LinkedHashSet<>(); // no SD card: both calls give the same folder
        roots.add(StorageHelper.getUnzipFolder(context, false));
        roots.add(StorageHelper.getUnzipFolder(context, true));

        long staleBefore = System.currentTimeMillis() - STALE_MS;
        int parts = 0, dirs = 0;
        for (File root : roots) {
            File[] folders = root == null ? null : root.listFiles(File::isDirectory);
            if (folders == null)
                continue;
            for (File dir : folders) {
                boolean removedSomething = false;
                File[] stale = dir.listFiles(f -> f.isFile() && f.getName().endsWith(".part")
                        && f.lastModified() < staleBefore);
                if (stale != null) {
                    for (File part : stale) {
                        if (part.delete()) {
                            parts++;
                            removedSomething = true;
                            myLogW("sweep => stale download leftover deleted: " + part.getAbsolutePath());
                        }
                    }
                }

                // A directory emptied just now, or empty for a day, that no book/podcast uses. A
                // fresh empty one may be an import or first download starting: left alone.
                String[] left = dir.list();
                if (left != null && left.length == 0 && (removedSomething || dir.lastModified() < staleBefore)
                        && !AppDatabase.getDatabase(context).folderDao().existsByPath(dir.getAbsolutePath())
                        && dir.delete()) {
                    dirs++;
                    myLogW("sweep => empty unused folder deleted: " + dir.getAbsolutePath());
                }
            }
        }
        if (parts > 0 || dirs > 0)
            myLogI("sweep => " + parts + " stale .part file(s), " + dirs + " empty unused folder(s) deleted");
    }
}
