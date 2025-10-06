    package com.driot.bookplayer.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.media.audiofx.Visualizer;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;

import com.driot.bookplayer.utils.KanLogger;

/**
 * FrequencyVisualizerView
 *
 * - Fixes "left busy / right dead" by using only POSITIVE frequency bins (first half of FFT).
 * - Proper magnitude = sqrt(re^2 + im^2).
 * - Even or log-ish bin grouping that fully covers the spectrum.
 * - Noise gate + light auto-gain + EMA smoothing.
 * - Two modes:
 *      BARS   -> classic vertical bars, evenly spread across width
 *      RADIAL -> a single CLOSED PATH (circle) whose radius deforms with the spectrum (no spokes)
 * - Two paints for the radial mode:
 *      ringPaint     -> the thin/normal deforming circle (stroke)
 *      outlinePaint  -> a thicker “outer” line you can tune independently
 */
public class FrequencyVisualizerView extends View {

    // Compile-time kill switch (set to true to test without any Visualizer code)
    private static final boolean VISUALIZER_DISABLED = false;

    // ───────────────────────────────────────────
    //                 KNOBS
    // ───────────────────────────────────────────
    /**
     * How many visual “bars” (and also how many angular samples for RADIAL).
     * Higher → smoother circle / more bars, but more draw cost.
     * 64–128 is a good sweet spot.
     */
    private static final int NB_BARS = 96;

    /**
     * Smoothing factor for the per-bar envelope (0..1).
     * 0.0 = raw/flickery. 0.5 = balanced. 0.8+ = sluggish.
     */
    private static final float SMOOTHING = 0.45f;

    /**
     * Noise gate on overall energy (0..1).
     * If average spectral energy is below this, we fade to zero.
     * Raise if you want silence to flatten more aggressively.
     */
    private static final float NOISE_GATE = 0.012f;

    /**
     * Auto-gain clamp: prevents gain from going too low/high.
     * If your visuals look tiny at normal listening volume, raise GAIN_MAX a bit.
     */
    private static final float GAIN_MIN = 0.6f;
    private static final float GAIN_MAX = 3.5f;

    /**
     * Frequency bin distribution curve.
     * 1.0 = linear (even across the spectrum).
     * >1  = more detail in bass (common for music). 1.3–1.6 is typical.
     */
    private static final float FREQ_WARP_EXPONENT = 0.5f;

    /**
     * In RADIAL mode, base circle radius as a fraction of the min(width, height).
     * 0.5–0.7 is typical; go smaller if you want more headroom for deformation.
     */
    private static final float RADIAL_BASE_RADIUS = 0.6f;

    /**
     * How much the circle deforms under strong signal (0..1 of base radius).
     * 0.3–0.8 is typical. Larger = more “pulsing”.
     */
    private static final float RADIAL_DEFORM_SCALE = 0.65f;

    /**
     * Stroke widths for radial mode:
     * - ringPaintWidth: thickness of the deforming circle itself.
     * - outlinePaintWidth: thickness of the outer “accent” line (drawn on top).
     * Tweak these to taste.
     */
    private static final float RADIAL_RING_STROKE_DP = 2.0f;
    private static final float RADIAL_OUTLINE_STROKE_DP = 5.0f;

    /**
     * In BARS mode, spacing between bars (px). 0 = no gaps.
     */
    private static final float BAR_SPACING_PX = 1f;

    // ───────────────────────────────────────────
    //                 MODE
    // ───────────────────────────────────────────
    public enum Mode { BARS, RADIAL }
    private Mode mode = Mode.BARS;
    public void setMode(Mode m) { mode = m; invalidate(); }

    // ───────────────────────────────────────────
    //              RUNTIME STATE
    // ───────────────────────────────────────────
    private Visualizer visualizer;
    private byte[] fftBytes;              // raw [re0, im0, re1, im1, ...]
    private final float[] bars = new float[NB_BARS];  // smoothed 0..1 values

