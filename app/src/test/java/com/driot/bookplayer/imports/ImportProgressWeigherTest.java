package com.driot.bookplayer.imports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.driot.bookplayer.global.Var;

import org.junit.Test;

/** The import progress bar is a single global percent stitched from several weighted steps. */
public class ImportProgressWeigherTest {

    private static ImportJob job() {
        ImportJob j = new ImportJob();
        j.importId = "test:1";
        return j;
    }

    @Test
    public void scanOnly_scanStepMapsDirectlyToGlobalPercent() {
        ImportJob j = job();
        assertEquals(0, ImportProgressWeigher.toGlobalPercent(j, Var.WORKER_TASK_LABEL_SCAN, 0));
        assertEquals(50, ImportProgressWeigher.toGlobalPercent(j, Var.WORKER_TASK_LABEL_SCAN, 50));
        assertEquals(100, ImportProgressWeigher.toGlobalPercent(j, Var.WORKER_TASK_LABEL_SCAN, 100));
    }

    @Test
    public void disabledStepContributesNothing() {
        ImportJob j = job(); // doDownload = false
        assertEquals(0, ImportProgressWeigher.toGlobalPercent(j, Var.WORKER_TASK_LABEL_DOWNLOAD, 100));
    }

    @Test
    public void unknownStepIsZero() {
        assertEquals(0, ImportProgressWeigher.toGlobalPercent(job(), "NoSuchStep", 80));
    }

    @Test
    public void downloadWeighsMoreThanCopy() {
        ImportJob j = job();
        j.doDownload = true;
        j.doCopy = true;
        // download finished => 20 of (20+3+2) = 80 %, copy has not started
        int afterDownload = ImportProgressWeigher.toGlobalPercent(j, Var.WORKER_TASK_LABEL_DOWNLOAD, 100);
        assertEquals(80, afterDownload);
        // copy at 100 % => everything but scan done => (20+3)/25 = 92 %
        int afterCopy = ImportProgressWeigher.toGlobalPercent(j, Var.WORKER_TASK_LABEL_COPY, 100);
        assertEquals(92, afterCopy);
    }

    @Test
    public void progressIsMonotonicAcrossStepsAndWithinAStep() {
        ImportJob j = job();
        j.doDownload = true;
        j.doCopy = true;
        j.doUnzip = true;
        j.doSplitM4b = true;

        String[] order = { Var.WORKER_TASK_LABEL_DOWNLOAD, Var.WORKER_TASK_LABEL_COPY,
                Var.WORKER_TASK_LABEL_DECOMPRESS, Var.WORKER_TASK_LABEL_SPLIT_M4B, Var.WORKER_TASK_LABEL_SCAN };
        int previous = -1;
        for (String step : order) {
            for (int p = 0; p <= 100; p += 10) {
                int g = ImportProgressWeigher.toGlobalPercent(j, step, p);
                assertTrue(step + "@" + p + " went backwards: " + g + " < " + previous, g >= previous);
                assertTrue(g >= 0 && g <= 100);
                previous = g;
            }
        }
        assertEquals(100, previous);
    }

    @Test
    public void massImport_onlyCountsWhenSourceLocationIsMassImport() {
        ImportJob plain = job();
        int scanPlain = ImportProgressWeigher.toGlobalPercent(plain, Var.WORKER_TASK_LABEL_SCAN, 0);
        assertEquals(0, scanPlain);

        ImportJob mass = job();
        mass.sourceLocation = Var.WORKER_MASS_IMPORT;
        // MassImport (weight 2) comes first: scan at 0 % already sits after the 2/4 mass part
        assertEquals(50, ImportProgressWeigher.toGlobalPercent(mass, Var.WORKER_TASK_LABEL_SCAN, 0));
        assertEquals(0, ImportProgressWeigher.toGlobalPercent(mass, Var.WORKER_MASS_IMPORT, 0));
    }

    @Test
    public void splitEbookAndSplitM4bAreIndependentSteps() {
        ImportJob j = job();
        j.doSplitEbook = true;
        int ebook = ImportProgressWeigher.toGlobalPercent(j, Var.WORKER_TASK_LABEL_SPLIT_EBOOK, 100);
        assertEquals(77, ebook); // 7 / (7+2) = 77.7 -> truncated
        assertEquals(0, ImportProgressWeigher.toGlobalPercent(j, Var.WORKER_TASK_LABEL_SPLIT_M4B, 100));
    }
}
