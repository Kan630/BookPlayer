package com.driot.bookplayer.services;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.work.WorkerParameters;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.imports.ImportHelper;
import com.driot.bookplayer.imports.ImportJob;
import com.driot.bookplayer.imports.ImportWorker;
import com.driot.bookplayer.objects.AudioInfo;
import com.driot.bookplayer.objects.AudioProber;
import com.coremedia.iso.boxes.sampleentry.AudioSampleEntry;
import com.googlecode.mp4parser.DataSource;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Sample;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.boxes.mp4.ESDescriptorBox;
import com.googlecode.mp4parser.boxes.mp4.objectdescriptors.AudioSpecificConfig;
import com.googlecode.mp4parser.FileDataSourceViaHeapImpl;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class M4bSplitWorker extends ImportWorker {

    private static final String TASK_NAME = Var.WORKER_TASK_LABEL_SPLIT_M4B;

    private final Context context;

    public M4bSplitWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        this.context = context.getApplicationContext();
    }

    @NonNull
    @Override
    public Result doWorkBody() {
        emitTaskStart(TASK_NAME,
                context.getString(R.string.import_task_m4b_split) + " " +
                        context.getString(R.string.import_task_start));
        ImportJob j = jobOrFail();

        final String m4bFilePath = ImportHelper.getSourceFilePathForWorker(j);
        final String destinationFolderPath = j.futureFolderPath;

        myLogD("----------------------------------------------------");
        myLog("m4bFilePath = " + m4bFilePath);
        myLog("destinationFolderPath = " + destinationFolderPath);
        myLogD("----------------------------------------------------");

        if (m4bFilePath == null || destinationFolderPath == null) {
            emitFailed(TASK_NAME, "Missing input data for M4bSplitWorker",
                    getApplicationContext().getString(R.string.invalid_resource));
            myLogEE(null, "Missing input data for M4bSplitWorker");
            return Result.failure();
        }

        FirebaseAnalyticsHelper.logEvent("m4b_worker");

        boolean success = splitM4bLocal(m4bFilePath, destinationFolderPath);
        return success ? Result.success() : Result.failure();
    }

    private boolean splitM4bLocal(String m4bFilePath, String destinationFolderPath) {
        Context context = getApplicationContext();
        File m4bFile = new File(m4bFilePath);

        // --- METADATA ---
        emitTextOnlyProgress(getApplicationContext().getString(R.string.parsing_metadata));
        try {
            AudioInfo audioInfo = AudioProber.probe(context, Uri.fromFile(new File(m4bFilePath)), true);
            if (audioInfo != null && audioInfo.cover != null) {
                audioInfo.saveCover(this.getApplicationContext());
            }
        } catch (Exception e) {
            myLogEE(e, "Error Parsing Metadata");
        }

        DataSource dataSource = null;

        // NEW: track created chapter files and whether the folder existed before
        File outputFolder = null;                          // NEW
        boolean outputFolderExistedBefore = false;         // NEW
        List<File> createdChapterFiles = new ArrayList<>();// NEW

        try {
            outputFolder = new File(destinationFolderPath);           // NEW (moved out of inner scope)
            outputFolderExistedBefore = outputFolder.exists();        // NEW

            // pas grave si false, on tente quand même
            //noinspection ResultOfMethodCallIgnored
            outputFolder.mkdirs();

            // IMPORTANT : éviter FileDataSourceImpl (mmap) → utiliser FileDataSourceViaHeapImpl
            dataSource = new FileDataSourceViaHeapImpl(m4bFilePath);
            Movie movie = MovieCreator.build(dataSource);

            Track aacTrack = null;
            Track chapterTrack = null;

            for (Track track : movie.getTracks()) {
                if (isStopped()) {
                    emitCancelled(TASK_NAME);
                    return false;
                }
                if ("soun".equals(track.getHandler())
                        && track.getSampleDescriptionBox().getSampleEntry().getType().equals("mp4a")) {
                    aacTrack = track;
                } else if ("text".equals(track.getHandler()) || "sbtl".equals(track.getHandler())) {
                    chapterTrack = track;
                }
            }

            if (aacTrack == null || chapterTrack == null) {
                throw new RuntimeException("Required audio or chapter track not found");
            }

            AudioSampleEntry ase = (AudioSampleEntry) aacTrack.getSampleDescriptionBox().getSampleEntry();
            ESDescriptorBox esds = ase.getBoxes(ESDescriptorBox.class, true).get(0);
            AudioSpecificConfig asc = esds.getEsDescriptor().getDecoderConfigDescriptor().getAudioSpecificInfo();

            int aot = asc.getAudioObjectType();
            int sfi = asc.samplingFrequencyIndex;
            int cc = asc.getChannelConfiguration();

            long audioTimescale = aacTrack.getTrackMetaData().getTimescale();
            long[] audioDurations = aacTrack.getSampleDurations();
            List<Sample> audioSamples = aacTrack.getSamples();

            List<Sample> chapterSamples = chapterTrack.getSamples();
            long[] chapterDurations = chapterTrack.getSampleDurations();
            long chapterTimescale = chapterTrack.getTrackMetaData().getTimescale();

            Set<String> usedNames = new HashSet<>();
            DecimalFormat chapterFormat = new DecimalFormat("000");

            long chapterTime = 0;
            byte[] frameBuffer = null; // on réutilise ce buffer pour limiter les allocations

            for (int c = 0; c < chapterSamples.size(); c++) {
                if (isStopped()) {
                    emitCancelled(TASK_NAME);
                    return false;
                }

                String title = extractCleanChapterTitle(chapterSamples.get(c));
                if (usedNames.contains(title) || title.isEmpty()) {
                    title = "chapter" + chapterFormat.format(c + 1);
                }
                usedNames.add(title);
                String filename = title + ".aac";

                long startTime = chapterTime;
                long duration = chapterDurations[c];
                chapterTime += duration;

                double startSec = (double) startTime / chapterTimescale;
                double endSec = (double) chapterTime / chapterTimescale;

                int startSample = findSampleIndexForTime(audioDurations, audioTimescale, startSec);
                int endSample = findSampleIndexForTime(audioDurations, audioTimescale, endSec);

                double progress = (double) (c + 1) / chapterSamples.size() * 100;
                String text = context.getString(R.string.Import_Progress_splitting_m4b_file)
                        + (c + 1) + "/" + chapterSamples.size() + "\n\n" + context.getString(R.string.Import_Progress_chapter_title) + " : " + title;
                emitStepProgress(TASK_NAME, (int) progress, text);
                myLogD((int) progress + "% - " + text.replace("\n", " - "));

                File outFile = new File(outputFolder, filename);   // NEW: explicit variable
                createdChapterFiles.add(outFile);                  // NEW: remember we created this chapter

                FileOutputStream fos = new FileOutputStream(outFile);
                try {
                    for (int i = startSample; i < endSample && i < audioSamples.size(); i++) {
                        if (isStopped()) {
                            emitCancelled(TASK_NAME);
                            return false;
                        }
                        ByteBuffer buffer = audioSamples.get(i).asByteBuffer();
                        int len = buffer.remaining();

                        if (frameBuffer == null || frameBuffer.length < len) {
                            frameBuffer = new byte[len];
                        }

                        buffer.get(frameBuffer, 0, len);
                        byte[] header = buildAdtsHeader(len, aot, sfi, cc);
                        fos.write(header);
                        fos.write(frameBuffer, 0, len);
                    }
                } finally {
                    fos.close();
                }
            }

            // SUCCESS → now it is safe to delete the source M4B
            if (!m4bFile.delete()) {
                myLogE("Error Deleting source M4B file after split.");
                emitWarning("Error Deleting source M4B file after split.");
            }

            emitTaskCompleted(TASK_NAME, outputFolder.getAbsolutePath(),
                    context.getString(R.string.import_task_m4b_split) + " " +
                            context.getString(R.string.done));
            return true;

        } catch (Exception e) {
            String raw = (e.getMessage() != null ? e.getMessage() : "");
            String causeMsg = (e.getCause() != null && e.getCause().getMessage() != null)
                    ? e.getCause().getMessage()
                    : "";

            String msg = (raw + " " + causeMsg).toLowerCase(Locale.ROOT);

            boolean noSpace = msg.contains("enospc")
                    || msg.contains("no space left")
                    || msg.contains("not enough space");

            if (noSpace) {
                // Disk full: we really can't continue
                myLogEE(e, "splitM4bLocal - disk full (ENOSPC)");
                String userMsg = context.getString(R.string.error_no_space_left)
                        + "\n\n" + context.getString(R.string.solution_free_space);
                emitWarning(userMsg);
                emitFailed(TASK_NAME, "No space left on device",
                        context.getString(R.string.error_no_space_left));
                return false; // hard failure
            }

            boolean tooLarge =
                    msg.contains("map failed")
                            || msg.contains("mmap")
                            || msg.contains("filechannelimpl.map")
                            || msg.contains("cannot allocate")
                            || msg.contains("outofmemory")
                            || msg.contains("enomem")
                            || msg.contains("scudo")
                            || msg.contains("markcompact")
                            || msg.contains("kernelpreparerange")
                            || msg.contains("size >")
                            || msg.contains("too large");

            if (tooLarge) {
                String userMsg = context.getString(R.string.m4b_error_too_large_or_incompatible_structure);
                return fallbackToSingleM4b(
                        context,
                        m4bFile,
                        outputFolder,
                        outputFolderExistedBefore,
                        createdChapterFiles,
                        userMsg,
                        "splitM4bLocal - file too large or mmap/memory issue");
            }

            boolean structure =
                    msg.contains("no suitable")
                            || msg.contains("required audio or chapter")
                            || msg.contains("parse")
                            || msg.contains("box")
                            || msg.contains("corrupt");

            if (structure) {
                String userMsg = context.getString(R.string.m4b_error_non_standard_chapter_format);
                return fallbackToSingleM4b(
                        context,
                        m4bFile,
                        outputFolder,
                        outputFolderExistedBefore,
                        createdChapterFiles,
                        userMsg,
                        "splitM4bLocal - non-standard M4B structure");
            }

            // Generic fallback
            String userMsg = context.getString(R.string.Import_Experimental_M4B_warning)
                    + "\n\n" + context.getString(R.string.Import_Experimental_M4B_iferror)
                    + ", " + context.getString(R.string.Import_Experimental_M4B_solution_1)
                    + "\n" + context.getString(R.string.Import_Experimental_M4B_solution_2)
                    + "\n\n"
                    + context.getString(R.string.m4b_error_will_import_as_single_file);
            return fallbackToSingleM4b(
                    context,
                    m4bFile,
                    outputFolder,
                    outputFolderExistedBefore,
                    createdChapterFiles,
                    userMsg,
                    "splitM4bLocal - generic error");

        } finally {
            if (dataSource != null) {
                try {
                    dataSource.close();
                } catch (Exception ignore) {
                }
            }
        }
    }


    private static int findSampleIndexForTime(long[] durations, long timescale, double timeInSec) {
        long target = (long) (timeInSec * timescale);
        long sum = 0;
        for (int i = 0; i < durations.length; i++) {
            if (sum >= target) return i;
            sum += durations[i];
        }
        return durations.length - 1;
    }

    private static byte[] buildAdtsHeader(int length, int aot, int freq, int channels) {
        int fullLen = length + 7;
        byte[] h = new byte[7];
        h[0] = (byte) 0xFF;
        h[1] = (byte) 0xF1;
        h[2] = (byte) (((aot - 1) << 6) | (freq << 2) | (channels >> 2));
        h[3] = (byte) (((channels & 3) << 6) | ((fullLen >> 11) & 0x03));
        h[4] = (byte) ((fullLen >> 3) & 0xFF);
        h[5] = (byte) (((fullLen & 7) << 5) | 0x1F);
        h[6] = (byte) 0xFC;
        return h;
    }

    private String extractCleanChapterTitle(Sample sample) {
        ByteBuffer buffer = sample.asByteBuffer();
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        String raw = new String(Arrays.copyOfRange(data, 2, data.length), StandardCharsets.UTF_8);
        raw = raw.replaceAll("encd.*$", "")
                .replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "")
                .replace("\uFEFF", "")
                .trim();
        return raw.isEmpty() ? "chapter" : raw;
    }

    private static String extractStringFromBox(Object box) {
        try {
            Method m = box.getClass().getMethod("getValue");
            return String.valueOf(m.invoke(box));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    // NEW: remove partial chapter files if something went wrong
    private void cleanupPartialOutputs(List<File> createdFiles,
                                       File outputFolder,
                                       boolean folderExistedBefore) {
        if (createdFiles != null) {
            for (File f : createdFiles) {
                if (f != null && f.exists() && f.isFile() && f.getName().endsWith(".aac")) {
                    if (!f.delete()) {
                        myLogE("Could not delete partial chapter file: " + f.getAbsolutePath());
                    }
                }
            }
        }

        // If we created the folder just for this import and it is now empty, try to remove it
        if (!folderExistedBefore && outputFolder != null && outputFolder.isDirectory()) {
            File[] remaining = outputFolder.listFiles();
            if (remaining == null || remaining.length == 0) {
                //noinspection ResultOfMethodCallIgnored
                outputFolder.delete();
            }
        }
    }
    // Fallback: keep single M4B, remove partial .aac, still mark task as completed
    private boolean fallbackToSingleM4b(Context context,
                                        File m4bFile,
                                        File outputFolder,
                                        boolean outputFolderExistedBefore,
                                        List<File> createdChapterFiles,
                                        String warningMessageForUser,
                                        String logTag) {

        FirebaseAnalyticsHelper.logEvent("m4b_fallback");

        // 1) Log + warn
        myLogE(logTag + " - falling back to single M4B import");
        if (warningMessageForUser != null && !warningMessageForUser.isEmpty()) {
            emitWarning(warningMessageForUser);
        } else {
            emitWarning("Error while splitting M4B. Importing original file instead.");
        }

        // 2) Cleanup any partial .aac files
        cleanupPartialOutputs(createdChapterFiles, outputFolder, outputFolderExistedBefore);

        // 3) Make sure M4B is inside the output folder, so the next step sees it
        try {
            if (outputFolder == null) {
                outputFolder = m4bFile.getParentFile();
            }

            if (outputFolder != null && !outputFolder.equals(m4bFile.getParentFile())) {
                File destM4b = new File(outputFolder, m4bFile.getName());
                if (!destM4b.equals(m4bFile)) {
                    // Try to move; if it fails, stay in original folder
                    if (!m4bFile.renameTo(destM4b)) {
                        myLogE("Could not move M4B to output folder, keeping original location: "
                                + m4bFile.getAbsolutePath());
                        // fall back: use the M4B parent as task path
                        outputFolder = m4bFile.getParentFile();
                    } else {
                        m4bFile = destM4b;
                    }
                }
            }
        } catch (Exception moveEx) {
            myLogEE(moveEx, "fallbackToSingleM4b - error while moving M4B");
            // If move fails, we still have the original file somewhere; import code
            // will just see it where it is.
        }


        // 4) Mark task as "completed with fallback" so the pipeline continues
        String completedMsg =
                context.getString(R.string.import_task_m4b_split) + " - "
                        + context.getString(R.string.Import_Experimental_M4B_iferror);
        String pathForNextStep =
                (outputFolder != null ? outputFolder.getAbsolutePath() : m4bFile.getParent());

        emitTaskCompleted(TASK_NAME, pathForNextStep, completedMsg);

        // We return true so doWorkBody() returns Result.success()
        return true;
    }

}
