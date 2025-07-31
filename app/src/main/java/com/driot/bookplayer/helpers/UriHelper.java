package com.driot.bookplayer.helpers;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.driot.bookplayer.utils.KanLogger;

import java.io.File;

public class UriHelper {

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

    public static boolean isFolder(Context context, Uri uri) {
        try {
            if (uri == null) {
                myLogW("isFolder: URI is null");
                return false;
            }
            String scheme = uri.getScheme();
            if (scheme == null || "file".equalsIgnoreCase(scheme)) {
                // Case 1: Raw file path or file:// URI
                String path = uri.getPath();
                if (path == null) {
                    myLogW("isFolder: URI path is null for file scheme");
                    return false;
                }
                File file = new File(path);
                boolean result = file.exists() && file.isDirectory();
                myLogD("isFolder: File path check: " + path + " => " + result);
                return result;
            } else if ("content".equalsIgnoreCase(scheme)) {
                // Case 2: content:// URI - try both DocumentFile approaches
                DocumentFile docFile = DocumentFile.fromTreeUri(context, uri);
                if (docFile == null || !docFile.exists()) {
                    docFile = DocumentFile.fromSingleUri(context, uri);
                }
                boolean result = docFile.exists() && docFile.isDirectory();
                myLogD("isFolder: DocumentFile check: " + uri + " => " + result);
                return result;
            } else {
                myLogW("isFolder: Unsupported URI scheme: " + scheme + " (" + uri + ")");
                return false;
            }
        } catch (Exception e) {
            myLogEE(e, "isFolder: Exception while checking URI: " + uri);
            return false;
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
