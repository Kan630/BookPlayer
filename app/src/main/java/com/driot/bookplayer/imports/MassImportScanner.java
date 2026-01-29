package com.driot.bookplayer.imports;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.ebooks.Fb2LowLevelHelper;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.CoverPictureDetection;
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
        void onProgress(int current, int total, String currentPath);

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
        List<DocumentFile> deferredArchives = new ArrayList<>();

        DocumentFile root = DocumentFile.fromTreeUri(context, rootUri);
        if (root == null || !root.isDirectory()) {
            myLogE("Root is not a directory or null: " + rootUri);
            return candidates;
        }

        // Count top-level items for progress tracking
        DocumentFile[] files = root.listFiles();
        int total = files.length;
        int current = 0;

        for (DocumentFile file : files) {
            if (isCancelled)
                break;

            if (file.isDirectory()) {
                // Directories are processed immediately
                current++;
                callback.onProgress(current, total, safeName(file));

                if (hasAnyAudioRecursive(file)) {
                    String fileName = safeName(file);
                    long size = calculateSize(file);
                    String hash = computeHash(file.getUri());
                    String existingBookName = checkHashExists(hash);
                    int tracksCount = calculateTrackCount(file);
                    String coverPath = detectCoverForFolder(file);

                    BookCandidate candidate = new BookCandidate(file.getUri(), fileName, "Folder",
                            safeName(root) + "/" + fileName, size, hash, existingBookName, tracksCount, coverPath);
                    addCandidate(candidate, candidates);
                }
            } else {
                String type = detectBookType(file);
                if ("ZIP".equals(type)) {
                    deferredArchives.add(file);
                    // Deferring: Do NOT increment current yet.
                    // We can notify the user we are deferring, but keep count unchanged?
                    // Or simply show nothing and move to next. User will see the count pause.
                    callback.onProgress(current, total, "Deferring: " + safeName(file));
                } else {
                    current++;
                    callback.onProgress(current, total, safeName(file));

                    if (type != null) {
                        String fileName = safeName(file);
                        String hash = computeHash(file.getUri());
                        String existingBookName = checkHashExists(hash);
                        String coverPath = detectCoverForFile(file, type);

                        BookCandidate candidate = new BookCandidate(file.getUri(), fileName, type,
                                safeName(root) + "/" + fileName, file.length(), hash, existingBookName, 1, coverPath);
                        addCandidate(candidate, candidates);
                    }
                }
            }
        }

        // Process deferred archives
        processDeferredFiles(deferredArchives, candidates, current, total);

        return candidates;
    }

    // Process deferred archives at the end
    private void processDeferredFiles(List<DocumentFile> deferredArchives, List<BookCandidate> candidates,
            int currentCount, int totalItems) {
        if (deferredArchives.isEmpty())
            return;

        myLog("Processing " + deferredArchives.size() + " deferred archives...");

        for (DocumentFile file : deferredArchives) {
            if (isCancelled)
                return;

            // Increment now that we are actually processing it
            currentCount++;
            String fileName = safeName(file);
            callback.onProgress(currentCount, totalItems, "Processing archive: " + fileName);

            String type = detectBookType(file);
            if (type != null) {
                String hash = computeHash(file.getUri());
                String existingBookName = checkHashExists(hash);
                String coverPath = detectCoverForFile(file, type);

                BookCandidate candidate = new BookCandidate(file.getUri(), fileName, type,
                        fileName, file.length(), hash, existingBookName, 1, coverPath);
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
            callback.onProgress(count, total, safeName(file));

            if (file.isDirectory()) {
                // Check logic for folder
                if (hasAnyAudioRecursive(file)) {
                    String fileName = safeName(file);
                    long size = calculateSize(file);
                    String hash = computeHash(file.getUri());
                    String existingBookName = checkHashExists(hash);
                    int tracksCount = calculateTrackCount(file);
                    String coverPath = detectCoverForFolder(file);
                    BookCandidate candidate = new BookCandidate(file.getUri(), fileName, "Folder",
                            safeName(root) + "/" + fileName, size, hash, existingBookName, tracksCount, coverPath);
                    addCandidate(candidate, candidates);
                }
            } else {
                // This method still uses the old processCandidate logic, which is now inlined
                // in scan()
                // For consistency, it should probably be updated to match scan()'s new inlined
                // logic.
                // For now, keeping it as is, but noting the discrepancy.
                String type = detectBookType(file);
                if ("ZIP".equals(type)) {
                    deferredArchives.add(file);
                } else if (type != null) {
                    String fileName = safeName(file);
                    String hash = computeHash(file.getUri());
                    String existingBookName = checkHashExists(hash);
                    String coverPath = detectCoverForFile(file, type);

                    BookCandidate candidate = new BookCandidate(file.getUri(), fileName, type,
                            safeName(root) + "/" + fileName, file.length(), hash, existingBookName, 1, coverPath);
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

    /**
     * Detects cover image for a folder by scanning for image files.
     * If no image found, tries to extract embedded cover from audio files.
     * 
     * @param folder DocumentFile representing the folder
     * @return Path to detected cover image, or null if none found
     */
    private String detectCoverForFolder(DocumentFile folder) {
        try {
            // Step 1: Try to find image files in folder
            CoverPictureDetection.CoverDetectionResult result = CoverPictureDetection.detectCoverFromFolder(context,
                    folder, null);

            if (result != null && result.imagePath != null) {
                myLogD("Cover image detected for folder: " + safeName(folder) + " -> " + result.imagePath);
                return result.imagePath;
            }

            // Step 2: No image found - try to extract embedded cover from audio files
            myLogD("No image file found, checking audio metadata for folder: " + safeName(folder));
            String embeddedCover = extractEmbeddedCoverFromFolder(folder);
            if (embeddedCover != null) {
                myLogD("Embedded cover extracted from audio for folder: " + safeName(folder) + " -> " + embeddedCover);
                return embeddedCover;
            }
        } catch (Exception e) {
            myLogEE(e, "Error detecting cover for folder: " + safeName(folder));
        }
        return null;
    }

    /**
     * Extracts embedded cover from the first audio file found in folder.
     * 
     * @param folder DocumentFile representing the folder
     * @return Path to extracted cover, or null if none found
     */
    private String extractEmbeddedCoverFromFolder(DocumentFile folder) {
        try {
            // Scan folder for audio files
            for (DocumentFile file : folder.listFiles()) {
                if (file.isFile() && isAudio(file)) {
                    // Try to extract embedded cover from this audio file
                    android.media.MediaMetadataRetriever mmr = new android.media.MediaMetadataRetriever();
                    try {
                        mmr.setDataSource(context, file.getUri());
                        CoverPictureDetection.CoverDetectionResult result = CoverPictureDetection
                                .extractEmbeddedCover(mmr);

                        if (result != null && result.bitmap != null) {
                            // Save bitmap to temp file with unique suffix to avoid collision
                            String suffix = "_" + file.getUri().hashCode();
                            String tempPath = com.driot.bookplayer.helpers.ImageHelper.saveTempBitmap(context,
                                    result.bitmap, suffix);
                            if (tempPath != null) {
                                myLogD("Embedded cover saved from audio file: " + safeName(file));
                                return tempPath;
                            }
                        }
                    } finally {
                        try {
                            mmr.release();
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        } catch (Exception e) {
            myLogEE(e, "Error extracting embedded cover from folder: " + safeName(folder));
        }
        return null;
    }

    /**
     * Detects cover image for a file (M4B, EPUB, etc).
     * Extracts embedded covers from M4B/audio files.
     * EPUB extraction skipped (too complex for scan).
     * 
     * @param file DocumentFile representing the file
     * @param type Type of file (M4B, Ebook, etc)
     * @return Path to detected cover image, or null if none found
     */
    private String detectCoverForFile(DocumentFile file, String type) {
        // Extract embedded cover from M4B and audio files
        if ("M4B".equals(type) || "Audio File".equals(type)) {
            try {
                android.media.MediaMetadataRetriever mmr = new android.media.MediaMetadataRetriever();
                try {
                    mmr.setDataSource(context, file.getUri());
                    CoverPictureDetection.CoverDetectionResult result = CoverPictureDetection.extractEmbeddedCover(mmr);

                    if (result != null && result.bitmap != null) {
                        // Save bitmap to temp file with unique suffix to avoid collision
                        String suffix = "_" + file.getUri().hashCode();
                        String tempPath = com.driot.bookplayer.helpers.ImageHelper.saveTempBitmap(context,
                                result.bitmap, suffix);
                        if (tempPath != null) {
                            myLogD("Embedded cover extracted from M4B/audio file: " + safeName(file));
                            return tempPath;
                        }
                    }
                } finally {
                    try {
                        mmr.release();
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception e) {
                myLogEE(e, "Error extracting embedded cover from file: " + safeName(file));
            }
        }

        // Extract cover from EPUB and FB2 files
        if ("Ebook".equals(type)) {
            String fileName = safeName(file).toLowerCase();

            if (fileName.endsWith(".epub")) {
                try {
                    // Unzip EPUB
                    java.util.Map<String, byte[]> zip = com.driot.bookplayer.ebooks.EpubCommonHelper.readZip(
                            file.getUri(), context);

                    // Find and parse OPF
                    byte[] containerXml = zip.get("META-INF/container.xml");
                    if (containerXml != null) {
                        String opfPath = com.driot.bookplayer.ebooks.EpubCommonHelper.findOpfPath(containerXml);
                        byte[] opfBytes = zip.get(opfPath);

                        if (opfBytes != null) {
                            // Parse OPF using EpubLowLevelHelper
                            com.driot.bookplayer.ebooks.EpubLowLevelHelper.OpfInfo opf = com.driot.bookplayer.ebooks.EpubLowLevelHelper
                                    .parseOpf(opfBytes);
                            opf.opfPath = opfPath; // Set the OPF path

                            // Extract cover
                            CoverPictureDetection.CoverDetectionResult result = CoverPictureDetection
                                    .detectCoverFromEpub(zip, opf);

                            if (result != null && result.bitmap != null) {
                                String suffix = "_" + file.getUri().hashCode();
                                String tempPath = com.driot.bookplayer.helpers.ImageHelper.saveTempBitmap(context,
                                        result.bitmap, suffix);
                                if (tempPath != null) {
                                    myLogD("Cover extracted from EPUB: " + safeName(file));
                                    return tempPath;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    myLogEE(e, "Error extracting EPUB cover: " + safeName(file));
                }
            } else if (fileName.endsWith(".fb2")) {
                // Extract cover from FB2 files
                try {
                    String xml = com.driot.bookplayer.ebooks.Fb2LowLevelHelper.readAllText(context, file.getUri());
                    Fb2LowLevelHelper.Meta meta = com.driot.bookplayer.ebooks.Fb2LowLevelHelper
                            .parseMetaAndBinaries(xml);

                    // Extract cover bitmap
                    if (meta.coverImageId != null && !meta.coverImageId.isEmpty()) {
                        byte[] imageBytes = meta.binaries.get(meta.coverImageId);
                        if (imageBytes != null) {
                            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(
                                    imageBytes, 0, imageBytes.length);

                            if (bitmap != null) {
                                String suffix = "_" + file.getUri().hashCode();
                                String tempPath = com.driot.bookplayer.helpers.ImageHelper.saveTempBitmap(context,
                                        bitmap, suffix);
                                if (tempPath != null) {
                                    myLogD("Cover extracted from FB2: " + safeName(file));
                                    return tempPath;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    myLogEE(e, "Error extracting FB2 cover: " + safeName(file));
                }
            }
        }

        // Extract cover from ZIP, 7Z and TAR files
        if ("ZIP".equals(type)) {
            String fileName = safeName(file).toLowerCase();

            // 1. 7Z Files (Requires seeking)
            if (fileName.endsWith(".7z")) {
                try {
                    try (android.os.ParcelFileDescriptor pfd = context.getContentResolver()
                            .openFileDescriptor(file.getUri(), "r")) {
                        if (pfd != null) {
                            try (java.nio.channels.FileChannel channel = new java.io.FileInputStream(
                                    pfd.getFileDescriptor()).getChannel()) {
                                try (org.apache.commons.compress.archivers.sevenz.SevenZFile sevenZFile = new org.apache.commons.compress.archivers.sevenz.SevenZFile(
                                        channel)) {

                                    org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry entry;
                                    byte[] largestImage = null;
                                    long largestSize = 0;
                                    String largestName = null;

                                    while ((entry = sevenZFile.getNextEntry()) != null) {
                                        if (!entry.isDirectory()) {
                                            String entryName = entry.getName().toLowerCase();
                                            if (entryName.endsWith(".jpg") || entryName.endsWith(".jpeg") ||
                                                    entryName.endsWith(".png") || entryName.endsWith(".webp")) {
                                                long size = entry.getSize();
                                                if (size > largestSize || (size == -1 && largestImage == null)) {
                                                    if (size > 10 * 1024 * 1024)
                                                        continue;
                                                    int contentSize = (int) size;
                                                    byte[] content = new byte[contentSize];
                                                    int bytesRead = 0;
                                                    while (bytesRead < contentSize) {
                                                        int read = sevenZFile.read(content, bytesRead,
                                                                contentSize - bytesRead);
                                                        if (read == -1)
                                                            break;
                                                        bytesRead += read;
                                                    }
                                                    if (bytesRead == contentSize) {
                                                        byte[] imageBytes = content;
                                                        if (imageBytes.length > largestSize) {
                                                            largestImage = imageBytes;
                                                            largestSize = imageBytes.length;
                                                            largestName = entry.getName();
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (largestImage != null) {
                                        android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(
                                                largestImage, 0, largestImage.length);
                                        if (bitmap != null) {
                                            String suffix = "_" + file.getUri().hashCode();
                                            String tempPath = com.driot.bookplayer.helpers.ImageHelper.saveTempBitmap(
                                                    context, bitmap, suffix);
                                            if (tempPath != null) {
                                                myLogD("Cover extracted from 7Z: " + safeName(file) + " -> "
                                                        + largestName);
                                                return tempPath;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    myLogEE(e, "Error extracting 7Z cover: " + safeName(file));
                }
            }
            // 2. TAR Files (inc. tgz, tbz2, txz)
            else if (fileName.endsWith(".tar") || fileName.endsWith(".tgz") || fileName.endsWith(".tar.gz")
                    || fileName.endsWith(".tbz2") || fileName.endsWith(".tar.bz2")
                    || fileName.endsWith(".txz") || fileName.endsWith(".tar.xz")) {

                try {
                    java.io.InputStream inputStream = context.getContentResolver().openInputStream(file.getUri());
                    if (inputStream != null) {
                        try (java.io.InputStream bis = new java.io.BufferedInputStream(inputStream);
                                java.io.InputStream cis = maybeWrapCompressor(bis);
                                org.apache.commons.compress.archivers.tar.TarArchiveInputStream tis = new org.apache.commons.compress.archivers.tar.TarArchiveInputStream(
                                        cis)) {

                            org.apache.commons.compress.archivers.tar.TarArchiveEntry entry;
                            byte[] largestImage = null;
                            long largestSize = 0;
                            String largestName = null;

                            while ((entry = tis.getNextTarEntry()) != null) {
                                if (!entry.isDirectory()) {
                                    String entryName = entry.getName().toLowerCase();
                                    if (entryName.endsWith(".jpg") || entryName.endsWith(".jpeg") ||
                                            entryName.endsWith(".png") || entryName.endsWith(".webp")) {

                                        long size = entry.getSize();
                                        if (size > largestSize || (size == -1 && largestImage == null)) {
                                            if (size > 10 * 1024 * 1024)
                                                continue; // Skip huge images to avoid OOM

                                            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                                            byte[] buffer = new byte[8192];
                                            int len;
                                            // TarInputStream reads until end of entry
                                            while ((len = tis.read(buffer)) > 0) {
                                                baos.write(buffer, 0, len);
                                            }
                                            byte[] imageBytes = baos.toByteArray();

                                            if (imageBytes.length > largestSize) {
                                                largestImage = imageBytes;
                                                largestSize = imageBytes.length;
                                                largestName = entry.getName();
                                            }
                                        }
                                    }
                                }
                            }

                            if (largestImage != null) {
                                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(
                                        largestImage, 0, largestImage.length);
                                if (bitmap != null) {
                                    String suffix = "_" + file.getUri().hashCode();
                                    String tempPath = com.driot.bookplayer.helpers.ImageHelper.saveTempBitmap(context,
                                            bitmap, suffix);
                                    if (tempPath != null) {
                                        myLogD("Cover extracted from TAR: " + safeName(file) + " -> " + largestName);
                                        return tempPath;
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    myLogEE(e, "Error extracting TAR cover: " + safeName(file));
                }
            }
            // 3. ZIP Files (Streamable) - Default fallback
            else {
                try {
                    // Stream through ZIP entries to find largest image file
                    java.io.InputStream inputStream = context.getContentResolver().openInputStream(file.getUri());
                    if (inputStream != null) {
                        java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(inputStream);
                        java.util.zip.ZipEntry entry;

                        byte[] largestImage = null;
                        long largestSize = 0;
                        String largestName = null;

                        while ((entry = zis.getNextEntry()) != null) {
                            if (!entry.isDirectory()) {
                                String entryName = entry.getName().toLowerCase();
                                // Check if it's an image file
                                if (entryName.endsWith(".jpg") || entryName.endsWith(".jpeg") ||
                                        entryName.endsWith(".png") || entryName.endsWith(".webp")) {

                                    long size = entry.getSize();
                                    // If size unknown or larger than current largest
                                    if (size > largestSize || (size == -1 && largestImage == null)) {
                                        // Read this image
                                        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                                        byte[] buffer = new byte[8192];
                                        int len;
                                        while ((len = zis.read(buffer)) > 0) {
                                            baos.write(buffer, 0, len);
                                        }
                                        byte[] imageBytes = baos.toByteArray();

                                        // Update if this is larger
                                        if (imageBytes.length > largestSize) {
                                            largestImage = imageBytes;
                                            largestSize = imageBytes.length;
                                            largestName = entry.getName();
                                        }
                                    }
                                    zis.closeEntry();
                                }
                            }
                        }
                        zis.close();

                        // Decode largest image if found
                        if (largestImage != null) {
                            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(
                                    largestImage, 0, largestImage.length);

                            if (bitmap != null) {
                                String suffix = "_" + file.getUri().hashCode();
                                String tempPath = com.driot.bookplayer.helpers.ImageHelper.saveTempBitmap(context,
                                        bitmap, suffix);
                                if (tempPath != null) {
                                    myLogD("Cover extracted from ZIP: " + safeName(file) + " -> " + largestName);
                                    return tempPath;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    myLogEE(e, "Error extracting ZIP cover: " + safeName(file));
                }
            }

        }

        return null;
    }

    private static java.io.InputStream maybeWrapCompressor(java.io.InputStream in)
            throws org.apache.commons.compress.compressors.CompressorException {
        // auto-detect gzip/bzip2/xz by magic bytes
        org.apache.commons.compress.compressors.CompressorStreamFactory f = new org.apache.commons.compress.compressors.CompressorStreamFactory(
                true);
        try {
            return f.createCompressorInputStream(in); // compressed tar
        } catch (org.apache.commons.compress.compressors.CompressorException notCompressed) {
            // plain .tar (no compression)
            return in;
        }
    }
}
