package com.driot.bookplayer.activities;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.imports.ImportJob;
import com.driot.bookplayer.imports.ImportUiMapper;
import com.driot.bookplayer.objects.TaskUiState;
import com.driot.bookplayer.utils.log.LoggingAndroidViewModel;

public class OngoingTaskViewModel extends LoggingAndroidViewModel {

    private final MediatorLiveData<String>  taskTitle     = new MediatorLiveData<>();
    private final MediatorLiveData<String>  progressText  = new MediatorLiveData<>();
    private final MediatorLiveData<Integer> progressPct   = new MediatorLiveData<>();
    private final MediatorLiveData<Boolean> running       = new MediatorLiveData<>();
    private final MediatorLiveData<Boolean> finished      = new MediatorLiveData<>();
    private final MediatorLiveData<Boolean> pauseAvail    = new MediatorLiveData<>();
    private final MediatorLiveData<Boolean> paused        = new MediatorLiveData<>();
    private final MediatorLiveData<String>  warningText   = new MediatorLiveData<>();
    private final MediatorLiveData<String>  errorText     = new MediatorLiveData<>();

    public OngoingTaskViewModel(@NonNull Application app) {
        super(app);

        taskTitle.setValue("");
        progressText.setValue("");
        progressPct.setValue(0);
        running.setValue(false);
        pauseAvail.setValue(false);
        paused.setValue(false);
        warningText.setValue(null);
        errorText.setValue(null);
        finished.setValue(false);

        AppDatabase db = AppDatabase.getInstance(app);
        LiveData<ImportJob> src = db.importJobDao()
                .observeCurrentActive(ImportJob.S_RUNNING, ImportJob.S_QUEUED, ImportJob.S_PAUSED);

        taskTitle.addSource(src, job -> mapToUi(job));
    }

    private void mapToUi(@Nullable ImportJob job) {
        TaskUiState s = (job == null) ? TaskUiState.idle() : ImportUiMapper.toUi(job);
        taskTitle.setValue(s.title);
        progressText.setValue(s.progressText);
        progressPct.setValue(s.progressPercent);
        running.setValue(s.running);
        pauseAvail.setValue(s.pauseAvailable);
        paused.setValue(s.paused);
        warningText.setValue(s.warningText);
        errorText.setValue(s.errorText);
        finished.setValue(s.finished);
    }

    public LiveData<String>  getTaskTitle()       { return taskTitle; }
    public LiveData<String>  getProgressText()    { return progressText; }
    public LiveData<Integer> getProgressPercent() { return progressPct; }
    public LiveData<Boolean> isTaskRunning()      { return running; }
    public LiveData<Boolean> isFinished()         { return finished; }
    public LiveData<Boolean> isPauseAvailable()   { return pauseAvail; }
    public LiveData<Boolean> isPaused()           { return paused; }
    public LiveData<String>  getWarningText()     { return warningText; }
    public LiveData<String>  getErrorText()       { return errorText; }

}
