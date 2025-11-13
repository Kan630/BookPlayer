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

        // --- CHAPTERS ---
        DataSource dataSource = null;
        try {
            File outputFolder = new File(destinationFolderPath);
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

                FileOutputStream fos = new FileOutputStream(new File(outputFolder, filename));
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

            if (!m4bFile.delete()) {
                myLogE("Error Deleting source M4B file after split.");
                emitWarning("Error Deleting source M4B file after split.");
            }

            emitTaskCompleted(TASK_NAME, outputFolder.getAbsolutePath(),
                    context.getString(R.string.import_task_m4b_split) + " " +
                            context.getString(R.string.import_task_complete));
            return true;

        } catch (Exception e) {
            // On enrichit le catch pour distinguer les cas
            String raw = (e.getMessage() != null ? e.getMessage() : "");
            String causeMsg = (e.getCause() != null && e.getCause().getMessage() != null)
                    ? e.getCause().getMessage()
                    : "";

            String msg = (raw + " " + causeMsg).toLowerCase(Locale.ROOT);

            // --- Case 1 : Disk full (ENOSPC) ---
            boolean noSpace = msg.contains("enospc")
                    || msg.contains("no space left")
                    || msg.contains("not enough space");

            if (noSpace) {
                myLogEE(e, "splitM4bLocal - disk full (ENOSPC)");
                String userMsg = context.getString(R.string.error_no_space_left)
                        + "\n\n" + context.getString(R.string.solution_free_space);
                emitWarning(userMsg);
                emitFailed(TASK_NAME, "No space left on device",
                        context.getString(R.string.error_no_space_left));
                return false;
            }

            // --- Case 2 : Fichier trop gros / mmap impossible / mémoire ---
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
                myLogEE(e, "splitM4bLocal - file too large or mmap/memory issue");
                String userMsg = ""
                        + "This M4B file is too large or uses a structure that is incompatible "
                        + "with Android's memory system.\n\n"
                        + "BookPlayer currently supports only standard M4B files.\n\n"
                        + "Tips:\n"
                        + "- Try converting the M4B with an external tool (ffmpeg, mp4box...),\n"
                        + "- Or split it on a computer and import the chapters separately.";
                emitWarning(userMsg);
                emitFailed(TASK_NAME, "Cannot split large M4B", userMsg);
                return false;
            }

            // --- Case 3 : Structure M4B non standard ---
            boolean structure =
                    msg.contains("no suitable")
                            || msg.contains("required audio or chapter")
                            || msg.contains("parse")
                            || msg.contains("box")
                            || msg.contains("corrupt");

            if (structure) {
                myLogEE(e, "splitM4bLocal - non-standard M4B structure");
                String userMsg = "This M4B file uses a non-standard chapter format.\n"
                        + "BookPlayer can only split M4B files that use embedded text chapters.\n\n"
                        + "Tip: try importing the file as a single track instead.";
                emitWarning(userMsg);
                emitFailed(TASK_NAME, "Unsupported M4B structure", userMsg);
                return false;
            }

            // --- Generic fallback ---
            myLogEE(e, "splitM4bLocal - generic error");
            emitWarning(context.getString(R.string.Import_Experimental_M4B_warning)
                    + "\n\n" + context.getString(R.string.Import_Experimental_M4B_iferror)
                    + ", " + context.getString(R.string.Import_Experimental_M4B_solution_1)
                    + "\n" + context.getString(R.string.Import_Experimental_M4B_solution_2));
            emitFailed(TASK_NAME, e.getMessage(), null);
            return false;

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

}
