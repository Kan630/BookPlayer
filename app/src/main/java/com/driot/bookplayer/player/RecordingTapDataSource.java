package com.driot.bookplayer.player;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Wraps a real ExoPlayer {@link DataSource} and, whenever a {@link RadioRecorder} is actively
 * recording, feeds it the exact same bytes ExoPlayer itself just read for playback - not a
 * second, independent HTTP connection, so no separate server-side "burst" and no drift between
 * what's heard and what's saved.
 *
 * Many Icecast/Shoutcast servers (confirmed via response headers: {@code icy-metaint}) interleave
 * a periodic non-audio metadata block (e.g. "now playing" title) directly into the byte stream
 * every N bytes of audio. ExoPlayer's own extractor pipeline strips this above the raw DataSource
 * layer before decoding, which is why playback is unaffected either way - but this tap sits right
 * at that raw layer, so without de-interleaving it here too, every recording would have those
 * blocks (as little as one stray length byte, or a real title string) spliced into the audio,
 * corrupting frame alignment for everything downstream of each insertion. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class RecordingTapDataSource implements DataSource {

    private static final AtomicInteger instanceCounter = new AtomicInteger(0);

    static final class Factory implements DataSource.Factory {
        private final DataSource.Factory delegateFactory;
        private final RadioRecorder radioRecorder;

        Factory(DataSource.Factory delegateFactory, RadioRecorder radioRecorder) {
            this.delegateFactory = delegateFactory;
            this.radioRecorder = radioRecorder;
        }

        @Override
        public DataSource createDataSource() {
            return new RecordingTapDataSource(delegateFactory.createDataSource(), radioRecorder);
        }
    }

    private final int instanceId = instanceCounter.incrementAndGet();
    private final DataSource delegate;
    private final RadioRecorder radioRecorder;

    // ICY de-interleaving state for this connection (reset per open(), since the server restarts
    // its own byte counter at the start of every response). 0 = no ICY metadata on this stream.
    private int icyMetaInt = 0;
    private long audioBytesUntilMarker = 0;
    private int metadataBytesRemaining = 0;

    private RecordingTapDataSource(DataSource delegate, RadioRecorder radioRecorder) {
        this.delegate = delegate;
        this.radioRecorder = radioRecorder;
    }

    @Override
    public void addTransferListener(TransferListener transferListener) {
        delegate.addTransferListener(transferListener);
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        long result = delegate.open(dataSpec);
        icyMetaInt = 0;
        metadataBytesRemaining = 0;
        try {
            Map<String, List<String>> headers = delegate.getResponseHeaders();
            String metaIntStr = firstHeader(headers, "icy-metaint");
            if (metaIntStr != null) {
                icyMetaInt = Integer.parseInt(metaIntStr.trim());
                myLogI("RecordingTapDataSource#" + instanceId + ": icy-metaint=" + icyMetaInt
                        + " - will strip interleaved ICY metadata before recording");
            }
        } catch (Exception e) {
            myLogW("RecordingTapDataSource#" + instanceId + ": failed to parse icy-metaint: " + e);
            icyMetaInt = 0;
        }
        audioBytesUntilMarker = icyMetaInt;
        return result;
    }

    @Nullable
    private static String firstHeader(@Nullable Map<String, List<String>> headers, String name) {
        if (headers == null)
            return null;
        List<String> values = headers.get(name);
        if (values == null) {
            // Header maps from different DataSource implementations aren't guaranteed
            // case-normalized the same way - fall back to a case-insensitive scan.
            for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) {
                    values = e.getValue();
                    break;
                }
            }
        }
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int n = delegate.read(buffer, offset, length);
        if (n > 0) {
            if (icyMetaInt > 0) {
                // The server's metadata-insertion cycle is keyed to total bytes since this
                // connection opened, not to whether anyone happens to be recording right now -
                // playback has usually been running for a while before the user presses record.
                // So this position tracking must run unconditionally on every read(), for the
                // whole life of the connection; only the actual write to the recorder is gated
                // on isRecording(). Getting this wrong (gating the tracking itself on
                // isRecording()) was the bug: the cycle position would start from scratch at
                // whatever point recording happened to begin, out of sync with where the server
                // really was - corrupting the very first metadata boundary the recording crossed.
                processIcyInterleaving(buffer, offset, n);
            } else {
                // radioRecorder.observe() itself decides whether to write to an active recording
                // or just top up the rewind buffer - always call it, same reasoning as above.
                radioRecorder.observe(instanceId, buffer, offset, n);
            }
        }
        return n;
    }

    /** Walks [offset, offset+length) tracking the ICY audio/metadata cycle, handing each
     * audio-only sub-range to the recorder (which itself decides whether to write it to an active
     * recording or just keep it in the rewind buffer) and always advancing the cycle position. */
    private void processIcyInterleaving(byte[] buffer, int offset, int length) {
        int pos = offset;
        int end = offset + length;
        while (pos < end) {
            if (metadataBytesRemaining > 0) {
                int skip = (int) Math.min(metadataBytesRemaining, end - pos);
                pos += skip;
                metadataBytesRemaining -= skip;
                continue;
            }
            if (audioBytesUntilMarker > 0) {
                int chunk = (int) Math.min(audioBytesUntilMarker, end - pos);
                radioRecorder.observe(instanceId, buffer, pos, chunk);
                pos += chunk;
                audioBytesUntilMarker -= chunk;
                continue;
            }
            // audioBytesUntilMarker == 0: this single byte is the metadata length indicator
            // (actual metadata byte count = value * 16; 0 means "no update this interval").
            int lengthByte = buffer[pos] & 0xFF;
            metadataBytesRemaining = lengthByte * 16;
            pos += 1;
            audioBytesUntilMarker = icyMetaInt;
        }
    }

    @Nullable
    @Override
    public Uri getUri() {
        return delegate.getUri();
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return delegate.getResponseHeaders();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
