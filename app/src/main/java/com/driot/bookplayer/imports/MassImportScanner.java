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
        List<DocumentFile> folderCandidates = new ArrayList<>();
        List<DocumentFile> fileCandidates = new ArrayList<>();
        List<DocumentFile> zipFiles = new ArrayList<>();

        if (callback != null) {
            callback.onProgress("scanning", 0, 0, "Counting items...");
        }

        collectCandidatesRecursive(root, folderCandidates, fileCandidates, zipFiles, new int[] { 0 });

        List<DocumentFile> processList = new ArrayList<>();
        processList.addAll(folderCandidates);
        processList.addAll(fileCandidates);
        processList.addAll(zipFiles);

        int total = processList.size();
        int current = 0;

        // Process folders
        for (DocumentFile file : folderCandidates) {
            if (isCancelled)
                return;
            current++;
            callback.onProgress("scanning", current, total, safeName(file));
            myLogD("--------------------------------------------------------");
            myLog("Scanning Folder n°" + current + "/" + total + " : " + safeName(file));
            myLogD("--------------------------------------------------------");

            String fileName = safeName(file);
            BookCandidate candidate = new BookCandidate(context, file.getUri());
            addCandidate(candidate, candidates);
        }

        // Process non-ZIP files (epub, m4b, etc.)
        for (DocumentFile file : fileCandidates) {
            if (isCancelled)
                return;
            current++;
            callback.onProgress("scanning", current, total, safeName(file));
            myLogD("--------------------------------------------------------");
            myLog("Scanning File n°" + current + "/" + total + " : " + safeName(file));
            myLogD("--------------------------------------------------------");

            String type = detectBookType(file);
            if (type != null) {
                String fileName = safeName(file);
                BookCandidate candidate = new BookCandidate(context, file.getUri());
                addCandidate(candidate, candidates);
            }
        }

        // Process deferred archives (ZIP, 7z, etc.)
        processDeferredFiles(zipFiles, candidates, current, total);
    }

    /**
     * Recursively collects: (1) directories that contain audio as folder
     * candidates,
     * (2) book-type files (epub, m4b, etc.) as file candidates, (3) archive files
     * (zip, 7z) for deferred processing.
     */
    private void collectCandidatesRecursive(DocumentFile dir,
            List<DocumentFile> folderCandidates, List<DocumentFile> fileCandidates,
            List<DocumentFile> zipFiles, int[] count) {
        if (isCancelled)
            return;
        DocumentFile[] files = dir.listFiles();
        if (files == null)
            return;

        // Update progress every now and then (or for every item if not too fast)
        // For smoother UI, maybe every 10 items or just every item ?
        // Let's report every item for "Counting... X"
        // But to avoid flooding, maybe we can throttle in UI or here.
        // For now, simple report.

        for (DocumentFile file : files) {
            if (isCancelled)
                return;

            count[0]++;
            //if (count[0] % 5 == 0 && callback != null) { // Report every 5 items to reduce spam
                callback.onProgress("counting", count[0], 0, "");
            //}

            if (file.isDirectory()) {
                if (hasAnyAudioRecursive(file)) {
                    folderCandidates.add(file);
                } else {
                    collectCandidatesRecursive(file, folderCandidates, fileCandidates, zipFiles, count);
                }
            } else {
                String type = detectBookType(file);
                if ("Archive".equals(type)) {
                    zipFiles.add(file);
                } else if (type != null) {
                    fileCandidates.add(file);
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
        int total = files.length;
        int current = 0;

        for (DocumentFile file : files) {
            if (isCancelled)
                break;

            if (file.isDirectory()) {
                current++;
                callback.onProgress("scanning", current, total, safeName(file));
                myLogD("--------------------------------------------------------");
                myLog("Scanning Folder n°" + current + "/" + total + " : " + safeName(file));
                myLogD("--------------------------------------------------------");

                if (hasAnyAudioRecursive(file)) {
                    String fileName = safeName(file);
                    BookCandidate candidate = new BookCandidate(context, file.getUri());
                    addCandidate(candidate, candidates);
                }
            } else {
                String type = detectBookType(file);
                if ("Archive".equals(type)) {
                    deferredArchives.add(file);
                    callback.onProgress("scanning", current, total, "Deferring: " + safeName(file));
                } else {
                    current++;
                    callback.onProgress("scanning", current, total, safeName(file));
                    myLogD("--------------------------------------------------------");
                    myLog("Scanning File n°" + current + "/" + total + " : " + safeName(file));
                    myLogD("--------------------------------------------------------");

                    if (type != null) {
                        String fileName = safeName(file);
                        BookCandidate candidate = new BookCandidate(context, file.getUri());
                        addCandidate(candidate, candidates);
                    }
                }
            }
        }
        myLogD("--------------------------------------------------------");
        processDeferredFiles(deferredArchives, candidates, current, total);
    }

    // Process deferred archives at the end
    private void processDeferredFiles(List<DocumentFile> deferredArchives, List<BookCandidate> candidates,
            int currentCount, int totalItems) {
        if (deferredArchives.isEmpty())
            return;

        for (DocumentFile file : deferredArchives) {
            if (isCancelled)
                return;

            // Increment now that we are actually processing it
            currentCount++;
            String fileName = safeName(file);
            callback.onProgress("scanning", currentCount, totalItems, "Processing archive: " + fileName);
            myLogD("--------------------------------------------------------");
            myLog("Scanning Bundle " + currentCount + "/" + totalItems + " : " + fileName);
            myLogD("--------------------------------------------------------");

            String type = detectBookType(file);
            if (type != null) {
                BookCandidate candidate = new BookCandidate(context, file.getUri());
                addCandidate(candidate, candidates);
            }
        }
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
        int total = files.length;
        int count = 0;
        List<DocumentFile> deferredArchives = new ArrayList<>();

        for (DocumentFile file : files) {
            if (isCancelled)
                break;
            count++;
            callback.onProgress("scanning", count, total, safeName(file));

            if (file.isDirectory()) {
                // Check logic for folder
                if (hasAnyAudioRecursive(file)) {
                    String fileName = safeName(file);
                    BookCandidate candidate = new BookCandidate(context, file.getUri());
                    addCandidate(candidate, candidates);
                }
            } else {
                String type = detectBookType(file);
                if ("Archive".equals(type)) {
                    deferredArchives.add(file);
                } else if (type != null) {
                    String fileName = safeName(file);
                    BookCandidate candidate = new BookCandidate(context, file.getUri());
                    addCandidate(candidate, candidates);
                }
            }
        }
        processDeferredFiles(deferredArchives, candidates, count, total);
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
