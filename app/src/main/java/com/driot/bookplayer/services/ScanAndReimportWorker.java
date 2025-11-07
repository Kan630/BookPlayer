package com.driot.bookplayer.services;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.imports.BookLoadingWorkLauncher;
import com.driot.bookplayer.imports.ImportHelper;
import com.driot.bookplayer.imports.ImportJob;
import com.driot.bookplayer.imports.ImportJobRepository;
import com.driot.bookplayer.imports.ImportWorker;
import com.driot.bookplayer.objects.LoadBookTaskState;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Scans a root folder (SAF tree URI) and re-enqueues missing audiobooks
 * using the new BookLoadingWorkLauncher pipeline.
 *
 * Input:
 *  - K_ROOT_TREE_URI (required): tree URI string of the root to scan
 *  - K_SOURCE_LOC    (optional): source location hint ("internal","sdcard","cloud", etc.)
 */
public class ScanAndReimportWorker extends ImportWorker {

    public static final String K_ROOT_TREE_URI = "root_tree_uri";
    public static final String K_SOURCE_LOC    = "source_location"; // optional

    private static final String TASK_NAME = Var.WORKER_MASS_IMPORT;

    ImportJob importJob;

    private final Context appContext;
    private int nbCandidates;

    public ScanAndReimportWorker(@NonNull Context appContext, @NonNull WorkerParameters params) {
        super(appContext, params);
        this.appContext = appContext.getApplicationContext();
    }

    @NonNull
    @Override
    public Result doWorkBody() {
        myLog("ScanAndReimportWorker start");

        importJob = jobOrFail(); //important ! to init repo...

        Context ctx = getApplicationContext();
        emitTaskStart(TASK_NAME, "scanning items...");

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
        nbCandidates =candidates.size();
        myLogD( nbCandidates+ " candidates found under: " + root.getName() + " (" + rootStr + ")");
        emitStepProgress(TASK_NAME, 0, "Found " + nbCandidates + " candidates");

        // 2) Filter out those already in DB (by SAF path key)
        ArrayList<DocumentFile> toImport = new ArrayList<>();
        for (DocumentFile cand : candidates) {
            String pathKey = cand.getUri().toString(); // we store folder path as SAF uri string
            boolean exists = DatabaseClient.getInstance(ctx)
                    .getAppDatabase()
                    .folderDao()
                    .existsByPath(pathKey);

            if (!exists) {
                toImport.add(cand);
            } else {
                emitWarning("[" + cand.getName() + "] already exists");
                myLogD("Skip (already in DB): " + cand.getName() + " -> " + pathKey);
            }
        }

        if (toImport.isEmpty()) {
            myLog("Nothing to re-import under: " + root.getName());
            return Result.success();
        }

        myLog(toImport.size() + " folders to re-import under: " + root.getName());

        String sourceLoc = getInputData().getString(K_SOURCE_LOC);
        if (sourceLoc == null) sourceLoc = ""; // optional hint only

        // 3) For each missing folder, create a LoadBookTaskState and launch via the new pipeline
        // We enqueue with sequential=true so everything is appended to the global "bookload-queue"
        for (int i = 0; i < toImport.size(); i++) {
            DocumentFile bookFolder = toImport.get(i);

            //emitTextOnlyProgress("Re-importing " + (i + 1) + " of " + toImport.size() + ": " + safeName(bookFolder));
            emitStepProgress(TASK_NAME, i+1/nbCandidates*100, "Re-importing " + (i + 1) + " of " + toImport.size() + ": " + safeName(bookFolder));
            myLog("enqueueing " + bookFolder.getName());

            // Optional: pick a cover from inside the folder
            String imageUri = pickLargestCoverUri(ctx, bookFolder);
            myLog("picked image: " + (imageUri == null ? "(none)" : imageUri));

            // Build state for the new pipeline.
            // We’re importing an EXISTING folder → no download, no copy, no split.
            LoadBookTaskState s = new LoadBookTaskState();
            s.title            = safeName(bookFolder);
            s.originalUri      = null; // legacy field; we rely on dynamicUri for the current source
            s.originalType     = "Folder";
            s.dynamicUri       = bookFolder.getUri();
            s.dynamicType      = "Folder";

            // Future folder “path” is the same SAF URI; the FinalParse worker will reconcile DB rows.
            s.futureFolderName = safeName(bookFolder);
            s.futureFolderPath = bookFolder.getUri().toString();

            // File characteristics (folder import → no extension, no split)
            s.fileExtension    = null;
            s.playType         = "Folder";
            s.mimeType         = "vnd.android.document/directory";

            // Options for this path (re-import means keep as-is; just parse/index)
            s.optionCopy   = false;
            s.optionSplit  = false;
            s.optionDelete = false;

            // Provenance (optional hint only; safe to leave empty)
            s.sourceLocation = sourceLoc;

            // Cover if we found one
            s.imagePath = imageUri;

            // Clear anything irrelevant
            s.originalFile = null;
            s.originalHash = null;

            // Launch into the new pipeline.
            // Using sequential=true ensures all these tasks go into one global WM queue ("bookload-queue").
            BookLoadingWorkLauncher.launch(ctx, s, /*sequential*/ true);
        }

        myLog("Queued " + toImport.size() + " missing audiobooks for re-import (new pipeline).");
        return Result.success();
    }

    // --------------------- helpers (unchanged from your old class) ---------------------

    private static String safeName(DocumentFile f) {
        String n = f.getName();
        return n == null ? "Untitled" : n;
    }

    private ArrayList<DocumentFile> findBookCandidates(DocumentFile root) {
        myLogD("findBookCandidates");
        ArrayList<DocumentFile> result = new ArrayList<>();

        boolean rootHasTopAudio = hasAudioAtTopLevel(root);

        // Collect child folders that contain audio somewhere in their subtree
        ArrayList<DocumentFile> childBooks = new ArrayList<>();
        for (DocumentFile child : root.listFiles()) {
            if (child.isDirectory() && hasAnyAudioRecursive(child)) {
                myLog("add " + child.getName());
                emitTextOnlyProgress("add " + child.getName());
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
