package com.driot.bookplayer.utils;

import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;
import com.driot.bookplayer.helpers.UriHelper;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.utils.log.KanLogger;
import com.driot.bookplayer.utils.log.LoggingWorker;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class HashWorker extends LoggingWorker {

    public static final String WORKER_TAG_COMPUTE_HASH = "WORKER_TAG_COMPUTE_HASH";
    public static final int MAX_BYTES_TO_HASH_PER_FILE_BIG = 1024 * 1024; // 1 MB
    public static final int MAX_BYTES_TO_HASH_PER_FILE_SMALL = 1024 * 50; // 50 KB
    public static final int COUNT_FILE_BIG_HASH = 3;
    public static final int COUNT_FILE_SMALL_HASH = 10;
    public static final String HASH_NOT_COMPUTED = "0";

    public static final boolean VERBOSE_DEBUG = false;

    public HashWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWorkBody() {
        // Optionally enter foreground:
        // setForegroundEarly(buildForegroundInfo());

        String uriStr = getInputData().getString("uri");
        if (uriStr != null) {
            return computeHashForUri(uriStr);
        } else {
            return backfillMissingHashesInDb();
        }
    }

    private Result computeHashForUri(String uriStr) {
        try {
            Uri uri = Uri.parse(uriStr);
            String hash = computeHashFromUri(getApplicationContext(), uri);
            boolean exists = AppDatabase.getDatabase(getApplicationContext()).folderDao().hashExists(hash);

            Data outputData = new Data.Builder()
                    .putString(WORKER_TAG_COMPUTE_HASH, hash)
                    .putBoolean("exists_in_db", exists)
                    .build();

            return Result.success(outputData);
        } catch (Exception e) {
            myLogEE(e, "Error computing hash from URI");
            return Result.success();
        }
    }

    private Result backfillMissingHashesInDb() {
        AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
        List<Folder> folders = db.folderDao().getAll();

        for (Folder folder : folders) {
            if (HASH_NOT_COMPUTED.equals(folder.getHash())) {
                try {
                    myLogD("Hashing folder: " + folder.getPath());
                    File dir = new File(folder.getPath());
                    String hash = computeFolderHash(dir);
                    db.folderDao().updateHash(folder.getId(), hash);
                } catch (Exception e) {
                    myLogEE(e, "Exception while hashing folder: " + folder.getPath());
                }
            }
        }
        return Result.success();
    }

    private String computeFolderHash(File folder) {
        // NOT USED YET, for background from Main Activity
        if (!folder.exists() || !folder.isDirectory())
            return "";
        File[] files = folder.listFiles();
        if (files == null)
            return "";

        Arrays.sort(files, Comparator.comparing(File::getName));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long totalStart = System.currentTimeMillis();
            long sumElapsed = 0;
            int fileCount = 0;

            for (int i = 0; i < files.length; i++) {
                File file = files[i];
                if (file.isFile()) {
                    long startTime = System.currentTimeMillis();
                    int bytesRead = 0;
                    String type;
                    try (FileInputStream fis = new FileInputStream(file)) {
                        if (i < COUNT_FILE_BIG_HASH) {
                            type = "BIG";
                            bytesRead = updateDigestFromStream(fis, digest, MAX_BYTES_TO_HASH_PER_FILE_BIG);
                        } else if (i < COUNT_FILE_BIG_HASH + COUNT_FILE_SMALL_HASH) {
                            type = "SMALL";
                            bytesRead = updateDigestFromStream(fis, digest, MAX_BYTES_TO_HASH_PER_FILE_SMALL);
                        } else {
                            break;
                        }
                    }
                    long elapsed = System.currentTimeMillis() - startTime;
                    sumElapsed += elapsed;
                    fileCount++;
                    myLogDD(elapsed + " ms to hash - HashType = " + type + " - Read = " + Tonio.getReadableSize(bytesRead) + " - " + file.getName());
                }
            }

            long totalElapsed = System.currentTimeMillis() - totalStart;
            myLogD("Nb of file : " + fileCount + " files.");
            myLogD("[Timing] " + totalElapsed + " ms Total Process time");
            myLogD("[Timing] " + sumElapsed + " ms Cumulative hash time");
            return formatHash(digest.digest());
        } catch (Exception e) {
            myLogEE(e, "Exception while computing hash");
            return "";
        }
    }

    private static int updateDigestFromStream(InputStream is, MessageDigest digest, int maxBytes) throws Exception {
        byte[] buffer = new byte[4096];
        int totalRead = 0;
        int read;
        while ((read = is.read(buffer)) != -1 && totalRead < maxBytes) {
            int bytesToUse = Math.min(read, maxBytes - totalRead);
            digest.update(buffer, 0, bytesToUse);
            totalRead += bytesToUse;
        }
        return totalRead;
    }

    /**
     * Public static method to compute hash from URI synchronously.
     * Can be used when hash needs to be computed before launching import.
     */
    public static String computeHashFromUri(Context context, Uri uri) {
        KanLogger.myLogD("HashWorker", "computeHashFromUri() START: " + uri);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long totalStart = System.currentTimeMillis();

            // shared counters across the whole operation
            long[] sumElapsed = new long[] { 0 };
            int[] fileCount = new int[] { 0 };
            int[] fileIndex = new int[] { 0 }; // drives BIG/SMALL tiering

            final String scheme = uri.getScheme();
            final String uriStr = uri.toString();

            // 1) http(s) → hash the string itself
            if (uriStr.startsWith("http://") || uriStr.startsWith("https://")) {
                KanLogger.myLogD("HashWorker", "Hashing URI string instead of content: " + uriStr);
                digest.update(uriStr.getBytes(StandardCharsets.UTF_8));
                return formatHash(digest.digest());
            }

            // 2) file://
            if ("file".equalsIgnoreCase(scheme)) {
                File f = new File(uri.getPath());
                if (f.isFile()) {
                    try (InputStream is = new FileInputStream(f)) {
                        hashAndLog(is, digest, fileIndex, sumElapsed, fileCount, f.getName());
                    }
                    KanLogger.myLogD("HashWorker", "---Nb of hashed file : " + fileCount[0] + " files.");
                    KanLogger.myLogD("HashWorker", "---[Timing] " + sumElapsed[0] + " ms Cumulative hash time");
                    KanLogger.myLogD("HashWorker", "---[Timing] "
                            + (System.currentTimeMillis() - totalStart - sumElapsed[0]) + " ms other processes time");
                    return formatHash(digest.digest());
                } else if (f.isDirectory()) {
                    computeFolderHashRecursiveFs(f, digest, sumElapsed, fileCount, fileIndex);
                    KanLogger.myLogD("HashWorker", "---Nb of hashed file : " + fileCount[0] + " files.");
                    KanLogger.myLogD("HashWorker", "---[Timing] " + sumElapsed[0] + " ms Cumulative hash time");
                    KanLogger.myLogD("HashWorker", "---[Timing] "
                            + (System.currentTimeMillis() - totalStart - sumElapsed[0]) + " ms other processes time");
                    return formatHash(digest.digest());
                } else {
                    throw new IllegalArgumentException("file:// is neither regular file nor directory: " + uri);
                }
            }

            // 3) content://
            if ("content".equalsIgnoreCase(scheme)) {
                DocumentFile doc = UriHelper.getDocumentFileFromAnyUri(context, uri);
                if (doc != null) {
                    if (doc.isFile()) {
                        try (InputStream is = context.getContentResolver().openInputStream(doc.getUri())) {
                            if (is != null) {
                                hashAndLog(is, digest, fileIndex, sumElapsed, fileCount, doc.getName());
                            }
                        }
                    } else if (doc.isDirectory()) {
                        computeFolderHashRecursive(context, doc, digest,
                                /* rootPathLen */ (uri.getPath() != null ? uri.getPath().length() : 0),
                                sumElapsed, fileCount, fileIndex);
                    }
                } else {
                    // Non-DocumentProvider content:// (e.g. MediaStore) → treat as a single
                    // readable stream
                    KanLogger.myLogD("HashWorker",
                            "Generic content URI (non-DocumentProvider or null DocFile). Hashing via openInputStream.");
                    try (InputStream is = context.getContentResolver().openInputStream(uri)) {
                        if (is == null)
                            throw new IllegalArgumentException("openInputStream returned null for: " + uri);
                        hashAndLog(is, digest, fileIndex, sumElapsed, fileCount, "content");
                    }
                }

                KanLogger.myLogD("HashWorker", "---Nb of hashed file : " + fileCount[0] + " files.");
                KanLogger.myLogD("HashWorker", "---[Timing] " + sumElapsed[0] + " ms Cumulative hash time");
                KanLogger.myLogD("HashWorker", "---[Timing] "
                        + (System.currentTimeMillis() - totalStart - sumElapsed[0]) + " ms other processes time");
                return formatHash(digest.digest());
            }

            throw new IllegalArgumentException("Unsupported URI scheme: " + scheme + " (" + uri + ")");
        } catch (Exception e) {
            KanLogger.myLogEE(e, "HashWorker", "Exception in computeHashFromUri()");
            return "";
        }
    }

    private static void computeFolderHashRecursive(Context context, DocumentFile folder, MessageDigest digest,
            int rootPathLen, long[] sumElapsed, int[] fileCount, int[] fileIndex) {
        if (fileIndex[0] >= COUNT_FILE_BIG_HASH + COUNT_FILE_SMALL_HASH)
            return;

        long listStart = System.currentTimeMillis();
        DocumentFile[] files = folder.listFiles();
        myLogDD(System.currentTimeMillis() - listStart + " ms to list " + files.length
                + " files in " + folder.getName());

        // TODO will only sort the first subFolder... but well...
        long sortStart = System.currentTimeMillis();
        int n = files.length;
        String[] names = new String[n];
        Integer[] indices = new Integer[n];
        for (int i = 0; i < n; i++) {
            names[i] = files[i].getName();
            indices[i] = i;
        }
        Arrays.sort(indices, Comparator.comparing(i -> names[i], Comparator.nullsFirst(String::compareToIgnoreCase)));
        myLogDD(System.currentTimeMillis() - sortStart + " ms to sort files in " + folder.getName());

        for (int i = 0; i < n; i++) {
            if (fileIndex[0] >= COUNT_FILE_BIG_HASH + COUNT_FILE_SMALL_HASH)
                return;
            DocumentFile file = files[indices[i]];

            if (file.isFile()) {
                try (InputStream is = context
                        .getContentResolver().openInputStream(file.getUri())) {
                    if (is != null) {
                        hashAndLog(is, digest, fileIndex, sumElapsed, fileCount, file.getName());
                    }
                } catch (Exception e) {
                    KanLogger.myLogEE(e, "HashWorker", "While hashing file: " + file.getName());
                }
            } else if (file.isDirectory()) {
                computeFolderHashRecursive(context, file, digest, rootPathLen, sumElapsed, fileCount, fileIndex);
            }

        }
    }

    /**
     * Recursively hash first N files (COUNT_FILE_BIG_HASH then
     * COUNT_FILE_SMALL_HASH) in a file:// folder tree.
     */
    private static void computeFolderHashRecursiveFs(File folder,
            MessageDigest digest,
            long[] sumElapsed,
            int[] fileCount,
            int[] fileIndex) {
        if (fileIndex[0] >= COUNT_FILE_BIG_HASH + COUNT_FILE_SMALL_HASH)
            return;
        if (folder == null || !folder.exists() || !folder.isDirectory())
            return;

        File[] files = folder.listFiles();
        if (files == null || files.length == 0)
            return;

        // Sort by name, case-insensitive, nulls first (parity with SAF sorting)
        Arrays.sort(files, Comparator.comparing(
                (File f) -> f.getName() == null ? "" : f.getName(),
                Comparator.nullsFirst(String::compareToIgnoreCase)));

        for (File f : files) {
            if (fileIndex[0] >= COUNT_FILE_BIG_HASH + COUNT_FILE_SMALL_HASH)
                return;

            if (f.isFile()) {
                try (InputStream is = new FileInputStream(f)) {
                    hashAndLog(is, digest, fileIndex, sumElapsed, fileCount, f.getName());
                } catch (Exception e) {
                    KanLogger.myLogEE(e, "HashWorker", "While hashing file: " + f.getAbsolutePath());
                }
            } else if (f.isDirectory()) {
                computeFolderHashRecursiveFs(f, digest, sumElapsed, fileCount, fileIndex);
            }
        }
    }

    /**
     * Hash a single file stream with tiering (BIG then SMALL), update counters, and
     * log.
     */
    private static void hashAndLog(InputStream is,
            MessageDigest digest,
            int[] fileIndex, // shared across the whole traversal
            long[] sumElapsed, // accumulates hashing time
            int[] fileCount, // number of files actually hashed
            String displayName) throws Exception {

        final boolean bigTier = fileIndex[0] < COUNT_FILE_BIG_HASH;
        final int maxBytes = bigTier ? MAX_BYTES_TO_HASH_PER_FILE_BIG : MAX_BYTES_TO_HASH_PER_FILE_SMALL;
        final String type = bigTier ? "BIG" : "SMALL";

        long start = System.currentTimeMillis();
        int bytesRead = updateDigestFromStream(is, digest, maxBytes);
        long elapsed = System.currentTimeMillis() - start;

        sumElapsed[0] += elapsed;
        fileCount[0]++;
        fileIndex[0]++;

        myLogDD(elapsed + " ms to hash - HashType = " + type +
                " - Read = " + Tonio.getReadableSize(bytesRead) +
                " - " + (displayName != null ? displayName : "(unnamed)"));
    }

    private static String formatHash(byte[] hashBytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static void myLogDD(String txt) {
        if (VERBOSE_DEBUG) KanLogger.myLogD(txt);
    }

}