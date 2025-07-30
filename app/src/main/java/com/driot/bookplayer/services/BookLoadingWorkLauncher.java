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
        LoadBookTaskState state = Pref.getLoadBookTaskState(context, false);
        if (state == null) throw new IllegalStateException("No task state found in BookLoadingWorkLauncher");
        state.onGoingLoading = true;
        setLoadBookTaskState(context, state);

        myLogD("....");
        myLogD("....");
        myLog("*********************************************************************************************************");
        myLog("*********************************************************************************************************");
        myLog("** title =            " + state.title + " **");
        myLog("---------------------------------------------------------------------------------------------------------");
        myLog("** futureFolderName = " + state.futureFolderName + " **");
        myLog("** futureFolderPath = " + state.futureFolderPath + " **");
        myLog("---------------------------------------------------------------------------------------------------------");
        myLog("** original uri =  " + state.originalUri + " **");
        myLog("** original type = " + state.originalType + " **");
        myLog("---------------------------------------------------------------------------------------------------------");
        myLog("** dynamic uri =  " + state.dynamicUri + " **");
        myLog("** dynamic type =  " + state.dynamicType + " **");
        myLog("---------------------------------------------------------------------------------------------------------");
        myLog("---------------------------------------------------------------------------------------------------------");
        myLog(state.toString().replace(",", "\n"));
        myLog("*********************************************************************************************************");
        myLog("*********************************************************************************************************");


        List<OneTimeWorkRequest> workChain = new ArrayList<>();


        state = Pref.getLoadBookTaskState(context, false);
        if (state == null) throw new IllegalStateException("No task state found in BookLoadingWorkLauncher 2");
        if (state.dynamicUri.toString().startsWith("http")) {

            state.downloadFileUrl = state.dynamicUri.toString();
            state.downloadDestinationFolder = StorageHelper.getDownloadFolderPath(context);
            state.onGoingLoading = true;
            setLoadBookTaskState(context, state);

            workChain.add(new OneTimeWorkRequest.Builder(DownloadRetryWorker.class).build());
        }

        if (state.optionCopy) {
            workChain.add(new OneTimeWorkRequest.Builder(CopyFileWorker.class).build());
        }

        if (state.fileExtension.equalsIgnoreCase("zip")) {

            Data input = new Data.Builder()
                    .putString(UnzipWorker.KEY_ZIP_PATH, state.downloadedFilePath)
                    .putString(UnzipWorker.KEY_DEST_PATH, state.futureFolderPath)
                    .build();
            OneTimeWorkRequest unzipWork = new OneTimeWorkRequest.Builder(UnzipWorker.class)
                    .setInputData(input)
                    .build();
            workChain.add(unzipWork);

        }

        if (state.optionSplit) {
            Data input = new Data.Builder()
                    .putString(M4bSplitWorker.KEY_INPUT_PATH, state.downloadedFilePath)
                    .putString(M4bSplitWorker.KEY_DEST_FOLDER, state.futureFolderPath)
                    .build();

            OneTimeWorkRequest m4bSplitWork = new OneTimeWorkRequest.Builder(M4bSplitWorker.class)
                    .setInputData(input)
                    .build();

            workChain.add(m4bSplitWork);
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
