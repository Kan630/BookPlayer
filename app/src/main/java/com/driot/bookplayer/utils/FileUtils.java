package com.driot.bookplayer.utils;

import static android.os.FileUtils.closeQuietly;
import static com.driot.tonylib.KanLogger.myLog;
import static com.driot.tonylib.KanLogger.myLogE;

import android.content.ContentUris;
import android.content.Context;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

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

    public static void copyFolder(Context context, Uri sourceUri, File destinationFolder, long[] copiedSize, long forceSize, String onlyMime, ProgressListener listener) throws IOException {
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
                    String documentId = cursor.getString(0);
                    String displayName = cursor.getString(1);
                    String mimeType = cursor.getString(2);

                    Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(sourceUri, documentId);

                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                        File subDir = new File(destinationFolder, displayName);
                        copyFolder(context, documentUri, subDir, copiedSize, forceSize, onlyMime, listener);  // Corrected parameters
                    } else {
                        myLog(displayName + "  ///  " + mimeType);
                        if (onlyMime != null && onlyMime.length()>0) {
                            if (mimeType.startsWith(onlyMime)) {
                                copyFile(context, documentUri, new File(destinationFolder, displayName), totalSize, copiedSize, listener);
                            }
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

    private static long getFileSize(Context context, Uri uri) throws IOException {
        try (ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "r")) {
            return pfd.getStatSize();
        }
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
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
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
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

}
