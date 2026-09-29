package com.driot.bookplayer.services.m4b;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Proves a chapter file written by M4bSplitter is playable before the original M4B is deleted: Android's own
 * extractor must see exactly the frames that were written, and the platform AAC decoder must decode its first and
 * last frames. Any doubt = the split is dropped and the book is imported unsplit (which always plays).
 */
final class M4bSplitVerifier {

    private M4bSplitVerifier() {
    }

    /** Frames decoded at the start and at the end of each chapter file. */
    private static final int EDGE_FRAMES = 6;
    private static final long TIMEOUT_US = 10_000;

    /** @return null when the file is fine, otherwise why not. */
    static String verify(File file, int expectedFrames) {
        MediaExtractor ex = new MediaExtractor();
        try {
            ex.setDataSource(file.getAbsolutePath());
            if (ex.getTrackCount() != 1)
                return file.getName() + ": " + ex.getTrackCount() + " tracks";
            MediaFormat format = ex.getTrackFormat(0);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime == null || !mime.startsWith("audio/"))
                return file.getName() + ": mime " + mime;
            ex.selectTrack(0);

            ByteBuffer buf = ByteBuffer.allocate(64 * 1024);
            List<byte[]> first = new ArrayList<>();
            ArrayDeque<byte[]> last = new ArrayDeque<>();
            int frames = 0;
            int size;
            while ((size = ex.readSampleData(buf, 0)) >= 0) {
                byte[] frame = new byte[size];
                buf.position(0);
                buf.get(frame, 0, size);
                if (first.size() < EDGE_FRAMES)
                    first.add(frame);
                last.addLast(frame);
                if (last.size() > EDGE_FRAMES)
                    last.removeFirst();
                frames++;
                buf.clear();
                ex.advance();
            }
            if (frames != expectedFrames)
                return file.getName() + ": " + frames + " frames read, " + expectedFrames + " written";

            String decode = decodes(mime, format, first);
            if (decode == null)
                decode = decodes(mime, format, new ArrayList<>(last));
            return decode == null ? null : file.getName() + ": " + decode;
        } catch (Throwable t) {
            return file.getName() + ": " + t;
        } finally {
            ex.release();
        }
    }

    /** Feeds frames to the platform decoder: must produce decoded audio, no error. */
    private static String decodes(String mime, MediaFormat format, List<byte[]> frames) {
        if (frames.isEmpty())
            return "no frame to decode";
        MediaCodec codec = null;
        try {
            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(format, null, null, 0);
            codec.start();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int fed = 0;
            long decodedBytes = 0;
            boolean eos = false;
            for (int loops = 0; loops < 500 && !eos; loops++) {
                if (fed <= frames.size()) {
                    int in = codec.dequeueInputBuffer(TIMEOUT_US);
                    if (in >= 0) {
                        ByteBuffer ib = codec.getInputBuffer(in);
                        if (fed < frames.size()) {
                            byte[] f = frames.get(fed);
                            ib.clear();
                            ib.put(f);
                            codec.queueInputBuffer(in, 0, f.length, fed * 20_000L, 0);
                        } else {
                            codec.queueInputBuffer(in, 0, 0, fed * 20_000L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        }
                        fed++;
                    }
                }
                int out = codec.dequeueOutputBuffer(info, TIMEOUT_US);
                if (out >= 0) {
                    decodedBytes += info.size;
                    eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    codec.releaseOutputBuffer(out, false);
                }
            }
            if (!eos)
                return "decoder never finished";
            return decodedBytes > 0 ? null : "decoder produced no audio";
        } catch (Throwable t) {
            return "decode failed: " + t;
        } finally {
            if (codec != null) {
                try {
                    codec.stop();
                } catch (Throwable ignored) {
                }
                codec.release();
            }
        }
    }
}
