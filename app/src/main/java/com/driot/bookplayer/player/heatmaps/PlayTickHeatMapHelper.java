package com.driot.bookplayer.player.heatmaps;

import java.util.List;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import com.driot.bookplayer.utils.Tonio;

public class PlayTickHeatMapHelper {

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

        final int smoothRadius  = 3;      // +/- buckets smoothing

        final float nb_tick_for_1_pass = (float) durationMs / 1000 / nbBuckets;
        //myLogD("nb_tick_for_1_pass=" + nb_tick_for_1_pass);

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
/*
        if (Var.HEATMAP_PROGRESSBAR_BUCKET_SIZE == nbBuckets) {
            myLogD(nbBuckets + " buckets => smoothing array");
            smoothed = smoothTicks(tickCounts, nbBuckets, smoothRadius);

        } else {
            myLogD(nbBuckets + " buckets => no smoothing");
            for (int i = 0; i < tickCounts.length; i++) {
                smoothed[i] = tickCounts[i];
            }
        }
        */

        for (int i = 0; i < tickCounts.length; i++) {
            smoothed[i] = tickCounts[i];
        }
        //myLogD("smoothed: " + Tonio.getStringFromFloatArray2digits2decimals(smoothed));

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
        //myLogD("pass:     " + Tonio.getStringFromFloatArray2digits2decimals(pass));

        return pass;





/*
        // 2) Compute base intensities from "number of passes", with an absolute mapping
        float[] base = new float[nbBuckets];

        // duration per bucket in ms and seconds
        double bucketDurationMs = (double) durationMs / (double) nbBuckets;
        if (bucketDurationMs <= 0.0) {
            bucketDurationMs = 1.0;
        }
        double bucketDurationSec = bucketDurationMs / 1000.0;
        //myLogD("bucketDurationMs=" + bucketDurationMs + " bucketDurationSec=" + bucketDurationSec);


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
/*
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

 */



    }



    public static float[] smoothTicks(long[] tickCounts,
                                      int nbBuckets,
                                      int smoothRadius) {

        float[] smooth = new float[nbBuckets];

        if (tickCounts == null || nbBuckets <= 0 || smoothRadius <= 0) {
            return smooth;
        }

        int idx = 0;
        while (idx < nbBuckets) {

            // skip silent zone
            if (tickCounts[idx] <= 0L) {
                idx++;
                continue;
            }

            // start of listened segment
            int segStart = idx;
            while (idx < nbBuckets && tickCounts[idx] > 0L) {
                idx++;
            }
            int segEnd = idx - 1;

            // smoothing inside [segStart, segEnd]
            for (int j = segStart; j <= segEnd; j++) {

                float sum  = 0f;
                float sumW = 0f;

                int from = Math.max(segStart, j - smoothRadius);
                int to   = Math.min(segEnd,   j + smoothRadius);

                for (int k = from; k <= to; k++) {
                    int dist = Math.abs(k - j);
                    float w = (float) (smoothRadius + 1 - dist); // triangle kernel

                    sum  += (float) tickCounts[k] * w;
                    sumW += w;
                }

                float v = (sumW > 0f) ? (sum / sumW) : (float) tickCounts[j];

                // clamp only (no renorm)
                if (v < 0f) v = 0f;
                if (v > 1f) v = 1f;

                smooth[j] = v;
            }
        }

        return smooth;
    }

    public static float[] gaussianSmooth(int[] input,
                                         int radius,
                                         float sigma) {

        int n = input.length;
        float[] output = new float[n];

        if (n == 0 || radius <= 0 || sigma <= 0f) {
            return output;
        }

        // 1) Build Gaussian kernel
        int size = radius * 2 + 1;
        float[] kernel = new float[size];

        float sum = 0f;
        float sigma2 = 2f * sigma * sigma;

        for (int i = -radius; i <= radius; i++) {
            float v = (float) Math.exp(-(i * i) / sigma2);
            kernel[i + radius] = v;
            sum += v;
        }

        // Normalize kernel
        for (int i = 0; i < size; i++) {
            kernel[i] /= sum;
        }

        // 2) Convolution
        for (int i = 0; i < n; i++) {
            float acc = 0f;

            int from = Math.max(0, i - radius);
            int to   = Math.min(n - 1, i + radius);

            for (int j = from; j <= to; j++) {
                int k = j - i + radius;
                acc += input[j] * kernel[k];
            }

            output[i] = acc;
        }

        return output;
    }


}
