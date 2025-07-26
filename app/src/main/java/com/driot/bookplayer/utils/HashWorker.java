package com.driot.bookplayer.utils;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class HashWorker extends Worker {

    public static final String WORKER_TAG_COMPUTE_HASH = "WORKER_TAG_COMPUTE_HASH";
    public static final int MAX_BYTES_TO_HASH_PER_FILE_BIG = 1024 * 1024; // 1 MB
    public static final int MAX_BYTES_TO_HASH_PER_FILE_SMALL = 1024 * 50; // 50 KB
    public static final int COUNT_FILE_BIG_HASH = 3;
    public static final int COUNT_FILE_SMALL_HASH = 10;
    public static final String HASH_NOT_COMPUTED = "0";

    public HashWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
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
            boolean exists = AppDatabase.getDatabase(getApplicationContext()).FolderDao().hashExists(hash);

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
        List<Folder> folders = db.FolderDao().getAll();

        for (Folder folder : folders) {
            if (HASH_NOT_COMPUTED.equals(folder.getHash())) {
                try {
                    myLogD("Hashing folder: " + folder.getPath());
                    File dir = new File(folder.getPath());
                    String hash = computeFolderHash(dir);
                    db.FolderDao().updateHash(folder.getId(), hash);
                } catch (Exception e) {
                    myLogEE(e, "Exception while hashing folder: " + folder.getPath());
                }
            }
        }
        return Result.success();
    }

    private String computeFolderHash(File folder) {
        // NOT USED YET, for background from Main Activity
        if (!folder.exists() || !folder.isDirectory()) return "";
        File[] files = folder.listFiles();
        if (files == null) return "";

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
                    myLogD(elapsed + " ms to hash - HashType = " + type + " - Read = " + Tonio.getReadableSize(bytesRead) + " - " + file.getName());
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

    private int updateDigestFromStream(InputStream is, MessageDigest digest, int maxBytes) throws Exception {
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

    private String computeHashFromUri(Context context, Uri uri) {
        myLogD("computeHashFromUri() START: " + uri);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long totalStart = System.currentTimeMillis();
            long[] sumElapsed = new long[]{0};
            int[] fileCount = new int[]{0};

            DocumentFile doc = DocumentFile.fromSingleUri(context, uri);
            if (doc != null && doc.isFile()) {
                try (InputStream is = context.getContentResolver().openInputStream(uri)) {
                    long startTime = System.currentTimeMillis();
                    if (is != null) updateDigestFromStream(is, digest, MAX_BYTES_TO_HASH_PER_FILE_BIG);
                    sumElapsed[0] = System.currentTimeMillis() - startTime;
                    fileCount[0]++;
                }
            } else {
                doc = DocumentFile.fromTreeUri(context, uri);
                if (doc != null && doc.isDirectory()) {
                    int[] fileIndex = new int[]{0};
                    computeFolderHashRecursive(doc, digest, uri.getPath().length(), sumElapsed, fileCount, fileIndex);
                } else {
                    throw new IllegalArgumentException("Invalid or unsupported URI: " + uri);
                }
            }

            myLogD("---Nb of hashed file : " + fileCount[0] + " files.");
            myLogD("---[Timing] " + sumElapsed[0] + " ms Cumulative hash time");
            myLogD("---[Timing] " + (System.currentTimeMillis() - totalStart - sumElapsed[0]) + " ms other processes time");
            return formatHash(digest.digest());
        } catch (Exception e) {
            myLogEE(e, "Exception in computeHashFromUri()");
            return "";
        }
    }

    private void computeFolderHashRecursive(DocumentFile folder, MessageDigest digest, int rootPathLen, long[] sumElapsed, int[] fileCount, int[] fileIndex) {
        if (fileIndex[0] >= COUNT_FILE_BIG_HASH + COUNT_FILE_SMALL_HASH) return;

        long listStart = System.currentTimeMillis();
        DocumentFile[] files = folder.listFiles();
        myLogD(System.currentTimeMillis() - listStart + " ms to list " + files.length + " files in " + folder.getName());

        //TODO will only sort the first subFolder... but well...
        long sortStart = System.currentTimeMillis();
        int n = files.length;
        String[] names = new String[n];
        Integer[] indices = new Integer[n];
        for (int i = 0; i < n; i++) {
            names[i] = files[i].getName();
            indices[i] = i;
        }
        Arrays.sort(indices, Comparator.comparing(i -> names[i], Comparator.nullsFirst(String::compareToIgnoreCase)));
        myLogD(System.currentTimeMillis() - sortStart + " ms to sort files in " + folder.getName());

        for (int i = 0; i < n; i++) {
            if (fileIndex[0] >= COUNT_FILE_BIG_HASH + COUNT_FILE_SMALL_HASH) return;
            DocumentFile file = files[indices[i]];

            if (file.isFile()) {
                long startTime = System.currentTimeMillis();
                int bytesRead = 0;
                String type;
                try (InputStream is = getApplicationContext().getContentResolver().openInputStream(file.getUri())) {
                    if (is != null) {
                        if (fileIndex[0] < COUNT_FILE_BIG_HASH) {
                            type = "BIG";
                            bytesRead = updateDigestFromStream(is, digest, MAX_BYTES_TO_HASH_PER_FILE_BIG);
                        } else {
                            type = "SMALL";
                            bytesRead = updateDigestFromStream(is, digest, MAX_BYTES_TO_HASH_PER_FILE_SMALL);
                        }
                    } else continue;
                } catch (Exception e) {
                    myLogEE(e, "While hashing file: " + file.getName());
                    continue;
                }
                long elapsed = System.currentTimeMillis() - startTime;
                sumElapsed[0] += elapsed;
                fileCount[0]++;
                myLogD(elapsed + " ms to hash - HashType = " + type + " - Read = " + Tonio.getReadableSize(bytesRead) + " - " + file.getName());
                fileIndex[0]++;
            } else if (file.isDirectory()) {
                computeFolderHashRecursive(file, digest, rootPathLen, sumElapsed, fileCount, fileIndex);
            }
        }
    }

    private String formatHash(byte[] hashBytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogInFile(String str) { KanLogger.myLogInFile(this.getClass().getName(), str); }
    private void myLogD(String str) { KanLogger.myLogD(this.getClass().getName(), str); }
    private void myLogI(String str) { KanLogger.myLogI(this.getClass().getName(), str); }
    private void myLogW(String str) { KanLogger.myLogW(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
    private void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, this.getClass().getName(), str); }
    private void myToast(String str) { KanLogger.myToast(this.getClass().getName(), str); }
    private void myToastE(String str) { KanLogger.myToastE(this.getClass().getName(), str); }
    private void myKeyFirebase(String strKey, String strValue) {KanLogger.myKeyFirebase(strKey, strValue);}
    private void myLogFirebase(String strLog) {KanLogger.myLogFirebase(strLog);}
}