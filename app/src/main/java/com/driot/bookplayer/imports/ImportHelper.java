package com.driot.bookplayer.imports;

import android.content.Context;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.helpers.FileHelper;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.objects.AppViewModelStoreOwner;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.work.WorkManager;

import java.io.File;
import java.util.concurrent.Executors;

public class ImportHelper {

    public static void cancelCurrentImport(Context ctx) {
        Context app = ctx.getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(app);

        Executors.newSingleThreadExecutor().execute(() -> {
            ImportJobDao dao = db.importJobDao();
            // Add this DAO method (see below)
            ImportJob job = dao.getUniqueJob();  //getMostRecentActive(ImportJob.S_RUNNING, ImportJob.S_QUEUED, ImportJob.S_PAUSED);
            if (job == null) {
                myLogW("No active import to cancel");
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

            cleanUp(app,true, job.futureFolderPath);
        });
    }

    public static void setShowToUser(Context ctx, boolean showToUser) {
        Context app = ctx.getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(app);

        AppDatabase.databaseWriteExecutor.execute(() -> {
            ImportJobDao dao = db.importJobDao();
            // Add this DAO method (see below)
            ImportJob job = dao.getUniqueJob();  //getMostRecentActive(ImportJob.S_RUNNING, ImportJob.S_QUEUED, ImportJob.S_PAUSED);
            if (job == null) {
                myLogW("No active import to deal with");
            } else {
                myLog("has been shown set");
                dao.setShowToUser(job.importId, showToUser, System.currentTimeMillis());
            }
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

    public static void cleanUp(Context context, boolean deleteBook, String futureFolderPath) {
        Thread.currentThread().setPriority(Thread.NORM_PRIORITY - 1);
        myLogD("Cleanup starting (bg)…   - deleteBook = [" + deleteBook + "] - futureFolderPath = [" + futureFolderPath + "]");

        // 3) Delete the unzip/working folder iff it's internal AND not referenced in DB
        if (deleteBook) {
            try {
                if (futureFolderPath != null && futureFolderPath.length() > 5) {
                    if (StorageHelper.isInInternalMemory(futureFolderPath)) {
                        if (FileHelper.exists(futureFolderPath)) {
                            // DB check on DB executor, then delete in THIS bg thread.
                            AppDatabase.databaseReadExecutor.execute(() -> {
                                boolean safeToDelete =
                                        AppDatabase.getDatabase(context)
                                                .folderDao()
                                                .folderAlreadyExist_checkFolderPath(futureFolderPath) == 0;
                                if (safeToDelete) {
                                    myLogI("cleanup - deleting internal audio folder [" + futureFolderPath + "]");
                                    try { FileHelper.deleteFolderRecursive(futureFolderPath); }
                                    catch (Exception e) { myLogEE(e, "delete internal audio folder"); }
                                } else {
                                    myLogW("cleanup - folder still in DB : [" + futureFolderPath + "]");
                                }
                            });
                        } else {
                            myLogD("cleanup - folderToDeletePath does not exist : [" + futureFolderPath + "]");
                        }
                    } else {
                        myLogD("cleanup - no delete for non internal folder : [" + futureFolderPath + "]");
                    }
                } else {
                    myLogEE(null,"cleanup - no futureFolderPath - no disk delete");
                }
            } catch (Exception e) {
                myLogEE(e, "cleanup - delete Internal (unzip) Folder : [" + futureFolderPath + "]");
            }
        }

        // 2) tidy the app’s Download folder CONTENTS (not the folder itself)
        try {
            String downloadDirPath = StorageHelper.getDownloadFolderPath(context);
            File dl = new File(downloadDirPath);
            if (dl.exists() && dl.isDirectory()) {
                FileHelper.deleteFolderChildren(dl); // implement: deletes children only
            } else if (!dl.exists()) {
                // Create if your pipeline assumes existence (still background, so OK)
                if (!dl.mkdirs()) myLogW("cleanup - mkdirs deleteBook for " + dl.getAbsolutePath());
            }
        } catch (Exception e) {
            myLogEE(e, "cleanup - tidy Download folder");
        }

        // 4) Temp image
        try { ImageHelper.deleteTempImportImage(context); }
        catch (Exception e) { myLogEE(e, "cleanup - delete Temp Import Image"); }

        // 5) Final fast things (prefs/viewmodels) — still safe in bg
        //try { Pref.clearLoadBookTaskState(context); } catch (Exception e) { myLogEE(e, "cleanup - clearLoadBookTaskState (bg)"); }

        try { AppViewModelStoreOwner.clear(); } catch (Exception e) { myLogEE(e, "cleanup - clear AppViewModelStoreOwner"); }

        myLogD("Cleanup finished (bg).");
    }
}
