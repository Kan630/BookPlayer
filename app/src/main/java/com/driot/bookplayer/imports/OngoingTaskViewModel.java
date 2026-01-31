package com.driot.bookplayer.imports;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.imports.BookCandidate;
import com.driot.bookplayer.utils.log.LoggingAndroidViewModel;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class OngoingTaskViewModel extends LoggingAndroidViewModel {

    private final MediatorLiveData<TaskUiState> ui = new MediatorLiveData<>();
    private final MassImportRepository massImportRepo;

    @Inject
    public OngoingTaskViewModel(@NonNull Application app, MassImportRepository massImportRepo) {
        super(app);
        this.massImportRepo = massImportRepo;
        ui.setValue(TaskUiState.idle());

        AppDatabase db = AppDatabase.getInstance(app);
        LiveData<ImportJob> dbSrc = db.importJobDao().observeUniqueJob();

        LiveData<Boolean> scanRunningSrc = massImportRepo.getIsScanning();
        LiveData<Boolean> scanFinishedSrc = massImportRepo.getIsScanFinished();
        LiveData<String> scanProgressSrc = massImportRepo.getProgressText();
        LiveData<List<BookCandidate>> scanCandidatesSrc = massImportRepo.getCandidates();

        ui.addSource(dbSrc, job -> updateUi(job, scanRunningSrc.getValue(), scanFinishedSrc.getValue(),
                scanProgressSrc.getValue(), scanCandidatesSrc.getValue()));
        ui.addSource(scanRunningSrc, isScanning -> updateUi(dbSrc.getValue(), isScanning, scanFinishedSrc.getValue(),
                scanProgressSrc.getValue(), scanCandidatesSrc.getValue()));
        ui.addSource(scanFinishedSrc, isFinished -> updateUi(dbSrc.getValue(), scanRunningSrc.getValue(), isFinished,
                scanProgressSrc.getValue(), scanCandidatesSrc.getValue()));
        ui.addSource(scanProgressSrc, progress -> updateUi(dbSrc.getValue(), scanRunningSrc.getValue(),
                scanFinishedSrc.getValue(), progress, scanCandidatesSrc.getValue()));
        ui.addSource(scanCandidatesSrc, candidates -> updateUi(dbSrc.getValue(), scanRunningSrc.getValue(),
                scanFinishedSrc.getValue(), scanProgressSrc.getValue(), candidates));
    }

    private void updateUi(ImportJob job, Boolean isScanning, Boolean isScanFinished, String scanProgress,
            List<BookCandidate> candidates) {
        if (Boolean.TRUE.equals(isScanning)) {
            // Priority 1: Scanning
            ui.setValue(TaskUiState.scanning(getApplication(), scanProgress != null ? scanProgress : "Scanning..."));
        } else if (Boolean.TRUE.equals(isScanFinished)) {
            // Priority 2: Scan Finished (waiting for user confirmation)
            int count = candidates != null ? candidates.size() : 0;
            if (count == 0) {
                // No book candidates: don't show the OngoingTaskFragment, consume state and go idle
                massImportRepo.consumeScanState();
                ui.setValue(TaskUiState.idle());
            } else {
                ui.setValue(TaskUiState.scanFinished(getApplication(), count));
            }
        } else if (job != null) {
            // Priority 3: Import Job - calculate position in queue
            calculateQueuePosition(job);
        } else {
            ui.setValue(TaskUiState.idle());
        }
    }

    private void calculateQueuePosition(ImportJob job) {
        // Simply read batch info directly from the job - no calculation needed!
        if ("MassImport".equals(job.sourceLocation) && job.batchIndex > 0 && job.batchTotal > 0) {
            ui.setValue(TaskUiState.from(job, job.batchIndex, job.batchTotal));
        } else {
            ui.setValue(TaskUiState.from(job, -1, -1));
        }
    }

    public LiveData<TaskUiState> getUi() {
        return ui;
    }
}