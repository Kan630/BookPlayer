package com.driot.bookplayer.imports;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.utils.log.LoggerHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class MassImportScanner extends LoggerHelper {

    private final Context context;
    private final Callback callback;
    private volatile boolean isCancelled = false;

    public interface Callback {
        void onProgress(String currentPath);

        void onFound(BookCandidate candidate);
    }

    public MassImportScanner(Context context, Callback callback) {
        super(MassImportScanner.class);
        this.context = context;
        this.callback = callback;
    }

    public void cancel() {
        isCancelled = true;
    }

    public List<BookCandidate> scan(Uri rootUri) {
        List<BookCandidate> candidates = new ArrayList<>();
        DocumentFile root = DocumentFile.fromTreeUri(context, rootUri);
        if (root == null || !root.isDirectory()) {
            myLogE("Root is not a directory or null: " + rootUri);
            return candidates;
        }

        scanRecursive(root, candidates);
        return candidates;
    }

    private void scanRecursive(DocumentFile dir, List<BookCandidate> candidates) {
        if (isCancelled)
            return;

        callback.onProgress(dir.getName());

        DocumentFile[] files = dir.listFiles();
        boolean hasAudio = false;

        for (DocumentFile file : files) {
            if (isCancelled)
                return;

            if (file.isDirectory()) {
                // Determine if this directory itself is a book or if we go deeper
                // Current logic in ScanAndReimport: if it has audio recursive, it's a
                // candidate.
                // But wait, if we go deeper, do we treat children as separate books?
                // The old logic was: "Collect child folders that contain audio somewhere in
                // their subtree"
                // AND "Library container: import children only, never the root" if children
                // have audio.
                // Let's refine:
                // If a folder has audio files DIRECTLY, it's a potential book.
                // If it has ONLY subfolders, we recurse.

                // Correction: The user wants "Add multiple books by selecting only a master
                // folder".
                // So the master folder is the container.
                // Anything inside is potential book.

                // Let's try a simple approach first:
                // Check if the current folder (file) is a book (has audio directly).
                // Or if it's just a container.

                // Actually, the previous logic was: findBookCandidates(root)
                // -> iterates ALL children of root.
                // -> for each child: check if it has audio recursive.
                // -> if yes, add child as candidate.

                // So we should do depth-first search but identifying "Book Roots".
                // A "Book Root" is typically a folder that contains audio files or subfolders
                // with audio (chapters).

                // Let's recursively scan.
                // But we need to handle "Single File Books" (ZIP, M4B) separately.

                // Re-implementing logic close to "findBookCandidates" but recursive?
                // Actually, we want to find "items" inside the Master Folder.
                // So we scan the children of the Master Folder.

                // If the Master Folder is the Root:
                // We look at each child.
                // Child A (Folder) -> Check if it contains audio (recursive). If yes ->
                // Candidate (Folder).
                // Child B (ZIP) -> Candidate (ZIP).
                // Child C (M4B) -> Candidate (M4B).
                // Child D (EPUB) -> Candidate (EPUB).

                // What if Child A contains sub-books?
                // E.g. Root/Author/Book1, Root/Author/Book2.
                // If we select Root.
                // Child A is "Author". It contains audio recursive (in Book1).
                // Do we import "Author" as one book? Or recurs?
                // The prompt says "functionnality that allow user to register/add multiple
                // books by selecting only a master folder."
                // "Right now it will import only child folders if they contain audio files."
                // Implicitly: It scans 1 level deep?

                // Let's look at ScanAndReimportWorker again.
                // `nbFolders = root.listFiles().length;`
                // `for (DocumentFile child : root.listFiles()) ...`
                // It only iterates IMMEDIATE children of root.

                // "I would like it to be able to take any kind of object in that master folder"

                // So safe assumption: We iterate immediate children of the root only.
                // If a child is a folder -> check if valid book (has audio recursive).
                // If a child is a file -> check if valid book (ZIP, M4B, EPUB).

                // Wait, what if the user selects a folder that contains "Artist/Album"
                // structure?
                // If I select "Artist", do I get "Album" as candidate?
                // ScanAndReimportWorker:
                // `hasAnyAudioRecursive(child)` -> if true, add `child`.
                // So `Artist` would be added as the book.

                // User Request: "right now it will import only child folders if they contain
                // audio files. I would like it to be able to take any kind of object in that
                // master folder"
                // So we stick to 1 level deep scanning for now, but expand the "Object types"
                // accepted.

                if (hasAnyAudioRecursive(file)) {
                    candidates.add(new BookCandidate(file.getUri(), safeName(file), "Folder",
                            safeName(dir) + "/" + safeName(file)));
                } else {
                    // Maybe it's a folder but has no audio, just other files. Ignore.
                }

            } else {
                // It's a file at the root level?
                // ScanAndReimportWorker ignored root files mostly.
                // "Optional: log any top-level standalone files so you can handle them later"
                // -> `listTopLevelStandaloneAudio`

                // Now we want to support them.
                String type = detectBookType(file);
                if (type != null) {
                    candidates.add(new BookCandidate(file.getUri(), safeName(file), type,
                            safeName(dir) + "/" + safeName(file)));
                }
            }
        }
    }

    // We only scan the immediate children of the root provided in `scan`.
    // Wait, `scanRecursive` suggests recursion.
    // Let's follow the requirement: "add multiple books by selecting only a master
    // folder".
    // "take any kind of object in that master folder".
    // This implies iterating over `root.listFiles()`.

    public void scanImmediateChildren(Uri rootUri, List<BookCandidate> candidates) {
        DocumentFile root = DocumentFile.fromTreeUri(context, rootUri);
        if (root == null || !root.isDirectory())
            return;

        DocumentFile[] files = root.listFiles();
        int total = files.length;
        int count = 0;

        for (DocumentFile file : files) {
            if (isCancelled)
                break;
            count++;
            // callback.onProgress based on count?
            // Or just report name
            callback.onProgress("Scanning " + count + "/" + total + ": " + safeName(file));

            if (file.isDirectory()) {
                if (hasAnyAudioRecursive(file)) {
                    candidates.add(new BookCandidate(file.getUri(), safeName(file), "Folder", safeName(file)));
                }
            } else {
                String type = detectBookType(file);
                if (type != null) {
                    candidates.add(new BookCandidate(file.getUri(), safeName(file), type, safeName(file)));
                }
            }
        }
    }

    private String detectBookType(DocumentFile file) {
        String name = safeName(file);
        String ext = getExt(name);
        String mime = Objects.toString(file.getType(), "");

        // 1. Archives
        if (Var.SUPPORTED_COMPRESSED_FILE_EXTENSIONS.contains(ext)) {
            return "ZIP";
        }

        // 2. Audio Files (M4B, etc - single file audiobooks)
        if (Var.SUPPORTED_AUDIO_EXTENSIONS.contains(ext)) {
            // We might want to be specific about M4B or long audio, but user said "m4b,
            // etc".
            // Since it's a "Book" import, we treat any supported audio file as a candidate
            // here.
            if ("m4b".equals(ext))
                return "M4B";
            return "Audio File";
        }

        // 3. Ebooks
        if (Var.SUPPORTED_EBOOK_EXTENSIONS.contains(ext)) {
            return "Ebook";
        }

        return null;
    }

    private boolean hasAnyAudioRecursive(DocumentFile dir) {
        DocumentFile[] files = dir.listFiles();
        for (DocumentFile f : files) {
            if (f.isDirectory()) {
                if (hasAnyAudioRecursive(f))
                    return true;
            } else {
                if (isAudio(f))
                    return true;
            }
        }
        return false;
    }

    private boolean isAudio(DocumentFile f) {
        String name = safeName(f);
        String ext = getExt(name);
        String mime = Objects.toString(f.getType(), "");
        if (mime != null && mime.startsWith(Var.ONLY_MIME_AUDIO))
            return true;
        return Var.SUPPORTED_AUDIO_EXTENSIONS.contains(ext);
    }

    private String getExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String safeName(DocumentFile f) {
        String n = f.getName();
        return n == null ? "Untitled" : n;
    }
}
