// app/src/androidTest/java/com/driot/bookplayer/testutil/HashAssert.java
package com.driot.bookplayer.testutil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;

public final class HashAssert {
    private HashAssert() {}

    /** Legacy per-file assert (kept for other tests). */
    public static void assertOrInit(Map<String, String> actual, Map<String, String> expected, boolean initMode) {
        if (initMode) {
            FixtureTestUtils.logHashes(actual);
            assertTrue(true);
            return;
        }
        assertEquals("Different files count", expected.size(), actual.size());
        for (Map.Entry<String, String> e : expected.entrySet()) {
            String rel = e.getKey();
            String expHash = e.getValue();
            String actHash = actual.get(rel);
            assertTrue("Missing file: " + rel, actHash != null);
            assertEquals("Hash mismatch for: " + rel, expHash.toLowerCase(), actHash.toLowerCase());
        }
    }

    // ===================== NEW: single-folder-hash assert =====================

    /**
     * If INIT=true: logs canonical listing and final folder hash, then passes.
     * Else: asserts the computed folder hash equals expectedHash (case-insensitive).
     */
    public static void assertOrInitFolderHash(java.io.File root, String expectedHash, boolean initMode) throws Exception {
        if (initMode) {
            FixtureTestUtils.logFolderListingAndHash(root);
            assertTrue(true);
            return;
        }
        String actual = FixtureTestUtils.computeFolderStructureHash(root);
        assertEquals("Folder structure hash mismatch", expectedHash.toLowerCase(), actual.toLowerCase());
    }
}
