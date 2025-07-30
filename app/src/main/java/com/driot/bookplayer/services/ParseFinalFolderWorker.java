package com.driot.bookplayer.services;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.WorkerParameters;

import com.driot.bookplayer.utils.log.LoggingWorker;

public class ParseFinalFolderWorker extends LoggingWorker {
    public static final String TASK_NAME = "final step";

    public ParseFinalFolderWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {

        return null;
    }
}
