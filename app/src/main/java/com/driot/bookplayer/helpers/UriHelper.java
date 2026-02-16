package com.driot.bookplayer.helpers;

import static com.driot.bookplayer.helpers.FileHelper.fileExists;

import android.content.Context;
import android.content.Intent;
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
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class UriHelper {

    private static final int MAX_RECURSION_DEPTH = 10;

    /**
     * Returns a DocumentFile regardless of whether the input URI is file-based or
     * content-based.
     */
    @Nullable
    public static DocumentFile getDocumentFileFromAnyUri(Context context, Uri uri) {
        // problems with :
        // content://media/external/audio/media/1000028186 (Samsung SM-F721B Android 15)
        // content://com.android.fileexplorer.myprovider/external_files/Nyla/Brainwashed%20by%20Nyla%20K.mp3
        // (Xiaomi 24117RN76E Android 15)
        // content://com.android.providers.downloads.documents/document/4204
        // content://com.android.providers.media.documents/document/audio%3A1000131747
        // content://com.android.providers.downloads.documents/document/msf%3A1000016621
        // content://com.android.externalstorage.documents/document/primary%3AAudiobooks%2FFrom%20Blood%20and%20Ash%20%231%20(2%20of%202)%20.mp3
        // content://com.android.externalstorage.documents/document/primary%3AMusic%2FTelegram%2FPrison%20Healer%20(Tome%201)%20-%20Lynette%20Noni.mp3
        // content://com.driot.bookplayer.debug.FileProvider/cache/fixtures/fixtures/single_files/AYAHUASCA
        // [Progressive Psytrance Mix - 2016].mp3
        // content://com.driot.bookplayer.debug.FileProvider/cache/fixtures/fixtures/single_files/AYAHUASCA%20%5BProgressive%20Psytrance%20Mix%20-%202016%5D.mp3

        if (uri == null) {
            myLogEE(null, "getDocumentFileFromAnyUri: null passed as uri argument");
            return null;
        }

        try {
            final String scheme = uri.getScheme();

            // 1) file:// → simple
            if ("file".equalsIgnoreCase(scheme)) {
                final String path = uri.getPath();
                if (path != null)
                    return DocumentFile.fromFile(new File(path));
                myLogEE(null, "getDocumentFileFromAnyUri: null path for file:// " + uri);
                return null;
            }

            // 2) Raw absolute path like "/sdcard/..." (rare)
            if (scheme == null) {
                final String s = uri.toString();
                if (!TextUtils.isEmpty(s) && s.startsWith("/"))
                    return DocumentFile.fromFile(new File(s));
                myLogEE(null, "getDocumentFileFromAnyUri: Raw absolute path " + Uri.decode(uri.toString()));
                return null;
            }

            // 3) content://
            if ("content".equalsIgnoreCase(scheme)) {
                // Prefer real SAF trees for directories
                boolean isTree = false, isDoc = false;
                try {
                    isTree = DocumentsContract.isTreeUri(uri);
                } catch (Throwable ignore) {
                }
                try {
                    isDoc = DocumentsContract.isDocumentUri(context, uri);
                } catch (Throwable ignore) {
                }

                if (isTree) {
                    DocumentFile tree = DocumentFile.fromTreeUri(context, uri);
                    if (tree != null && tree.exists())
                        return tree;
                    myLogEE(null, "getDocumentFileFromAnyUri: fromTreeUri returned null or !exists for "
                            + Uri.decode(uri.toString()));
                    return null;
                }

                if (isDoc || uri.toString().contains("/document/")) {
                    DocumentFile documentFile = DocumentFile.fromSingleUri(context, uri);
                    if (documentFile != null && documentFile.exists())
                        return documentFile;
                    myLogEE(null, "getDocumentFileFromAnyUri: fromSingleUri returned null or !exists for "
                            + Uri.decode(uri.toString()));
                    return null;
                }

                // Generic content URI fallback (e.g. FileProvider, MediaStore)
                DocumentFile generic = DocumentFile.fromSingleUri(context, uri);
                if (generic != null && generic.exists()) {
                    myLogW("new check");
                    return generic;
                }


                // Not a DocumentsProvider → cannot create a DocumentFile
                // (MediaStore: content://media/..., Xiaomi, gallery, cloud, etc.)
                myLogD("--------");
                myLog("getDocumentFileFromAnyUri: non-DocumentsProvider content URI → returning null: "
                        + Uri.decode(uri.toString()));
                myLog(".....only way would be to write (=cache) the uri to a temp file...");
                myLogD("scheme/authority : " + uri.getScheme() + "/" + uri.getAuthority());
                myLogD("--------");
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
        // Fallbacks:
        if ("file".equalsIgnoreCase(uri.getScheme()) || uri.getScheme() == null) {
            File f = new File(uri.getPath() != null ? uri.getPath() : uri.toString());
            return f.isDirectory() ? getFolderSizeFs(f, 0) : f.length();
        }
        return getFileSize(context, uri);

    }

    // --- Public dispatcher (keeps your signature) ---
    private static long getFolderSize(Context context, Uri uri, int step) {
        if (step > MAX_RECURSION_DEPTH) {
            myLogW("getFolderSize: Max recursion depth reached");
            return 0;
        }
        myLog("getFolderSize()" + (step > 0 ? " - step " + step : "") + " - " + uri);

        if (uri == null)
            return 0;

        final String scheme = uri.getScheme();

        // 1) Plain filesystem (file:// or raw path)
        if (scheme == null || "file".equalsIgnoreCase(scheme)) {
            String path = (scheme == null) ? uri.toString() : uri.getPath();
            if (path == null)
                return 0;
            return getFolderSizeFs(new File(path), step);
        }

        // 2) SAF / content:// → go via DocumentFile (works for both tree + file-backed)
        if ("content".equalsIgnoreCase(scheme)) {
            DocumentFile doc = getDocumentFileFromAnyUri(context, uri);
            if (doc == null || !doc.exists() || !doc.isDirectory()) {
                myLogW("getFolderSize: not a directory (or not resolvable) -> " + Uri.decode(uri.toString()));
                return 0;
            }
            return getFolderSizeDoc(context, doc, step);
        }

        myLogW("getFolderSize: unsupported scheme " + scheme + " for " + uri);
        return 0;
    }

    // --- Filesystem recursion for file:// ---
    private static long getFolderSizeFs(File dir, int step) {
        if (dir == null || !dir.exists() || !dir.isDirectory())
            return 0;
        long total = 0;
        File[] kids = dir.listFiles();
        if (kids == null)
            return 0;
        for (File f : kids) {
            if (f.isDirectory()) {
                total += getFolderSizeFs(f, step + 1);
            } else {
                total += f.length();
            }
        }
        return total;
    }

    // --- SAF recursion via DocumentFile (works for fromTreeUri AND fromFile) ---
    private static long getFolderSizeDoc(Context context, DocumentFile dir, int step) {
        if (dir == null || !dir.exists() || !dir.isDirectory())
            return 0;
        long total = 0;
        for (DocumentFile child : dir.listFiles()) {
            if (child.isDirectory()) {
                total += getFolderSizeDoc(context, child, step + 1);
            } else {
                // Prefer DocumentFile.length(); fallback to openFileDescriptor if needed
                long len = child.length();
                if (len <= 0) {
                    try (ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(child.getUri(),
                            "r")) {
                        if (pfd != null)
                            len = pfd.getStatSize();
                    } catch (Exception ignored) {
                    }
                }
                if (len > 0)
                    total += len;
            }
        }
        return total;
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
                        myLogEE(null, "Invalid or non-file SAF Uri in single file case : " + uriToPlay);
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
        myLogEE(null, "Unable to build URI for: " + folderPathOrUri + "/" + fileName);
        return null;
    }

    @Nullable
    public static String getPathFromUri(Context context, Uri uri) {
        if (uri == null)
            return null;
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
        if (uri == null)
            return null;

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
                    if (file.exists())
                        return file;
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

                    myLogW("getFileFromUri: fallback copy success: " + tempFile.getAbsolutePath());
                    return tempFile;
                }
            }

            // CASE 3: SAF Document URI
            if (DocumentsContract.isDocumentUri(context, uri)) {
                String path = getPathFromUri(context, uri);
                if (path != null) {
                    File file = new File(path);
                    if (file.exists())
                        return file;
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
                if (u == null)
                    return null;
                DocumentFile f = DocumentFile.fromSingleUri(context, u);
                if (f != null && f.exists() && f.isFile())
                    return u;

                // fallback: try the raw content Uri
                u = Uri.parse(zf.getPath());
                f = DocumentFile.fromSingleUri(context, u);
                return (f != null && f.exists() && f.isFile()) ? u : null;
            }

            // file://
            if (zf.getPath() != null && zf.getPath().startsWith("file://")) {
                Uri u = Uri.parse(zf.getPath());
                String p = u.getPath();
                if (p == null)
                    return null;
                File f = new File(p);
                if (f.exists() && f.isFile())
                    return u;

                // sometimes folder path + name
                File maybe = new File(p, zf.getName());
                return (maybe.exists() && maybe.isFile()) ? Uri.fromFile(maybe) : null;
            }

            // Plain filesystem path
            String base = zf.getPath();
            if (base == null)
                return null;
            if (fileExists(base))
                return Uri.fromFile(new File(base));
            String withName = base + "/" + zf.getName();
            return fileExists(withName) ? Uri.fromFile(new File(withName)) : null;

        } catch (Throwable t) {
            myLogEE(t, "resolvePlayableUri");
            return null;
        }
    }

    @Nullable
    public static Uri resolveUriFromPath(Context context, String path) {
        if (path == null)
            return null;
        try {
            // Content (SAF)
            if (path.startsWith("content://")) {
                Uri u = Uri.parse(path);
                DocumentFile f = DocumentFile.fromSingleUri(context, u);
                return (f.exists() && f.isFile()) ? u : null;
            }

            // file://
            if (path.startsWith("file://")) {
                Uri u = Uri.parse(path);
                String p = u.getPath();
                if (p == null)
                    return null;
                File f = new File(p);
                if (f.exists() && f.isFile())
                    return u;
            }

            // Plain filesystem path
            if (fileExists(path))
                return Uri.fromFile(new File(path));

        } catch (Throwable t) {
            myLogEE(t, "resolvePlayableUri");
            return null;
        }
        return null;
    }

    public static boolean checkLongTermReadable(Context ctx, Uri src) {
        // Try to persist; if it succeeds, just return src.
        try {
            ctx.getContentResolver().takePersistableUriPermission(
                    src, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            myLog("persisting permission for uri [" + src + "]");
            return true; // persistable → good
        } catch (SecurityException e) {
            myLogEE(null, "could not persist permission for uri [" + src + "] - " + e.getMessage());
            return false;
        } catch (Exception e) {
            myLogEE(null, "Exception while checking persisted permission for uri [" + src + "] - " + e.getMessage());
            return false;
        }

    }

    public static boolean isReturnedUriOk(Intent data) {
        try {
            Uri uri = data.getData();
            if (uri == null || uri.getPath() == null) {
                myToastE("checkDataOk : Error getting URI of selected item.");
                return false;
            }
            return true;
        } catch (Exception e) {
            myLogEE(e, "checkDataOk is KO");
            return false;
        }
    }

}
