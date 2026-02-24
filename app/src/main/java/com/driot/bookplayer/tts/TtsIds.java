package com.driot.bookplayer.tts;

import androidx.annotation.Nullable;

public final class TtsIds {
    private TtsIds() {
    }

    public static String utt(int start, int end) {
        return utt(start, end, null);
    }

    public static String utt(int start, int end, @Nullable String tag) {
        return "utt_" + start + "_" + end + (tag != null ? "_" + tag : "");
    }

    public static boolean isWarmup(@Nullable String id) {
        return id != null && id.startsWith("warmup-");
    }

    /** returns int[]{start,end} or null. Ignores optional tag at the end. */
    @Nullable
    public static int[] parseUtt(@Nullable String id) {
        try {
            if (id == null || !id.startsWith("utt_"))
                return null;
            String[] parts = id.split("_");
            if (parts.length < 3)
                return null;
            int s = Integer.parseInt(parts[1]);
            int e = Integer.parseInt(parts[2]);
            return new int[] { s, e };
        } catch (Throwable ignore) {
            return null;
        }
    }
}
