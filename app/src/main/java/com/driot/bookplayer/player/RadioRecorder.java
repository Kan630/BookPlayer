package com.driot.bookplayer.player;

import android.content.Context;

import com.driot.bookplayer.global.Option;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicLong;

import com.driot.bookplayer.utils.log.LoggerHelper;

/**
 * Records a live radio stream to a local file by tapping the bytes ExoPlayer itself reads for
 * playback (see {@link RecordingTapDataSource}) - not a second, independent HTTP connection. This
 * guarantees the recording is byte-for-byte what's actually playing: no separate connection means
 * no separate server-side "burst", and no drift between what's heard and what's saved. Only
 * progressive (plain HTTP audio) streams are supported: {@link #canRecord(String)} returns false
 * for HLS (.m3u8) streams, whose tap would also capture the manifest/segment-boundary bytes.
 *
 * The tap sits at the network layer, which runs ahead of what's actually audible by however much
 * ExoPlayer has buffered (seconds, not milliseconds, for a live stream). Left uncorrected, that
 * means a fresh recording's content starts and ends that many seconds later than what the user
 * was actually hearing at the moment they pressed record/stop. This is corrected in two symmetric
 * pieces: {@link #observe} continuously maintains a short rewind buffer of recent audio (even
 * while not recording) so {@link #start} can prepend the pre-roll that was already buffered
 * before the button press; {@link #stop} trims the equivalent amount off the tail, since those
 * last bytes were still in flight (not yet actually heard) at the moment of the button press.
 */
public class RadioRecorder extends LoggerHelper {

    public interface Listener {
        /** Called once a recording stops, successful or not (file == null on failure/empty
         * capture - already cleaned up). */
        void onRecordingFinished(File file, String stationName, String coverUrl, String streamUrl, long stationId,
                long elapsedMs, long bytesWritten);
    }

    // Generous cap on how much rewind history to keep while idle - well above any realistic
    // ExoPlayer buffer depth, just bounding memory use (a few hundred KB at typical bitrates).
    private static final long REWIND_BUFFER_MAX_MS = 60_000;

    private static final class TimestampedChunk {
        final long timestampMs;
        final byte[] data;

        TimestampedChunk(long timestampMs, byte[] data) {
            this.timestampMs = timestampMs;
            this.data = data;
        }
    }

    private final Context appCtx;
    private final Listener listener;
    private final Object lock = new Object();

    private volatile boolean active = false;
    private volatile long startTimeMs = 0;
    private volatile long prependedBytesAtStart = 0;
    private final AtomicLong bytesWritten = new AtomicLong(0);

    private final Object rewindLock = new Object();
    private final ArrayDeque<TimestampedChunk> rewindBuffer = new ArrayDeque<>();

    // Guarded by lock
    private FileOutputStream out;
    private File outFile;
    private String stationName;
    private String coverUrl;
    private String streamUrl;
    private long stationId;

    public RadioRecorder(Context context, Listener listener) {
        super(RadioRecorder.class);
        this.appCtx = context.getApplicationContext();
        this.listener = listener;
    }

    public static boolean canRecord(String url) {
        return url != null && !url.isEmpty() && !ExoRadioPlayerEngine.isHlsUrl(url.toLowerCase());
    }

    public boolean isRecording() {
        return active;
    }

    public long getElapsedMs() {
        return active ? System.currentTimeMillis() - startTimeMs : 0;
    }

    public long getBytesWritten() {
        return bytesWritten.get();
    }

    /** @param bufferedMsAtStart ExoPlayer's current buffered-ahead depth (from
     * {@link ExoRadioPlayerEngine#getBufferedDurationMs()}) at the moment of the button press -
     * used to backdate the recording's start by prepending that much rewind history. Pass 0 if
     * unknown; the recording still works, just without the start-of-file correction. */
    public synchronized boolean start(String url, String stationName, String coverUrl, long stationId,
            long bufferedMsAtStart) {
        if (active) {
            myLogW("start() ignored: already recording");
            return false;
        }
        if (!canRecord(url)) {
            myLogW("start() refused: not a recordable (progressive) stream - " + url);
            return false;
        }

        String name = (stationName != null && !stationName.isEmpty()) ? stationName : "Radio";
        File folder = RadioRecordingHelper.buildRecordingFolder(appCtx, name);
        if (!folder.exists())
            folder.mkdirs();
        File file = new File(folder, RadioRecordingHelper.buildRecordingFileName());

        FileOutputStream fos;
        try {
            fos = new FileOutputStream(file);
        } catch (IOException e) {
            myLogEE(e, "start(): failed to open output file " + file.getAbsolutePath());
            return false;
        }

        long recordStartTime = System.currentTimeMillis();
        long prependCutoff = recordStartTime - Math.max(0, bufferedMsAtStart);
        long prependedBytes = 0;
        synchronized (rewindLock) {
            for (TimestampedChunk chunk : rewindBuffer) {
                if (chunk.timestampMs < prependCutoff)
                    continue;
                try {
                    fos.write(chunk.data);
                    prependedBytes += chunk.data.length;
                } catch (IOException e) {
                    myLogEE(e, "start(): failed writing rewind pre-roll");
                    break;
                }
            }
            rewindBuffer.clear();
        }

        synchronized (lock) {
            this.out = fos;
            this.outFile = file;
            this.stationName = name;
            this.coverUrl = coverUrl;
            this.streamUrl = url;
            this.stationId = stationId;
        }
        bytesWritten.set(prependedBytes);
        startTimeMs = recordStartTime;
        prependedBytesAtStart = prependedBytes;
        active = true;
        myLogI("start(): recording station=[" + name + "] -> " + file.getAbsolutePath()
                + " bufferedMsAtStart=" + bufferedMsAtStart + " prependedPreRollBytes=" + prependedBytes);
        return true;
    }

