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
import androidx.work.WorkContinuation;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.objects.LoadBookTaskState;
import com.driot.bookplayer.services.CopyFileWorker;
import com.driot.bookplayer.services.DownloadWorker;
import com.driot.bookplayer.services.EbookSplitWorker;
import com.driot.bookplayer.services.FinalParseFolderWorker;
import com.driot.bookplayer.services.M4bSplitWorker;
import com.driot.bookplayer.services.UnzipWorker;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class BookLoadingWorkLauncher {

    public static final String BOOK_LOADING_WORKERS = "BOOK_LOADING_WORKERS";

    public static void launch(Context context) {
        boolean doDownload = false;
        boolean doCopy = false;
        boolean doSplitM4b = false;
        boolean doSplitEbook = false;
        boolean doUnzip = false;

        LoadBookTaskState bookState = Pref.getLoadBookTaskState();
        if (bookState == null) throw new IllegalStateException("No task bookState found in BookLoadingWorkLauncher");

        myLogD("....");
        myLogD("....");
        myLog("*********************************************************************************************************");
        myLog("*********************************************************************************************************");
        myLog("** title =            " + bookState.title + " **");
        myLog("---------------------------------------------------------------------------------------------------------");
        myLog("** futureFolderName = " + bookState.futureFolderName + " **");
        myLog("** futureFolderPath = " + bookState.futureFolderPath + " **");
        myLog("---------------------------------------------------------------------------------------------------------");
        myLog("** original uri =  " + bookState.originalUri + " **");
        myLog("** original type = " + bookState.originalType + " **");
        myLog("---------------------------------------------------------------------------------------------------------");
        myLog("** dynamic uri =  " + bookState.dynamicUri + " **");
        myLog("** dynamic type =  " + bookState.dynamicType + " **");
        myLog("---------------------------------------------------------------------------------------------------------");
        myLog("** extension =  " + bookState.fileExtension + " **");
        myLog("** playType =  " + bookState.playType + " **");
        myLog("---------------------------------------------------------------------------------------------------------");
        //myLog("---------------------------------------------------------------------------------------------------------");
        //myLog(bookState.toString().replace(",", "\n"));
        myLog("*********************************************************************************************************");
        myLog("*********************************************************************************************************");

        if (bookState.dynamicUri.toString().startsWith("http")) {
            myLogD("http => download");
            doDownload = true;
        }
        if (bookState.optionCopy || bookState.sourceLocation.equals("cloud")) {
            myLogD("copy");
            doCopy = true;
        }
        if (bookState.fileExtension!=null && bookState.fileExtension.equalsIgnoreCase("zip")) {
            myLogD("ZIP => unzip + copy");
            doUnzip = true;
            doCopy = true;
        }
        if (bookState.fileExtension!=null && bookState.fileExtension.equalsIgnoreCase("m4b") && bookState.optionSplit) {
            myLogD("m4b to split");
            doSplitM4b = true;
            doCopy = true;
        }
        if (bookState.fileExtension!=null && bookState.fileExtension.equalsIgnoreCase("epub")) {
            myLogD("epub");
            doSplitEbook = true;
            doCopy = true;
        } else if (bookState.fileExtension!=null && bookState.fileExtension.equalsIgnoreCase("fb2")) {
            myLogD("fb2");
            doSplitEbook = true;
            doCopy = true;
        } else if (bookState.fileExtension!=null && bookState.fileExtension.equalsIgnoreCase("odt")) {
            myLogD("odt");
            doSplitEbook = true;
            doCopy = true;
        }
        if (doDownload) {
            if  (doCopy) {
                doCopy = false;
                myLogW("doCopy should be false, reset");
            }
        }

        FirebaseAnalyticsHelper.tellAnalyticsWork(String.valueOf(bookState.originalUri), bookState.fileExtension, doDownload);
        FirebaseAnalyticsHelper.setCustomKeyCrashlytics("worker_originalType", bookState.originalType);
        FirebaseAnalyticsHelper.setCustomKeyCrashlytics("worker_extension", bookState.fileExtension);
        FirebaseAnalyticsHelper.setCustomKeyCrashlytics("worker_sourceLocation", bookState.sourceLocation);
        FirebaseAnalyticsHelper.setCustomKeyCrashlytics("worker_playType", bookState.playType);
        FirebaseAnalyticsHelper.setCustomKeyCrashlytics("worker_originalUri", String.valueOf(bookState.originalUri));

        bookState.doDownload = doDownload;
        bookState.doCopy = doCopy;
        bookState.doSplitM4b = doSplitM4b;
        bookState.doSplitEbook = doSplitEbook;
        bookState.doUnzip = doUnzip;
        Pref.setLoadBookTaskState(bookState);

        if (doDownload) {

            bookState.downloadFileUrl = bookState.dynamicUri.toString();
            bookState.downloadDestinationFolder = StorageHelper.getDownloadFolderPath(context);
            bookState.onGoingLoading = true;
            Pref.setLoadBookTaskState(bookState);

            Constraints constraints;
            NetworkHelper.NetworkPolicyManual policy = Option.getNetworkPolicyManualDownload();

            com.driot.bookplayer.helpers.NetworkHelper.logCurrentNetworkState(context);

            switch (policy) {
                case NETWORK_POLICY_NEVER_ASK:
                    // Manual downloads: just require a connection, not necessarily Wi-Fi
                    constraints = new Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .setRequiresStorageNotLow(true)
                            .build();
                    break;

                case NETWORK_POLICY_UNMETERED:
                    // Allow only on unmetered networks (Wi-Fi, Ethernet, etc.)
                    constraints = new Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.UNMETERED)
                            .setRequiresStorageNotLow(true)
                            .build();
                    break;

                case NETWORK_POLICY_NOT_ROAMING:
                    // Allow only on unmetered networks (Wi-Fi, Ethernet, etc.)
                    constraints = new Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.NOT_ROAMING)
                            .setRequiresStorageNotLow(true)
                            .build();
                    break;

                default:
                    // fallback, just in case
                    constraints = new Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .setRequiresStorageNotLow(true)
                            .build();
                    break;
            }

            // --- Input data for the Foreground DownloadWorker ---
            Data input = new Data.Builder()
                    .putString(DownloadWorker.KEY_URL, bookState.downloadFileUrl)
                    .putString(DownloadWorker.KEY_DEST_FOLDER, bookState.downloadDestinationFolder)
                    .putString(DownloadWorker.KEY_TITLE, bookState.title)
                    .putBoolean(DownloadWorker.KEY_IS_MANUAL, true) // keep true if you still apply manual policy inside the worker
                    .build();

            OneTimeWorkRequest downloadWork = new OneTimeWorkRequest.Builder(DownloadWorker.class)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .setInputData(input)
                    .addTag(BookLoadingWorkLauncher.BOOK_LOADING_WORKERS) // existing pipeline tag
                    .addTag(DownloadWorker.TAG_DOWNLOAD)                  // << new: tag ONLY the download step
                    .build();

            UUID downloadWorkId = downloadWork.getId();
            bookState.setDownloadWorkId(downloadWorkId);
            Pref.setLoadBookTaskState(bookState);
            myLog("downloadWorkId = " + downloadWorkId);

            List<OneTimeWorkRequest> postChain = buildPostDownloadChain(bookState);

            String uniqueChainName = "bookload:" + (bookState.futureFolderName != null ? bookState.futureFolderName : String.valueOf(downloadWork.getId()));
            bookState.uniqueChainName = uniqueChainName;
            Pref.setLoadBookTaskState(bookState);
            WorkContinuation workContinuation = WorkManager.getInstance(context)
                    .beginUniqueWork(uniqueChainName, ExistingWorkPolicy.REPLACE, downloadWork);

            for (OneTimeWorkRequest step : postChain) {
                workContinuation = workContinuation.then(step);
            }
            workContinuation.enqueue();
            FirebaseAnalyticsHelper.tellAnalyticsManualDownload(bookState.downloadFileUrl, bookState.downloadDestinationFolder);
            return;
        }

        launchAfterDownload(context);
    }

    public static void launchAfterDownload(Context context) {
        LoadBookTaskState bookState = Pref.getLoadBookTaskState();
        if (bookState == null) throw new IllegalStateException("No task bookState found in BookLoadingWorkLauncher");

        List<OneTimeWorkRequest> workChain = new ArrayList<>();

        if (bookState.doCopy) {workChain.add(new OneTimeWorkRequest.Builder(CopyFileWorker.class).addTag(BOOK_LOADING_WORKERS).build());}
        if (bookState.doUnzip) {workChain.add(new OneTimeWorkRequest.Builder(UnzipWorker.class).addTag(BOOK_LOADING_WORKERS).build());}
        if (bookState.doSplitM4b) {workChain.add(new OneTimeWorkRequest.Builder(M4bSplitWorker.class).addTag(BOOK_LOADING_WORKERS).build());}
        if (bookState.doSplitEbook) { workChain.add(new OneTimeWorkRequest.Builder(EbookSplitWorker.class).addTag(BOOK_LOADING_WORKERS).build()); }

        workChain.add(new OneTimeWorkRequest.Builder(FinalParseFolderWorker.class).addTag(BOOK_LOADING_WORKERS).build());

        WorkContinuation workContinuation    = WorkManager.getInstance(context).beginWith(workChain.get(0));
        for (int i = 1; i < workChain.size(); i++) {
            myLogD("adding continuation " + i + "/" + workChain.size() + " to chain" + workChain.get(i).toString());
            workContinuation = workContinuation.then(workChain.get(i));
        }
        workContinuation.enqueue();
    }

    // Build the post-download chain so we can reuse both in the download path and the “no-download” path
    public static List<OneTimeWorkRequest> buildPostDownloadChain(LoadBookTaskState bookState) {
        List<OneTimeWorkRequest> list = new ArrayList<>();
        if (bookState.doCopy) { list.add(new OneTimeWorkRequest.Builder(CopyFileWorker.class).addTag(BOOK_LOADING_WORKERS).build()); }
        if (bookState.doUnzip) { list.add(new OneTimeWorkRequest.Builder(UnzipWorker.class).addTag(BOOK_LOADING_WORKERS).build()); }
        if (bookState.doSplitM4b) { list.add(new OneTimeWorkRequest.Builder(M4bSplitWorker.class).addTag(BOOK_LOADING_WORKERS).build()); }
        if (bookState.doSplitEbook) { list.add(new OneTimeWorkRequest.Builder(EbookSplitWorker.class).addTag(BOOK_LOADING_WORKERS).build()); }
        list.add(new OneTimeWorkRequest.Builder(FinalParseFolderWorker.class).addTag(BOOK_LOADING_WORKERS).build());
        return list;
    }

    /// ---------------------------------------------------
    ///  NEW
    /// ---------------------------------------------------
    public static void enqueueOneNoDownload(Context ctx, LoadBookTaskState s, boolean sequential) {
        if (s == null) throw new IllegalStateException("No task bookState found for BookLoadingWorkLauncher");
        myLogD("enqueueOneNoDownload : " + s.title);
        String importId = (s.futureFolderName != null ? s.futureFolderName : "book") + ":" + UUID.randomUUID();

        boolean doDownload = false;
        boolean doCopy = false;
        boolean doSplitM4b = false;
        boolean doSplitEbook = false;
        boolean doUnzip = false;

        myLogD("....");
        myLogD("....V2");
        myLog("*********************************************************************************************************");
        myLog("*********************************************************************************************************");
        myLog("** title =            " + s.title + " **");
        myLog("---------------------------------------------------------------------------------------------------------");
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
        if (s.fileExtension!=null && s.fileExtension.equalsIgnoreCase("zip")) {
            myLogD("ZIP => unzip + copy");
            doUnzip = true;
            doCopy = true;
        }
        if (s.fileExtension!=null && s.fileExtension.equalsIgnoreCase("m4b") && s.optionSplit) {
            myLogD("m4b to split");
            doSplitM4b = true;
            doCopy = true;
        }
        if (s.fileExtension!=null && s.fileExtension.equalsIgnoreCase("epub")) {
            myLogD("epub");
            doSplitEbook = true;
            doCopy = true;
        } else if (s.fileExtension!=null && s.fileExtension.equalsIgnoreCase("fb2")) {
            myLogD("fb2");
            doSplitEbook = true;
            doCopy = true;
        } else if (s.fileExtension!=null && s.fileExtension.equalsIgnoreCase("odt")) {
            myLogD("odt");
            doSplitEbook = true;
            doCopy = true;
        }
        if (doDownload) {
            if  (doCopy) {
                doCopy = false;
                myLogW("doCopy should be false, reset");
            }
        }

        FirebaseAnalyticsHelper.tellAnalyticsWork(String.valueOf(s.originalUri), s.fileExtension, doDownload);
        FirebaseAnalyticsHelper.setCustomKeyCrashlytics("worker_originalType", s.originalType);
        FirebaseAnalyticsHelper.setCustomKeyCrashlytics("worker_extension", s.fileExtension);
        FirebaseAnalyticsHelper.setCustomKeyCrashlytics("worker_sourceLocation", s.sourceLocation);
        FirebaseAnalyticsHelper.setCustomKeyCrashlytics("worker_playType", s.playType);
        FirebaseAnalyticsHelper.setCustomKeyCrashlytics("worker_originalUri", String.valueOf(s.originalUri));
        FirebaseAnalyticsHelper.setCustomKeyCrashlytics("worker_doDownload", String.valueOf(doDownload));
        FirebaseAnalyticsHelper.setCustomKeyCrashlytics("worker_doCopy", String.valueOf(doCopy));
        FirebaseAnalyticsHelper.setCustomKeyCrashlytics("worker_doUnzip", String.valueOf(doUnzip));
        FirebaseAnalyticsHelper.setCustomKeyCrashlytics("worker_doSplitM4b", String.valueOf(doSplitM4b));
        FirebaseAnalyticsHelper.setCustomKeyCrashlytics("worker_doSplitEbook", String.valueOf(doSplitEbook));

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
        j.doUnzip = doUnzip;
        j.doCopy = doCopy;

        /*
        j.doCopy = doCopy || doUnzip || doSplitM4b || doSplitEbook;
        j.doUnzip = doUnzip;
        j.doSplitM4b = doSplitM4b;
        j.doSplitEbook = doSplitEbook;
        j.doDownload = false;
         */

        j.status = ImportJob.S_RUNNING; //.S_QUEUED;
        j.createdAt = j.updatedAt = System.currentTimeMillis();

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
                    .build());
        }

        ImportJobRepository repo = new ImportJobRepository(ctx);
        repo.upsert(j);

        if (j.doCopy) steps.add(new OneTimeWorkRequest.Builder(CopyFileWorker.class)
                .setInputData(common).addTag(BOOK_LOADING_WORKERS).addTag("import:" + importId).build());
        if (j.doUnzip) steps.add(new OneTimeWorkRequest.Builder(UnzipWorker.class)
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
        ExistingWorkPolicy policy = sequential ? ExistingWorkPolicy.APPEND : ExistingWorkPolicy.REPLACE;
        myLogD("ExistingWorkPolicy = " + policy);

        WorkContinuation cont = wm.beginUniqueWork(uniqueName, policy, steps.get(0));
        for (int i = 1; i < steps.size(); i++) cont = cont.then(steps.get(i));
        cont.enqueue();


// some logging
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
