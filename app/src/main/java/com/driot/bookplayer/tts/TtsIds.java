package com.driot.bookplayer.tts;

import androidx.annotation.Nullable;

public final class TtsIds {
    private static final String SESSION_SALT = String.valueOf(System.currentTimeMillis() % 10000);

    private TtsIds() {
    }

    public static String utt(int start, int end) {
        return "utt_" + SESSION_SALT + "_" + start + "_" + end;
    }

    public static boolean isWarmup(@Nullable String id) {
        return id != null && id.startsWith("warmup-");
    }

    /** returns int[]{start,end} or null */
    @Nullable
    public static int[] parseUtt(@Nullable String id) {
        try {
            if (id == null || !id.startsWith("utt_"))
                return null;
            String[] parts = id.split("_");
            // format: utt_{salt}_{start}_{end}
            if (parts.length < 4)
                return null;
            int s = Integer.parseInt(parts[2]);
            int e = Integer.parseInt(parts[3]);
            return new int[] { s, e };
        } catch (Throwable ignore) {
            return null;
        }
    }
}
