package com.driot.bookplayer.imports;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.imports.BookCandidate;
import com.driot.bookplayer.utils.log.LoggingAndroidViewModel;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class OngoingTaskViewModel extends LoggingAndroidViewModel {

    private final MediatorLiveData<TaskUiState> ui = new MediatorLiveData<>();
    private final Executor executor = Executors.newSingleThreadExecutor();

    @Inject
    public OngoingTaskViewModel(@NonNull Application app, MassImportRepository massImportRepo) {
        super(app);
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
            ui.setValue(TaskUiState.scanFinished(getApplication(), count));
        } else if (job != null) {
            // Priority 3: Import Job - calculate position in queue
            calculateQueuePosition(job);
        } else {
            ui.setValue(TaskUiState.idle());
        }
    }

    private void calculateQueuePosition(ImportJob job) {
        executor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(getApplication());
                
                if ("MassImport".equals(job.sourceLocation)) {
                    long timeWindow = 60_000; // 60 seconds
                    long batchStartTime = job.createdAt - timeWindow;
                    long batchEndTime = job.createdAt; // Only include jobs created up to current job
                    
                    List<ImportJob> allJobs = db.importJobDao().getAll();
                    if (allJobs == null || allJobs.isEmpty()) {
                        ui.postValue(TaskUiState.from(job, -1, -1));
                        return;
                    }
                    
                    List<ImportJob> batchJobs = new java.util.ArrayList<>();
                    int allMassImportCount = 0;
                    for (ImportJob j : allJobs) {
                        if ("MassImport".equals(j.sourceLocation)) {
                            allMassImportCount++;
                            if (j.createdAt >= batchStartTime && j.createdAt <= batchEndTime) {
                                batchJobs.add(j);
                            }
                        }
                    }
                    
                    myLogD("calculateQueuePosition: Found " + allMassImportCount + " total MassImport jobs, " + 
                           batchJobs.size() + " in time window [" + batchStartTime + " to " + batchEndTime + "]");
                    
                    if (batchJobs.isEmpty()) {
                        ui.postValue(TaskUiState.from(job, -1, -1));
                        return;
                    }
                    
                    batchJobs.sort((a, b) -> Long.compare(a.createdAt, b.createdAt));
                    
                    int currentPosition = -1;
                    for (int i = 0; i < batchJobs.size(); i++) {
                        ImportJob j = batchJobs.get(i);
                        myLogD("calculateQueuePosition: batchJobs[" + i + "] = " + j.title + 
                               " (importId=" + j.importId + ", createdAt=" + j.createdAt + ")");
                        if (j.importId.equals(job.importId)) {
                            currentPosition = i; // 0-based
                            myLogD("calculateQueuePosition: Found current job at index " + i);
                            break;
                        }
                    }
                    
                    if (currentPosition < 0) {
                        myLogW("calculateQueuePosition: Current job not found in batch. job.importId=" + job.importId + 
                               ", batchJobs.size()=" + batchJobs.size());
                        ui.postValue(TaskUiState.from(job, -1, -1));
                        return;
                    }
                    
                    int totalCount = batchJobs.size();
                    // Convert to 1-based for display
                    currentPosition = currentPosition + 1;
                    
                    myLogD("calculateQueuePosition: FINAL - job=" + job.title + 
                           ", totalCount=" + totalCount +
                           ", currentPosition(0-based)=" + (currentPosition - 1) +
                           ", currentPosition(1-based)=" + currentPosition +
                           ", result=" + currentPosition + "/" + totalCount);
                    
                    ui.postValue(TaskUiState.from(job, currentPosition, totalCount));
                } else {
                    ui.postValue(TaskUiState.from(job, -1, -1));
                }
            } catch (Exception e) {
                myLogEE(e, "Error calculating queue position");
                ui.postValue(TaskUiState.from(job, -1, -1));
            }
        });
    }

    public LiveData<TaskUiState> getUi() {
        return ui;
    }
}