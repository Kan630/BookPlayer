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
    private final MutableLiveData<String> errorText = new MutableLiveData<>("");
    private final MutableLiveData<String> warningText = new MutableLiveData<>("");

    private final MutableLiveData<Boolean> taskRunning = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> pauseAvailable = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isPaused = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isFinished = new MutableLiveData<>(false);


    public OngoingTaskViewModel(@NonNull Application application) {
        super(application);
        LoadBookTaskState state = Pref.getLoadBookTaskState(false);
        if (state != null && state.onGoingLoading) {
            taskTitle.postValue(state.title);
            progressText.postValue(state.progressText);
            progressPercent.postValue(state.progressPercent);

            taskRunning.postValue(true);
            pauseAvailable.postValue(state.originalUri != null && state.originalUri.toString().startsWith("http"));
            isPaused.postValue(false);
            isFinished.postValue(false);
        }
    }

    public LiveData<String> getTaskTitle() { return taskTitle; }
    public LiveData<String> getProgressText() { return progressText; }
    public LiveData<Integer> getProgressPercent() { return progressPercent; }
    public LiveData<String> getErrorText() { return errorText; }
    public LiveData<String> getWarningText() { return warningText; }

    public LiveData<Boolean> isTaskRunning() { return taskRunning; }
    public LiveData<Boolean> isPauseAvailable() { return pauseAvailable; }
    public LiveData<Boolean> isPaused() { return isPaused; }
    public LiveData<Boolean> isFinished() { return isFinished; }

    public void setPauseState(boolean available, boolean paused) {
        pauseAvailable.postValue(available);
        isPaused.postValue(paused);
    }

    public void updateTitle(String title) {
        taskTitle.postValue(title);
        taskRunning.postValue(true);
    }

    public void updateProgressFull(String text, int percent) {
        progressText.postValue(text);
        progressPercent.postValue(percent);
    }

    public void updateProgressText(String text) {
        progressText.postValue(text);
    }
    public void tellWarning(String text) { warningText.postValue(text); }
    public void tellError(String text) {
        errorText.postValue(text);
    }
    public void removePauseCapability() {pauseAvailable.postValue(false);}


    public void tellEnd() {
        taskRunning.postValue(false);
        pauseAvailable.postValue(false);
        isFinished.postValue(false);
        progressText.postValue("finished");
        progressPercent.postValue(100);
    }
}

