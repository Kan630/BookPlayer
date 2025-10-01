package com.driot.bookplayer.helpers;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.TextUtils;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.utils.KanLogger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;

public class FileHelper {
    public static String getRealPathFromURI(final Context context, final Uri uri) {
        String path = "";
        try {
            path = processUri(context, uri);
        } catch (Exception e) {
            myLogEE(e,"error in getRealPathFromURI");
            e.printStackTrace();
        }
        if (TextUtils.isEmpty(path)) {
            myLog("getRealPathFromURI is empty => get from copyFile to cache");
            path = copyFile(context, uri);
            myLog("getRealPathFromURI is empty => returned path = [" + path + "]");
        }
        return path;
    }

    private static String processUri(Context context, Uri uri) {
        @SuppressLint("ObsoleteSdkInt") final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT; // unecessary but keeping for later reuse....
        myLog("processUri - isKitKat : " + String.valueOf(isKitKat));
        String path = "";
        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                myLog("processUri ExternalStorageProvider");
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    path = Environment.getExternalStorageDirectory() + "/" + split[1];
                }
            } else if (isDownloadsDocument(uri)) { // DownloadsProvider
                myLog("processUri DownloadsProvider");
                final String id = DocumentsContract.getDocumentId(uri);
                myLog("DocumentsContract.getDocumentId(uri) : [" + id + "]");
                //Starting with Android O, this "id" is not necessarily a long (row number),
                //but might also be a "raw:/some/file/path" URL
                if (id != null && id.startsWith("raw:/")) {
                    Uri rawuri = Uri.parse(id);
                    path = rawuri.getPath();
                } else {
                    String[] contentUriPrefixesToTry = new String[]{
                            "content://downloads/public_downloads",
                            "content://downloads/my_downloads"
                    };
                    for (String contentUriPrefix : contentUriPrefixesToTry) {
                        final Uri contentUri = ContentUris.withAppendedId(
                                Uri.parse(contentUriPrefix), Long.valueOf(id));
                        path = getDataColumn(context, contentUri, null, null);
                        if (!TextUtils.isEmpty(path)) {
                            break;
                        }
                    }
                }
            } else if (isMediaDocument(uri)) { // MediaProvider
                myLog("processUri MediaProvider");
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];
                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[] {
                        split[1]
                };

                path = getDataColumn(context, contentUri, selection, selectionArgs);
            }  else if ("content".equalsIgnoreCase(uri.getScheme())) {
                myLog("processUri content");
                path = getDataColumn(context, uri, null, null);
            }
        } else if ("content".equalsIgnoreCase(uri.getScheme())) { // MediaStore (and general)
            myLog("processUri general content (MediaStore (and general))");
            path = getDataColumn(context, uri, null, null);
        } else if ("file".equalsIgnoreCase(uri.getScheme())) { // File
            myLog("processUri file content");
            path = uri.getPath();
        }
        return path;
    }

    private static String destFilePath;
    static String copyFile(Context context, Uri uri) {
        Thread thread_one;
        thread_one = new Thread() {
            @Override
            public void run() {
                try {
                    InputStream attachment = context.getContentResolver().openInputStream(uri);
                    if (attachment != null) {
                        String filename = getContentName(context.getContentResolver(), uri);
                        if (filename != null) {
                            File file = new File(context.getCacheDir(), filename);
                            FileOutputStream tmp = new FileOutputStream(file);
                            byte[] buffer = new byte[1024];
                            while (attachment.read(buffer) > 0) {
                                tmp.write(buffer);
                            }
                            tmp.close();
                            attachment.close();
                            destFilePath = file.getAbsolutePath();
                        }
                    }
                } catch (Exception e) {
                    myLogEE(e,"ERR copyFile");
                }
            }

        }    ;
        thread_one.start();
        myLog("copy started (finding uri) to : [" + destFilePath + "]");
        return destFilePath;
    }

    private static String getContentName(ContentResolver resolver, Uri uri) {
        Cursor cursor = resolver.query(uri, null, null, null, null);
        if (cursor != null) {
            cursor.moveToFirst();
            int nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
            if (nameIndex >= 0) {
                String name = cursor.getString(nameIndex);
                cursor.close();
                return name;
            }
        }
        return null;
    }

    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context The context.
     * @param uri The Uri to query.
     * @param selection (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     */
    public static String getDataColumn(Context context, Uri uri, String selection,
                                       String[] selectionArgs) {
        Cursor cursor = null;
        String result = null;
        final String column = "_data";
        final String[] projection = { column };
        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int index = cursor.getColumnIndexOrThrow(column);
                result = cursor.getString(index);
            }
        } catch (Exception e) {
            myLogEE(e,"error with getDataColumn");
            return null;
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return result;
    }


    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }


    public static boolean exists(String path) {
        try {
            File f = new File(path);
            if (f.exists()) {
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    // don't delete DEBUG (Main)
    public static void listAllFiles(File dir) {
        if (dir == null || !dir.exists()) {
            myLog("Directory [" + dir + "] does not exist.");
            return;
        }
        if (!dir.isDirectory()) {
            myLog("Provided path [" + dir + "] is not a directory.");
            return;
        }

        listFilesRecursive(dir, "");
    }

    private static void listFilesRecursive(File dir, String indent) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                myLog(indent + "[DIR]  " + file.getAbsolutePath());
                listFilesRecursive(file, indent + "  ");
            } else {
                myLog(indent + "[FILE] " + file.getAbsolutePath());
            }
        }
    }

    public static String sanitizeFilename(String input) {
        if (input==null) return null;
        return input.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    public static boolean deleteFolderRecursive(String strPath) {
        String starter = "file:///";
        if (strPath.length()>5) {
            strPath = strPath.replace(starter,"");
            try {
                File zikFileToDelete = new File(strPath);
                if(zikFileToDelete.exists()) {
                    if (recursiveRemove(zikFileToDelete)) {
                        myLog("Deleted from Disk : [" + strPath + "]");
                        return true;
                    } else {
                        myLog("NOT Deleted from Disk : [" + strPath + "]");
                        return false;
                    }
                } else {
                    myLogE("file does not exist : [" + strPath + "]");
                    return false;
                }
            } catch (Exception e) {
                myLogEE(e,"Error remove ZikFile from Disk : [" + strPath + "]");
                return false;
            }
        } else {
            myLogE("should not happen uri less than 5 chars for path [" + strPath + "]");
            return false;
        }
    }

    public static boolean recursiveRemove(File file) {
        if(file == null  || !file.exists()) {
            myLogE("recursiveRemove() => File does not exist.... [" + file.toString() + "]");
            return false;
        }

        if(file.isDirectory()) {
            File[] list = file.listFiles();
            if(list != null) {
                for(File item : list) {
                    recursiveRemove(item);
                }
            }
        }
        if(file.exists()) {
            if (file.delete()) {
                myLog("recursiveRemove() => delete OK.... [" + file.toString() + "]");
            } else {
                myLogE("recursiveRemove() => delete KO.... [" + file.toString() + "]");
            }
        }
        return !file.exists();
    }
    public static void RemoveCachedImages(Context context, File file) {
        AppDatabase.databaseReadExecutor.execute(() -> {
            recursiveRemoveCachedImages(context, file);
        });
    }
    private static void recursiveRemoveCachedImages(Context context, File file) {
        if (file == null || !file.exists()) {
            myLogE("recursiveRemoveImages() => File does not exist.... [" + file + "]");
            return;
        }
        if (file.isDirectory()) {
            File[] list = file.listFiles();
            if (list != null) {
                for (File item : list) {
                    recursiveRemoveCachedImages(context, item);
                }
            }
        } else {
            String name = file.getName().toLowerCase(Locale.ROOT);
            if (name.startsWith("librivox_img") || name.startsWith("podcast_feed")) {
                boolean exists = AppDatabase.getDatabase(context).FolderDao().doesImageExist(name);
                if (exists) {
                    myLogD("image is in DB book covers - bypassing : " + name);
                }
                    if (file.delete()) {
                        myLogD("recursiveRemoveImages() => delete OK.... [" + file + "]");
                    } else {
                        myLogE("recursiveRemoveImages() => delete KO.... [" + file + "]");
                    }
            }
        }
    }

    // DUREE AUDIO
    public static long getMediaDurationFromPath(String path) {
        if (path == null) {
            myLogEE(null, "duration: null path");
            return 0L;
        }
        File f = new File(path);
        if (!f.exists()) {
            myLogEE(null, "duration: file does not exist: " + path);
            return 0L;
        }
        if (f.length() <= 0) {
            myLogEE(null, "duration: file is empty: " + path);
            return 0L;
        }

        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try (FileInputStream fis = new FileInputStream(f)) {
            // Using FD avoids many charset / path edge cases
            mmr.setDataSource(fis.getFD());
            String durMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (durMs == null) {
                myLogEE(null, "duration: METADATA_KEY_DURATION is null for " + path);
                return 0L;
            }
            return Long.parseLong(durMs);
        } catch (Exception e) {
            myLogEE(e, "error getting duration of media for " + path);
            return 0L;
        } finally {
            try { mmr.release(); } catch (Throwable ignore) {}
        }
    }

    public static boolean deleteFile(Context context, String path) {
        if (path == null) return false;

        try {
            if (path.startsWith("file://")) {
                path = Uri.parse(path).getPath();
            }
            if (path == null) return false;
            File file = new File(path);
            if (file.exists()) {
                return file.delete();
            }
        } catch (Exception e) {
            myLogEE(e,"error in deleteFile for path [" + path + "]");
        }
        return false;
    }

    // ----------------------- LOG -----------------------
    private static final String TAG = "FileHelper";
    private static void myLog(String str) { KanLogger.myLog(TAG, str); }
    private static void myLogD(String str) { KanLogger.myLogD(TAG, str); }
    private static void myLogI(String str) { KanLogger.myLogI(TAG, str); }
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
    private static void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, TAG, str); }
    private static void myToastEE(Throwable t, String str) { KanLogger.myToastEE(t, TAG, str); }

}
