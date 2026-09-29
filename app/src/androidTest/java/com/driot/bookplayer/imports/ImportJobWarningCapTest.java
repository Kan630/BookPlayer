package com.driot.bookplayer.imports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.driot.bookplayer.db.AppDatabase;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * ImportJob.warningText was appended forever (appendWarning, downloadPause); a row grew past the 2 MB CursorWindow
 * and observeUniqueJob crashed at every launch (SQLiteBlobTooBigException, Crashlytics). Appends are now capped to
 * the last 4000 chars and AppUpgrade trims rows grown before the cap. Uses the real DB with its own job id and
 * deletes it afterwards.
 */
@RunWith(AndroidJUnit4.class)
public class ImportJobWarningCapTest {

    private static final int CAP = 4000;

    @Test
    public void oversizedWarningIsTrimmed_andAppendsStayCapped() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AppDatabase db = AppDatabase.getDatabase(ctx);
        ImportJobDao dao = db.importJobDao();
        String id = "warning-cap-test:" + System.nanoTime();
        try {
            ImportJob job = new ImportJob();
            job.importId = id;
            job.createdAt = 1; // oldest possible: never becomes the "latest" job the UI observes
            job.updatedAt = 1;
            job.warningText = repeat('w', 3 * 1024 * 1024); // bigger than a CursorWindow
            dao.upsert(job);

            assertTrue(dao.trimOversizedWarnings() >= 1);
            assertEquals(CAP, warningLength(db, id));

            dao.appendWarning(id, repeat('x', 5000) + "END", 1);
            assertEquals(CAP, warningLength(db, id));

            ImportJob read = dao.get(id); // was SQLiteBlobTooBigException before the trim
            assertNotNull(read);
            assertTrue(read.warningText.endsWith("xEND")); // the newest text is the one kept
        } finally {
            db.getOpenHelper().getWritableDatabase().execSQL("DELETE FROM ImportJob WHERE importId = ?",
                    new Object[] { id });
        }
    }

    private static long warningLength(AppDatabase db, String id) {
        try (Cursor c = db.getOpenHelper().getReadableDatabase()
                .query("SELECT length(warningText) FROM ImportJob WHERE importId = ?", new Object[] { id })) {
            assertTrue(c.moveToFirst());
            return c.getLong(0);
        }
    }

    private static String repeat(char ch, int n) {
        char[] a = new char[n];
        java.util.Arrays.fill(a, ch);
        return new String(a);
    }
}
