package com.driot.bookplayer.imports;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.utils.log.LoggingAndroidViewModel;

public class OngoingTaskViewModel extends LoggingAndroidViewModel {

    private final MediatorLiveData<TaskUiState> ui = new MediatorLiveData<>();

    public OngoingTaskViewModel(@NonNull Application app) {
        super(app);
        ui.setValue(TaskUiState.idle());

        AppDatabase db = AppDatabase.getInstance(app);
        LiveData<ImportJob> src = db.importJobDao().observeUniqueJob();

        ui.addSource(src, job -> {
            //if (job == null) return; // 👈 no "idle" emission
            //TaskUiState next = TaskUiState.from(job);
            TaskUiState next = (job == null) ? TaskUiState.idle() : TaskUiState.from(job);
            TaskUiState prev = ui.getValue();
            if (!next.equals(prev)) ui.setValue(next); // distinctUntilChanged
        });
    }

    public LiveData<TaskUiState> getUi() { return ui; }
}