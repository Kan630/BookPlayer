package com.driot.bookplayer.helpers;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.driot.bookplayer.utils.KanLogger;

import java.io.File;

public class UriHelper {

    private static final int MAX_RECURSION_DEPTH = 10;

    /**
     * Returns a DocumentFile regardless of whether the input URI is file-based or content-based.
     */
    @Nullable
    public static DocumentFile getDocumentFileFromAnyUri(Context context, Uri uri) {
        if (uri == null) return null;

        String scheme = uri.getScheme();
        if ("content".equalsIgnoreCase(scheme)) {
            try {
                // If the URI contains "/document/", treat as single document
                if (uri.toString().contains("/document/") && !uri.toString().contains("/tree/")) {
                    DocumentFile single = DocumentFile.fromSingleUri(context, uri);
                    if (single.exists()) return single;
                }
                // Otherwise try as tree URI
                DocumentFile tree = DocumentFile.fromTreeUri(context, uri);
                if (tree != null && tree.exists()) return tree;
            } catch (Exception e) {
                myLogEE(e, "getDocumentFileFromAnyUri failed with [" + uri + "]");
            }
        } else if ("file".equalsIgnoreCase(scheme)) {
            String path = uri.getPath();
            if (path != null) {
                File file = new File(path);
                return DocumentFile.fromFile(file);
            } else {
                // handle error: path is null
                myLogEE(null , "getDocumentFileFromAnyUri failed, URI path is null:  [" + uri + "]");
            }

        } else {
            String path = uri.toString();
            if (!TextUtils.isEmpty(path) && path.startsWith("/")) {
                return DocumentFile.fromFile(new File(path));
            }
        }

        return null;
    }




    @Nullable
    public static String getRealPathFromContentUri(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{MediaStore.MediaColumns.DATA}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (Exception e) {
            myLogEE(e, "getRealPathFromContentUri failed");
        }
        return null;
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
