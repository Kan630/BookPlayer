package com.driot.bookplayer.activities;

import android.app.Application;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.objects.LoadBookTaskState;
import com.driot.bookplayer.utils.log.LoggingAndroidViewModel;

public class OngoingTaskViewModel extends LoggingAndroidViewModel {

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
        myLogD("Constructor... Is main thread: " + (Looper.myLooper() == Looper.getMainLooper()));

        LoadBookTaskState state = Pref.getLoadBookTaskState(false);
        if (state != null && state.onGoingLoading) {
            myLogD("onGoingLoading = true");
            taskTitle.setValue(state.title);
            progressText.setValue(state.progressText);
            progressPercent.setValue(state.progressPercent);

            taskRunning.setValue(true);
            pauseAvailable.setValue(state.originalUri != null && state.originalUri.toString().startsWith("http"));
            isPaused.setValue(false);
            isFinished.setValue(false);
        }
    }

    // Expose LiveData
    public LiveData<String> getTaskTitle() { return taskTitle; }
    public LiveData<String> getProgressText() { return progressText; }
    public LiveData<Integer> getProgressPercent() { return progressPercent; }
    public LiveData<String> getErrorText() { return errorText; }
    public LiveData<String> getWarningText() { return warningText; }

    public LiveData<Boolean> isTaskRunning() { return taskRunning; }
    public LiveData<Boolean> isPauseAvailable() { return pauseAvailable; }
    public LiveData<Boolean> isPaused() { return isPaused; }
    public LiveData<Boolean> isFinished() { return isFinished; }

    // Public state update methods
    public void tellStart() {
        taskRunning.postValue(true);
    }

    public void tellProgress(String text, int percent) {
        progressText.postValue(text);
        progressPercent.postValue(percent);
    }

    public void tellProgressText(String text) {
        progressText.postValue(text);
    }

    public void tellWarning(String text) {
        String current = warningText.getValue();
        if (current == null || current.isEmpty()) {
            warningText.postValue(text);
        } else {
            warningText.postValue(current + "\n" + text);
        }
    }

    public void tellError(String text) {
        errorText.postValue(text);
        taskRunning.postValue(false);
        pauseAvailable.postValue(false);
        isFinished.postValue(true);
        progressText.postValue(getApplication().getString(R.string.Import_failed));
        progressPercent.postValue(100);
    }

    public void removePauseCapability() {
        pauseAvailable.postValue(false);
    }

    public void tellEnd() {
        taskRunning.postValue(false);
        pauseAvailable.postValue(false);
        isFinished.postValue(true);
        progressText.postValue(getApplication().getString(R.string.Finished));
        progressPercent.postValue(100);
    }
}
