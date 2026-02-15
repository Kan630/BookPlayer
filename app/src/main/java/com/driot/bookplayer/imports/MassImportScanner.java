package com.driot.bookplayer.imports;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.utils.Tonio;
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
        void onProgress(String type, int current, int total, String currentPath);

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

    /**
     * Scans the given root URI for book candidates (epub, m4b, zip, 7z, audio
     * folders, etc.).
     *
     * @param rootUri           Tree URI of the picked folder
     * @param includeSubfolders If true, recursively searches subfolders for book
     *                          files and audio folders.
     *                          If false, only top-level items under root are
     *                          considered.
     */
    public List<BookCandidate> scan(Uri rootUri, boolean includeSubfolders) {
        List<BookCandidate> candidates = new ArrayList<>();
        List<DocumentFile> deferredArchives = new ArrayList<>();

        DocumentFile root = DocumentFile.fromTreeUri(context, rootUri);
        if (root == null || !root.isDirectory()) {
            myLogE("Root is not a directory or null: " + rootUri);
            return candidates;
        }

        if (includeSubfolders) {
            scanRecursive(root, candidates, deferredArchives);
        } else {
            scanRootOnly(root, candidates, deferredArchives);
        }

        return candidates;
    }

    /** Legacy: same as scan(rootUri, false). */
    public List<BookCandidate> scan(Uri rootUri) {
        return scan(rootUri, false);
    }

    /**
     * Recursively collects book files (epub, m4b, zip, 7z, etc.) and audio folders
     * from root and all subfolders,
     * then processes them with progress reporting.
     */
    private void scanRecursive(DocumentFile root, List<BookCandidate> candidates,
            List<DocumentFile> deferredArchives) {
        List<BookCandidate> foundCandidates = new ArrayList<>();

        if (callback != null) {
            callback.onProgress("scanning", 0, 0, "Counting items...");
        }

        collectCandidatesRecursive(root, foundCandidates, new int[] { 0 }, 0, "");

        candidates.addAll(foundCandidates);
    }

    /**
     * Recursively collects: (1) directories that contain audio as folder
     * candidates,
     * (2) book-type files (epub, m4b, etc.) as file candidates, (3) archive files
     * (zip, 7z) for deferred processing.
     */
    private void collectCandidatesRecursive(DocumentFile dir,
            List<BookCandidate> candidates, int[] count, int level, String currentPath) {
        if (isCancelled)
            return;
        DocumentFile[] files = dir.listFiles();
        if (files == null)
            return;

        myLogD(Tonio.lpad(level, (level + 1) * 3) + " | Scanning path: " + currentPath);

        for (DocumentFile file : files) {
            if (isCancelled)
                return;

            count[0]++;
            callback.onProgress("counting", count[0], 0, "");

            String fileName = safeName(file);
            String childPath = currentPath.isEmpty() ? fileName : currentPath + "/" + fileName;

            if (file.isDirectory()) {
                if (hasAnyAudioRecursive(file)) {
                    // It's a candidate folder
                    myLog(Tonio.lpad(level, (level + 1) * 3) + " | Registered Folder Candidate: " + childPath);
                    BookCandidate candidate = new BookCandidate(context, file.getUri(), fileName, "Folder", -1);
                    candidate.path = childPath;
                    addCandidate(candidate, candidates);
                } else {
                    // Not a candidate folder, keep searching
                    collectCandidatesRecursive(file, candidates, count, level + 1, childPath);
                }
            } else {
                String type = detectBookType(file);
                if (type != null) {
                    // If it's an archive, we treat it as a container/candidate similarly
                    myLog(Tonio.lpad(level, (level + 1) * 3) + " | Registered " + type + " Candidate: " + childPath);
                    BookCandidate candidate = new BookCandidate(context, file.getUri(), fileName, type, file.length());
                    candidate.path = childPath;
                    addCandidate(candidate, candidates);
                }
            }
        }
    }

    /**
     * Original behavior: only scan top-level items under root (no subfolders).
     */
    private void scanRootOnly(DocumentFile root, List<BookCandidate> candidates,
            List<DocumentFile> deferredArchives) {
        DocumentFile[] files = root.listFiles();
        if (files == null)
            return;
        int total = files.length;
        int current = 0;

        myLogD("L0 | Scanning path (root only): " + root.getName());

        for (DocumentFile file : files) {
            if (isCancelled)
                break;

            current++;
            callback.onProgress("counting", current, total, safeName(file));

            String fileName = safeName(file);
            if (file.isDirectory()) {
                if (hasAnyAudioRecursive(file)) {
                    myLog("Registered Folder Candidate: " + fileName);
                    BookCandidate candidate = new BookCandidate(context, file.getUri(), fileName, "Folder", -1);
                    candidate.path = fileName;
                    addCandidate(candidate, candidates);
                }
            } else {
                String type = detectBookType(file);
                if (type != null) {
                    myLog("Registered " + type + " Candidate: " + fileName);
                    BookCandidate candidate = new BookCandidate(context, file.getUri(), fileName, type,
                            file.length());
                    candidate.path = fileName;
                    addCandidate(candidate, candidates);
                }
            }
        }
    }

    // Process deferred archives at the end - DEPRECATED in new three-phase logic
    private void processDeferredFiles(List<DocumentFile> deferredArchives, List<BookCandidate> candidates,
            int currentCount, int totalItems) {
        // No longer used in three-phase logic as everything is a candidate now
    }

    private void addCandidate(BookCandidate candidate, List<BookCandidate> list) {
        list.add(candidate);
        callback.onFound(candidate);
    }

    public void scanImmediateChildren(Uri rootUri, List<BookCandidate> candidates) {
        DocumentFile root = DocumentFile.fromTreeUri(context, rootUri);
        if (root == null || !root.isDirectory())
            return;

        DocumentFile[] files = root.listFiles();
        if (files == null)
            return;
        int total = files.length;
        int count = 0;

        myLogD("L0 | Scanning immediate children: " + root.getName());

        for (DocumentFile file : files) {
            if (isCancelled)
                break;
            count++;
            callback.onProgress("counting", count, total, safeName(file));

            String fileName = safeName(file);
            if (file.isDirectory()) {
                if (hasAnyAudioRecursive(file)) {
                    myLog("L0 | Registered Folder Candidate: " + fileName);
                    BookCandidate candidate = new BookCandidate(context, file.getUri(), fileName, "Folder", -1);
                    candidate.path = fileName;
                    addCandidate(candidate, candidates);
                }
            } else {
                String type = detectBookType(file);
                if (type != null) {
                    myLog("L0 | Registered " + type + " Candidate: " + fileName);
                    BookCandidate candidate = new BookCandidate(context, file.getUri(), fileName, type,
                            file.length());
                    candidate.path = fileName;
                    addCandidate(candidate, candidates);
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
            return "Archive";
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
