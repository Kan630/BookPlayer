package com.driot.bookplayer.services.m4b;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import android.media.MediaExtractor;
import android.media.MediaFormat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.coremedia.iso.boxes.sampleentry.AudioSampleEntry;
import com.driot.bookplayer.helpers.FileHelper;
import com.googlecode.mp4parser.DataSource;
import com.googlecode.mp4parser.FileDataSourceViaHeapImpl;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Sample;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.boxes.mp4.ESDescriptorBox;
import com.googlecode.mp4parser.boxes.mp4.objectdescriptors.AudioSpecificConfig;

import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Splits an M4B into one ADTS .aac file per chapter, with one guarantee: the result is never worse than importing the
 * M4B unsplit.
 * <ol>
 * <li>Chapters + audio from mp4parser (the historical path). If mp4parser can't parse the file (a malformed extra
 * track, a box size it refuses, out of memory...): chapters from {@link Mp4ChapterReader} (chapter track, then Nero
 * chpl) and audio from the platform MediaExtractor.</li>
 * <li>Chapters must pass {@link ChapterPlan}; the AAC config must fit ADTS ({@link AacConfig}).</li>
 * <li>Everything is written into a temporary folder, then each file must be read back frame for frame and decode
 * ({@link M4bSplitVerifier}).</li>
 * <li>Only then are the files moved into the book folder and the M4B deleted. Otherwise the temporary files are
 * removed and the M4B is left untouched: {@link Outcome.Kind#NOT_SPLIT}.</li>
 * </ol>
 */
public final class M4bSplitter {

    public interface Host {
        boolean isCancelled();

        void onChapter(int index, int count, String title);
    }

    public static final class Outcome {
        public enum Kind {
            SPLIT, NOT_SPLIT, CANCELLED, NO_SPACE
        }

        public final Kind kind;
        /** SPLIT: chapter files, in the destination folder. */
        public final List<File> files;
        /** SPLIT: file name -> chapter title (FinalParseFolderWorker "track_titles"). */
        public final JSONObject titles;
        /** SPLIT: "mp4parser", "extractor+chapter-track", "extractor+chpl". */
        public final String method;
        /** NOT_SPLIT: why (for logs and diagnostics). */
        public final String reason;
        /** NOT_SPLIT/NO_SPACE: the exception behind it, if any (drives the user message). */
        public final Throwable error;
        /** Box structure of the file when the fallback was needed (diagnostics). */
        public final String boxMap;

        Outcome(Kind kind, List<File> files, JSONObject titles, String method, String reason, Throwable error,
                String boxMap) {
            this.kind = kind;
            this.files = files;
            this.titles = titles;
            this.method = method;
            this.reason = reason;
            this.error = error;
            this.boxMap = boxMap;
        }

        static Outcome notSplit(String reason, Throwable error, String boxMap) {
            return new Outcome(Kind.NOT_SPLIT, null, null, null, reason, error, boxMap);
        }
    }

    /** Thrown by writers when the host cancels. */
    private static final class Cancelled extends Exception {
    }

    public static final String METHOD_MP4PARSER = "mp4parser";

    private final Host host;

    public M4bSplitter(@NonNull Host host) {
        this.host = host;
    }

    // ------------------------------------------------------------------------------------ entry point

    public Outcome split(@NonNull File m4b, @NonNull File destFolder) {
        // Cheap structure check first: on a file whose top-level boxes are garbage (seen: one stray byte before
        // "ftyp"), mp4parser doesn't fail, it loops forever - the import would hang until Android kills the worker.
        Mp4ChapterReader.Result structure = Mp4ChapterReader.read(m4b);
        if (!hasBox(structure.boxMap, "moov"))
            return Outcome.notSplit("not a readable MP4 (no moov box): " + structure.problem, null, structure.boxMap);

        Throwable mp4parserError;
        try {
            return splitWithMp4parser(m4b, destFolder);
        } catch (Cancelled c) {
            return new Outcome(Outcome.Kind.CANCELLED, null, null, null, "cancelled", null, null);
        } catch (Throwable t) {
            if (isNoSpace(t))
                return new Outcome(Outcome.Kind.NO_SPACE, null, null, null, "no space left", t, null);
            mp4parserError = t;
            myLogW("mp4parser can't split [" + m4b.getName() + "], trying the fallback reader: " + t);
        }

        try {
            return splitWithExtractor(m4b, destFolder, mp4parserError);
        } catch (Cancelled c) {
            return new Outcome(Outcome.Kind.CANCELLED, null, null, null, "cancelled", null, null);
        } catch (Throwable t) {
            if (isNoSpace(t))
                return new Outcome(Outcome.Kind.NO_SPACE, null, null, null, "no space left", t, null);
            return Outcome.notSplit("fallback failed: " + t + " (mp4parser: " + mp4parserError + ")",
                    mp4parserError != null ? mp4parserError : t, null);
        }
    }

    // ------------------------------------------------------------------------------------ mp4parser path

    /**
     * @return an outcome when mp4parser could read the file (split or refused on its own data); throws when
     *         mp4parser itself failed (then the fallback is tried).
     */
    private Outcome splitWithMp4parser(File m4b, File destFolder) throws Exception {
        DataSource ds = new FileDataSourceViaHeapImpl(m4b.getAbsolutePath());
        try {
            Movie movie = MovieCreator.build(ds);
            Track aac = null, chapters = null;
            for (Track t : movie.getTracks()) {
                if ("soun".equals(t.getHandler())
                        && t.getSampleDescriptionBox().getSampleEntry().getType().equals("mp4a"))
                    aac = t;
                else if ("text".equals(t.getHandler()) || "sbtl".equals(t.getHandler()))
                    chapters = t;
            }
            if (aac == null || chapters == null)
                throw new IllegalStateException("Required audio or chapter track not found");

            AudioSampleEntry ase = (AudioSampleEntry) aac.getSampleDescriptionBox().getSampleEntry();
            ESDescriptorBox esds = ase.getBoxes(ESDescriptorBox.class, true).get(0);
            AudioSpecificConfig asc = esds.getEsDescriptor().getDecoderConfigDescriptor().getAudioSpecificInfo();
            AacConfig config = AacConfig.of(asc.getAudioObjectType(), asc.samplingFrequencyIndex,
                    asc.getChannelConfiguration());
            if (config == null)
                return Outcome.notSplit("AAC config can't be written as ADTS: aot=" + asc.getAudioObjectType()
                        + " sfi=" + asc.samplingFrequencyIndex + " ch=" + asc.getChannelConfiguration(), null, null);

            // chapters exactly as the previous implementation read them (same titles = same file names)
            long chapterTs = chapters.getTrackMetaData().getTimescale();
            long[] chapterDur = chapters.getSampleDurations();
            List<Sample> chapterSamples = chapters.getSamples();
            List<Mp4ChapterReader.Chapter> list = new ArrayList<>();
            long t0 = 0;
            for (int i = 0; i < chapterSamples.size(); i++) {
                list.add(new Mp4ChapterReader.Chapter(legacyTitle(chapterSamples.get(i)), t0 * 1000 / chapterTs,
                        chapterDur[i] * 1000 / chapterTs));
                t0 += chapterDur[i];
            }

            long audioTs = aac.getTrackMetaData().getTimescale();
            long[] audioDur = aac.getSampleDurations();
            long audioTotal = 0;
            for (long d : audioDur)
                audioTotal += d;
            ChapterPlan plan = ChapterPlan.of(list, audioTotal * 1000 / audioTs);
            if (!plan.ok())
                return Outcome.notSplit("chapters refused: " + plan.rejectReason, null, null);

            List<Sample> audio = aac.getSamples();
            SampleSource source = new SampleSource() {
                int i = 0;
                long time = 0;

                @Override
                public boolean next(Frame f) {
                    if (i >= audio.size())
                        return false;
                    f.bytes = audio.get(i).asByteBuffer();
                    f.timeUs = time * 1_000_000 / audioTs;
                    time += audioDur[i];
                    i++;
                    return true;
                }

                @Override
                public int total() {
                    return audio.size();
                }
            };
            return writeVerifyCommit(m4b, destFolder, plan, config, source, METHOD_MP4PARSER, null);
        } finally {
            try {
                ds.close();
            } catch (Exception ignored) {
            }
        }
    }

    /** The historical title extraction (M4bSplitWorker.extractCleanChapterTitle), kept for identical names. */
    static String legacyTitle(Sample sample) {
        ByteBuffer buffer = sample.asByteBuffer();
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        if (data.length < 2)
            return "chapter";
        String raw = new String(Arrays.copyOfRange(data, 2, data.length), StandardCharsets.UTF_8);
        raw = raw.replaceAll("encd.*$", "")
                .replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "")
                .replace("﻿", "")
                .trim();
        return raw.isEmpty() ? "chapter" : raw;
    }

    // ------------------------------------------------------------------------------------ fallback path

    private Outcome splitWithExtractor(File m4b, File destFolder, Throwable mp4parserError) throws Exception {
        Mp4ChapterReader.Result chapters = Mp4ChapterReader.read(m4b);
        String map = chapters.boxMap + (chapters.problem != null ? " | " + chapters.problem : "");

        MediaExtractor ex = new MediaExtractor();
        try {
            ex.setDataSource(m4b.getAbsolutePath());
            int track = -1;
            MediaFormat format = null;
            for (int i = 0; i < ex.getTrackCount(); i++) {
                MediaFormat f = ex.getTrackFormat(i);
                if ("audio/mp4a-latm".equals(f.getString(MediaFormat.KEY_MIME))) {
                    track = i;
                    format = f;
                    break;
                }
            }
            if (track < 0)
                return Outcome.notSplit("no AAC track for MediaExtractor", mp4parserError, map);
            ByteBuffer csd = format.containsKey("csd-0") ? format.getByteBuffer("csd-0") : null;
            byte[] ascBytes = null;
            if (csd != null) {
                ascBytes = new byte[csd.remaining()];
                csd.duplicate().get(ascBytes);
            }
            AacConfig config = AacConfig.fromAudioSpecificConfig(ascBytes);
            if (config == null)
                return Outcome.notSplit("AAC config can't be written as ADTS", mp4parserError, map);

            long audioMs = format.containsKey(MediaFormat.KEY_DURATION)
                    ? format.getLong(MediaFormat.KEY_DURATION) / 1000
                    : chapters.audioDurationMs;
            ChapterPlan plan = ChapterPlan.of(chapters.chapters, audioMs);
            if (!plan.ok())
                return Outcome.notSplit("chapters refused (" + chapters.source + "): " + plan.rejectReason,
                        mp4parserError, map);

            ex.selectTrack(track);
            ByteBuffer buf = ByteBuffer.allocate(256 * 1024);
            SampleSource source = new SampleSource() {
                @Override
                public boolean next(Frame f) {
                    buf.clear();
                    int size = ex.readSampleData(buf, 0);
                    if (size < 0)
                        return false;
                    buf.limit(size);
                    f.bytes = buf;
                    f.timeUs = ex.getSampleTime();
                    ex.advance();
                    return true;
                }

                @Override
                public int total() {
                    return -1; // unknown up front: checked against what was read
                }
            };
            return writeVerifyCommit(m4b, destFolder, plan, config, source, "extractor+" + chapters.source, map);
        } finally {
            ex.release();
        }
    }

    // ------------------------------------------------------------------------------------ write, verify, commit

    static final class Frame {
        ByteBuffer bytes;
        long timeUs;
    }

    interface SampleSource {
        boolean next(Frame f) throws IOException;

        /** Number of frames, -1 when unknown. */
        int total();
    }

    private Outcome writeVerifyCommit(File m4b, File destFolder, ChapterPlan plan, AacConfig config,
            SampleSource source, String method, @Nullable String boxMap) throws Exception {
        List<ChapterPlan.Segment> segs = plan.segments;
        File tmp = new File(destFolder, ".m4bsplit-" + System.nanoTime());
        if (!tmp.mkdirs())
            throw new IOException("cannot create " + tmp);
        try {
            // names: same rules as before (sanitized title, "chapterNNN" when empty or duplicated)
            List<String> names = new ArrayList<>();
            JSONObject titles = new JSONObject();
            Set<String> used = new HashSet<>();
            DecimalFormat nnn = new DecimalFormat("000");
            for (int c = 0; c < segs.size(); c++) {
                String title = segs.get(c).title;
                String name = FileHelper.sanitizeFilename(title);
                if (used.contains(name) || name.isEmpty())
                    name = "chapter" + nnn.format(c + 1);
                used.add(name);
                names.add(name + ".aac");
                titles.put(name + ".aac", title);
            }

            int[] framesPerFile = new int[segs.size()];
            int totalFrames = 0;
            Frame f = new Frame();
            byte[] payload = new byte[0];
            int seg = -1;
            OutputStream out = null;
            try {
                while (source.next(f)) {
                    if (host.isCancelled())
                        throw new Cancelled();
                    long tMs = f.timeUs / 1000;
                    int target = Math.max(seg, 0);
                    while (target + 1 < segs.size() && tMs >= segs.get(target + 1).startMs)
                        target++;
                    if (target != seg) {
                        if (out != null)
                            out.close();
                        seg = target;
                        host.onChapter(seg, segs.size(), segs.get(seg).title);
                        out = new BufferedOutputStream(new FileOutputStream(new File(tmp, names.get(seg))), 256 * 1024);
                    }
                    int len = f.bytes.remaining();
                    if (payload.length < len)
                        payload = new byte[len];
                    f.bytes.get(payload, 0, len);
                    out.write(config.adtsHeader(len));
                    out.write(payload, 0, len);
                    framesPerFile[seg]++;
                    totalFrames++;
                }
            } finally {
                if (out != null)
                    out.close();
            }

            // every source frame written, every chapter got audio
            if (source.total() >= 0 && totalFrames != source.total())
                return Outcome.notSplit("wrote " + totalFrames + "/" + source.total() + " frames", null, boxMap);
            for (int c = 0; c < segs.size(); c++)
                if (framesPerFile[c] == 0)
                    return Outcome.notSplit("chapter " + (c + 1) + " got no audio", null, boxMap);

            for (int c = 0; c < segs.size(); c++) {
                if (host.isCancelled())
                    throw new Cancelled();
                String bad = M4bSplitVerifier.verify(new File(tmp, names.get(c)), framesPerFile[c]);
                if (bad != null)
                    return Outcome.notSplit("verification failed: " + bad, null, boxMap);
            }

            // commit
            List<File> committed = new ArrayList<>();
            for (String name : names) {
                File from = new File(tmp, name);
                File to = new File(destFolder, name);
                if (to.exists() && !to.delete())
                    throw new IOException("cannot replace " + to);
                if (!from.renameTo(to)) {
                    for (File done : committed)
                        //noinspection ResultOfMethodCallIgnored
                        done.delete();
                    return Outcome.notSplit("cannot move " + name + " into the book folder", null, boxMap);
                }
                committed.add(to);
            }
            if (!m4b.delete())
                myLogW("split done but the source M4B could not be deleted: " + m4b);
            myLogI("M4B split (" + method + "): " + committed.size() + " chapters, " + totalFrames + " frames, "
                    + config);
            return new Outcome(Outcome.Kind.SPLIT, committed, titles, method, null, null, boxMap);
        } finally {
            deleteRecursively(tmp);
        }
    }

    private static void deleteRecursively(File f) {
        File[] kids = f.listFiles();
        if (kids != null)
            for (File k : kids)
                deleteRecursively(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    static boolean hasBox(String boxMap, String type) {
        return boxMap != null && (boxMap.startsWith(type) || boxMap.contains(" " + type) || boxMap.contains("[" + type));
    }

    static boolean isNoSpace(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            String m = String.valueOf(c.getMessage()).toLowerCase(Locale.ROOT);
            if (m.contains("enospc") || m.contains("no space left") || m.contains("not enough space"))
                return true;
            if (c.getCause() == c)
                break;
        }
        return false;
    }
}
