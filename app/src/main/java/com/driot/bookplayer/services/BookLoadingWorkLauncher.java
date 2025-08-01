package com.driot.bookplayer.services;

import static com.driot.bookplayer.global.Pref.setLoadBookTaskState;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkManager;

import com.driot.bookplayer.objects.LoadBookTaskState;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.objects.TaskStateManager;
import com.driot.bookplayer.utils.KanLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class BookLoadingWorkLauncher {

    public static final String BOOK_LOADING_WORKERS = "BookLoadingWorkers";

    public static void launch(Context context) {
        boolean doDownload = false;
        boolean doCopy = false;
        boolean doSplit = false;
        boolean doUnzip = false;


        TaskStateManager.tellStart();

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

        List<OneTimeWorkRequest> workChain = new ArrayList<>();

        bookState = Pref.getLoadBookTaskState(false);
        if (bookState == null) throw new IllegalStateException("No task bookState found in BookLoadingWorkLauncher 2");

        class StepInfo {
            public final int weight;
            public final String label;

            public StepInfo(int weight, String label) {
                this.weight = weight;
                this.label = label;
            }
        }
        Map<String, StepInfo> stepMap = new HashMap<>();
        stepMap.put("download", new StepInfo(20, "Download"));
        stepMap.put("unzip", new StepInfo(7, "Unzip"));
        stepMap.put("split", new StepInfo(7, "m4b Split"));
        stepMap.put("copy", new StepInfo(3, "Copy"));
        stepMap.put("scan", new StepInfo(2, "Scan audio"));



        if (bookState.dynamicUri.toString().startsWith("http")) {
            doDownload = true;
        }
        if (bookState.optionCopy || bookState.sourceLocation.equals("cloud")) {
            doCopy = true;
        }
        if (bookState.fileExtension!=null && bookState.fileExtension.equalsIgnoreCase("zip")) {
            doUnzip = true;
            doCopy = true;
        }
        if (bookState.fileExtension!=null && bookState.fileExtension.equalsIgnoreCase("m4b") && bookState.optionSplit) {
            doSplit = true;
            doCopy = true;
        }
        if (doDownload) {
            doCopy = false;
        }

        bookState.doDownload = doDownload;
        bookState.doCopy = doCopy;
        bookState.doSplit = doSplit;
        bookState.doUnzip = doUnzip;
        setLoadBookTaskState(bookState);



        if (doDownload) {

            bookState.downloadFileUrl = bookState.dynamicUri.toString();
            bookState.downloadDestinationFolder = StorageHelper.getDownloadFolderPath(context);
            bookState.onGoingLoading = true;
            setLoadBookTaskState(bookState);

            workChain.add(new OneTimeWorkRequest.Builder(DownloadRetryWorker.class)
                            .setBackoffCriteria(
                            BackoffPolicy.EXPONENTIAL, // or BackoffPolicy.EXPONENTIAL
                            30, TimeUnit.SECONDS       // initial wait time before retry
                    )
                    .addTag(BOOK_LOADING_WORKERS)
                    .build());
        }

        if (doCopy) {workChain.add(new OneTimeWorkRequest.Builder(CopyFileWorker.class).addTag(BOOK_LOADING_WORKERS).build());}
        if (doUnzip) {workChain.add(new OneTimeWorkRequest.Builder(UnzipWorker.class).addTag(BOOK_LOADING_WORKERS).build());}
        if (doSplit) {workChain.add(new OneTimeWorkRequest.Builder(M4bSplitWorker.class).addTag(BOOK_LOADING_WORKERS).build());}
        workChain.add(new OneTimeWorkRequest.Builder(ParseFinalFolderWorker.class).addTag(BOOK_LOADING_WORKERS).build());


        WorkContinuation continuation = WorkManager.getInstance(context).beginWith(workChain.get(0));
        for (int i = 1; i < workChain.size(); i++) {
            myLogD("adding continuation " + i + "/" + workChain.size() + " to chain" + workChain.get(i).toString());
            continuation = continuation.then(workChain.get(i));
        }
        continuation.enqueue();
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
