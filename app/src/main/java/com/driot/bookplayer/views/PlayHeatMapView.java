package com.driot.bookplayer.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.driot.bookplayer.R;

public class PlayHeatMapView extends View {

    private float[] intensities = new float[0]; // 0..1
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int baseColor;

    public PlayHeatMapView(Context context) {
        super(context);
        init(context, null);
    }

    public PlayHeatMapView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public PlayHeatMapView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        int defaultColor = 0xFF4CAF50; // vert, fallback
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PlayHeatMapView);
            baseColor = a.getColor(R.styleable.PlayHeatMapView_heatmapColor, defaultColor);
            a.recycle();
        } else {
            baseColor = defaultColor;
        }
        paint.setStyle(Paint.Style.FILL);
    }

    /**
     * Met à jour les intensités (0..1) et redraw.
     */
    public void setIntensities(float[] intensities) {
        if (intensities == null) {
            this.intensities = new float[0];
        } else {
            this.intensities = intensities;
        }
        invalidate();
    }

    public float[] getIntensities() {
        return intensities;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        if (width <= 0 || height <= 0 || intensities.length == 0) {
            return;
        }

        int nbBuckets = intensities.length;
        float bucketWidth = (float) width / nbBuckets;

        // décomposer la couleur de base
        int aBase = (baseColor >> 24) & 0xFF;
        int rBase = (baseColor >> 16) & 0xFF;
        int gBase = (baseColor >> 8) & 0xFF;
        int bBase = baseColor & 0xFF;

        for (int i = 0; i < nbBuckets; i++) {
            float intensity = intensities[i]; // 0..1
            if (intensity <= 0f) {
                continue; // pas dessiné
            }

            float left = i * bucketWidth;
            float right = left + bucketWidth;

            // alpha proportionnel à l'intensité
            int alpha = (int) (aBase * Math.max(0f, Math.min(intensity, 1f)));

            int color = (alpha << 24) | (rBase << 16) | (gBase << 8) | bBase;
            paint.setColor(color);

            canvas.drawRect(left, 0, right, height, paint);
        }
    }
}
