package com.driot.bookplayer.imports;

import com.driot.bookplayer.global.Var;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ImportProgressWeigher {

    static class StepInfo {
        final int order, weight;
        StepInfo(int order, int weight) { this.order = order; this.weight = weight; }
    }

    private static final Map<String, StepInfo> steps = new LinkedHashMap<>();
    static {
        steps.put(Var.WORKER_MASS_IMPORT,           new StepInfo(0, 2));
        steps.put(Var.WORKER_TASK_LABEL_DOWNLOAD,   new StepInfo(1, 20));
        steps.put(Var.WORKER_TASK_LABEL_COPY,       new StepInfo(2, 3));
        steps.put(Var.WORKER_TASK_LABEL_UNZIP,      new StepInfo(3, 7));
        steps.put(Var.WORKER_TASK_LABEL_SPLIT_M4B,  new StepInfo(4, 12));
        steps.put(Var.WORKER_TASK_LABEL_SPLIT_EBOOK,new StepInfo(4, 7));
        steps.put(Var.WORKER_TASK_LABEL_SCAN,       new StepInfo(5, 2));
    }

    private ImportProgressWeigher() {}

    /** Convert the *current step's* 0..100 to global 0..100. */
    public static int toGlobalPercent(ImportJob j, String stepKey, int stepPercent) {
        int total = totalWeight(j);
        if (total <= 0) return 0;

        int acc = 0;
        for (Map.Entry<String, StepInfo> e : steps.entrySet()) {
            String key = e.getKey();
            StepInfo info = e.getValue();
            if (!isEnabled(j, key)) continue;

            if (key.equals(stepKey)) {
                float partial = (stepPercent / 100f) * info.weight;
                float totalPct = (acc + partial) / total * 100f;
                return (int) totalPct;
            }
            acc += info.weight;
        }
        return 0;
    }

    private static boolean isEnabled(ImportJob j, String key) {
        switch (key) {
            case Var.WORKER_TASK_LABEL_DOWNLOAD:   return j.doDownload;
            case Var.WORKER_TASK_LABEL_COPY:       return j.doCopy;
            case Var.WORKER_TASK_LABEL_UNZIP:      return j.doUnzip;
            case Var.WORKER_TASK_LABEL_SPLIT_M4B:  return j.doSplitM4b;
            case Var.WORKER_TASK_LABEL_SPLIT_EBOOK:return j.doSplitEbook;
            case Var.WORKER_MASS_IMPORT:           return Objects.equals(j.sourceLocation, "MassImport");
            case Var.WORKER_TASK_LABEL_SCAN:       return true;
        }
        return false;
    }

    private static int totalWeight(ImportJob j) {
        int t = 0;
        if (j.doDownload)  t += steps.get(Var.WORKER_TASK_LABEL_DOWNLOAD).weight;
        if (j.doCopy)      t += steps.get(Var.WORKER_TASK_LABEL_COPY).weight;
        if (j.doUnzip)     t += steps.get(Var.WORKER_TASK_LABEL_UNZIP).weight;
        if (j.doSplitM4b)  t += steps.get(Var.WORKER_TASK_LABEL_SPLIT_M4B).weight;
        if (j.doSplitEbook)t += steps.get(Var.WORKER_TASK_LABEL_SPLIT_EBOOK).weight;
        if (Objects.equals(j.sourceLocation, "MassImport"))t += steps.get(Var.WORKER_MASS_IMPORT).weight;
        t += steps.get(Var.WORKER_TASK_LABEL_SCAN).weight;
        return t;
    }
}
