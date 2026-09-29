package com.driot.bookplayer.services.m4b;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads the chapters of an MP4/M4B by walking only the boxes it needs, without mp4parser.
 * <p>
 * mp4parser's MovieCreator builds every track up front, so one malformed extra track (a cover stored as a video
 * track with no chunk-offset table, a box size it refuses...) makes the whole parse fail although the audio and the
 * chapters are fine - Crashlytics: "getChunkOffsetBox() on a null object", "I don't know how to deal with UInt64".
 * This reader looks at:
 * <ol>
 * <li>the QuickTime chapter track (text track referenced by the audio track's tref/chap), the format of the m4b
 * files seen so far, same data mp4parser used;</li>
 * <li>otherwise the Nero "chpl" atom in moov/udta.</li>
 * </ol>
 * Never throws: problems end up in {@link Result#problem}. Pure Java (JVM-testable).
 */
public final class Mp4ChapterReader {

    public static final String SOURCE_CHAPTER_TRACK = "chapter-track";
    public static final String SOURCE_CHPL = "chpl";

    /** A chapter as stored in the file (validation/normalization is ChapterPlan's job). */
    public static final class Chapter {
        public final String title;
        public final long startMs;
        public final long durationMs;

        public Chapter(String title, long startMs, long durationMs) {
            this.title = title;
            this.startMs = startMs;
            this.durationMs = durationMs;
        }

        @Override
        public String toString() {
            return startMs + "+" + durationMs + " " + title;
        }
    }

    public static final class Result {
        /** Never null, may be empty. */
        public final List<Chapter> chapters;
        /** {@link #SOURCE_CHAPTER_TRACK}, {@link #SOURCE_CHPL} or null. */
        public final String source;
        /** Duration of the audio track, -1 if unknown. */
        public final long audioDurationMs;
        /** Compact structure of the file, for diagnostics ("moov[trak[...] udta[chpl]] mdat"). */
        public final String boxMap;
        /** Null when nothing looked wrong. */
        public final String problem;

        Result(List<Chapter> chapters, String source, long audioDurationMs, String boxMap, String problem) {
            this.chapters = chapters;
            this.source = source;
            this.audioDurationMs = audioDurationMs;
            this.boxMap = boxMap;
            this.problem = problem;
        }
    }

    /** Box limits a chapter track could reasonably need (a chapter title table is tiny). */
    private static final int MAX_CHAPTERS = 10_000;
    private static final int MAX_TITLE_BYTES = 4096;
    private static final int MAX_BOX_MAP_CHARS = 1000;
    private static final int MAX_DEPTH = 8;

    private final FileChannel ch;
    private final long base;
    private final long length;
    private final StringBuilder problems = new StringBuilder();

    private Mp4ChapterReader(FileChannel ch, long base, long length) {
        this.ch = ch;
        this.base = base;
        this.length = length;
    }

    public static Result read(File file) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            return read(raf.getChannel(), 0, raf.length());
        } catch (IOException e) {
            return new Result(Collections.emptyList(), null, -1, "", "cannot open: " + e);
        }
    }

    /**
     * @param base   offset of the MP4 inside the channel (AssetFileDescriptor start offset)
     * @param length length of the MP4 (-1 or unknown: channel size - base)
     */
    public static Result read(FileChannel ch, long base, long length) {
        try {
            long len = length > 0 ? length : ch.size() - base;
            return new Mp4ChapterReader(ch, base, len).run();
        } catch (Throwable t) {
            return new Result(Collections.emptyList(), null, -1, "", "reader error: " + t);
        }
    }

    // ------------------------------------------------------------------------------------------ boxes

    private static final class Box {
        final String type;
        final long start;
        final long contentStart;
        final long end;

        Box(String type, long start, long contentStart, long end) {
            this.type = type;
            this.start = start;
            this.contentStart = contentStart;
            this.end = end;
        }

        long contentSize() {
            return end - contentStart;
        }
    }

    private ByteBuffer readAt(long pos, int n) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(n);
        while (b.hasRemaining()) {
            int r = ch.read(b, base + pos + b.position());
            if (r < 0)
                throw new IOException("EOF at " + (pos + b.position()));
        }
        b.flip();
        return b;
    }

    /** Children of [from, to). Stops at the first box whose size doesn't fit its parent. */
    private List<Box> children(long from, long to) throws IOException {
        List<Box> out = new ArrayList<>();
        long pos = from;
        while (pos + 8 <= to) {
            ByteBuffer h = readAt(pos, (int) Math.min(16, to - pos));
            long size = h.getInt(0) & 0xFFFFFFFFL;
            String type = fourcc(h, 4);
            long header = 8;
            if (size == 1) {
                if (h.limit() < 16) {
                    problem("truncated largesize " + type + " at " + pos);
                    break;
                }
                size = h.getLong(8); // negative when > Long.MAX_VALUE: rejected just below
                header = 16;
            } else if (size == 0) {
                size = to - pos;
            }
            if (size < header || size > to - pos) {
                problem("bad size " + size + " for " + type + " at " + pos);
                break;
            }
            out.add(new Box(type, pos, pos + header, pos + size));
            pos += size;
        }
        return out;
    }

    private static String fourcc(ByteBuffer b, int at) {
        char[] c = new char[4];
        for (int i = 0; i < 4; i++) {
            int v = b.get(at + i) & 0xFF;
            c[i] = (v >= 0x20 && v < 0x7F) ? (char) v : '?';
        }
        return new String(c);
    }

    private static Box find(List<Box> boxes, String type) {
        for (Box b : boxes)
            if (b.type.equals(type))
                return b;
        return null;
    }

    private void problem(String p) {
        if (problems.length() < 500) {
            if (problems.length() > 0)
                problems.append("; ");
            problems.append(p);
        }
    }

    // ------------------------------------------------------------------------------------------ tracks

    private static final class Trak {
        long id = -1;
        String handler = "";
        long timescale;
        long duration;
        final List<Long> chapRefs = new ArrayList<>();
        Box stbl;
    }

    private Result run() throws IOException {
        List<Box> top = children(0, length);
        StringBuilder map = new StringBuilder();
        describe(top, map, 0);

        Box moov = find(top, "moov");
        if (moov == null) {
            problem("no moov box");
            return new Result(Collections.emptyList(), null, -1, clip(map), problems.toString());
        }
        List<Box> moovKids = children(moov.contentStart, moov.end);

        long movieDurationMs = -1;
        Box mvhd = find(moovKids, "mvhd");
        if (mvhd != null) {
            long[] tsDur = timescaleAndDuration(mvhd);
            if (tsDur != null && tsDur[0] > 0)
                movieDurationMs = tsDur[1] * 1000 / tsDur[0];
        }

        List<Trak> traks = new ArrayList<>();
        for (Box b : moovKids)
            if (b.type.equals("trak"))
                traks.add(parseTrak(b));

        Trak audio = null;
        for (Trak t : traks)
            if ("soun".equals(t.handler)) {
                audio = t;
                break;
            }
        long audioDurationMs = (audio != null && audio.timescale > 0) ? audio.duration * 1000 / audio.timescale
                : movieDurationMs;

        // 1) QuickTime chapter track: the one the audio track references, else any text track
        // tref/chap can list several tracks: the titles (text) and per-chapter images (vide) - take the text one
        Trak chapterTrak = null;
        if (audio != null)
            for (long ref : audio.chapRefs)
                for (Trak t : traks)
                    if (chapterTrak == null && t.id == ref && t != audio
                            && ("text".equals(t.handler) || "sbtl".equals(t.handler)))
                        chapterTrak = t;
        if (chapterTrak == null)
            for (Trak t : traks)
                if ("text".equals(t.handler) || "sbtl".equals(t.handler)) {
                    chapterTrak = t;
                    break;
                }

        List<Chapter> fromTrack = Collections.emptyList();
        if (chapterTrak != null) {
            try {
                fromTrack = readChapterTrack(chapterTrak);
            } catch (Exception e) {
                problem("chapter track: " + e);
            }
        }
        if (fromTrack.size() >= 2)
            return new Result(fromTrack, SOURCE_CHAPTER_TRACK, audioDurationMs, clip(map), nullIfEmpty());

        // 2) Nero chpl
        List<Chapter> fromChpl = Collections.emptyList();
        Box udta = find(moovKids, "udta");
        if (udta != null) {
            Box chpl = find(children(udta.contentStart, udta.end), "chpl");
            if (chpl != null) {
                try {
                    fromChpl = readChpl(chpl, audioDurationMs);
                } catch (Exception e) {
                    problem("chpl: " + e);
                }
            }
        }
        if (fromChpl.size() >= 2)
            return new Result(fromChpl, SOURCE_CHPL, audioDurationMs, clip(map), nullIfEmpty());

        if (!fromTrack.isEmpty())
            return new Result(fromTrack, SOURCE_CHAPTER_TRACK, audioDurationMs, clip(map), nullIfEmpty());
        return new Result(fromChpl, fromChpl.isEmpty() ? null : SOURCE_CHPL, audioDurationMs, clip(map),
                nullIfEmpty());
    }

    private String nullIfEmpty() {
        return problems.length() == 0 ? null : problems.toString();
    }

    private Trak parseTrak(Box trak) throws IOException {
        Trak t = new Trak();
        List<Box> kids = children(trak.contentStart, trak.end);
        Box tkhd = find(kids, "tkhd");
        if (tkhd != null && tkhd.contentSize() >= 24) {
            ByteBuffer b = readAt(tkhd.contentStart, 24);
            int version = b.get(0) & 0xFF;
            t.id = (version == 1 ? b.getInt(20) : b.getInt(12)) & 0xFFFFFFFFL;
        }
        Box tref = find(kids, "tref");
        if (tref != null) {
            for (Box r : children(tref.contentStart, tref.end)) {
                if (!r.type.equals("chap"))
                    continue;
                long n = Math.min(r.contentSize() / 4, 64);
                ByteBuffer b = readAt(r.contentStart, (int) (n * 4));
                for (int i = 0; i < n; i++)
                    t.chapRefs.add(b.getInt(i * 4) & 0xFFFFFFFFL);
            }
        }
        Box mdia = find(kids, "mdia");
        if (mdia != null) {
            List<Box> mdiaKids = children(mdia.contentStart, mdia.end);
            Box mdhd = find(mdiaKids, "mdhd");
            if (mdhd != null) {
                long[] tsDur = timescaleAndDuration(mdhd);
                if (tsDur != null) {
                    t.timescale = tsDur[0];
                    t.duration = tsDur[1];
                }
            }
            Box hdlr = find(mdiaKids, "hdlr");
            if (hdlr != null && hdlr.contentSize() >= 12)
                t.handler = fourcc(readAt(hdlr.contentStart, 12), 8);
            Box minf = find(mdiaKids, "minf");
            if (minf != null)
                t.stbl = find(children(minf.contentStart, minf.end), "stbl");
        }
        return t;
    }

    /** mvhd/mdhd: {timescale, duration}. */
    private long[] timescaleAndDuration(Box full) throws IOException {
        if (full.contentSize() < 20)
            return null;
        ByteBuffer b = readAt(full.contentStart, (int) Math.min(32, full.contentSize()));
        int version = b.get(0) & 0xFF;
        if (version == 1) {
            if (b.limit() < 32)
                return null;
            return new long[] { b.getInt(20) & 0xFFFFFFFFL, b.getLong(24) };
        }
        return new long[] { b.getInt(12) & 0xFFFFFFFFL, b.getInt(16) & 0xFFFFFFFFL };
    }

    private List<Chapter> readChapterTrack(Trak t) throws IOException {
        if (t.stbl == null)
            throw new IOException("no stbl");
        if (t.timescale <= 0)
            throw new IOException("no timescale");
        List<Box> st = children(t.stbl.contentStart, t.stbl.end);

        // stsz: sample count + sizes
        Box stsz = find(st, "stsz");
        if (stsz == null)
            throw new IOException("no stsz");
        ByteBuffer h = readAt(stsz.contentStart, 12);
        int constSize = h.getInt(4);
        long count = h.getInt(8) & 0xFFFFFFFFL;
        if (count == 0 || count > MAX_CHAPTERS)
            throw new IOException("sample count " + count);
        int n = (int) count;
        int[] sizes = new int[n];
        if (constSize != 0) {
            java.util.Arrays.fill(sizes, constSize);
        } else {
            ByteBuffer s = readAt(stsz.contentStart + 12, n * 4);
            for (int i = 0; i < n; i++)
                sizes[i] = s.getInt(i * 4);
        }

        // stts: durations
        Box stts = find(st, "stts");
        if (stts == null)
            throw new IOException("no stts");
        long entries = readAt(stts.contentStart + 4, 4).getInt(0) & 0xFFFFFFFFL;
        if (entries > MAX_CHAPTERS || entries * 8 > stts.contentSize())
            throw new IOException("stts entries " + entries);
        ByteBuffer e = readAt(stts.contentStart + 8, (int) entries * 8);
        long[] durations = new long[n];
        int k = 0;
        for (int i = 0; i < entries && k < n; i++) {
            long c = e.getInt(i * 8) & 0xFFFFFFFFL;
            long d = e.getInt(i * 8 + 4) & 0xFFFFFFFFL;
            for (long j = 0; j < c && k < n; j++)
                durations[k++] = d;
        }

        // stsc + stco/co64: where each sample is
        long[] offsets = sampleOffsets(st, sizes);

        List<Chapter> out = new ArrayList<>(n);
        long t0 = 0;
        for (int i = 0; i < n; i++) {
            String title = readTextSample(offsets[i], sizes[i]);
            long startMs = t0 * 1000 / t.timescale;
            long durMs = durations[i] * 1000 / t.timescale;
            out.add(new Chapter(title.isEmpty() ? "" : title, startMs, durMs));
            t0 += durations[i];
        }
        return out;
    }

    private long[] sampleOffsets(List<Box> st, int[] sizes) throws IOException {
        Box stco = find(st, "stco");
        Box co64 = find(st, "co64");
        long[] chunks;
        if (stco != null) {
            long c = readAt(stco.contentStart + 4, 4).getInt(0) & 0xFFFFFFFFL;
            if (c > MAX_CHAPTERS || c * 4 > stco.contentSize())
                throw new IOException("stco count " + c);
            ByteBuffer b = readAt(stco.contentStart + 8, (int) c * 4);
            chunks = new long[(int) c];
            for (int i = 0; i < c; i++)
                chunks[i] = b.getInt(i * 4) & 0xFFFFFFFFL;
        } else if (co64 != null) {
            long c = readAt(co64.contentStart + 4, 4).getInt(0) & 0xFFFFFFFFL;
            if (c > MAX_CHAPTERS || c * 8 > co64.contentSize())
                throw new IOException("co64 count " + c);
            ByteBuffer b = readAt(co64.contentStart + 8, (int) c * 8);
            chunks = new long[(int) c];
            for (int i = 0; i < c; i++)
                chunks[i] = b.getLong(i * 8);
        } else {
            throw new IOException("no stco/co64");
        }

        Box stsc = find(st, "stsc");
        if (stsc == null)
            throw new IOException("no stsc");
        long entries = readAt(stsc.contentStart + 4, 4).getInt(0) & 0xFFFFFFFFL;
        if (entries == 0 || entries > MAX_CHAPTERS || entries * 12 > stsc.contentSize())
            throw new IOException("stsc entries " + entries);
        ByteBuffer b = readAt(stsc.contentStart + 8, (int) entries * 12);

        long[] offsets = new long[sizes.length];
        int sample = 0;
        for (int chunk = 1; chunk <= chunks.length && sample < sizes.length; chunk++) {
            long perChunk = 0;
            for (int i = 0; i < entries; i++) {
                long first = b.getInt(i * 12) & 0xFFFFFFFFL;
                if (first <= chunk)
                    perChunk = b.getInt(i * 12 + 4) & 0xFFFFFFFFL;
                else
                    break;
            }
            long pos = chunks[chunk - 1];
            for (long j = 0; j < perChunk && sample < sizes.length; j++) {
                offsets[sample] = pos;
                pos += sizes[sample];
                sample++;
            }
        }
        if (sample < sizes.length)
            throw new IOException("only " + sample + "/" + sizes.length + " samples located");
        return offsets;
    }

    /** QuickTime text sample: 16-bit length, then the text (UTF-8, or UTF-16 with a BOM), then optional atoms. */
    private String readTextSample(long offset, int size) throws IOException {
        if (size < 2 || offset < 0 || offset + size > length)
            return "";
        ByteBuffer b = readAt(offset, Math.min(size, MAX_TITLE_BYTES + 2));
        int len = Math.min(b.getShort(0) & 0xFFFF, b.limit() - 2);
        byte[] raw = new byte[len];
        b.position(2);
        b.get(raw);
        String s;
        if (len >= 2 && (raw[0] & 0xFF) == 0xFE && (raw[1] & 0xFF) == 0xFF)
            s = new String(raw, 2, len - 2, StandardCharsets.UTF_16BE);
        else if (len >= 2 && (raw[0] & 0xFF) == 0xFF && (raw[1] & 0xFF) == 0xFE)
            s = new String(raw, 2, len - 2, StandardCharsets.UTF_16LE);
        else
            s = new String(raw, StandardCharsets.UTF_8);
        return cleanTitle(s);
    }

    static String cleanTitle(String s) {
        return s.replace("﻿", "").replaceAll("\\p{Cntrl}", " ").trim();
    }

    /**
     * Nero chapters: version(1) flags(3) [reserved(4) when version 1] count(1), then per chapter start(8, in
     * 100 ns units) titleLength(1) title(UTF-8). Same layout as ffmpeg's mov_read_chpl.
     */
    private List<Chapter> readChpl(Box chpl, long audioDurationMs) throws IOException {
        long size = chpl.contentSize();
        if (size < 5 || size > 1_000_000)
            throw new IOException("chpl size " + size);
        ByteBuffer b = readAt(chpl.contentStart, (int) size);
        int version = b.get() & 0xFF;
        b.position(4);
        if (version != 0)
            b.position(8);
        int count = b.get() & 0xFF;
        List<long[]> starts = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        for (int i = 0; i < count && b.remaining() >= 9; i++) {
            long start100ns = b.getLong();
            int tl = b.get() & 0xFF;
            if (tl > b.remaining())
                throw new IOException("chpl title overflow at chapter " + i);
            byte[] t = new byte[tl];
            b.get(t);
            starts.add(new long[] { start100ns / 10_000 });
            titles.add(cleanTitle(new String(t, StandardCharsets.UTF_8)));
        }
        List<Chapter> out = new ArrayList<>(starts.size());
        for (int i = 0; i < starts.size(); i++) {
            long start = starts.get(i)[0];
            long end = (i + 1 < starts.size()) ? starts.get(i + 1)[0] : audioDurationMs;
            out.add(new Chapter(titles.get(i), start, Math.max(0, end - start)));
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------ box map

    private static final java.util.Set<String> CONTAINERS = new java.util.HashSet<>(java.util.Arrays.asList(
            "moov", "trak", "mdia", "minf", "stbl", "udta", "edts", "tref", "dinf", "mvex", "moof", "traf"));

    private void describe(List<Box> boxes, StringBuilder out, int depth) throws IOException {
        String prev = null;
        int repeat = 0;
        for (Box b : boxes) {
            if (out.length() > MAX_BOX_MAP_CHARS)
                return;
            String label = b.type;
            if (b.type.equals("hdlr") && b.contentSize() >= 12)
                label = "hdlr:" + fourcc(readAt(b.contentStart, 12), 8);
            if (label.equals(prev) && !CONTAINERS.contains(b.type)) {
                repeat++;
                continue;
            }
            if (repeat > 0)
                out.append("×").append(repeat + 1);
            repeat = 0;
            if (out.length() > 0 && out.charAt(out.length() - 1) != '[')
                out.append(' ');
            out.append(label);
            prev = label;
            if (CONTAINERS.contains(b.type) && depth < MAX_DEPTH) {
                out.append('[');
                describe(children(b.contentStart, b.end), out, depth + 1);
                out.append(']');
            }
        }
        if (repeat > 0)
            out.append("×").append(repeat + 1);
    }

    private static String clip(StringBuilder s) {
        return s.length() <= MAX_BOX_MAP_CHARS ? s.toString() : s.substring(0, MAX_BOX_MAP_CHARS) + "…";
    }
}
