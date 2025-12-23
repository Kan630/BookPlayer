package com.driot.bookplayer.player.heatmaps;

import java.util.List;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;
import com.driot.bookplayer.utils.Tonio;

public class PlayTickHeatMapHelper {

    public final static boolean LOG_DEBUG_PLAYTICK = false;

    public static float[] computeIntensities(List<PlayTickBucket> buckets,
                                             long durationMs,
                                             int nbBuckets) {
        float[] result = new float[nbBuckets];
        if (nbBuckets <= 0 || durationMs <= 0) {
            return result;
        }

        final float nb_tick_for_1_pass = (float) durationMs / 1000 / nbBuckets;
        if (LOG_DEBUG_PLAYTICK) myLogD("nb_tick_for_1_pass=" + nb_tick_for_1_pass);

        long[] tickCounts = new long[nbBuckets];

        // 1) Populate tickCounts from SQL buckets
        if (buckets != null) {
            for (PlayTickBucket b : buckets) {
                int idx = (int) b.bucket;
                if (idx < 0 || idx >= nbBuckets) continue;
                tickCounts[idx] = b.ticks;
            }
        }

        // 1D morphological “isolated outlier correction” (a.k.a. impulse removal).
        if (nbBuckets >= 3) {
            long[] fixed = tickCounts.clone();

            for (int i = 1; i < nbBuckets - 1; i++) {
                long left  = tickCounts[i - 1];
                long mid   = tickCounts[i];
                long right = tickCounts[i + 1];

                if (left == right && mid != left) {
                    fixed[i] = left;
                }
            }
            tickCounts = fixed;
        }
        // 2nd : 1D morphological “isolated outlier correction” (a.k.a. impulse removal).
        if (nbBuckets >= 3) {
            long[] fixed = tickCounts.clone();

            for (int i = 1; i < nbBuckets - 1; i++) {
                long left  = tickCounts[i - 1];
                long mid   = tickCounts[i];
                long right = tickCounts[i + 1];

                if (left == right && mid != left) {
                    fixed[i] = left;
                }
            }
            tickCounts = fixed;
        }

        float[] smoothed = new float[tickCounts.length];
        for (int i = 0; i < tickCounts.length; i++) {
            smoothed[i] = tickCounts[i];
        }
        if (LOG_DEBUG_PLAYTICK) myLogD("smoothed: " + Tonio.getStringFromFloatArray2digits2decimals(smoothed));

        float[] pass = new float[nbBuckets];
        for (int i = 0; i < nbBuckets; i++) {
            float zePass = smoothed[i] / nb_tick_for_1_pass;
            if (zePass <= 0.5) {
                zePass = 0f;
            } else if (zePass <= 1.5) {
                zePass = 0.5f;
            } else if (zePass <= 2.5) {
                zePass = 0.75f;
            } else if (zePass <= 3.5) {
                zePass = 0.87f;
            } else if (zePass <= 4.5) {
                zePass = 0.93f;
            } else if (zePass <= 5.5) {
                zePass = 0.96f;
            } else if (zePass <= 6.5) {
                zePass = 0.98f;
            } else if (zePass <= 7.5) {
                zePass = 0.99f;
            } else {
                zePass = 1f;
            }
            pass[i] = zePass;
        }
        if (LOG_DEBUG_PLAYTICK) myLogD("pass:     " + Tonio.getStringFromFloatArray2digits2decimals(pass));

        return pass;
    }


}
