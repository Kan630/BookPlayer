package com.driot.bookplayer.helpers;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;

/**
 * Helper to count files in a folder, with realtime progress callback.
 *
 * depth = 1 → only files in this folder
 * depth = 2 → this folder + direct subfolders
 * depth >= 3 → this folder + all levels (we clamp to "unlimited")
 */
public class FileCounterHelper {

    // region --- Public API : SAF (treeUri) -----------------------------------

    /**
     * Count files under a SAF folder (treeUri from ACTION_OPEN_DOCUMENT_TREE),
     * calling callback.onCountUpdated() in realtime as files are discovered.
     *
     * Must be called from a background thread.
     */
    public static void countFilesFromTreeUriRealtime(
            @NonNull Context context,
            @NonNull Uri treeUri,
            int depth,
            @NonNull CountCallback callback
    ) {
        DocumentFile root = DocumentFile.fromTreeUri(context, treeUri);
        if (root == null || !root.isDirectory()) {
            return;
        }

        int effectiveDepth = normalizeDepth(depth);
        int[] count = new int[]{0};
        int[] countFolder = new int[]{0};
        traverseDocumentDir(root, effectiveDepth, callback, count, countFolder);

        if (!callback.isCancelled()) {
            callback.onFinished(count[0], countFolder[0]);
        }
    }

    // endregion

    // region --- Public API : normal file path --------------------------------

    /**
     * Count files under a classic File path, with realtime callback.
     *
     * folderPath can be something like /storage/emulated/0/MyBooks
     * Must be called from a background thread.
     */
    /*
    public static void countFilesFromFolderPathRealtime(
            @NonNull String folderPath,
            int depth,
            @NonNull CountCallback callback
    ) {
        File root = new File(folderPath);
        if (!root.exists() || !root.isDirectory()) {
            return;
        }

        int effectiveDepth = normalizeDepth(depth);
        int[] count = new int[]{0};
        traverseFileDir(root, effectiveDepth, callback, count);
    }

     */

    // endregion

    // region --- Internal traversal : SAF -------------------------------------

    private static void traverseDocumentDir(
            @NonNull DocumentFile dir,
            int depthLeft,
            @NonNull CountCallback callback,
            @NonNull int[] count,
            @NonNull int[] countFolder
    ) {
        if (depthLeft <= 0 || callback.isCancelled()) {
            return;
        }

        DocumentFile[] children = dir.listFiles();
        if (children == null) return;

        for (DocumentFile child : children) {
            if (callback.isCancelled()) {
                return;
            }

            if (child.isFile()) {
                //check type
                if (SupportedFilesHelper.isAudio(child)) count[0]++;
                String name = child.getName();
                callback.onCountUpdated(count[0], name, countFolder[0]);
            } else if (child.isDirectory()) {
                countFolder[0]++;
                if (depthLeft > 1) {
                    traverseDocumentDir(child, depthLeft - 1, callback, count, countFolder);
                }
            }
        }
    }

    // endregion

    // region --- Internal traversal : java.io.File ----------------------------
/*
    private static void traverseFileDir(
            @NonNull File dir,
            int depthLeft,
            @NonNull CountCallback callback,
            @NonNull int[] count,
            @NonNull int[] countFolder
    ) {
        if (depthLeft <= 0 || callback.isCancelled()) {
            return;
        }

        File[] children = dir.listFiles();
        if (children == null) return;

        for (File child : children) {
            if (callback.isCancelled()) {
                return;
            }

            if (child.isFile()) {
                if (SupportedFilesHelper.isAudio(child)) count[0]++;
                callback.onCountUpdated(count[0], child.getAbsolutePath());
            } else if (child.isDirectory()) {
                if (depthLeft > 1) {
                    traverseFileDir(child, depthLeft - 1, callback, count);
                }
            }
        }
    }

 */

    // endregion

    // region --- Depth helper -------------------------------------------------

    /**
     * depth = 1 → first level only
     * depth = 2 → levels 1 + 2
     * depth >= 3 → treat as "all levels"
     */
    private static int normalizeDepth(int depth) {
        if (depth <= 0) return 0;
        if (depth == 1) return 1;
        if (depth == 2) return 2;
        // 3 or more → large number == "unlimited"
        return 1000;
    }

    // endregion
}
