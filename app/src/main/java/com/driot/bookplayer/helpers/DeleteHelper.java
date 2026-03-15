package com.driot.bookplayer.helpers;

import android.content.Context;

import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.services.DeleteFolderWorker;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import java.util.List;

public class DeleteHelper {

    /** Helper for tests or non-UI usage to clear books. */
    public static void deleteBooksByTimeDelta(Context context, int minutesAgo) {
        long cutoffTime = System.currentTimeMillis() - (minutesAgo * 60L * 1000L);
        AppDatabase db = AppDatabase.getDatabase(context);
        myLog("deleting books imported last " + minutesAgo + " min.");
        AppDatabase.databaseReadExecutor.execute(() -> {
            List<Folder> foldersToDelete = db.folderDao().getFoldersCreatedSince(cutoffTime);
            if (foldersToDelete.isEmpty()) return;

            WorkManager wm = WorkManager.getInstance(context.getApplicationContext());
            for (Folder folder : foldersToDelete) {
                Data input = new Data.Builder()
                        .putLong(DeleteFolderWorker.KEY_FOLDER_ID, (long) folder.getId())
                        .putString(DeleteFolderWorker.KEY_FOLDER_NAME, folder.getName())
                        .build();

                OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(DeleteFolderWorker.class)
                        .addTag("delete_folder_" + folder.getId())
                        .setInputData(input)
                        .build();

                // Use unique work name to prevent duplicates
                wm.enqueueUniqueWork(
                        "delete_folder_unique_" + folder.getId(),
                        ExistingWorkPolicy.KEEP,
                        req);
            }
        });
    }


}
