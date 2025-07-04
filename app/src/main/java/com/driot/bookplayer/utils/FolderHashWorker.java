package com.driot.bookplayer.utils;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
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
import java.util.List;
import java.util.Objects;

public class FolderHashWorker extends Worker {

    public static final int MAX_BYTES_TO_HASH_PER_FILE = 1024 * 1024; // 1 MB

    public FolderHashWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String uriStr = getInputData().getString("uri");
        if (uriStr != null) {
            try {
                Uri uri = Uri.parse(uriStr);
                String hash = computeHashFromUri(getApplicationContext(), uri);

                Data outputData = new Data.Builder()
                        .putString("computed_hash", hash)
                        .build();

                return Result.success(outputData);

            } catch (Exception e) {
                myLogEE(e, "Error computing hash from URI");
            }
            return Result.success();
        }

        AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
        List<Folder> folders = db.FolderDao().getAll(); // Make sure this method exists

        for (Folder folder : folders) {
            if (Objects.equals(folder.getHash(), "0")) {
                try {
                    myLogD("Hashing folder: " + folder.getPath());
                    File dir = new File(folder.getPath());
                    String hash = computeFolderHash(dir);
                    db.FolderDao().updateHash(folder.getId(), hash);
                } catch (Exception e) {
                    myLogEE(e, "Exception while hashing folder: " + folder.getPath());
                    e.printStackTrace();
                    // Optional: return failure if you want to retry later
                }
            }
        }

        return Result.success();

    }
    private String computeFolderHash(File folder) {
        String zeHash;
        if (!folder.exists() || !folder.isDirectory()) return "";
        File[] files = folder.listFiles();
        if (files == null) return "";

        Arrays.sort(files); // Ensure order
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (File file : files) {
                if (file.isFile()) {
                    byte[] buffer = new byte[4096];
                    try (FileInputStream fis = new FileInputStream(file)) {
                        long totalRead = 0;
                        int read;
                        while ((read = fis.read(buffer)) != -1 && totalRead < MAX_BYTES_TO_HASH_PER_FILE) {
                            digest.update(buffer, 0, read);
                            totalRead += read;
                        }
                    }
                }
            }
            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) sb.append(String.format("%02x", b));
            zeHash = sb.toString();
            myLogD("computeFolderHash(): [" + zeHash + "]");
            return zeHash;
        } catch (Exception e) {
            myLogEE(e, "Exception while computing hash");
            return "";
        }
    }

    private String computeHashFromUri(Context context, Uri uri) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[4096];
            long totalRead = 0;
            int read;
            try (InputStream is = context.getContentResolver().openInputStream(uri)) {
                if (is == null) return "";
                while ((read = is.read(buffer)) != -1 && totalRead < MAX_BYTES_TO_HASH_PER_FILE) {
                    digest.update(buffer, 0, read);
                    totalRead += read;
                }
            }
            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) sb.append(String.format("%02x", b));
            String zeHash = sb.toString();
            myLogD("computeHashFromUri(): [" + zeHash + "]");
            return zeHash;
        } catch (Exception e) {
            myLogEE(e, "Exception while hashing Uri");
            return "";
        }
    }

    //--- FULL LOG --------------------------
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

