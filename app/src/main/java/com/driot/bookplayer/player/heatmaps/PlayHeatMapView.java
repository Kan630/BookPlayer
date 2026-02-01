package com.driot.bookplayer.player.heatmaps;

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
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float[] cursors = new float[0]; // positions 0..1 relative to duration (saved position = vertical line)
    private float playingCursor = -1f;      // 0..1 = current play position (triangle, left edge at position); <0 = none
    private final Paint cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint playingCursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int baseColor;
    private float triangleWidthPx;

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
        progressPaint.setStyle(Paint.Style.FILL);

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(ViewHelper.dp(context,1)); // 1dp de bordure
        borderPaint.setColor(Color.DKGRAY);     // couleur de la bordure

        int cursorSizeDp = context.getResources().getInteger(R.integer.heatmaps_cursor_size_dp);
        float cursorWidthPx = ViewHelper.dp(context, cursorSizeDp);
        cursorPaint.setStrokeWidth(cursorWidthPx);
        cursorPaint.setColor(0xFFFF5722); // orange/red for saved position

        triangleWidthPx = ViewHelper.dp(context, 10);
        playingCursorPaint.setStyle(Paint.Style.FILL);
        playingCursorPaint.setColor(0xFFFF5722); // same color, triangular play marker for current track
        playingCursorPaint.setAntiAlias(true);
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
                continue;
            }

            float left = i * bucketWidth;
            float right = left + bucketWidth;

            int alpha = (int) (aBase * Math.max(0f, Math.min(intensity, 1f)));

            int color = (alpha << 24) | (rBase << 16) | (gBase << 8) | bBase; //ex : 0xFF6496C8.  pour alpha=255, r=100, g=150, b=200
            progressPaint.setColor(color);
            progressPaint.setAntiAlias(false);

            canvas.drawRect(left, 0, right, height, progressPaint);

        }

        // Draw saved-position cursors (vertical lines)
        for (float c : cursors) {
            if (c < 0f || c >= 1f) continue; // skip 100%
            float x = c * width;
            canvas.drawLine(x, 0, x, height, cursorPaint);
        }

        // Draw playing cursor: triangle with tip pointing right (play shape), base at position
        if (playingCursor >= 0f && playingCursor < 1f && height > 0) {
            float xLeft = playingCursor * width;
            float xRight = Math.min(xLeft + triangleWidthPx, width);
            float halfH = height * 0.5f;
            Path tri = new Path();
            tri.moveTo(xLeft, 0);       // base left = position
            tri.lineTo(xLeft, height);
            tri.lineTo(xRight, halfH);  // tip on the right
            tri.close();
            canvas.drawPath(tri, playingCursorPaint);
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

    /** Position 0..1 for the currently playing track (triangle marker). Left edge of triangle = position. Use < 0 to hide. */
    public void setPlayingCursor(float position0to1) {
        if (position0to1 != playingCursor) {
            playingCursor = position0to1;
            invalidate();
        }
    }

}
