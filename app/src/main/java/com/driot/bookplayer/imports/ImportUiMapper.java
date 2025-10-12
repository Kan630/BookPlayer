package com.driot.bookplayer.imports;

import androidx.annotation.NonNull;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

public final class ImportUiMapper {
    private ImportUiMapper() {
    }

    @NonNull
    public static TaskUiState toUi(@NonNull ImportJob j) {
        boolean running = ImportJob.S_RUNNING.equals(j.status)
                || ImportJob.S_QUEUED.equals(j.status)
                || ImportJob.S_PAUSED.equals(j.status);

        TaskUiState base = TaskUiState.idle();

        String title = j.title != null ? j.title : "";
        String pText = j.progressText != null ? j.progressText :
                (j.currentOperation != null ? j.currentOperation : "");
        int pPct = Math.max(0, Math.min(100, j.progressPercent));

        if (running) {
            TaskUiState s = base.started(title)
                    .setPauseAvailable(j.isPauseAvailable)
                    .setPaused(j.isLoadingPaused)
                    .withProgress(pPct, pText);
            if (j.warningText != null && !j.warningText.trim().isEmpty())
                s = s.withWarning(j.warningText);
            return s.withShowToUser(j.showToUser);
        }

        if (ImportJob.S_SUCCEEDED.equals(j.status)) {
            return base.success(pText.isEmpty() ? "Finished" : pText).withShowToUser(j.showToUser);
        }
        if (ImportJob.S_CANCELLED.equals(j.status)) {
            myLogW("Import cancelled");
            return base.started(title).cancelled("Cancelled", pText.isEmpty() ? "Cancelled" : pText).withShowToUser(j.showToUser);
        }
        if (ImportJob.S_FAILED.equals(j.status)) {
            myLogE("Import failed: " + j.errorTextDev);
            return base.started(title).failed(
                    j.errorTextUser != null ? j.errorTextUser : j.errorTextDev,
                    pText.isEmpty() ? "Import failed" : pText).withShowToUser(j.showToUser);
        }


        // QUEUED but not yet running
        return base.started(title).withProgress(pPct, pText.isEmpty() ? "Queued…" : pText).withShowToUser(j.showToUser);
    }

}