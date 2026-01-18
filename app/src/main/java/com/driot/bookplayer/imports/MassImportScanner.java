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
                    candidates.add(new BookCandidate(file.getUri(), fileName, "Folder",
                            safeName(dir) + "/" + fileName));
                } else {
                    myLogD("-> Ignored folder (no audio): " + fileName);
                }

            } else {
                String type = detectBookType(file);
                if (type != null) {
                    myLogD("-> Found File candidate [" + type + "]: " + fileName);
                    candidates.add(new BookCandidate(file.getUri(), fileName, type,
                            safeName(dir) + "/" + fileName));
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
