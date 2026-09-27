package com.driot.bookplayer.helpers;

import android.content.Context;

import androidx.annotation.Nullable;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.player.PlayList;
import com.driot.bookplayer.redownload.RedownloadHelper;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Storage quick actions of a book (CleanItemSheet, long press in the Clean screen): which ones
 * apply, and deleting track files while keeping the book. Only ever deletes audio files inside
 * BookPlayer's own book folders - never a track linked from shared storage or through SAF (the
 * user's own files) - and never the track loaded in the player. The rows stay: progress,
 * statistics and play history are kept, the tracks show as missing until downloaded again
 * (RedownloadHelper). All methods: off the main thread.
 */
public final class BookSpaceHelper {

    /** A track counts as listened from here (books have no auto-delete threshold). */
    public static final int LISTENED_PERCENT = 95;

    private BookSpaceHelper() {
    }

    /** What the quick actions would do for one book. */
    public static final class Preview {
        @Nullable
        public Folder folder;
        public int trackCount;
        public boolean redownloadable;
        public boolean sdCardPresent;
        public int missingCount;
        /** Every app-owned track file present: "free up space, keep the book". */
        public final List<Long> ownedIds = new ArrayList<>();
        public long ownedBytes;
        /** The listened ones among them: "remove listened tracks". */
        public final List<Long> listenedIds = new ArrayList<>();
        public long listenedBytes;
    }

    public static Preview preview(Context context, long folderId) {
        Preview p = new Preview();
        AppDatabase db = AppDatabase.getDatabase(context.getApplicationContext());
        p.folder = db.folderDao().getById(folderId);
        p.redownloadable = RedownloadHelper.isRedownloadable(context, folderId);
        p.sdCardPresent = StorageHelper.getSdCardUnzippedFolder(context) != null;
        long playingId = playingTrackId();
        for (ZikFile z : db.zikFileDao().getZikFiles(folderId)) {
            p.trackCount++;
            File owned = ownedFile(context, z);
            if (owned == null) {
                if (com.driot.bookplayer.helpers.UriHelper.resolvePlayableUri(context, z) == null)
                    p.missingCount++;
                continue;
            }
            if (z.getId() == playingId)
                continue;
            long size = owned.length();
            p.ownedIds.add(z.getId());
            p.ownedBytes += size;
            if (z.isFinished() || z.getPercentdone() >= LISTENED_PERCENT) {
                p.listenedIds.add(z.getId());
                p.listenedBytes += size;
            }
        }
        return p;
    }

    /** Outcome of deleteTrackFiles(). */
    public static final class Result {
        public int deleted;
        public long freedBytes;
    }

    /** Deletes the files of these tracks - app-owned ones only, not the one loaded in the player.
     *  The rows are kept (see class comment). */
    public static Result deleteTrackFiles(Context context, List<Long> zikFileIds) {
        Result r = new Result();
        AppDatabase db = AppDatabase.getDatabase(context.getApplicationContext());
        long playingId = playingTrackId();
        for (long id : zikFileIds) {
            ZikFile z = db.zikFileDao().getById(id);
            if (z == null || z.getId() == playingId)
                continue;
            File f = ownedFile(context, z);
            if (f == null)
                continue;
            long size = f.length();
            if (f.delete()) {
                r.deleted++;
                r.freedBytes += size;
                myLogD("BookSpace => deleted track file: " + f.getAbsolutePath());
            } else {
                myLogE("BookSpace => could not delete: " + f.getAbsolutePath());
            }
        }
        myLogI("BookSpace => " + r.deleted + "/" + zikFileIds.size() + " track file(s) deleted, " + r.freedBytes
                + " bytes freed (rows kept)");
        return r;
    }

    private static long playingTrackId() {
        PlayList pl = PlayList.getInstance();
        return (pl != null && !pl.isStream() && pl.getZikFile() != null) ? pl.getZikFile().getId() : -1;
    }

    /** The track's file when it exists inside BookPlayer's own book folders, else null (linked,
     *  SAF, missing...). Handles the legacy "folder path + separate name" rows. */
    @Nullable
    private static File ownedFile(Context context, ZikFile z) {
        String plain = RedownloadHelper.plainPath(z.getPath());
        if (plain == null)
            return null;
        File f = new File(plain);
        if (!f.isFile())
            f = new File(plain, z.getName());
        if (!f.isFile())
            return null;
        return isUnderAppBookFolders(context, f) ? f : null;
    }

    private static boolean isUnderAppBookFolders(Context context, File f) {
        try {
            String path = f.getCanonicalPath();
            for (boolean sd : new boolean[] { false, true }) {
                File root = StorageHelper.getUnzipFolder(context, sd);
                if (root != null && path.startsWith(root.getCanonicalPath() + File.separator))
                    return true;
            }
        } catch (IOException e) {
            myLogEE(e, "isUnderAppBookFolders " + f);
        }
        return false;
    }
}
