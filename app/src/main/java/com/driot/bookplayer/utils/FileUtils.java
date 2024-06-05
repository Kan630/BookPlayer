package com.driot.bookplayer.utils;

import android.content.Context;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileUtils {

    public interface ProgressListener {
        void onProgressUpdate(long progress, long nbMoCopied);
    }

    public static long calculateFolderSize(Context context, Uri uri) throws IOException {
        long totalSize = 0;
        ContentResolver contentResolver = context.getContentResolver();
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri));

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

    public static void copyFolder(Context context, Uri sourceUri, File destinationFolder, ProgressListener listener) throws IOException {
        if (!destinationFolder.exists()) {
            destinationFolder.mkdirs();
        }

        ContentResolver contentResolver = context.getContentResolver();
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(sourceUri, DocumentsContract.getTreeDocumentId(sourceUri));
        long totalSize = calculateFolderSize(context, sourceUri);
        long[] copiedSize = {0};

        try (Cursor cursor = contentResolver.query(childrenUri, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String documentId = cursor.getString(0);
                    String displayName = cursor.getString(1);
                    String mimeType = cursor.getString(2);

                    Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(sourceUri, documentId);

                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                        File subDir = new File(destinationFolder, displayName);
                        copyFolder(context, documentUri, subDir, listener);
                    } else {
                        copyFile(context, documentUri, new File(destinationFolder, displayName), totalSize, copiedSize, listener);
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
}
