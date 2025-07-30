package com.driot.bookplayer.activities;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.objects.LoadBookTaskState;
import com.driot.bookplayer.utils.log.LoggingViewModel;

public class OngoingTaskViewModel extends LoggingViewModel {
    private final MutableLiveData<String> taskTitle = new MutableLiveData<>("");
    private final MutableLiveData<String> progressText = new MutableLiveData<>("");
    private final MutableLiveData<Integer> progressPercent = new MutableLiveData<>(0);
    private final MutableLiveData<Boolean> taskRunning = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> pauseAvailable = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isPaused = new MutableLiveData<>(false);


    public OngoingTaskViewModel(@NonNull Application application) {
        super(application);
        LoadBookTaskState state = Pref.getLoadBookTaskState(application, false);
        if (state != null && state.onGoingLoading) {
            taskTitle.setValue(state.title);
            progressText.setValue(state.progressText);
            progressPercent.setValue(state.progressPercent);
            taskRunning.setValue(true);
            pauseAvailable.setValue(state.originalUri != null && state.originalUri.toString().startsWith("http"));
            isPaused.setValue(state.isLoadingPaused);
        }
    }

    public LiveData<String> getTaskTitle() { return taskTitle; }
    public LiveData<String> getProgressText() { return progressText; }
    public LiveData<Integer> getProgressPercent() { return progressPercent; }
    public LiveData<Boolean> isTaskRunning() { return taskRunning; }
    public LiveData<Boolean> isPauseAvailable() { return pauseAvailable; }
    public LiveData<Boolean> isPaused() { return isPaused; }

    public void setPauseState(boolean available, boolean paused) {
        pauseAvailable.setValue(available);
        isPaused.setValue(paused);
    }

    public void updateTitle(String title) {
        taskTitle.setValue(title);
        taskRunning.setValue(true);
    }

    public void updateProgressFull(String text, int percent) {
        progressText.setValue(text);
        progressPercent.setValue(percent);
    }

    public void updateProgressText(String text) {
        progressText.setValue(text);
    }

    //TODO
    public void tellWarning(String text) {
        progressText.setValue("TODO WARNING: " + text);
    }
    public void tellError(String text) {
        progressText.setValue("TODO ERROR: " + text);
    }


    public void endTask() {
        taskRunning.setValue(false);
        taskTitle.setValue("");
        progressText.setValue("");
        progressPercent.setValue(0);
    }
}

