package com.driot.bookplayer.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.driot.bookplayer.helpers.UriAccess.Ctx;
import com.driot.bookplayer.helpers.UriAccess.Reason;

import org.junit.Test;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Locks UriAccess.classify() against the exception messages actually seen in production
 * Crashlytics (v287..v328). These strings come from real reports, so a refactor that stops
 * recognising one of them would silently turn a diagnosable failure back into "UNKNOWN".
 *
 * Uri itself is not exercised here: android.net.Uri is unusable in a plain JVM test, hence
 * UriAccess.providerOf(scheme, authority) taking strings.
 */
public class UriAccessClassifyTest {

    private static Reason of(Throwable t) {
        return UriAccess.classify(t, null);
    }

    // ---------- lost / missing read grants: the messenger-share bug ----------

    @Test
    public void downloadsProviderWithoutOpenDocument_isGrantLost() {
        // Pixel 7 Pro, v328
        assertEquals(Reason.GRANT_LOST, of(new SecurityException(
                "Permission Denial: reading com.android.providers.downloads.DownloadStorageProvider uri "
                        + "content://com.android.providers.downloads.documents/document/msf%3A1000095793 "
                        + "from pid=7088, uid=10388 requires that you obtain access using ACTION_OPEN_DOCUMENT "
                        + "or related APIs")));
    }

    @Test
    public void foreignFileProviderNotExported_isGrantLost() {
        // Galaxy S23+, v295 - the Telegram .m4b share
        assertEquals(Reason.GRANT_LOST, of(new SecurityException(
                "Permission Denial: opening provider androidx.core.content.FileProvider from "
                        + "ProcessRecord{5dc9663 32183:com.driot.bookplayerfull/u0a337} (pid=32183, uid=10337) "
                        + "that is not exported from UID 10218")));
    }

    @Test
    public void legacyAllDownloadsNeedingSystemPermission_isGrantLost() {
        // Samsung A52s, v295 - content://downloads/all_downloads/<id>
        assertEquals(Reason.GRANT_LOST, of(new SecurityException(
                "Permission Denial: reading com.android.providers.downloads.DownloadProvider uri "
                        + "content://downloads/all_downloads/4141 from pid=28449, uid=10325 requires "
                        + "android.permission.ACCESS_ALL_DOWNLOADS, or grantUriPermission()")));
    }

    @Test
    public void eaccesOnOpen_isGrantLost() {
        assertEquals(Reason.GRANT_LOST, of(new IOException("open failed: EACCES (Permission denied)")));
    }

    @Test
    public void noPersistableGrant_isNotPersistable() {
        // What checkLongTermReadable() gets back for a Telegram/WhatsApp/Gmail share
        assertEquals(Reason.NOT_PERSISTABLE, of(new SecurityException(
                "No persistable permission grants found for UID 10337 and Uri "
                        + "content://org.telegram.messenger.provider/media/...")));
    }

    // ---------- provider / file gone ----------

    @Test
    public void noContentProvider_isProviderGone() {
        assertEquals(Reason.PROVIDER_GONE, of(new FileNotFoundException(
                "No content provider: content://org.telegram.messenger.provider/media/Android/data/"
                        + "org.telegram.messenger/files/Telegram/Telegram%20Files/book.m4b")));
    }

    @Test
    public void missingFile_isFileGone() {
        assertEquals(Reason.FILE_GONE, of(new FileNotFoundException(
                "Missing file for 5ACE-9727:.android_secure/com.snapchat.android-2.asec "
                        + "at /storage/5ACE-9727/.android_secure/com.snapchat.android-2.asec")));
    }

    // ---------- cloud vs disk full: order matters ----------

    @Test
    public void driveFileNotDownloaded_isCloudUnavailable() {
        // 16 users on v328 - Drive file that is not available offline
        assertEquals(Reason.CLOUD_UNAVAILABLE, of(new FileNotFoundException("otl: Cello error 2. ")));
    }

    @Test
    public void driveErrorCarryingEnospc_isNoSpaceNotCloud() {
        // OnePlus with 160 MB free, v320: the actionable cause is the full disk, not Drive
        assertEquals(Reason.NO_SPACE, of(new FileNotFoundException(
                "sxl: Cello error 14. write failed: ENOSPC (No space left on device)")));
    }

    // ---------- malformed uris ----------

    @Test
    public void msfIdParsedAsLong_isBadUri() {
        assertEquals(Reason.BAD_URI, of(new NumberFormatException("For input string: \"msf:84753\"")));
    }

    @Test
    public void invalidUri_isBadUri() {
        assertEquals(Reason.BAD_URI, of(new IllegalArgumentException(
                "Invalid URI: content://com.android.externalstorage.documents/tree/0123-4567%3ADownload")));
    }

    // ---------- fallbacks ----------

    @Test
    public void unrecognisedMessage_isUnknown() {
        assertEquals(Reason.UNKNOWN, of(new IllegalStateException("something nobody has seen yet")));
    }

    @Test
    public void nullThrowableAndNullUri_isUnknown() {
        assertEquals(Reason.UNKNOWN, UriAccess.classify(null, null));
    }

    @Test
    public void causeChainIsInspected() {
        assertEquals(Reason.NO_SPACE,
                of(new IOException("copy failed", new IOException("write failed: ENOSPC"))));
    }

    @Test
    public void selfReferencingCauseDoesNotLoop() {
        // A cause chain that points at itself must not hang the classifier
        IOException e = new IOException("boom") {
            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        };
        assertEquals(Reason.UNKNOWN, of(e));
    }

    // ---------- severity split: backfill must stay countable but not a non-fatal ----------

    @Test
    public void onlyBestEffortContextsAreNonUserFacing() {
        assertFalse(UriAccess.isUserFacing(Ctx.BACKFILL));
        assertFalse(UriAccess.isUserFacing(Ctx.SCAN));
        assertTrue(UriAccess.isUserFacing(Ctx.IMPORT_PREVIEW));
        assertTrue(UriAccess.isUserFacing(Ctx.IMPORT_COPY));
        assertTrue(UriAccess.isUserFacing(Ctx.PLAYBACK));
        assertTrue(UriAccess.isUserFacing(Ctx.OTHER));
    }

    // ---------- provider labels ----------

    @Test
    public void providerLabelsAreStableAndLowCardinality() {
        assertEquals("telegram", UriAccess.providerOf("content", "org.telegram.messenger.provider"));
        assertEquals("gmail", UriAccess.providerOf("content", "com.google.android.gm.sapi"));
        assertEquals("drive", UriAccess.providerOf("content", "com.google.android.apps.docs.storage"));
        assertEquals("downloads", UriAccess.providerOf("content", "com.android.providers.downloads.documents"));
        assertEquals("externalstorage", UriAccess.providerOf("content", "com.android.externalstorage.documents"));
        assertEquals("mediadocs", UriAccess.providerOf("content", "com.android.providers.media.documents"));
        assertEquals("mediastore", UriAccess.providerOf("content", "media"));
        assertEquals("self", UriAccess.providerOf("content", "com.driot.bookplayerfull.FileProvider"));
        assertEquals("file", UriAccess.providerOf("file", null));
        assertEquals("rawpath", UriAccess.providerOf(null, null));
        assertEquals("other", UriAccess.providerOf("content", "com.acme.unknown.thing"));
    }

    @Test
    public void authorityMatchingIsCaseInsensitive() {
        assertEquals("telegram", UriAccess.providerOf("content", "ORG.TELEGRAM.MESSENGER.PROVIDER"));
        assertEquals("file", UriAccess.providerOf("FILE", null));
    }
}
