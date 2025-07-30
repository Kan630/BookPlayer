package com.driot.bookplayer.services;

import static com.driot.bookplayer.global.Pref.setLoadBookTaskState;

import android.content.Context;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkManager;

import com.driot.bookplayer.objects.LoadBookTaskState;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.utils.KanLogger;
import com.driot.bookplayer.utils.StorageHelper;

import java.util.ArrayList;
import java.util.List;

public class BookLoadingWorkLauncher {

    public static void launch(Context context) {
        boolean doDownload = false;
        boolean doCopy = false;
        boolean doSplit = false;
        boolean doUnzip = false;

        LoadBookTaskState bookState = Pref.getLoadBookTaskState(context);
        if (bookState == null) throw new IllegalStateException("No task bookState found in BookLoadingWorkLauncher");
        bookState.onGoingLoading = true;
        setLoadBookTaskState(context, bookState);

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

        bookState = Pref.getLoadBookTaskState(context, false);
        if (bookState == null) throw new IllegalStateException("No task bookState found in BookLoadingWorkLauncher 2");

        if (bookState.dynamicUri.toString().startsWith("http")) {
            doDownload = true;
        }

        if (bookState.optionCopy || bookState.sourceLocation.equals("cloud")) {
            doCopy = true;
        }

        if (bookState.fileExtension.equalsIgnoreCase("zip")) {
            doUnzip = true;
            doCopy = true;
        }
        if (bookState.fileExtension.equalsIgnoreCase("m4b") && bookState.optionSplit) {
            doSplit = true;
            doCopy = true;
        }
        if (doDownload) {
            doCopy = false;
        }



        if (doDownload) {

            bookState.downloadFileUrl = bookState.dynamicUri.toString();
            bookState.downloadDestinationFolder = StorageHelper.getDownloadFolderPath(context);
            bookState.onGoingLoading = true;
            setLoadBookTaskState(context, bookState);

            workChain.add(new OneTimeWorkRequest.Builder(DownloadRetryWorker.class).build());
        }

        if (doCopy) {
            workChain.add(new OneTimeWorkRequest.Builder(CopyFileWorker.class).build());
        }
        if (doUnzip) {
                    //.putString(UnzipWorker.KEY_ZIP_PATH, bookState.downloadedFilePath)
                    //.putString(UnzipWorker.KEY_DEST_PATH, bookState.futureFolderPath)
            workChain.add(new OneTimeWorkRequest.Builder(CopyFileWorker.class).build());
        }
        if (doSplit) {
                    //.putString(M4bSplitWorker.KEY_INPUT_PATH, bookState.downloadedFilePath)
                    //.putString(M4bSplitWorker.KEY_DEST_FOLDER, bookState.futureFolderPath)
            workChain.add(new OneTimeWorkRequest.Builder(CopyFileWorker.class).build());
        }


        workChain.add(new OneTimeWorkRequest.Builder(ParseFinalFolderWorker.class).build());


        if (!workChain.isEmpty()) {
            WorkContinuation continuation = WorkManager.getInstance(context).beginWith(workChain.get(0));
            for (int i = 1; i < workChain.size(); i++) {
                continuation = continuation.then(workChain.get(i));
            }
            continuation.enqueue();
        }
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
