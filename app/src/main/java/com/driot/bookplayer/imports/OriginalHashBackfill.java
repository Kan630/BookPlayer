package com.driot.bookplayer.imports;

import static com.driot.bookplayer.utils.HashWorker.HASH_NOT_COMPUTED;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.utils.HashWorker;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Gives books imported without an originalHash one after the fact, so the "already imported"
 * check (ImportValidator.checkHashExists) also protects them. The single import screen saved no
 * hash at all from 2026-02-14 to 2026-09-25, and older paths never did for some types.
 * <p>
 * The hash has to be the one a re-import would compute, i.e. HashWorker's hash of the picked
 * SOURCE - not of the app's own copy. Sources tried, first hashable one wins:
 * 1. the book's own path, when it is a linked source (not a folder the app owns);
 * 2. the source recorded in the import history (ImportJob.originalUri, matched on the path) -
 *    the exact uri that was hashed at import time (a web link hashes its URL text).
 * A source that is gone or no longer readable is skipped: that book stays unhashed.
 * BookSource.source_url is deliberately NOT used: for Gutenberg it's the gutenberg.org address,
 * while the import hashed the mirror-rewritten download URL, so it would never match.
 */
public final class OriginalHashBackfill {

    private OriginalHashBackfill() {
    }

    /** Blocking (reads files) - call from a background thread. */
    public static void run(Context context) {
        AppDatabase db = AppDatabase.getDatabase(context);
        List<Folder> folders = db.folderDao().getFoldersWithoutOriginalHash();
        myLogI("OriginalHashBackfill: " + folders.size() + " book(s) without originalHash");
        int nbDone = 0;
        for (Folder folder : folders) {
            String hash = null;
            for (String source : candidateSources(db, folder)) {
                hash = hashOf(context, source);
                if (hash != null) {
                    myLogD("OriginalHashBackfill: [" + folder.getName() + "] <- " + source);
                    break;
                }
            }
            if (hash != null) {
                db.folderDao().updateOriginalHash(folder.getId(), hash);
                nbDone++;
            } else {
                myLogD("OriginalHashBackfill: no readable source for [" + folder.getName() + "]");
            }
        }
        myLogI("OriginalHashBackfill: " + nbDone + "/" + folders.size() + " book(s) hashed");
    }

    private static List<String> candidateSources(AppDatabase db, Folder folder) {
        List<String> out = new ArrayList<>();
        String path = folder.getPath();
        if (path != null && !path.isEmpty()) {
            if (!StorageHelper.isInInternalMemory(path))
                out.add(path);
            String fromHistory = db.importJobDao().getOriginalUriForFolderPath(path);
            if (fromHistory != null && !out.contains(fromHistory))
                out.add(fromHistory);
        }
        return out;
    }

    @Nullable
    private static String hashOf(Context context, String source) {
        Uri uri = source.startsWith("/") ? Uri.fromFile(new File(source)) : Uri.parse(source);
        if (source.startsWith("/") && !new File(source).exists())
            return null;
        String hash = HashWorker.computeHashFromUri(context, uri); // "" when unreadable
        return (hash == null || hash.isEmpty() || HASH_NOT_COMPUTED.equals(hash)) ? null : hash;
    }
}
