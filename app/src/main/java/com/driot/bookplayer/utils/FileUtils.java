package com.driot.bookplayer.utils;

import static com.driot.bookplayer.utils.Tonio.getExtension;

import android.content.ContentUris;
import android.content.Context;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.driot.bookplayer.services.CopyFileService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;

public class FileUtils {


    public interface ProgressListener {
        void onProgressUpdate(long progress, long nbMoCopied);
    }

    public static long calculateFolderSize(Context context, Uri uri) throws IOException {
        myLog("calculateFolderSize()");
        long totalSize = 0;
        ContentResolver contentResolver = context.getContentResolver();
        //Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri));
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
                        totalSize += calculateFolderSize(context, documentUri);
                    } else {
                        totalSize += getFileSize(context, documentUri);
                    }
                }
            }
        }
        return totalSize;
    }

    public static void copyFolder(Context context, Uri sourceUri, File destinationFolder
            , long[] copiedSize, long forceSize
            , String onlyMime, Set<String> onlyExtension
            , ProgressListener listener) throws IOException {
        if (!destinationFolder.exists()) {
            destinationFolder.mkdirs();
        }
        listener.onProgressUpdate(0, 0);
        if (copiedSize==null) copiedSize = new long[]{0};

        ContentResolver contentResolver = context.getContentResolver();
        Uri childrenUri;
        try {
            //for children and sub child dirs
            childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(sourceUri, DocumentsContract.getDocumentId(sourceUri));
        } catch (Exception e) {
            //for parent dir
            childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(sourceUri, DocumentsContract.getTreeDocumentId(sourceUri));
        }

        long totalSize; //used to update progressBar
        if (forceSize>0) {
            totalSize = forceSize;
        } else {
            totalSize = calculateFolderSize(context, sourceUri);
        }

        //long[] copiedSize = {0};

        try (Cursor cursor = contentResolver.query(childrenUri, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    if (!CopyFileService.isCopyRunning) {
                        myLog("copyFolder() canceled");
                        throw new IOException("Copy canceled"); // will be catch by calling line in CopyFileService
                    }
                    String documentId = cursor.getString(0);
                    String displayName = cursor.getString(1);
                    String mimeType = cursor.getString(2);
                    String fileExtension = getExtension(displayName);

                    Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(sourceUri, documentId);

                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                        if (!CopyFileService.isCopyRunning) {
                            myLog("cancel before recursive copyFolder() call");
                            throw new IOException("Copy canceled");
                        }
                        File subDir = new File(destinationFolder, displayName);
                        copyFolder(context, documentUri, subDir, copiedSize, forceSize, onlyMime, onlyExtension, listener);  // Corrected parameters
                    } else {
                        myLog(displayName + "  ///  " + mimeType);
                        boolean doCopy = false;
                        if (onlyMime != null && !onlyMime.isEmpty()) {
                            if (mimeType.startsWith(onlyMime)) {
                                doCopy = true;
                            }
                        } else {
                            doCopy = true;
                        }
                        if (!onlyExtension.isEmpty() && onlyExtension.contains(fileExtension)) {
                            doCopy = true;
                        }
                        if (doCopy) {
                            if (!CopyFileService.isCopyRunning) {
                                myLog("cancel before copyFile()");
                                throw new IOException("Copy canceled");
                            }
                            copyFile(context, documentUri, new File(destinationFolder, displayName), totalSize, copiedSize, listener);
                        }
                    }
                }
            }
        }
    }

    private static void copyFile(Context context, Uri sourceUri, File destinationFile, long totalSize, long[] copiedSize, ProgressListener listener) throws IOException {
        try (ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(sourceUri, "r");
             FileInputStream inputStream = new FileInputStream(pfd.getFileDescriptor());
             FileOutputStream outputStream = new FileOutputStream(destinationFile)) {

            int nbBuffCopied = 0;
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
                copiedSize[0] += length;
                long progress = (int) ((copiedSize[0] * 100) / totalSize);
                if (nbBuffCopied % 1024 == 0) listener.onProgressUpdate(progress, copiedSize[0] / 1024 / 1024);
                nbBuffCopied+=1;
            }
        }
    }

    public static long getFileSize(Context context, Uri uri) throws IOException {
        try (ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "r")) {
            return pfd.getStatSize();
        } catch (Exception e) {
            myLogEE(e,"getFileSize() for uri [" + uri + "]");
            return -1;
        }
    }

/*
    public static long getFileSize(Context context, Uri uri) throws IOException {
        ContentResolver contentResolver = context.getContentResolver();
        ParcelFileDescriptor parcelFileDescriptor = contentResolver.openFileDescriptor(uri, "r");

        if (parcelFileDescriptor != null) {
            FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
            FileInputStream fileInputStream = new FileInputStream(fileDescriptor);
            long size = fileInputStream.getChannel().size();

            //if (size > 0) size = size  / 1024 / 1024;

            // Close resources
            fileInputStream.close();
            parcelFileDescriptor.close();

            myLog("parcelFileDescriptor return size : " + formatMem(size, 0)  + " Mo.");
            return size;
        } else {
            myLogE("parcelFileDescriptor is null");
        }
        return -3;
    }

 */
/*
    @Nullable
    public static Uri buildFileUri(Uri folderUri, String fileName) {
        // SAF documents URI are like content://com.android.externalstorage.documents/tree/...
        // We need to build a child document Uri using DocumentsContract
        try {
            if (DocumentsContract.isTreeUri(folderUri)) {
                return DocumentsContract.buildDocumentUriUsingTree(
                        folderUri,
                        DocumentsContract.getDocumentId(folderUri) + "/" + fileName
                );
            } else {
                myLogEE(null, "DocumentsContract.isTreeUri(folderUri).. KO..");
            }
        } catch (Exception e) {
            myLogEE(e,"buildFileUri");
        }
        return null;
    }

 */

    @Nullable
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

    public static String getRealPathFromURI(Context context, Uri uri) {
        String path = null;
        if (DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if ("com.android.externalstorage.documents".equals(uri.getAuthority())) {
                String docId = DocumentsContract.getDocumentId(uri);
                String[] split = docId.split(":");
                String type = split[0];
                if ("primary".equalsIgnoreCase(type)) {
                    path = context.getExternalFilesDir(null) + "/" + split[1];
                }
            }
            // DownloadsProvider
            else if ("com.android.providers.downloads.documents".equals(uri.getAuthority())) {
                String id = DocumentsContract.getDocumentId(uri);
                Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.parseLong(id));
                path = getDataColumn(context, contentUri, null, null);
            }
            // MediaProvider
            else if ("com.android.providers.media.documents".equals(uri.getAuthority())) {
                String docId = DocumentsContract.getDocumentId(uri);
                String[] split = docId.split(":");
                String type = split[0];
                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }
                String selection = "_id=?";
                String[] selectionArgs = new String[] { split[1] };
                path = getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            path = getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            path = uri.getPath();
        }
        return path;
    }

    private static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        Cursor cursor = null;
        String[] projection = { MediaStore.MediaColumns.DATA };
        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                int column_index = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
                return cursor.getString(column_index);
            }
        } catch (Exception e) {
            myLogEE(e,"getDataColumn()");
        }
        finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

    // ----------------------- LOG -----------------------
    private static final String TAG = "FileUtils";
    private static void myLog(String str) { KanLogger.myLog(TAG, str); }
    private static void myLogD(String str) { KanLogger.myLogD(TAG, str); }
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
    private static void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, TAG, str); }
    private static void myToastEE(Throwable t, String str) { KanLogger.myToastEE(t, TAG, str); }
}
