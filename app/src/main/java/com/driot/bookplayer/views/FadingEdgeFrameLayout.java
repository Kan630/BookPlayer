package com.driot.bookplayer.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;

/**
 * A FrameLayout that fades its children to (near-)transparent at the top.
 *
 * The fade is split into two zones:
 *
 *   ┌─────────────────────────┐  ← top (y=0)
 *   │   SOLID zone            │  fully faded — content at maxAlpha transparency
 *   │   (solidHeight px)      │
 *   ├─────────────────────────┤
 *   │   GRADIENT zone         │  fades from maxAlpha → fully opaque
 *   │   (fadeHeight px total) │
 *   ├─────────────────────────┤  ← y = fadeHeight
 *   │   normal content        │  untouched
 *   └─────────────────────────┘
 *
 * Tune via InsetHelper constants:
 *   FADE_HEIGHT_MULTIPLIER — total fade region height (× status bar height)
 *   FADE_SOLID_RATIO       — fraction of that region that is a hard cut (0.0 = no solid zone)
 *   FADE_MAX_ALPHA         — how transparent the strongest point is (0.0=invisible, 1.0=fully visible)
 */
public class FadingEdgeFrameLayout extends FrameLayout {

    private final Paint maskPaint;
    private LinearGradient shader;

    private int fadeHeight   = 0;
    private int solidHeight  = 0;
    private float maxAlpha   = 0f; // 0=fully transparent at peak, 1=no fade at all

    private int   lastWidth       = -1;
    private int   lastFadeHeight  = -1;
    private int   lastSolidHeight = -1;
    private float lastMaxAlpha    = -1f;

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
     * @param solidHeight height of the hard-cut zone at the top in px (can be 0)
     * @param maxAlpha    peak transparency of the fade: 0.0 = content fully hidden,
     *                    1.0 = content fully visible (no fade). E.g. 0.2 = 80% faded.
     */
    public void setFadeParams(@Px int fadeHeight,
                              @Px int solidHeight,
                              @FloatRange(from = 0.0, to = 1.0) float maxAlpha) {
        if (this.fadeHeight == fadeHeight
                && this.solidHeight == solidHeight
                && this.maxAlpha == maxAlpha) return;
        this.fadeHeight  = fadeHeight;
        this.solidHeight = solidHeight;
        this.maxAlpha    = maxAlpha;
        shader = null; // invalidate cached shader
        invalidate();
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);

        if (fadeHeight <= 0) return;

        if (shader == null
                || getWidth()   != lastWidth
                || fadeHeight   != lastFadeHeight
                || solidHeight  != lastSolidHeight
                || maxAlpha     != lastMaxAlpha) {

            lastWidth       = getWidth();
            lastFadeHeight  = fadeHeight;
            lastSolidHeight = solidHeight;
            lastMaxAlpha    = maxAlpha;

            // Peak color: BLACK with alpha = maxAlpha (DST_IN uses alpha channel only).
            // maxAlpha=0 → fully transparent (content invisible).
            // maxAlpha=1 → fully opaque (content unchanged, no fade).
            int peakColor = Color.argb(Math.round(maxAlpha * 255), 0, 0, 0);

            if (solidHeight > 0 && solidHeight < fadeHeight) {
                float solidFraction = (float) solidHeight / fadeHeight;
                shader = new LinearGradient(
                        0, 0,
                        0, fadeHeight,
                        new int[]  { peakColor,    peakColor,    Color.BLACK },
                        new float[]{ 0f, solidFraction, 1f },
                        Shader.TileMode.CLAMP);
            } else {
                shader = new LinearGradient(
                        0, 0,
                        0, fadeHeight,
                        peakColor,
                        Color.BLACK,
                        Shader.TileMode.CLAMP);
            }

            maskPaint.setShader(shader);
        }

        canvas.drawRect(0, 0, getWidth(), fadeHeight, maskPaint);
    }
}
