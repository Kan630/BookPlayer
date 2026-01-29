package com.driot.bookplayer.imports;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.Operation;
import androidx.work.WorkContinuation;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.objects.LoadBookTaskState;
import com.driot.bookplayer.services.CopyFileWorker;
import com.driot.bookplayer.services.DownloadWorker;
import com.driot.bookplayer.services.EbookSplitWorker;
import com.driot.bookplayer.services.FinalParseFolderWorker;
import com.driot.bookplayer.services.M4bSplitWorker;
import com.driot.bookplayer.services.UncompressWorker;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class BookLoadingWorkLauncher {

    public static final String BOOK_LOADING_WORKERS = "BOOK_LOADING_WORKERS";

    public static void launch(Context ctx, LoadBookTaskState s, boolean sequential) {
        if (s == null) throw new IllegalStateException("No task bookState found for BookLoadingWorkLauncher");
        AppDatabase.databaseWriteExecutor.execute(() -> {
            String importId = (s.futureFolderName != null ? s.futureFolderName : "book") + ":" + UUID.randomUUID();

            boolean doDownload = false;
            boolean doCopy = false;
            boolean doSplitM4b = false;
            boolean doSplitEbook = false;
            boolean doUncompress = false;

            myLog("*********************************************************************************************************");
            myLog("*********************************************************************************************************");
            myLog("** title =            " + s.title + " **");
            myLog("---------------------------------------------------------------------------------------------------------");
            if (s.addToExistingFolderId>0) myLog("** ADD TO EXISTING BOOK - ID : [" + s.addToExistingFolderId + "] **");
            myLog("** futureFolderName = " + s.futureFolderName + " **");
            myLog("** futureFolderPath = " + s.futureFolderPath + " **");
            myLog("---------------------------------------------------------------------------------------------------------");
            myLog("** original uri =  " + s.originalUri + " **");
            myLog("** original type = " + s.originalType + " **");
            myLog("---------------------------------------------------------------------------------------------------------");
            myLog("** dynamic uri =  " + s.dynamicUri + " **");
            myLog("** dynamic type =  " + s.dynamicType + " **");
            myLog("---------------------------------------------------------------------------------------------------------");
            myLog("** extension =  " + s.fileExtension + " **");
            myLog("** playType =  " + s.playType + " **");
            myLog("---------------------------------------------------------------------------------------------------------");
            myLog("** option copy =  " + s.optionCopy + " **");
            myLog("** option split =  " + s.optionSplit + " **");
            myLog("** option delete =  " + s.optionDelete + " **");
            myLog("---------------------------------------------------------------------------------------------------------");
            //myLog("---------------------------------------------------------------------------------------------------------");
            //myLog(bookState.toString().replace(",", "\n"));
            myLog("*********************************************************************************************************");
            myLog("*********************************************************************************************************");

            if (s.dynamicUri.toString().startsWith("http")) {
                myLogD("http => download");
                doDownload = true;
            }
            if (s.optionCopy || s.sourceLocation.equals("cloud")) {
                myLogD("copy");
                doCopy = true;
            }
            if (s.optionSplit && s.fileExtension != null && s.fileExtension.equalsIgnoreCase("m4b")) {
                myLogD("m4b to split");
                doCopy = true;
                doSplitM4b = true;
            }
            if (s.fileExtension != null && Var.SUPPORTED_COMPRESSED_FILE_EXTENSIONS.contains(s.fileExtension.toLowerCase(Locale.ROOT))) {
                myLogD("COMPRESSED => unzip + copy");
                doCopy = true;
                doUncompress = true;
            }
            if (s.fileExtension != null && s.fileExtension.equalsIgnoreCase("epub")) {
                myLogD("epub");
                doCopy = true;
                doSplitEbook = true;
            } else if (s.fileExtension != null && s.fileExtension.equalsIgnoreCase("fb2")) {
                myLogD("fb2");
                doCopy = true;
                doSplitEbook = true;
            } else if (s.fileExtension != null && s.fileExtension.equalsIgnoreCase("odt")) {
                myLogD("odt");
                doCopy = true;
                doSplitEbook = true;
            }
            if (doDownload) {
                if (doCopy) {
                    doCopy = false;
                    myLogD("http has copy integrated, so copy => false");
                }
            }

            FirebaseAnalyticsHelper.tellAnalyticsWork(s, doDownload);
            FirebaseAnalyticsHelper.setCustomKeyCrashlytics("worker_originalType", s.originalType);
            FirebaseAnalyticsHelper.setCustomKeyCrashlytics("worker_extension", s.fileExtension);
            FirebaseAnalyticsHelper.setCustomKeyCrashlytics("worker_sourceLocation", s.sourceLocation);
            FirebaseAnalyticsHelper.setCustomKeyCrashlytics("worker_playType", s.playType);
            FirebaseAnalyticsHelper.setCustomKeyCrashlytics("worker_originalUri", String.valueOf(s.originalUri));
            FirebaseAnalyticsHelper.setCustomKeyCrashlytics("worker_doDownload", String.valueOf(doDownload));
            FirebaseAnalyticsHelper.setCustomKeyCrashlytics("worker_doCopy", String.valueOf(doCopy));
            FirebaseAnalyticsHelper.setCustomKeyCrashlytics("worker_doUnzip", String.valueOf(doUncompress));
            FirebaseAnalyticsHelper.setCustomKeyCrashlytics("worker_doSplitM4b", String.valueOf(doSplitM4b));
            FirebaseAnalyticsHelper.setCustomKeyCrashlytics("worker_doSplitEbook", String.valueOf(doSplitEbook));
            FirebaseAnalyticsHelper.setCustomKeyCrashlytics("worker_addToExistingFolderId", String.valueOf(s.addToExistingFolderId));
            FirebaseAnalyticsHelper.setCustomKeyCrashlytics("worker_futureFolderName", String.valueOf(s.futureFolderName));

            // Create job row
            ImportJob j = new ImportJob();
            j.importId = importId;
            j.title = s.title;

            j.originalUri = s.originalUri != null ? s.originalUri.toString() : null;
            j.originalType = s.originalType;
            j.dynamicUri = s.dynamicUri != null ? s.dynamicUri.toString() : null;
            j.dynamicType = s.dynamicType;
            j.futureFolderName = s.futureFolderName;
            j.futureFolderPath = s.futureFolderPath;

            j.fileExtension = s.fileExtension;
            j.sourceLocation = s.sourceLocation;
            j.optionSplit = s.optionSplit;
            j.optionCopy = s.optionCopy;
            j.optionDelete = s.optionDelete;
            j.playType = s.playType;

            j.originalFile = s.originalFile;
            j.originalHash = s.originalHash;
            j.mimeType = s.mimeType;
            j.imagePath = s.imagePath;

            j.doDownload = doDownload;
            j.doSplitM4b = doSplitM4b;
            j.doSplitEbook = doSplitEbook;
            j.doUnzip = doUncompress;
            j.doCopy = doCopy;

            j.addToExistingFolderId = s.addToExistingFolderId;

            j.status = Var.IMPORT_STATUS_RUNNING; //_QUEUED;
            j.createdAt = j.updatedAt = System.currentTimeMillis();
            //j.showToUser = true;

            Data common = new Data.Builder().putString(ImportWorker.KEY_IMPORT_ID, importId).build();
            List<OneTimeWorkRequest> steps = new ArrayList<>();

            if (doDownload) {
                j.isPauseAvailable = true;
                j.downloadFileUrl = s.dynamicUri.toString();
                j.downloadDestinationFolder = StorageHelper.getDownloadFolderPath(ctx.getApplicationContext());
                Constraints constraints = buildDownloadConstraints();
                steps.add(new OneTimeWorkRequest.Builder(DownloadWorker.class)
                        .setConstraints(constraints)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                        .setInputData(common) // workers now read importId → get url/dest from Room
                        .addTag(BOOK_LOADING_WORKERS)
                        .addTag("import:" + importId)
                        .addTag(DownloadWorker.TAG_DOWNLOAD)
                        .build());
            }

            ImportJobRepository repo = new ImportJobRepository(ctx);
            repo.upsert(j);

            if (j.doCopy) steps.add(new OneTimeWorkRequest.Builder(CopyFileWorker.class)
                    .setInputData(common).addTag(BOOK_LOADING_WORKERS).addTag("import:" + importId).build());
            if (j.doUnzip) steps.add(new OneTimeWorkRequest.Builder(UncompressWorker.class)
                    .setInputData(common).addTag(BOOK_LOADING_WORKERS).addTag("import:" + importId).build());
            if (j.doSplitM4b) steps.add(new OneTimeWorkRequest.Builder(M4bSplitWorker.class)
                    .setInputData(common).addTag(BOOK_LOADING_WORKERS).addTag("import:" + importId).build());
            if (j.doSplitEbook) steps.add(new OneTimeWorkRequest.Builder(EbookSplitWorker.class)
                    .setInputData(common).addTag(BOOK_LOADING_WORKERS).addTag("import:" + importId).build());

            steps.add(new OneTimeWorkRequest.Builder(FinalParseFolderWorker.class)
                    .setInputData(common).addTag(BOOK_LOADING_WORKERS).addTag("import:" + importId).build());

            WorkManager wm = WorkManager.getInstance(ctx);

            String uniqueName = sequential ? "bookload-queue" : "bookload:" + importId;
            myLogD("uniqueName = " + uniqueName);
            
            // Check if there's active work in the queue before deciding policy
            ExistingWorkPolicy policy;
            if (sequential) {
                // For sequential work, check if there's any active (RUNNING or ENQUEUED) work
                try {
                    java.util.List<WorkInfo> existingInfos = wm.getWorkInfosForUniqueWork(uniqueName).get();
                    boolean hasActiveWork = false;
                    if (existingInfos != null && !existingInfos.isEmpty()) {
                        for (WorkInfo wi : existingInfos) {
                            if (wi.getState() == WorkInfo.State.RUNNING || wi.getState() == WorkInfo.State.ENQUEUED) {
                                hasActiveWork = true;
                                break;
                            }
                        }
                    }
                    if (!hasActiveWork && existingInfos != null && !existingInfos.isEmpty()) {
                        // All work is finished (SUCCEEDED/FAILED), prune it before appending
                        myLogD("Pruning finished work from queue before appending new work");
                        wm.pruneWork();
                    }
                    policy = ExistingWorkPolicy.APPEND;
                } catch (Exception e) {
                    myLogEE(e, "Error checking existing work, using APPEND");
                    policy = ExistingWorkPolicy.APPEND;
                }
            } else {
                policy = ExistingWorkPolicy.REPLACE;
            }
            myLogD("ExistingWorkPolicy = " + policy);

            if (steps.isEmpty()) {
                myLogE("BookLoadingWorkLauncher: No steps to enqueue! This should not happen.");
                return;
            }

            myLogD("BookLoadingWorkLauncher: Enqueuing " + steps.size() + " steps");
            for (int i = 0; i < steps.size(); i++) {
                String className = steps.get(i).getClass().getSimpleName();
                myLogD("  Step[" + i + "]: " + className);
            }

            WorkContinuation cont = wm.beginUniqueWork(uniqueName, policy, steps.get(0));
            for (int i = 1; i < steps.size(); i++) cont = cont.then(steps.get(i));
            
            myLogD("BookLoadingWorkLauncher: Calling enqueue() for importId=" + importId);
            Operation result = cont.enqueue();
            myLogD("BookLoadingWorkLauncher: Enqueue operation submitted");
            
            // Log work info after a short delay to see if it starts
            Handler main = new Handler(Looper.getMainLooper());
            final String checkImportId = importId; // Capture for lambda
            main.postDelayed(() -> {
                try {
                    java.util.List<WorkInfo> infos = wm.getWorkInfosForUniqueWork(uniqueName).get();
                    if (infos != null && !infos.isEmpty()) {
                        myLogD("WM unique '" + uniqueName + "' -> Found " + infos.size() + " work items:");
                        int newWorkCount = 0;
                        int activeWorkCount = 0;
                        for (WorkInfo wi : infos) {
                            boolean isNewWork = wi.getTags().contains("import:" + checkImportId);
                            boolean isActive = wi.getState() == WorkInfo.State.RUNNING || 
                                            wi.getState() == WorkInfo.State.ENQUEUED ||
                                            wi.getState() == WorkInfo.State.BLOCKED;
                            if (isNewWork) newWorkCount++;
                            if (isActive) activeWorkCount++;
                            
                            if (isNewWork || isActive) {
                                myLogD("  - " + wi.getId() + " state=" + wi.getState() + " tags=" + wi.getTags() + 
                                    (isNewWork ? " [NEW]" : "") + (isActive ? " [ACTIVE]" : ""));
                            }
                            if (wi.getState() == WorkInfo.State.BLOCKED) {
                                myLogW("  Work is BLOCKED - may be waiting for previous work to complete");
                            } else if (wi.getState() == WorkInfo.State.ENQUEUED) {
                                myLogD("  Work is ENQUEUED - should start soon");
                            } else if (wi.getState() == WorkInfo.State.RUNNING) {
                                myLogD("  Work is RUNNING");
                            }
                        }
                        myLogD("Summary: " + newWorkCount + " new work items, " + activeWorkCount + " active work items");
                        if (newWorkCount == 0) {
                            myLogE("ERROR: No new work items found for importId=" + checkImportId + " - work may not have been enqueued!");
                        }
                    } else {
                        myLogW("WM unique '" + uniqueName + "' -> No work found!");
                    }
                } catch (Exception e) {
                    myLogEE(e, "Error checking work status");
                }
            }, 2000); // Increased delay to 2 seconds to give WorkManager more time

            // some logging
            /*
            Handler main = new Handler(Looper.getMainLooper());
            main.post(() -> {
                        wm.getWorkInfosForUniqueWorkLiveData("bookload-queue").observeForever(infos -> {
                            if (infos == null) return;
                            for (WorkInfo wi : infos) {
                                myLogD("WM unique 'bookload-queue' -> " + wi.getId() + " state=" + wi.getState() + " tags=" + wi.getTags());
                            }
                        });
                    });


            for (int i = 0; i < steps.size(); i++) {
                myLogD("step[" + i + "] id=" + steps.get(i).getId() + " cls=" + steps.get(i).getClass().getSimpleName());
            }

            Handler main = new Handler(Looper.getMainLooper());
            main.post(() -> {
                WorkManager.getInstance(ctx)
                        .getWorkInfosForUniqueWorkLiveData(uniqueName)
                        .observeForever(infos -> {
                            if (infos == null) return;
                            for (WorkInfo wi : infos) {
                                myLogD("WM unique '" + uniqueName + "' -> " + wi.getId() + " state=" + wi.getState() + " tags=" + wi.getTags());
                            }
                        });
                WorkManager.getInstance(ctx)
                        .getWorkInfosByTagLiveData("import:" + importId)
                        .observeForever(infos -> {
                            if (infos == null) return;
                            for (WorkInfo wi : infos) {
                                myLogD("WM tag 'import:" + importId + "' -> " + wi.getId() + " state=" + wi.getState());
                            }
                        });
            });

             */
        });
    }

    private static Constraints buildDownloadConstraints() {
        NetworkHelper.NetworkPolicyManual policy = Option.getNetworkPolicyManualDownload();
        return switch (policy) {
            case NETWORK_POLICY_UNMETERED -> new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresStorageNotLow(true)
                    .build();
            case NETWORK_POLICY_NOT_ROAMING -> new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_ROAMING)
                    .setRequiresStorageNotLow(true)
                    .build();
            default -> new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build();
        };
    }
}
