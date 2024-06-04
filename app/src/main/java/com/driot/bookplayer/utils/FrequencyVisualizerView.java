package com.driot.bookplayer.utils;

import static com.google.android.material.color.MaterialColors.getColor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.audiofx.Visualizer;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.driot.bookplayer.R;
import com.driot.tonylib.KanLogger;



public class FrequencyVisualizerView extends View {

    private static final int nbHistoBar = 128;
    private byte[] fftBytes;
    private byte[] fftBytes2 = new byte[nbHistoBar];
    private Paint paint = new Paint();
    private Visualizer visualizer;
    private int minFrequencyIndex, maxFrequencyIndex;
    public FrequencyVisualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        myLog("init()");
        TypedValue typedValue = new TypedValue();
        this.getContext().getTheme().resolveAttribute(android.R.attr.colorPrimary, typedValue, true);
        int primaryColor = typedValue.data;
        paint.setColor(primaryColor);
        paint.setStrokeWidth(5f);
    }

    public void link_toto(int audioSessionId) {
        if (visualizer == null) {
            link(audioSessionId);
        }
    }

    private void link(int audioSessionId) {
        myLog("link - audioSessionId = [" + audioSessionId + "]");
        visualizer = new Visualizer(audioSessionId);
        visualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
        visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
            @Override
            public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) {
                // Not used in this example
            }

            @Override
            public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
                fftBytes = fft;
                invalidate();
                System.arraycopy(fftBytes, 0, fftBytes2, 0, nbHistoBar);
            }
        }, Visualizer.getMaxCaptureRate() / 2, false, true);
        visualizer.setEnabled(true);

        // Calculate the frequency indexes for the range 50Hz to 500Hz
        int captureSize = visualizer.getCaptureSize();
        int samplingRate = visualizer.getSamplingRate();
        minFrequencyIndex = (50 * captureSize) / samplingRate;
        maxFrequencyIndex = (500 * captureSize) / samplingRate;
        myLog("Visualizer => captureSize = [" + captureSize + "]");
        myLog("Visualizer => samplingRate = [" + samplingRate + "]");
        myLog("Visualizer => Frequency indexes = [" + minFrequencyIndex + "]-[" + maxFrequencyIndex + "]");

    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (fftBytes2 == null) {
            return;
        }
        //myLog("Visualizer => fftBytes.length = [" + fftBytes.length + "]"); 1024
        float width = getWidth();
        float height = getHeight();
        int halfLength = fftBytes2.length / 2;
/*
        // Draw only the frequencies between 50Hz and 500Hz
        for (int i = minFrequencyIndex; i <= maxFrequencyIndex && i < halfLength; i++) {
            float x = (i - minFrequencyIndex) * width / (maxFrequencyIndex - minFrequencyIndex);
            int magnitude = Math.abs(fftBytes[i * 2]) + Math.abs(fftBytes[i * 2 + 1]);
            float y = magnitude * height / 256;
            canvas.drawLine(x, height, x, height - y, paint);
        }

 */

        for (int i = 0; i < fftBytes2.length / 2; i++) {
            float x = i * width / (fftBytes2.length / 2);
            int magnitude = Math.abs(fftBytes2[i * 2]) + Math.abs(fftBytes2[i * 2 + 1]);
            float y = magnitude * height / 256;
            canvas.drawLine(x, height, x, height - y, paint);
        }

    }

    @Override
    protected void onDetachedFromWindow() {
        myLog("onDetachedFromWindow()");
        super.onDetachedFromWindow();
        if (visualizer != null) {
            visualizer.release();
        }
    }
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }

}
