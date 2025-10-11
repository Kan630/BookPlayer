package com.driot.bookplayer.imports;

import android.content.Context;

import com.driot.bookplayer.db.AppDatabase;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.work.WorkManager;

import java.util.concurrent.Executors;

public class ImportHelper {

    public static void cancelCurrentImport(Context ctx) {
        Context app = ctx.getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(app);

        Executors.newSingleThreadExecutor().execute(() -> {
            ImportJobDao dao = db.importJobDao();
            // Add this DAO method (see below)
            ImportJob job = dao.getMostRecentActive(ImportJob.S_RUNNING, ImportJob.S_QUEUED, ImportJob.S_PAUSED);
            if (job == null) {
                myLogD("No active import to cancel");
                return;
            }

            WorkManager wm = WorkManager.getInstance(app);

            // Prefer cancelling by the unique chain name if you stored it:
            if (job.uniqueChainName != null && !job.uniqueChainName.isEmpty()) {
                wm.cancelUniqueWork(job.uniqueChainName);
            } else {
                // Fallback: cancel by tag you added on each step
                wm.cancelAllWorkByTag("import:" + job.importId);
            }

            // Reflect cancellation in Room (so UI updates immediately)
            new ImportJobRepository(app).cancel(job.importId);
        });
    }


    public static boolean isAnyImportActiveSync(Context ctx) {
        AppDatabase db = AppDatabase.getInstance(ctx.getApplicationContext());
        return db.importJobDao().countActive(ImportJob.S_RUNNING, ImportJob.S_QUEUED, ImportJob.S_PAUSED) > 0;
    }

    public static LiveData<Boolean> observeAnyImportActive(Context ctx) {
        AppDatabase db = AppDatabase.getInstance(ctx.getApplicationContext());
        return Transformations.map(
                db.importJobDao().observeActiveCount(ImportJob.S_RUNNING, ImportJob.S_QUEUED, ImportJob.S_PAUSED),
                c -> c != null && c > 0
        );
    }
}
