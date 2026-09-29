package com.driot.bookplayer.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.UriPermission;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Folder imports hand SupportedFilesHelper.getFileName() a tree Uri (ACTION_OPEN_DOCUMENT_TREE), which a
 * ContentResolver query rejects with "Unsupported Uri" (~110 users/90 days of Crashlytics non-fatals).
 * The name must come from the tree's root document, with a path fallback that never throws.
 */
@RunWith(AndroidJUnit4.class)
public class SupportedFilesHelperTreeUriTest {

    private final Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test
    public void grantedTreeUri_returnsTheFolderName() {
        Uri tree = null;
        for (UriPermission p : ctx.getContentResolver().getPersistedUriPermissions()) {
            if (p.isReadPermission() && DocumentsContract.isTreeUri(p.getUri())) {
                tree = p.getUri();
                break;
            }
        }
        // needs a folder granted once on the device (e.g. the LoadManyBookTest fixtures folder)
        assumeTrue("no persisted tree grant on this device", tree != null);

        String docId = DocumentsContract.getTreeDocumentId(tree); // e.g. "3334-3933:fixtures" or "primary:A/B"
        String expected = docId.substring(Math.max(docId.lastIndexOf('/'), docId.lastIndexOf(':')) + 1);

        // the old path fallback returned "3334-3933:fixtures" for a top-level folder
        assertEquals(expected, SupportedFilesHelper.getFileName(ctx, tree));
    }

    @Test
    public void notGrantedTreeUri_fallsBackToThePathWithoutThrowing() {
        Uri tree = DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents",
                "primary:Download/Quick Share/No Grant Book");
        assertEquals("No Grant Book", SupportedFilesHelper.getFileName(ctx, tree));

        Uri topLevel = DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents",
                "primary:Audiobooks");
        assertEquals("Audiobooks", SupportedFilesHelper.getFileName(ctx, topLevel));
    }
}
