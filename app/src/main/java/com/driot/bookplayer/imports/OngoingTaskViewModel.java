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
                
                // For mass imports, count all jobs from the same batch (same sourceLocation and similar createdAt)
                // This gives us the original total count, not just active jobs
                if ("MassImport".equals(job.sourceLocation)) {
                    // Get all jobs from MassImport created around the same time (within 10 seconds)
                    // This identifies jobs from the same mass import batch
                    long timeWindow = 10_000; // 10 seconds
                    long batchStartTime = job.createdAt - timeWindow;
                    long batchEndTime = job.createdAt + timeWindow;
                    
                    List<ImportJob> allJobs = db.importJobDao().getAll();
                    if (allJobs == null || allJobs.isEmpty()) {
                        ui.postValue(TaskUiState.from(job, -1, -1));
                        return;
                    }
                    
                    // Filter to jobs from the same mass import batch
                    List<ImportJob> batchJobs = new java.util.ArrayList<>();
                    for (ImportJob j : allJobs) {
                        if ("MassImport".equals(j.sourceLocation) &&
                            j.createdAt >= batchStartTime && j.createdAt <= batchEndTime) {
                            batchJobs.add(j);
                        }
                    }
                    
                    if (batchJobs.isEmpty()) {
                        ui.postValue(TaskUiState.from(job, -1, -1));
                        return;
                    }
                    
                    // Sort by creation time to get queue order
                    batchJobs.sort((a, b) -> Long.compare(a.createdAt, b.createdAt));
                    
                    int totalCount = batchJobs.size();
                    int currentPosition = -1;
                    
                    // Find position of current job in the batch
                    for (int i = 0; i < batchJobs.size(); i++) {
                        if (batchJobs.get(i).importId.equals(job.importId)) {
                            currentPosition = i;
                            break;
                        }
                    }
                    
                    // Position is simply the order in the batch (1-based for display)
                    // e.g., if this is the 3rd job created, it's position 3 out of totalCount
                    if (currentPosition >= 0) {
                        currentPosition = currentPosition + 1; // Convert to 1-based for display
                    } else {
                        // Job not found in batch - shouldn't happen, but fallback
                        ui.postValue(TaskUiState.from(job, -1, -1));
                        return;
                    }
                    
                    ui.postValue(TaskUiState.from(job, currentPosition, totalCount));
                } else {
                    // Not a mass import - no counter
                    ui.postValue(TaskUiState.from(job, -1, -1));
                }
            } catch (Exception e) {
                myLogEE(e, "Error calculating queue position");
                // Fallback: use default (no position info)
                ui.postValue(TaskUiState.from(job, -1, -1));
            }
        });
    }

    public LiveData<TaskUiState> getUi() {
        return ui;
    }
}