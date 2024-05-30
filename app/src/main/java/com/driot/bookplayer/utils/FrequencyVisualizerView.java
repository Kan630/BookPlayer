package com.driot.bookplayer.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.audiofx.Visualizer;
import android.util.AttributeSet;
import android.view.View;

public class FrequencyVisualizerView extends View {
    private byte[] fftBytes;
    private Paint paint = new Paint();
    private Visualizer visualizer;

    public FrequencyVisualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint.setColor(Color.GREEN);
        paint.setStrokeWidth(5f);
    }

    public void link(int audioSessionId) {
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
            }
        }, Visualizer.getMaxCaptureRate() / 2, false, true);
        visualizer.setEnabled(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (fftBytes == null) {
            return;
        }
        float width = getWidth();
        float height = getHeight();
        for (int i = 0; i < fftBytes.length / 2; i++) {
            float x = i * width / (fftBytes.length / 2);
            int magnitude = Math.abs(fftBytes[i * 2]) + Math.abs(fftBytes[i * 2 + 1]);
            float y = magnitude * height / 256;
            canvas.drawLine(x, height, x, height - y, paint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (visualizer != null) {
            visualizer.release();
        }
    }
}
