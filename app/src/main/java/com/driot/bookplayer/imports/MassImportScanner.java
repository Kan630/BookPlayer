package com.driot.bookplayer.imports;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.utils.HashWorker;
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
        myLogD("Scanning directory: " + dir.getName());

        DocumentFile[] files = dir.listFiles();
        boolean hasAudio = false;

        for (DocumentFile file : files) {
            if (isCancelled)
                return;

            String fileName = safeName(file);
            myLogD("Checking file: " + fileName);

            if (file.isDirectory()) {
                if (hasAnyAudioRecursive(file)) {
                    myLogD("-> Found Audio Folder candidate: " + fileName);
                    long size = calculateSize(file);
                    // Compute hash for folder and check if already imported
                    String hash = computeHash(file.getUri());
                    String existingBookName = checkHashExists(hash);
                    int tracksCount = calculateTrackCount(file);
                    candidates.add(new BookCandidate(file.getUri(), fileName, "Folder",
                            safeName(dir) + "/" + fileName, size, hash, existingBookName, tracksCount));
                } else {
                    myLogD("-> Ignored folder (no audio): " + fileName);
                }

            } else {
                String type = detectBookType(file);
                if (type != null) {
                    myLogD("-> Found File candidate [" + type + "]: " + fileName);
                    // Compute hash for file and check if already imported
                    String hash = computeHash(file.getUri());
                    String existingBookName = checkHashExists(hash);
                    candidates.add(new BookCandidate(file.getUri(), fileName, type,
                            safeName(dir) + "/" + fileName, file.length(), hash, existingBookName, 1));
                } else {
                    myLogD("-> Ignored file (unsupported type): " + fileName);
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
                    long size = com.driot.bookplayer.utils.Tonio
                            .getFolderSize(new java.io.File(file.getUri().getPath())); // This might not work for
                                                                                       // TreeUri content://
                    // Tonio.getFolderSize(File) only works for java.io.File.
                    // For DocumentFile, we need a recursive calculation distinct from java.io.File
                    // if it's not a raw file path.
                    // However, Tonio.getFolderSize(String) exists but might expect a path.
                    // Let's implement a recursive size for DocumentFile here or reuse Tonio if
                    // possible,
                    // BUT Tonio.getFolderSize uses File.
                    // Let's rely on a local helper for DocumentFile size.
                    size = calculateSize(file);

                    String hash = computeHash(file.getUri());
                    String existingBookName = checkHashExists(hash);
                    int tracksCount = calculateTrackCount(file);
                    candidates.add(new BookCandidate(file.getUri(), safeName(file), "Folder", safeName(file), size,
                            hash, existingBookName, tracksCount));
                }
            } else {
                String type = detectBookType(file);
                if (type != null) {
                    String hash = computeHash(file.getUri());
                    String existingBookName = checkHashExists(hash);
                    candidates
                            .add(new BookCandidate(file.getUri(), safeName(file), type, safeName(file), file.length(),
                                    hash, existingBookName, 1));
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

    private long calculateSize(DocumentFile file) {
        if (!file.isDirectory()) {
            return file.length();
        }
        long size = 0;
        for (DocumentFile child : file.listFiles()) {
            size += calculateSize(child);
        }
        return size;
    }

    private int calculateTrackCount(DocumentFile file) {
        if (!file.isDirectory()) {
            // If it's a file, check if it's audio
            return isAudio(file) ? 1 : 0;
        }
        int count = 0;
        for (DocumentFile child : file.listFiles()) {
            count += calculateTrackCount(child);
        }
        return count;
    }

    private String safeName(DocumentFile f) {
        String n = f.getName();
        return n == null ? "Untitled" : n;
    }

    private String computeHash(Uri uri) {
        try {
            String hash = HashWorker.computeHashFromUri(context, uri);
            if (hash != null && !hash.isEmpty()) {
                // Try to get name for logging, but don't fail if it doesn't work
                String name = uri.getLastPathSegment();
                if (name != null && name.contains("/")) {
                    name = name.substring(name.lastIndexOf('/') + 1);
                }
                myLogD("Computed hash for [" + (name != null ? name : "item") + "]: " + hash);
                return hash;
            } else {
                myLogW("Failed to compute hash for URI: " + uri);
                return null;
            }
        } catch (Exception e) {
            myLogEE(e, "Error computing hash for URI: " + uri);
            return null;
        }
    }

    private String checkHashExists(String hash) {
        if (hash == null || hash.isEmpty()) {
            return null;
        }
        try {
            String existingBookName = AppDatabase.getDatabase(context).folderDao()
                    .originalHashAlreadyExist_getBookName(hash);
            if (existingBookName != null) {
                myLogD("Hash already exists in DB for book: " + existingBookName);
            }
            return existingBookName;
        } catch (Exception e) {
            myLogEE(e, "Error checking if hash exists in DB: " + hash);
            return null;
        }
    }
}
