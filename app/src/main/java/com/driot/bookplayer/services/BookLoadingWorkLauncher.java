package com.driot.bookplayer.services;

import static com.driot.bookplayer.global.Pref.setLoadBookTaskState;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkManager;

import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.objects.LoadBookTaskState;
import com.driot.bookplayer.utils.KanLogger;

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
        myLog("---------------------------------------------------------------------------------------------------------");
        myLog(bookState.toString().replace(",", "\n"));
        myLog("*********************************************************************************************************");
        myLog("*********************************************************************************************************");

        FirebaseAnalyticsHelper.tellAnalyticsWork(String.valueOf(bookState.originalUri));

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

        bookState.doDownload = doDownload;
        bookState.doCopy = doCopy;
        bookState.doSplitM4b = doSplitM4b;
        bookState.doSplitEbook = doSplitEbook;
        bookState.doUnzip = doUnzip;
        setLoadBookTaskState(bookState);

        if (doDownload) {

            bookState.downloadFileUrl = bookState.dynamicUri.toString();
            bookState.downloadDestinationFolder = StorageHelper.getDownloadFolderPath(context);
            bookState.onGoingLoading = true;
            setLoadBookTaskState(bookState);

            // CONNECTED is enough for manual; if you want stricter auto policy use UNMETERED when you enqueue “auto” jobs.
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build();

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
            setLoadBookTaskState(bookState);
            myLog("downloadWorkId = " + downloadWorkId);

            List<OneTimeWorkRequest> postChain = buildPostDownloadChain(bookState);

            String uniqueChainName = "bookload:" + (bookState.futureFolderName != null ? bookState.futureFolderName : String.valueOf(downloadWork.getId()));
            bookState.uniqueChainName = uniqueChainName;
            setLoadBookTaskState(bookState);
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

        workChain.add(new OneTimeWorkRequest.Builder(ParseFinalFolderWorker.class).addTag(BOOK_LOADING_WORKERS).build());

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
        list.add(new OneTimeWorkRequest.Builder(ParseFinalFolderWorker.class).addTag(BOOK_LOADING_WORKERS).build());
        return list;
    }

    ////////////////////////////////////////////////////////
    private static final String TAG = "BookLoadingWorkLauncher";
    private static void myLog(String str) { KanLogger.myLog(TAG, str); }
    private static void myLogD(String str) { KanLogger.myLogD(TAG, str); }
    private static void myLogI(String str) { KanLogger.myLogI(TAG, str); }
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
    private static void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, TAG, str); }
    private static void myToastEE(Throwable t, String str) { KanLogger.myToastEE(t, TAG, str); }

}