    /** Called by {@link RecordingTapDataSource} - on ExoPlayer's own loading thread, not the main
     * thread - for every chunk of audio it reads for playback (already stripped of any
     * interleaved ICY metadata), regardless of whether a recording is active: while recording,
     * bytes are written straight to the file; while idle, they feed the rewind buffer so a
     * recording started moments later can be backdated. Cheap either way. */
    void observe(int instanceId, byte[] buffer, int offset, int length) {
        if (active) {
            feed(instanceId, buffer, offset, length);
            return;
        }
        if (!Option.getRadioRecordingEnabled())
            return;
        byte[] copy = new byte[length];
        System.arraycopy(buffer, offset, copy, 0, length);
        long now = System.currentTimeMillis();
        long cutoff = now - REWIND_BUFFER_MAX_MS;
        synchronized (rewindLock) {
            rewindBuffer.addLast(new TimestampedChunk(now, copy));
            while (!rewindBuffer.isEmpty() && rewindBuffer.peekFirst().timestampMs < cutoff) {
                rewindBuffer.removeFirst();
            }
        }
    }

    private void feed(int instanceId, byte[] buffer, int offset, int length) {
        synchronized (lock) {
            if (out == null)
                return;
            try {
                out.write(buffer, offset, length);
                bytesWritten.addAndGet(length);
            } catch (IOException e) {
                myLogEE(e, "feed(): write failed, stopping recording");
                stopInternal(0);
            }
        }
    }

    /** @param bufferedMsAtStop ExoPlayer's current buffered-ahead depth at the moment of the
     * button press - used to trim that much off the tail of the file, since those last bytes were
     * still in flight (not yet actually audible) when the user pressed stop. Pass 0 if unknown;
     * the recording still finalizes, just without the end-of-file correction. */
    public void stop(long bufferedMsAtStop) {
        if (!active)
            return;
        myLogI("stop() requested");
        stopInternal(bufferedMsAtStop);
    }

    private void stopInternal(long bufferedMsAtStop) {
        File file;
        String name;
        String cover;
        String stream;
        long station;
        long bytes;
        long elapsed;
        synchronized (lock) {
            if (!active)
                return;
            active = false;
            try {
                if (out != null)
                    out.close();
            } catch (IOException ignored) {
            }
            file = outFile;
            name = stationName;
            cover = coverUrl;
            stream = streamUrl;
            station = stationId;
            bytes = bytesWritten.get();
            elapsed = System.currentTimeMillis() - startTimeMs;
            out = null;
            outFile = null;
        }

        // Rate must be measured over the *live-tapped* bytes only: the prepended pre-roll (see
        // start()) was written instantly, in zero wall-clock time, so including it here would
        // inflate the measured rate and over-trim the tail (confirmed: ~180kbps measured vs the
        // station's real 128kbps, over-trimming by several extra seconds).
        long liveBytes = bytes - prependedBytesAtStart;
        if (file != null && liveBytes > 0 && elapsed > 0 && bufferedMsAtStop > 0) {
            double avgBytesPerMs = (double) liveBytes / elapsed;
            long trimBytes = Math.min(bytes - 1, Math.round(avgBytesPerMs * bufferedMsAtStop));
            if (trimBytes > 0) {
                try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
                    long newLength = Math.max(0, file.length() - trimBytes);
                    raf.setLength(newLength);
                    bytes = newLength;
                    myLogI("stopInternal(): trimmed " + trimBytes + " trailing bytes (bufferedMsAtStop="
                            + bufferedMsAtStop + ")");
                } catch (IOException e) {
                    myLogEE(e, "stopInternal(): failed to trim trailing buffer");
                }
            }
        }

        if (bytes > 0) {
            myLogI("RadioRecorder finished: station=" + name + " bytes=" + bytes + " elapsedMs=" + elapsed
                    + " file=" + (file != null ? file.getAbsolutePath() : "?"));
            listener.onRecordingFinished(file, name, cover, stream, station, elapsed, bytes);
        } else {
            myLogW("RadioRecorder produced no usable data, discarding " + (file != null ? file.getAbsolutePath() : "?"));
            if (file != null) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
            listener.onRecordingFinished(null, name, cover, stream, station, elapsed, bytes);
        }
    }
}
