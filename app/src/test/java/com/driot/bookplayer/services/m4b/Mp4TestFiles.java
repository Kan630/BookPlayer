package com.driot.bookplayer.services.m4b;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** Builds small synthetic MP4 files (only the boxes the chapter reader looks at). */
final class Mp4TestFiles {

    private Mp4TestFiles() {
    }

    static byte[] box(String type, byte[]... parts) {
        int len = 8;
        for (byte[] p : parts)
            len += p.length;
        ByteBuffer b = ByteBuffer.allocate(len);
        b.putInt(len).put(type.getBytes(StandardCharsets.US_ASCII));
        for (byte[] p : parts)
            b.put(p);
        return b.array();
    }

    static byte[] ints(long... v) {
        ByteBuffer b = ByteBuffer.allocate(v.length * 4);
        for (long x : v)
            b.putInt((int) x);
        return b.array();
    }

    static byte[] cat(byte[]... parts) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (byte[] p : parts)
            o.write(p, 0, p.length);
        return o.toByteArray();
    }

    static byte[] fullBox(String type, byte[]... parts) {
        return box(type, cat(ints(0), cat(parts))); // version 0, flags 0
    }

    static byte[] tkhd(int id) {
        return fullBox("tkhd", ints(0, 0, id, 0, 0));
    }

    static byte[] mdhd(int timescale, long duration) {
        return fullBox("mdhd", ints(0, 0, timescale, duration, 0));
    }

    static byte[] hdlr(String handler) {
        return fullBox("hdlr", ints(0), handler.getBytes(StandardCharsets.US_ASCII), ints(0, 0, 0), new byte[] { 0 });
    }

    static byte[] mvhd(int timescale, long duration) {
        return fullBox("mvhd", ints(0, 0, timescale, duration), new byte[80]);
    }

    /** A QuickTime text sample: 16-bit length + UTF-8 text + an "encd" atom like real files. */
    static byte[] textSample(String title) {
        byte[] t = title.getBytes(StandardCharsets.UTF_8);
        ByteBuffer b = ByteBuffer.allocate(2 + t.length);
        b.putShort((short) t.length).put(t);
        return cat(b.array(), box("encd", ints(0x100)));
    }

    /**
     * File: ftyp, mdat(chapter text samples), moov[mvhd, audio trak (tref/chap -> 2), chapter text trak(2), extra].
     * durationsMs: chapter durations in ms (timescale 1000).
     */
    static byte[] withChapterTrack(String[] titles, long[] durationsMs, long audioMs, byte[] extraMoovChild,
            byte[] udta) {
        byte[] ftyp = box("ftyp", "M4B ".getBytes(StandardCharsets.US_ASCII), ints(0));
        byte[][] samples = new byte[titles.length][];
        int mdatPayload = 0;
        for (int i = 0; i < titles.length; i++) {
            samples[i] = textSample(titles[i]);
            mdatPayload += samples[i].length;
        }
        byte[] mdat = box("mdat", cat(samples));
        long firstSampleOffset = ftyp.length + 8;

        long[] sizes = new long[titles.length];
        for (int i = 0; i < titles.length; i++)
            sizes[i] = samples[i].length;
        long[] stts = new long[titles.length * 2];
        for (int i = 0; i < titles.length; i++) {
            stts[i * 2] = 1;
            stts[i * 2 + 1] = durationsMs[i];
        }
        byte[] textStbl = box("stbl",
                fullBox("stsd", ints(0)),
                fullBox("stts", ints(titles.length), ints(stts)),
                fullBox("stsc", ints(1, 1, titles.length, 1)), // one chunk with all samples
                fullBox("stsz", ints(0, titles.length), ints(sizes)),
                fullBox("stco", ints(1, firstSampleOffset)));
        long chapTotal = 0;
        for (long d : durationsMs)
            chapTotal += d;
        byte[] textTrak = box("trak", tkhd(2),
                box("mdia", mdhd(1000, chapTotal), hdlr("text"), box("minf", textStbl)));

        byte[] audioTrak = box("trak", tkhd(1), box("tref", box("chap", ints(2))),
                box("mdia", mdhd(44100, audioMs * 44100 / 1000), hdlr("soun"),
                        box("minf", box("stbl", fullBox("stsd", ints(0))))));

        byte[] moov = box("moov", mvhd(1000, audioMs), audioTrak, textTrak,
                extraMoovChild == null ? new byte[0] : extraMoovChild, udta == null ? new byte[0] : udta);
        return cat(ftyp, mdat, moov);
    }

    /** Nero chpl (version 1): starts in 100 ns units. */
    static byte[] udtaWithChpl(String[] titles, long[] startsMs) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(1); // version
        o.write(0);
        o.write(0);
        o.write(0); // flags
        o.write(0);
        o.write(0);
        o.write(0);
        o.write(0); // reserved (version 1)
        o.write(titles.length);
        for (int i = 0; i < titles.length; i++) {
            ByteBuffer b = ByteBuffer.allocate(8);
            b.putLong(startsMs[i] * 10_000);
            o.write(b.array(), 0, 8);
            byte[] t = titles[i].getBytes(StandardCharsets.UTF_8);
            o.write(t.length);
            o.write(t, 0, t.length);
        }
        return box("udta", box("chpl", o.toByteArray()));
    }

    /** Audio-only file (no chapter track) + optional udta. */
    static byte[] audioOnly(long audioMs, byte[] udta) {
        byte[] ftyp = box("ftyp", "M4B ".getBytes(StandardCharsets.US_ASCII), ints(0));
        byte[] audioTrak = box("trak", tkhd(1),
                box("mdia", mdhd(44100, audioMs * 44100 / 1000), hdlr("soun"),
                        box("minf", box("stbl", fullBox("stsd", ints(0))))));
        byte[] moov = box("moov", mvhd(1000, audioMs), audioTrak, udta == null ? new byte[0] : udta);
        return cat(ftyp, box("mdat", new byte[16]), moov);
    }

    /** The Crashlytics case: a cover stored as a video track whose sample table has no chunk offsets. */
    static byte[] brokenVideoTrak() {
        return box("trak", tkhd(3), box("mdia", mdhd(600, 600), hdlr("vide"),
                box("minf", box("stbl", fullBox("stsd", ints(0)), fullBox("stts", ints(0)),
                        fullBox("stsc", ints(0)), fullBox("stsz", ints(0, 1, 5000)))))); // no stco
    }

    static File write(File dir, String name, byte[] data) throws IOException {
        File f = new File(dir, name);
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(data);
        }
        return f;
    }
}
