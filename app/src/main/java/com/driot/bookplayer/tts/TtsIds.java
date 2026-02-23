package com.driot.bookplayer.tts;

import androidx.annotation.Nullable;

public final class TtsIds {
    private TtsIds() {}
    public static String utt(int start, int end) { return "utt_" + start + "_" + end; }
    public static boolean isWarmup(@Nullable String id) { return id != null && id.startsWith("warmup-"); }

    /** returns int[]{start,end} or null */
    @Nullable public static int[] parseUtt(@Nullable String id) {
        try {
            if (id == null || !id.startsWith("utt_")) return null;
            int sep = id.lastIndexOf('_');
            int s = Integer.parseInt(id.substring(4, sep));
            int e = Integer.parseInt(id.substring(sep + 1));
            return new int[]{s, e};
        } catch (Throwable ignore) { return null; }
    }
}
