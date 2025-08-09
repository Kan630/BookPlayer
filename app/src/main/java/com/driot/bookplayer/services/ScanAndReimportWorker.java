package com.driot.bookplayer.services;

import static com.driot.bookplayer.services.ParseFinalFolderWorker.*;
import static com.driot.bookplayer.global.Var.ONLY_MIME_AUDIO;
import static com.driot.bookplayer.global.Var.SUPPORTED_AUDIO_EXTENSIONS;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.utils.log.LoggingWorker;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;

public class ScanAndReimportWorker extends LoggingWorker {

    public static final String K_ROOT_TREE_URI = "root_tree_uri";
    public static final String K_SOURCE_LOC    = "source_location"; // optional

    public ScanAndReimportWorker(@NonNull Context appContext, @NonNull WorkerParameters params) {
        super(appContext, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        String rootStr = getInputData().getString(K_ROOT_TREE_URI);
        if (rootStr == null) {
            myLogE("ScanAndReimportWorker: missing root_tree_uri");
            return Result.failure();
        }

        Uri rootUri = Uri.parse(rootStr);
        DocumentFile root = DocumentFile.fromTreeUri(ctx, rootUri);
        if (root == null || !root.isDirectory()) {
            myLogE("ScanAndReimportWorker: root is not a directory: " + rootStr);
            return Result.failure();
        }

        // 1) Find candidate audiobook folders under root
        ArrayList<DocumentFile> candidates = findBookCandidates(root);

        // 2) Filter out those already in DB (by path)
        ArrayList<DocumentFile> toImport = new ArrayList<>();
        for (DocumentFile cand : candidates) {
            String pathKey = cand.getUri().toString(); // we store path as SAF uri string
            boolean exists = DatabaseClient.getInstance(ctx)
                    .getAppDatabase()
                    .FolderDao()
                    .existsByPath(pathKey);

            if (!exists) {
                toImport.add(cand);
            } else {
                myLogD("Skip (already in DB): " + cand.getName() + " -> " + pathKey);
            }
        }

        if (toImport.isEmpty()) {
            myLog("Nothing to re-import under: " + root.getName());
            return Result.success();
        }

        // 3) Build a sequential chain of ParseFinalFolderWorker for each missing folder
        WorkManager wm = WorkManager.getInstance(ctx);
        WorkContinuation chain = null;

        String sourceLoc = getInputData().getString(K_SOURCE_LOC);

        for (int i = 0; i < toImport.size(); i++) {
            DocumentFile bookFolder = toImport.get(i);

            // Optional: pick a cover from inside the folder
            String imageUri = pickLargestCoverUri(ctx, bookFolder);

            Data data = new Data.Builder()
                    .putString(K_DYNAMIC_URI, bookFolder.getUri().toString())
                    .putString(K_DYNAMIC_TYPE, "Folder")
                    .putString(K_TITLE, safeName(bookFolder))
                    .putString(K_FUTURE_PATH, bookFolder.getUri().toString())
                    .putString(K_SOURCE_LOC, sourceLoc == null ? "" : sourceLoc)
                    .putString(K_IMAGE_URI, imageUri == null ? "" : imageUri)
                    .build();

            OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(ParseFinalFolderWorker.class)
                    .setInputData(data)
                    .addTag("BulkReimport")
                    .build();

            if (chain == null) {
                chain = wm.beginWith(req);
            } else {
                chain = chain.then(req);
            }
        }

        if (chain != null) chain.enqueue();

        myLog("Queued " + toImport.size() + " missing audiobooks for re-import.");
        return Result.success();
    }

    private static String safeName(DocumentFile f) {
        String n = f.getName();
        return n == null ? "Untitled" : n;
    }


    private ArrayList<DocumentFile> findBookCandidates(DocumentFile root) {
        ArrayList<DocumentFile> result = new ArrayList<>();

        boolean rootHasTopAudio = hasAudioAtTopLevel(root);

        // Collect child folders that contain audio somewhere in their subtree
        ArrayList<DocumentFile> childBooks = new ArrayList<>();
        for (DocumentFile child : root.listFiles()) {
            if (child.isDirectory() && hasAnyAudioRecursive(child)) {
                childBooks.add(child);
            }
        }

        if (!childBooks.isEmpty()) {
            // Library container: import children only, never the root
            result.addAll(childBooks);

            // Optionally: log any top-level standalone files so you can handle them later
            ArrayList<DocumentFile> topStandalone = listTopLevelStandaloneAudio(root);
            for (DocumentFile f : topStandalone) {
                myLogW("Standalone top-level audio skipped (not imported): " + f.getName());
            }
        } else {
            // No child folders with audio → root is a single book (files may be at top level)
            if (rootHasTopAudio) {
                result.add(root);
            } else {
                myLog("No audio found under root: " + root.getName());
            }
        }
        return result;
    }

    private boolean hasAudioAtTopLevel(DocumentFile dir) {
        for (DocumentFile f : dir.listFiles()) {
            if (!f.isDirectory() && isAudio(f)) return true;
        }
        return false;
    }

    private boolean hasAnyAudioRecursive(DocumentFile dir) {
        for (DocumentFile f : dir.listFiles()) {
            if (f.isDirectory()) {
                if (hasAnyAudioRecursive(f)) return true;
            } else if (isAudio(f)) {
                return true;
            }
        }
        return false;
    }

    private ArrayList<DocumentFile> listTopLevelStandaloneAudio(DocumentFile dir) {
        ArrayList<DocumentFile> list = new ArrayList<>();
        for (DocumentFile f : dir.listFiles()) {
            if (!f.isDirectory() && isAudio(f)) list.add(f);
        }
        return list;
    }

    private boolean isAudio(DocumentFile f) {
        String name = Objects.toString(f.getName(), "");
        String ext  = getExt(name);
        String mime = Objects.toString(f.getType(), "");
        if (mime != null && mime.startsWith(Var.ONLY_MIME_AUDIO)) return true;
        return Var.SUPPORTED_AUDIO_EXTENSIONS.contains(ext);
    }

    private String getExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }


    private String pickLargestCoverUri(Context ctx, DocumentFile folder) {
        // mirrors your logic (simple & fast): biggest jpg/png/etc in subtree
        DocumentFile best = pickLargestCoverRecursive(folder, null);
        return best == null ? null : best.getUri().toString();
    }

    private DocumentFile pickLargestCoverRecursive(DocumentFile dir, DocumentFile best) {
        for (DocumentFile f : dir.listFiles()) {
            if (f.isDirectory()) {
                best = pickLargestCoverRecursive(f, best);
            } else {
                String name = Objects.toString(f.getName(), "");
                String ext  = getExt(name);
                if (com.driot.bookplayer.global.Var.SUPPORTED_COVER_PICTURE_EXTENSIONS.contains(ext)) {
                    if (best == null || f.length() > best.length()) best = f;
                }
            }
        }
        return best;
    }
}
