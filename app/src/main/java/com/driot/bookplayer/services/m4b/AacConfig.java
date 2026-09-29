package com.driot.bookplayer.services.m4b;

/**
 * AAC parameters needed to wrap raw MP4 AAC frames into ADTS (.aac chapter files).
 * <p>
 * ADTS stores the object type in 2 bits (profile = objectType - 1), so only AAC Main/LC/SSR/LTP (1..4) fit. HE-AAC
 * signalled explicitly (object type 5/29) is written as its LC core - decoders detect SBR/PS implicitly, and the
 * split is decoded before it is kept (M4bSplitVerifier), so a wrong guess only means "not split". The previous code
 * wrote objectType - 1 = 4 for HE-AAC, overflowing the field: unplayable chapter files.
 * Pure Java (JVM-testable).
 */
public final class AacConfig {

    public final int objectType; // ADTS-compatible (1..4)
    public final int samplingFrequencyIndex; // 0..12
    public final int channelConfiguration; // 1..7

    private AacConfig(int objectType, int samplingFrequencyIndex, int channelConfiguration) {
        this.objectType = objectType;
        this.samplingFrequencyIndex = samplingFrequencyIndex;
        this.channelConfiguration = channelConfiguration;
    }

    private static final int[] FREQUENCIES = { 96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000,
            11025, 8000, 7350 };

    /** @return null when this can't be written as ADTS (then: don't split). */
    public static AacConfig of(int objectType, int samplingFrequencyIndex, int channelConfiguration) {
        int aot = (objectType == 5 || objectType == 29) ? 2 : objectType;
        if (aot < 1 || aot > 4)
            return null;
        if (samplingFrequencyIndex < 0 || samplingFrequencyIndex > 12)
            return null;
        if (channelConfiguration < 1 || channelConfiguration > 7)
            return null;
        return new AacConfig(aot, samplingFrequencyIndex, channelConfiguration);
    }

    /** From raw AudioSpecificConfig bytes (MediaFormat "csd-0"). Null when unsupported. */
    public static AacConfig fromAudioSpecificConfig(byte[] asc) {
        if (asc == null || asc.length < 2)
            return null;
        BitReader r = new BitReader(asc);
        int aot = readObjectType(r);
        int sfi = r.bits(4);
        if (sfi == 15)
            sfi = indexOf(r.bits(24));
        int channels = r.bits(4);
        if (aot == 5 || aot == 29) {
            // explicit SBR/PS: extension frequency, then the core object type
            int ext = r.bits(4);
            if (ext == 15)
                r.bits(24);
            aot = readObjectType(r);
        }
        return of(aot, sfi, channels);
    }

    private static int readObjectType(BitReader r) {
        int aot = r.bits(5);
        return aot == 31 ? 32 + r.bits(6) : aot;
    }

    private static int indexOf(int frequency) {
        for (int i = 0; i < FREQUENCIES.length; i++)
            if (FREQUENCIES[i] == frequency)
                return i;
        return -1;
    }

    public int sampleRate() {
        return FREQUENCIES[samplingFrequencyIndex];
    }

    /** 7-byte ADTS header (no CRC) for a raw AAC frame of payloadLength bytes. */
    public byte[] adtsHeader(int payloadLength) {
        int full = payloadLength + 7;
        byte[] h = new byte[7];
        h[0] = (byte) 0xFF;
        h[1] = (byte) 0xF1;
        h[2] = (byte) (((objectType - 1) << 6) | (samplingFrequencyIndex << 2) | (channelConfiguration >> 2));
        h[3] = (byte) (((channelConfiguration & 3) << 6) | ((full >> 11) & 0x03));
        h[4] = (byte) ((full >> 3) & 0xFF);
        h[5] = (byte) (((full & 7) << 5) | 0x1F);
        h[6] = (byte) 0xFC;
        return h;
    }

    @Override
    public String toString() {
        return "aot=" + objectType + " sfi=" + samplingFrequencyIndex + " ch=" + channelConfiguration;
    }

    private static final class BitReader {
        private final byte[] data;
        private int bit;

        BitReader(byte[] data) {
            this.data = data;
        }

        int bits(int n) {
            int v = 0;
            for (int i = 0; i < n; i++) {
                int byteIndex = bit >> 3;
                int b = byteIndex < data.length ? (data[byteIndex] >> (7 - (bit & 7))) & 1 : 0;
                v = (v << 1) | b;
                bit++;
            }
            return v;
        }
    }
}
