package com.driot.bookplayer.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.audiofx.Visualizer;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;

import com.driot.tonylib.KanLogger;



public class FrequencyVisualizerView extends View {

    private static final int nbBars = 128;
    private byte[] fftBytes;
    private final byte[] fftBytes2 = new byte[nbBars];
    private final Paint paint = new Paint();
    private Visualizer visualizer;
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
                System.arraycopy(fftBytes, 0, fftBytes2, 0, nbBars);
            }
        }, Visualizer.getMaxCaptureRate() / 2, false, true);
        visualizer.setEnabled(true);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (fftBytes2 == null) {
            return;
        }

        float width = getWidth();
        float height = getHeight();

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
