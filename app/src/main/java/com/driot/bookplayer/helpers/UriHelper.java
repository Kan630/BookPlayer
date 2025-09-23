package com.driot.bookplayer.helpers;

import static com.driot.bookplayer.utils.Tonio.fileExists;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.utils.KanLogger;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class UriHelper {

    private static final int MAX_RECURSION_DEPTH = 10;

    /**
     * Returns a DocumentFile regardless of whether the input URI is file-based or content-based.
     */
    @Nullable
    public static DocumentFile getDocumentFileFromAnyUri(Context context, Uri uri) {
        //problems with :
        //content://media/external/audio/media/1000028186 (Samsung SM-F721B   Android 15)
        //content://com.android.fileexplorer.myprovider/external_files/Nyla/Brainwashed%20by%20Nyla%20K.mp3   (Xiaomi 24117RN76E  Android 15)
        //content://com.android.providers.downloads.documents/document/4204
        //content://com.android.providers.media.documents/document/audio%3A1000131747
        //content://com.android.providers.downloads.documents/document/msf%3A1000016621
        //content://com.android.externalstorage.documents/document/primary%3AAudiobooks%2FFrom%20Blood%20and%20Ash%20%231%20(2%20of%202)%20.mp3
        //content://com.android.externalstorage.documents/document/primary%3AMusic%2FTelegram%2FPrison%20Healer%20(Tome%201)%20-%20Lynette%20Noni.mp3
        if (uri == null) return null;

        try {
            final String scheme = uri.getScheme();

            // 1) file:// → simple
            if ("file".equalsIgnoreCase(scheme)) {
                final String path = uri.getPath();
                if (path != null) return DocumentFile.fromFile(new File(path));
                myLogEE(null, "getDocumentFileFromAnyUri: null path for file:// " + uri);
                return null;
            }

            // 2) Raw absolute path like "/sdcard/..." (rare)
            if (scheme == null) {
                final String s = uri.toString();
                if (!TextUtils.isEmpty(s) && s.startsWith("/")) return DocumentFile.fromFile(new File(s));
                myLogEE(null,"getDocumentFileFromAnyUri: Raw absolute path " + Uri.decode(uri.toString()));
                return null;
            }

            // 3) content://
            if ("content".equalsIgnoreCase(scheme)) {
                // Prefer real SAF trees for directories
                boolean isTree = false, isDoc = false;
                try { isTree = DocumentsContract.isTreeUri(uri); } catch (Throwable ignore) {}
                try { isDoc  = DocumentsContract.isDocumentUri(context, uri); } catch (Throwable ignore) {}

                if (isTree) {
                    DocumentFile tree = DocumentFile.fromTreeUri(context, uri);
                    if (tree != null && tree.exists()) return tree;
                    myLogEE(null,"getDocumentFileFromAnyUri: fromTreeUri returned null or !exists for " + Uri.decode(uri.toString()));
                    return null;
                }

                if (isDoc || uri.toString().contains("/document/")) {
                    DocumentFile single = DocumentFile.fromSingleUri(context, uri);
                    if (single != null && single.exists()) return single;
                    myLogEE(null,"getDocumentFileFromAnyUri: fromSingleUri returned null or !exists for " + Uri.decode(uri.toString()));
                    return null;
                }

                // Not a DocumentsProvider → cannot create a DocumentFile
                // (MediaStore: content://media/..., Xiaomi, gallery, cloud, etc.)
                myLogEE(null, "getDocumentFileFromAnyUri: non-DocumentsProvider content URI → returning null: " + Uri.decode(uri.toString()));
                return null;
            }

            myLogW("getDocumentFileFromAnyUri: unsupported scheme " + scheme + " for " + uri);
            return null;

        } catch (Exception e) {
            myLogEE(e, "getDocumentFileFromAnyUri failed with [" + uri + "]");
            return null;
        }
    }


    /** Optional helpers if you want explicit branching later */
    private static boolean isMediaStoreUri(@Nullable Uri uri) {
        return uri != null
                && "content".equalsIgnoreCase(uri.getScheme())
                && "media".equalsIgnoreCase(uri.getAuthority());
    }


    public static boolean isFolder(Context context, Uri uri) {
        try {
            if (uri == null) {
                myLogW("isFolder: URI is null");
                return false;
            }

            String scheme = uri.getScheme();
            if (scheme == null || "file".equalsIgnoreCase(scheme)) {
                String path = uri.getPath();
                if (path == null) {
                    myLogW("isFolder: URI path is null for file scheme");
                    return false;
                }
                File file = new File(path);
                boolean result = file.exists() && file.isDirectory();
                myLogD("isFolder: File path check: " + path + " => " + result);
                return result;
            }

            // Reuse your helper here:
            DocumentFile docFile = getDocumentFileFromAnyUri(context, uri);
            if (docFile == null) {
                myLogW("isFolder: DocumentFile is null for URI: " + Uri.decode(uri.toString()));
                return false;
            }

            boolean result = docFile.exists() && docFile.isDirectory();
            myLogD("isFolder: DocumentFile check: " + Uri.decode(uri.toString()) + " => " + result);
            return result;

        } catch (Exception e) {
            myLogEE(e, "isFolder: Exception while checking URI: " + Uri.decode(uri.toString()));
            return false;
        }
    }

    public static long getSize(Context context, Uri uri) {
        DocumentFile docFile = getDocumentFileFromAnyUri(context, uri);

        if (docFile != null && docFile.exists()) {
            if (docFile.isDirectory()) {
                return getFolderSize(context, uri, 0);
            } else {
                return getFileSize(context, uri);
            }
        }
        // Fallback when DocumentFile doesn't work like on [content://media/external/file/1000000103] (MediaStore)
        myLogW("getSize: Falling back to manual check");
        if (isFolder(context, uri)) {
            return getFolderSize(context, uri, 0);
        } else {
            return getFileSize(context, uri);
        }
    }

    private static long getFolderSize(Context context, Uri uri, int recursiveStep) {
        if (recursiveStep > MAX_RECURSION_DEPTH) {
            myLogW("getFolderSize: Max recursion depth reached");
            return 0;
        }
        myLog("calculateFolderSize()" + (recursiveStep>0 ? " - step " + recursiveStep : "") + " - " + uri);
        long totalSize = 0;
        ContentResolver contentResolver = context.getContentResolver();
        Uri childrenUri;
        try {
            //for children and sub child dirs
            childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getDocumentId(uri));
        } catch (Exception e) {
            //for parent dir
            childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri));
        }

        try (Cursor cursor = contentResolver.query(childrenUri, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String documentId = cursor.getString(0);
                    String mimeType = cursor.getString(1);

                    Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId);

                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                        totalSize += getFolderSize(context, documentUri, recursiveStep + 1);
                    } else {
                        totalSize += getFileSize(context, documentUri);
                    }
                }
            }
        }
        return totalSize;
    }

    private static long getFileSize(Context context, Uri uri) {
        if (uri == null) {
            myLogE("getFileSize Uri null");
            return -1;
        }
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            try {
                if (uri.getPath() != null) {
                    return new File(uri.getPath()).length();
                }
            } catch (Exception e) {
                myLogEE(e, "getFileSize() - file://");
                return -1;
            }
        } else {
            try (ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "r")) {
                return (pfd != null) ? pfd.getStatSize() : -1;
            } catch (Exception e) {
                myLogEE(e, "getFileSize() - content://");
                return -1;
            }
        }
        return -1;
    }


    public static Uri buildFileUri(Context context, String folderPathOrUri, String fileName) {
        if (folderPathOrUri == null || fileName == null) {
            myLogEE(null, "buildFileUri - null args");
            return null;
        }
        try {
            Uri folderUri = Uri.parse(folderPathOrUri);

            // ✅ CASE 1: SAF URI (content://...)
            if ("content".equalsIgnoreCase(folderUri.getScheme())) {
                // Folder + fileName
                if (DocumentsContract.isTreeUri(folderUri)) {
                    String parentDocumentId = DocumentsContract.getTreeDocumentId(folderUri);
                    String childDocumentId = parentDocumentId + "/" + fileName;

                    return DocumentsContract.buildDocumentUriUsingTree(folderUri, childDocumentId);
                } else {
                    // Folder is fileName !
                    Uri uriToPlay = Uri.parse(folderPathOrUri);
                    DocumentFile file = DocumentFile.fromSingleUri(context, Uri.parse(folderPathOrUri));
                    if (!file.exists() || !file.isFile()) {
                        myLogEE(null,"Invalid or non-file SAF Uri in single file case : " + uriToPlay);
                    }
                    return uriToPlay;
                }
            } else {
                myLogEE(null, "scheme is not Content");
            }

        } catch (Exception e) {
            myLogW("Could not parse URI, trying legacy fallback: " + folderPathOrUri);
        }

        // ✅ CASE 2: Fallback for legacy file-based paths
        try {
            File file = new File(folderPathOrUri, fileName);
            if (file.exists()) {
                return Uri.fromFile(file);
            }
        } catch (Exception e) {
            myLogEE(null, "Fallback for legacy file-based paths.. KO..");
        }

        // ❌ Neither SAF nor legacy path worked
        myLogEE(null,"Unable to build URI for: " + folderPathOrUri + "/" + fileName);
        return null;
    }



    @Nullable
    public static String getPathFromUri(Context context, Uri uri) {
        if (uri == null) return null;
        String scheme = uri.getScheme();
        try {
            if ("file".equalsIgnoreCase(scheme)) {
                return uri.getPath();
            } else if ("content".equalsIgnoreCase(scheme)) {
                // Handle MediaStore (images, audio, etc.)
                String[] projection = { MediaStore.MediaColumns.DATA };
                try (Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
                        return cursor.getString(columnIndex);
                    }
                } catch (Exception e) {
                    myLogW("getPathFromUri: fallback to fileDescriptor due to exception: " + e.getMessage());
                }

                // Fallback: Try using FileDescriptor to infer a path
                try (ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "r")) {
                    if (pfd != null) {
                        FileDescriptor fd = pfd.getFileDescriptor();
                        FileInputStream fis = new FileInputStream(fd);
                        File tempFile = File.createTempFile("uri_temp_", null, context.getCacheDir());
                        FileOutputStream fos = new FileOutputStream(tempFile);
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = fis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                        fos.close();
                        fis.close();
                        return tempFile.getAbsolutePath();
                    }
                } catch (Exception e) {
                    myLogEE(e, "getPathFromUri: FileDescriptor fallback failed");
                }

            } else if (DocumentsContract.isDocumentUri(context, uri)) {
                String docId = DocumentsContract.getDocumentId(uri);
                String[] split = docId.split(":");
                if (split.length == 2) {
                    String type = split[0];
                    String realPath = split[1];

                    if ("primary".equalsIgnoreCase(type)) {
                        return "/storage/emulated/0/" + realPath;
                    } else {
                        // Handle SD card
                        return "/storage/" + type + "/" + realPath;
                    }
                }
            }

        } catch (Exception e) {
            myLogEE(e, "getPathFromUri failed for: " + uri.toString());
        }

        myLogW("getPathFromUri: Fallback to null for uri: " + uri.toString());
        return null;
    }


    @Nullable
    public static File getFileFromUri(Context context, Uri uri) {
        if (uri == null) return null;

        String scheme = uri.getScheme();

        try {
            // CASE 1: file:// scheme
            if ("file".equalsIgnoreCase(scheme)) {
                return new File(uri.getPath());
            }

            // CASE 2: content:// scheme, try resolving via MediaStore path
            if ("content".equalsIgnoreCase(scheme)) {
                String path = getPathFromUri(context, uri);
                if (path != null) {
                    File file = new File(path);
                    if (file.exists()) return file;
                }

                // Fallback: try copying to temp file
                ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "r");
                if (pfd != null) {
                    FileInputStream inputStream = new FileInputStream(pfd.getFileDescriptor());
                    File tempFile = File.createTempFile("uri_tmp_", null, context.getCacheDir());
                    FileOutputStream outputStream = new FileOutputStream(tempFile);

                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = inputStream.read(buffer)) > 0) {
                        outputStream.write(buffer, 0, len);
                    }

                    inputStream.close();
                    outputStream.close();
                    pfd.close();

                    myLogD("getFileFromUri: fallback copy success: " + tempFile.getAbsolutePath());
                    return tempFile;
                }
            }

            // CASE 3: SAF Document URI
            if (DocumentsContract.isDocumentUri(context, uri)) {
                String path = getPathFromUri(context, uri);
                if (path != null) {
                    File file = new File(path);
                    if (file.exists()) return file;
                }
            }
        } catch (Exception e) {
            myLogEE(e, "getFileFromUri failed for: " + uri);
        }

        myLogW("getFileFromUri: Fallback to null for uri: " + uri);
        return null;
    }


    // TODO, use openFileDescriptor & remove legacy from manifest
    @Nullable
    public static Uri resolvePlayableUri(Context context, @NonNull ZikFile zf) {
        try {
            // Content (SAF)
            if (zf.getPath() != null && zf.getPath().startsWith("content://")) {
                Uri u = UriHelper.buildFileUri(context, zf.getPath(), zf.getName());
                if (u == null) return null;
                DocumentFile f = DocumentFile.fromSingleUri(context, u);
                if (f != null && f.exists() && f.isFile()) return u;

                // fallback: try the raw content Uri
                u = Uri.parse(zf.getPath());
                f = DocumentFile.fromSingleUri(context, u);
                return (f != null && f.exists() && f.isFile()) ? u : null;
            }

            // file://
            if (zf.getPath() != null && zf.getPath().startsWith("file://")) {
                Uri u = Uri.parse(zf.getPath());
                String p = u.getPath();
                if (p == null) return null;
                File f = new File(p);
                if (f.exists() && f.isFile()) return u;

                // sometimes folder path + name
                File maybe = new File(p, zf.getName());
                return (maybe.exists() && maybe.isFile()) ? Uri.fromFile(maybe) : null;
            }

            // Plain filesystem path
            String base = zf.getPath();
            if (base == null) return null;
            if (fileExists(base)) return Uri.fromFile(new File(base));
            String withName = base + "/" + zf.getName();
            return fileExists(withName) ? Uri.fromFile(new File(withName)) : null;

        } catch (Throwable t) {
            myLogEE(t, "resolvePlayableUri");
            return null;
        }
    }



    // ----------------------- LOG -----------------------
    private static final String TAG = "UriHelper";
    private static void myLog(String str) { KanLogger.myLog(TAG, str); }
    private static void myLogD(String str) { KanLogger.myLogD(TAG, str); }
    private static void myLogI(String str) { KanLogger.myLogI(TAG, str); }
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
    private static void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, TAG, str); }
    private static void myToastEE(Throwable t, String str) { KanLogger.myToastEE(t, TAG, str); }
}
