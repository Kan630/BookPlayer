package com.driot.bookplayer.testutil;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.PriorityQueue;

public class StubDocumentProvider extends DocumentsProvider {
    private static final String ROOTS_ID = "stub_roots";
    private static final String ROOT_ID = "stub_root";
    private static final String AUTHORITY = "com.driot.bookplayer.test.documents";

    private File mBaseDir;
    private static File sStaticBaseDir;

    public static void setStaticBaseDir(File dir) {
        sStaticBaseDir = dir;
    }

    @Override
    public void attachInfo(android.content.Context context, android.content.pm.ProviderInfo info) {
        try {
            super.attachInfo(context, info);
        } catch (SecurityException e) {
            // DocumentsProvider enforces MANAGE_DOCUMENTS permission if exported=true.
            // Since we are running a test, we bypass this check.
            // The authority is set by super.attachInfo before the check.
        }
    }

    @Override
    public boolean onCreate() {
        if (sStaticBaseDir != null) {
            mBaseDir = sStaticBaseDir;
        } else {
            // We will serve from the app's cache dir where fixtures are staged
            mBaseDir = new File(getContext().getCacheDir(), "fixtures");
        }
        mBaseDir.mkdirs();
        return true;
    }

    @Override
    public Cursor queryRoots(String[] projection) throws FileNotFoundException {
        final MatrixCursor result = new MatrixCursor(resolveRootProjection(projection));
        final MatrixCursor.RowBuilder row = result.newRow();
        row.add(Root.COLUMN_ROOT_ID, ROOT_ID);
        row.add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_IS_CHILD | Root.FLAG_SUPPORTS_CREATE | Root.FLAG_LOCAL_ONLY);
        row.add(Root.COLUMN_TITLE, "Stub Root");
        row.add(Root.COLUMN_DOCUMENT_ID, getDocIdForFile(mBaseDir));
        row.add(Root.COLUMN_MIME_TYPES, "*/*");
        row.add(Root.COLUMN_AVAILABLE_BYTES, mBaseDir.getFreeSpace());
        return result;
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        final MatrixCursor result = new MatrixCursor(resolveDocumentProjection(projection));
        includeFile(result, documentId, getFileForDocId(documentId));
        return result;
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder)
            throws FileNotFoundException {
        final MatrixCursor result = new MatrixCursor(resolveDocumentProjection(projection));
        final File parent = getFileForDocId(parentDocumentId);
        for (File file : parent.listFiles()) {
            includeFile(result, getDocIdForFile(file), file);
        }
        return result;
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal)
            throws FileNotFoundException {
        final File file = getFileForDocId(documentId);
        final int pfdMode = ParcelFileDescriptor.parseMode(mode);
        return ParcelFileDescriptor.open(file, pfdMode);
    }

    private String getDocIdForFile(File file) {
        String path = file.getAbsolutePath();
        String rootPath = mBaseDir.getAbsolutePath();
        if (path.equals(rootPath)) {
            return ROOT_ID;
        }
        if (path.startsWith(rootPath + "/")) {
            return path.substring(rootPath.length() + 1);
        }
        return path;
    }

    private File getFileForDocId(String docId) throws FileNotFoundException {
        if (ROOT_ID.equals(docId)) {
            return mBaseDir;
        }
        final File target = new File(mBaseDir, docId);
        if (!target.exists()) {
            throw new FileNotFoundException("Missing file for " + docId + " at " + target);
        }
        return target;
    }

    private void includeFile(MatrixCursor result, String docId, File file) throws FileNotFoundException {
        final MatrixCursor.RowBuilder row = result.newRow();
        row.add(Document.COLUMN_DOCUMENT_ID, docId);
        row.add(Document.COLUMN_DISPLAY_NAME, file.getName());
        row.add(Document.COLUMN_SIZE, file.length());
        row.add(Document.COLUMN_LAST_MODIFIED, file.lastModified());
        String mimeType = getMimeType(file);
        row.add(Document.COLUMN_MIME_TYPE, mimeType);

        int flags = Document.FLAG_SUPPORTS_DELETE | Document.FLAG_SUPPORTS_WRITE | Document.FLAG_SUPPORTS_RENAME;
        if (file.isDirectory()) {
            flags |= Document.FLAG_DIR_SUPPORTS_CREATE;
        }
        row.add(Document.COLUMN_FLAGS, flags);
    }

    private String getMimeType(File file) {
        if (file.isDirectory()) {
            return Document.MIME_TYPE_DIR;
        }
        final String name = file.getName();
        final int lastDot = name.lastIndexOf('.');
        if (lastDot >= 0) {
            final String extension = name.substring(lastDot + 1).toLowerCase();
            final String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (mime != null)
                return mime;
        }
        return "application/octet-stream";
    }

    private static String[] resolveRootProjection(String[] projection) {
        return projection != null ? projection
                : new String[] {
                        Root.COLUMN_ROOT_ID,
                        Root.COLUMN_FLAGS,
                        Root.COLUMN_TITLE,
                        Root.COLUMN_DOCUMENT_ID,
                        Root.COLUMN_MIME_TYPES,
                        Root.COLUMN_AVAILABLE_BYTES
                };
    }

    private static String[] resolveDocumentProjection(String[] projection) {
        return projection != null ? projection
                : new String[] {
                        Document.COLUMN_DOCUMENT_ID,
                        Document.COLUMN_DISPLAY_NAME,
                        Document.COLUMN_SIZE,
                        Document.COLUMN_LAST_MODIFIED,
                        Document.COLUMN_MIME_TYPE,
                        Document.COLUMN_FLAGS
                };
    }
}