    // ───────────────────────────────────────────
    //                 PAINTS
    // ───────────────────────────────────────────
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public FrequencyVisualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPaints();
    }

    private void initPaints() {
        // Pull a primary color from theme
        TypedValue tv = new TypedValue();
        getContext().getTheme().resolveAttribute(android.R.attr.colorPrimary, tv, true);
        int primary = tv.data;

        // Bars
        barPaint.setColor(primary);
        barPaint.setStyle(Paint.Style.STROKE);
        barPaint.setStrokeWidth(5f);

        // Radial main ring (deforming circle)
        ringPaint.setColor(primary);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp(RADIAL_RING_STROKE_DP));

        // Radial outline (thicker accent line drawn on top)
        outlinePaint.setColor(primary);
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(dp(RADIAL_OUTLINE_STROKE_DP));
        outlinePaint.setAlpha(180); // slightly translucent accent
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    /** Keep your original entry point name */
    public void link_toto(int audioSessionId) {
        if (VISUALIZER_DISABLED) {
            myLog("Visualizer disabled (test mode)");
            // Make sure we don’t hold any native resources
            if (visualizer != null) { try { visualizer.release(); } catch (Throwable ignored) {} }
            visualizer = null;
            fftBytes = null;
            invalidate();
            return;
        }
        if (visualizer == null) link(audioSessionId);
    }

    private void link(int audioSessionId) {
        if (VISUALIZER_DISABLED) return;
        myLog("link - audioSessionId = [" + audioSessionId + "]");
        try {
            visualizer = new Visualizer(audioSessionId);
            int maxSize = Visualizer.getCaptureSizeRange()[1];
            visualizer.setCaptureSize(maxSize);

            try {
                // Reflect device volume (as-played)
                visualizer.setScalingMode(Visualizer.SCALING_MODE_AS_PLAYED);
            } catch (Throwable ignore) {}

            visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override public void onWaveFormDataCapture(Visualizer v, byte[] wf, int sr) { /* unused */ }

                @Override
                public void onFftDataCapture(Visualizer v, byte[] fft, int samplingRate) {
                    fftBytes = fft; // interleaved complex bins
                    postInvalidateOnAnimation();
                }
            }, Visualizer.getMaxCaptureRate(), false, true);

            visualizer.setEnabled(true);
        } catch (Throwable t) {
            myLogE("Visualizer init failed: " + t);
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (VISUALIZER_DISABLED || fftBytes == null) {
            drawIdle(canvas);
            return;
        }

        // Compute smoothed 0..1 bar values from POSITIVE freqs only
        computeBarsFromPositiveSpectrum(fftBytes, bars);

        if (mode == Mode.RADIAL) {
            drawRadialDeformingCircle(canvas, bars);
        } else {
            drawBars(canvas, bars);
        }
    }

    /**
     * Core: map FFT -> NB_BARS values.
     * - Only use the positive-frequency half (first half of complex bins).
     * - Fully cover the range (no dead right side).
     * - Optional log-ish warping to emphasize bass.
     * - Apply noise gate, auto-gain, EMA smoothing.
     */
    private void computeBarsFromPositiveSpectrum(byte[] fft, float[] out) {
        final int complexBins = fft.length / 2;   // pairs (re,im)
        if (complexBins <= 2) {                   // corrupted/too small
            for (int i = 0; i < out.length; i++) out[i] *= 0.9f;
            return;
        }

        // Positive frequencies are bins 1..(Npos-1), where Npos = complexBins/2
        // (bin 0 is DC, bins Npos..end are the mirrored negative freqs for real signals)
        final int Npos = complexBins / 2;
        if (Npos <= 1) {
            for (int i = 0; i < out.length; i++) out[i] *= 0.9f;
            return;
        }

        // Gather global stats (max + avg energy) over positive range
        float maxMag = 1e-6f;
        float energy = 0f;
        for (int i = 1; i < Npos; i++) {
            int re = fft[2 * i];
            int im = fft[2 * i + 1];
            float m = (float) Math.hypot(re, im);
            energy += m;
            if (m > maxMag) maxMag = m;
        }
        energy /= Math.max(1, (Npos - 1));
        float energyNorm = energy / 128f;

        // Noise gate
        if (energyNorm < NOISE_GATE) {
            for (int i = 0; i < out.length; i++) out[i] *= 0.9f; // gentle fade
            return;
        }

        // Auto-gain (clamped)
        float gain = 96f / maxMag; // heuristic that maps typical mags near 1
        if (gain < GAIN_MIN) gain = GAIN_MIN;
        if (gain > GAIN_MAX) gain = GAIN_MAX;

        // Bin grouping across [1..Npos-1]
        final int srcBins = Npos - 1;
        for (int b = 0; b < NB_BARS; b++) {
            // linear position 0..1
            float t0 = (float) b / NB_BARS;
            float t1 = (float) (b + 1) / NB_BARS;

            // warp (emphasize bass) but still fully cover end of range
            float w0 = (float) Math.pow(t0, FREQ_WARP_EXPONENT);
            float w1 = (float) Math.pow(t1, FREQ_WARP_EXPONENT);

            int start = 1 + Math.min(srcBins - 1, Math.max(0, Math.round(w0 * (srcBins - 1))));
            int end   = 1 + Math.min(srcBins,     Math.max(1, Math.round(w1 * (srcBins - 1)) + 1));
            if (end <= start) end = Math.min(start + 1, 1 + srcBins);

            float sum = 0f;
            int count = 0;
            for (int k = start; k < end; k++) {
                int re = fft[2 * k];
                int im = fft[2 * k + 1];
                sum += (float) Math.hypot(re, im);
                count++;
            }
            float avg = (count > 0) ? (sum / count) : 0f;

            // Scale approx to 0..1, clamp, and smooth (EMA)
            float value = Math.max(0f, Math.min(1f, (avg * gain) / 128f));
            out[b] = SMOOTHING * out[b] + (1f - SMOOTHING) * value;
        }
    }

    // ───────────────────────────────────────────
    //                 DRAWING
    // ───────────────────────────────────────────

    private void drawBars(Canvas canvas, float[] vals) {
        float w = getWidth();
        float h = getHeight();
        float barW = Math.max(1f, (w / NB_BARS) - BAR_SPACING_PX);

        for (int i = 0; i < NB_BARS; i++) {
            float x = i * (barW + BAR_SPACING_PX) + BAR_SPACING_PX * 0.5f;
            float bh = vals[i] * h;
            canvas.drawLine(x, h, x, h - bh, barPaint);
        }
    }

    /**
     * Draw a CLOSED PATH whose radius varies with angle.
     * - Base radius = RADIAL_BASE_RADIUS * min(w, h)/2.
     * - Added radius = vals[i] * (baseRadius * RADIAL_DEFORM_SCALE).
     * - Then we draw TWO strokes: the normal ring + a thicker outline on top.
     */
    private void drawRadialDeformingCircle(Canvas canvas, float[] vals) {
        float cx = getWidth() * 0.5f;
        float cy = getHeight() * 0.5f;
        float baseR = Math.min(cx, cy) * RADIAL_BASE_RADIUS;

        // Build the closed path
        Path ring = new Path();
        boolean started = false;

        for (int i = 0; i <= NB_BARS; i++) {
            // wrap around at the end to close the path
            int idx = (i == NB_BARS) ? 0 : i;
            // angle: 0 at RIGHT, increasing CCW; we rotate so "top" is included naturally
            float angle = (float) (2 * Math.PI * idx / NB_BARS);

            // deformation proportional to value
            float deform = vals[idx] * (baseR * RADIAL_DEFORM_SCALE);
            float r = baseR + deform;

            float x = cx + (float) Math.cos(angle) * r;
            float y = cy + (float) Math.sin(angle) * r;

            if (!started) {
                ring.moveTo(x, y);
                started = true;
            } else {
                ring.lineTo(x, y);
            }
        }

        // Draw the main ring first
        canvas.drawPath(ring, ringPaint);
        // Add the thicker outline for presence
        canvas.drawPath(ring, outlinePaint);
    }

    private void drawIdle(Canvas canvas) {
        if (mode == Mode.RADIAL) {
            float cx = getWidth() * 0.5f, cy = getHeight() * 0.5f;
            float r = Math.min(cx, cy) * RADIAL_BASE_RADIUS;
            canvas.drawCircle(cx, cy, r, ringPaint);
            canvas.drawCircle(cx, cy, r, outlinePaint);
        } else {
            float h = getHeight();
            canvas.drawLine(0, h - 2, getWidth(), h - 2, barPaint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        myLog("onDetachedFromWindow()");
        super.onDetachedFromWindow();
        if (visualizer != null) {
            try { visualizer.release(); } catch (Throwable ignored) {}
            visualizer = null;
        }
    }

    // ───────────────────────────────────────────
    //               LOGGING HELPERS
    // ───────────────────────────────────────────
    private void myLog(String str)  { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
}
