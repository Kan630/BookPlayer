package com.driot.bookplayer.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.helpers.ViewHelper;

public class PlayHeatMapView extends View {

    private float[] intensities = new float[0]; // 0..1
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float[] cursors = new float[0]; // positions 0..1 relative to duration
    private final Paint cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
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

        cursorPaint.setColor(0xFFFF5722); // orange for last-listened
        cursorPaint.setStrokeWidth(ViewHelper.dp(context, 2)); // 2dp line

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(ViewHelper.dp(context,1)); // 1dp de bordure
        borderPaint.setColor(Color.DKGRAY);     // couleur de la bordure
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        float cornerRadius = ViewHelper.dp(getContext(), 4);


        // create rounded rectangle path
        Path clipPath = new Path();
        clipPath.addRoundRect(0, 0, width, height, cornerRadius, cornerRadius, Path.Direction.CW);

        // clip everything inside the rounded rect
        canvas.save();
        canvas.clipPath(clipPath);

        //canvas.drawRoundRect(0, 0, width, height, cornerRadius, cornerRadius, borderPaint);

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

            int alpha = (int) (aBase * Math.max(0f, Math.min(intensity, 1f)));

            int color = (alpha << 24) | (rBase << 16) | (gBase << 8) | bBase; //ex : 0xFF6496C8.  pour alpha=255, r=100, g=150, b=200
            paint.setColor(color);

            //canvas.drawColor(Color.TRANSPARENT);
            canvas.drawRoundRect(left, 0, right, height, cornerRadius, cornerRadius, paint);

        }

        // Draw cursors inside clip
        for (float c : cursors) {
            if (c < 0f || c >= 1f) continue; // skip 100%
            float x = c * width;
            canvas.drawLine(x, 0, x, height, cursorPaint);
        }

        canvas.restore(); // remove clipping

        // Draw border **on top**
        borderPaint.setStyle(Paint.Style.STROKE);
        canvas.drawRoundRect(0, 0, width, height, cornerRadius, cornerRadius, borderPaint);
    }

    //-----------------

    public void setIntensities(float[] intensities) {
        if (intensities == null) {
            this.intensities = new float[0];
        } else {
            this.intensities = intensities;
        }
        invalidate();
    }

    public void setCursors(float[] cursors) {
        if (cursors == null) {
            this.cursors = new float[0];
        } else {
            this.cursors = cursors;
        }
        invalidate();
    }

}
