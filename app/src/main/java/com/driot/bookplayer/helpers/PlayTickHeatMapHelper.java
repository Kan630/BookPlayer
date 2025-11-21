package com.driot.bookplayer.helpers;

import com.driot.bookplayer.objects.PlayTickBucket;

import java.util.List;

public class PlayTickHeatMapHelper {


    public static float[] computeIntensities2(List<PlayTickBucket> buckets,
                                             long durationMs,
                                             int nbBuckets) {
        float[] result = new float[nbBuckets];
        if (nbBuckets <= 0 || durationMs <= 0) {
            return result;
        }
        long[] tickCounts = new long[nbBuckets];

        // 1) Populate tickCounts from SQL buckets
        if (buckets != null) {
            for (PlayTickBucket b : buckets) {
                int idx = (int) b.bucket;
                if (idx < 0 || idx >= nbBuckets) continue;
                tickCounts[idx] = b.ticks;
            }
        }

        // 2) Compute base intensities from "number of passes", with an absolute mapping
        float[] base = new float[nbBuckets];

        // duration per bucket in ms and seconds
        double bucketDurationMs = (double) durationMs / (double) nbBuckets;
        if (bucketDurationMs <= 0.0) {
            bucketDurationMs = 1.0;
        }
        double bucketDurationSec = bucketDurationMs / 1000.0;

        for (int i = 0; i < nbBuckets; i++) {
            long ticks = tickCounts[i];
            if (ticks <= 0) {
                base[i] = 0f;
            } else {
                // passes = how many times, on average, this segment was fully listened
                /*
                double passes = ticks / bucketDurationSec; // ~1 for one full pass
                double intensity = passes / passesForFullColor; // 3 passes -> 1.0
                if (intensity > 1.0) intensity = 1.0;
                if (intensity < 0.0) intensity = 0.0;
                base[i] = (float) intensity;
                 */
                base[i] = 1;
            }
        }

        System.arraycopy(base, 0, result, 0, nbBuckets);
        return result;

    }







        /**
         * - Builds base intensities from tickCounts using an absolute mapping:
         *   1 pass -> fixed intensity, 2 passes -> darker, etc.
         * - Fills small gaps of zeros between non-zero buckets (<= maxGapBuckets)
         * - Applies local smoothing inside listened segments
         * - Never renormalizes based on max; 1-pass color is stable
         */
    public static float[] computeIntensities(List<PlayTickBucket> buckets,
                                             long durationMs,
                                             int nbBuckets) {
        float[] result = new float[nbBuckets];
        if (nbBuckets <= 0 || durationMs <= 0) {
            return result;
        }

        final int maxGapBuckets = 3;      // used to fill small holes
        final int smoothRadius  = 5;      // +/- buckets smoothing
        final float passesForFullColor = 3f; // 3 full passes => intensity 1.0

        long[] tickCounts = new long[nbBuckets];

        // 1) Populate tickCounts from SQL buckets
        if (buckets != null) {
            for (PlayTickBucket b : buckets) {
                int idx = (int) b.bucket;
                if (idx < 0 || idx >= nbBuckets) continue;
                tickCounts[idx] = b.ticks;
            }
        }

        // 2) Compute base intensities from "number of passes", with an absolute mapping
        float[] base = new float[nbBuckets];

        // duration per bucket in ms and seconds
        double bucketDurationMs = (double) durationMs / (double) nbBuckets;
        if (bucketDurationMs <= 0.0) {
            bucketDurationMs = 1.0;
        }
        double bucketDurationSec = bucketDurationMs / 1000.0;

        for (int i = 0; i < nbBuckets; i++) {
            long ticks = tickCounts[i];
            if (ticks <= 0) {
                base[i] = 0f;
            } else {
                // passes = how many times, on average, this segment was fully listened
                double passes = ticks / bucketDurationSec; // ~1 for one full pass
                double intensity = passes / passesForFullColor; // 3 passes -> 1.0
                if (intensity > 1.0) intensity = 1.0;
                if (intensity < 0.0) intensity = 0.0;
                base[i] = (float) intensity;
            }
        }

        // 3) Fill small gaps of zeros between non-zero neighbors (continuity)
        float[] filled = new float[nbBuckets];
        System.arraycopy(base, 0, filled, 0, nbBuckets);

        int i = 0;
        while (i < nbBuckets) {
            if (filled[i] > 0f) {
                i++;
                continue;
            }

            int start = i;
            while (i < nbBuckets && filled[i] == 0f) {
                i++;
            }
            int end = i - 1;
            int gapLen = end - start + 1;

            float left  = (start > 0) ? filled[start - 1] : 0f;
            float right = (end < nbBuckets - 1) ? filled[end + 1] : 0f;

            if (gapLen <= maxGapBuckets && left > 0f && right > 0f) {
                // Fill with min of neighbors (or (left+right)/2f if you prefer)
                float fill = Math.min(left, right);
                for (int j = start; j <= end; j++) {
                    filled[j] = fill;
                }
            }
        }

        // 4) Smoothing inside listened segments only (no renorm)
        float[] smooth = new float[nbBuckets];

        int idx = 0;
        while (idx < nbBuckets) {
            // skip zeros => silent zone
            if (filled[idx] <= 0f) {
                idx++;
                continue;
            }

            // start of a listened segment
            int segStart = idx;
            while (idx < nbBuckets && filled[idx] > 0f) {
                idx++;
            }
            int segEnd = idx - 1;

            // smoothing with window +/- smoothRadius inside [segStart, segEnd]
            for (int j = segStart; j <= segEnd; j++) {
                float sum = 0f;
                float sumW = 0f;

                int from = Math.max(segStart, j - smoothRadius);
                int to   = Math.min(segEnd, j + smoothRadius);

                for (int k2 = from; k2 <= to; k2++) {
                    int dist = Math.abs(k2 - j);
                    int w = smoothRadius + 1 - dist; // triangle kernel: e.g. 6,5,4,...

                    sum  += filled[k2] * w;
                    sumW += w;
                }

                float v = (sumW > 0f) ? (sum / sumW) : filled[j];
                // clamp 0..1, but DO NOT renormalize per segment
                if (v > 1f) v = 1f;
                if (v < 0f) v = 0f;
                smooth[j] = v;
            }
        }

        // 5) Copy smoothed result; zeros remain zeros outside segments
        System.arraycopy(smooth, 0, result, 0, nbBuckets);
        return result;
    }
}
