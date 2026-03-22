package com.driot.bookplayer.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.graphics.Color;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;

/**
 * A FrameLayout that fades its children to transparent at the top.
 *
 * The fade is split into two zones:
 *
 *   ┌─────────────────────────┐  ← top (y=0)
 *   │   SOLID zone            │  fully transparent — content completely hidden
 *   │   (solidHeight px)      │
 *   ├─────────────────────────┤
 *   │   GRADIENT zone         │  transparent → opaque — content fades in
 *   │   (fadeHeight px total) │
 *   ├─────────────────────────┤  ← y = fadeHeight
 *   │   normal content        │  fully opaque — no masking
 *   └─────────────────────────┘
 *
 * Tune via InsetHelper constants:
 *   FADE_HEIGHT_MULTIPLIER — how tall the entire fade region is (× status bar height)
 *   FADE_SOLID_RATIO       — fraction of that region that is a hard cut (0.0 = no solid zone)
 */
public class FadingEdgeFrameLayout extends FrameLayout {

    private final Paint maskPaint;
    private LinearGradient shader;

    private int fadeHeight = 0;
    private int solidHeight = 0;
    private int lastWidth = -1;
    private int lastFadeHeight = -1;
    private int lastSolidHeight = -1;

    public FadingEdgeFrameLayout(@NonNull Context context) {
        this(context, null);
    }

    public FadingEdgeFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FadingEdgeFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));

        // Hardware layer is mandatory: DST_IN composites against this view's
        // own layer, not the entire screen. Without it the effect is invisible.
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    /**
     * @param fadeHeight  total height of the fade region in px (solid + gradient)
     * @param solidHeight height of the fully-transparent solid zone at the top, in px
     */
    public void setFadeParams(@Px int fadeHeight, @Px int solidHeight) {
        if (this.fadeHeight == fadeHeight && this.solidHeight == solidHeight) return;
        this.fadeHeight = fadeHeight;
        this.solidHeight = solidHeight;
        shader = null; // invalidate cached shader
        invalidate();
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);

        if (fadeHeight <= 0) return;

        // Rebuild shader only when dimensions or params change.
        if (shader == null
                || getWidth() != lastWidth
                || fadeHeight != lastFadeHeight
                || solidHeight != lastSolidHeight) {

            lastWidth       = getWidth();
            lastFadeHeight  = fadeHeight;
            lastSolidHeight = solidHeight;

            if (solidHeight > 0 && solidHeight < fadeHeight) {
                // Three-stop gradient:
                //   y=0            → TRANSPARENT (solid zone starts)
                //   y=solidHeight  → TRANSPARENT (solid zone ends, gradient begins)
                //   y=fadeHeight   → BLACK       (gradient ends, content fully visible)
                float solidFraction = (float) solidHeight / fadeHeight;
                shader = new LinearGradient(
                        0, 0,
                        0, fadeHeight,
                        new int[]  { Color.TRANSPARENT, Color.TRANSPARENT, Color.BLACK },
                        new float[]{ 0f, solidFraction, 1f },
                        Shader.TileMode.CLAMP);
            } else {
                // No solid zone — plain two-stop gradient.
                shader = new LinearGradient(
                        0, 0,
                        0, fadeHeight,
                        Color.TRANSPARENT,
                        Color.BLACK,
                        Shader.TileMode.CLAMP);
            }

            maskPaint.setShader(shader);
        }

        canvas.drawRect(0, 0, getWidth(), fadeHeight, maskPaint);
    }
}
