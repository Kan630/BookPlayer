package com.driot.bookplayer.imports;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import com.driot.bookplayer.utils.log.LoggingAndroidViewModel;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class MassImportViewModel extends LoggingAndroidViewModel {

    private final MassImportRepository repository;

    @Inject
    public MassImportViewModel(@NonNull Application application, MassImportRepository repository) {
        super(application);
        this.repository = repository;
    }

    public LiveData<List<BookCandidate>> getCandidates() {
        return repository.getCandidates();
    }

    public LiveData<String> getProgressText() {
        return repository.getProgressText();
    }

    public LiveData<Boolean> getIsScanning() {
        return repository.getIsScanning();
    }

    public void startScan(Uri rootUri) {
        repository.startScan(rootUri);
    }

    public void cancelScan() {
        repository.cancelScan();
    }

    public void consumeScanState() {
        repository.consumeScanState();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        // Do NOT cancel scan on clear, to allow background scanning
        // repository.cancelScan();
    }
}
