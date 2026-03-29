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
 * A FrameLayout that fades its children to (near-)transparent at the top and/or bottom.
 *
 * TOP fade (behind status bar):
 *   ┌─────────────────────────┐  ← y=0
 *   │   SOLID zone            │  fully faded (maxAlpha transparent)
 *   │   (solidHeight px)      │
 *   ├─────────────────────────┤
 *   │   GRADIENT zone         │  fades maxAlpha → fully opaque
 *   │   (fadeHeight px total) │
 *   ├─────────────────────────┤  ← y = fadeHeight
 *   │   normal content        │
 *   └─────────────────────────┘
 *
 * BOTTOM fade (behind sys nav bar) — mirror of the top:
 *   ┌─────────────────────────┐
 *   │   normal content        │
 *   ├─────────────────────────┤  ← y = height - bottomFadeHeight
 *   │   GRADIENT zone         │  fades fully opaque → maxAlpha
 *   ├─────────────────────────┤
 *   │   SOLID zone            │  fully faded (maxAlpha transparent)
 *   └─────────────────────────┘  ← y = height
 *
 * Tune via InsetHelper constants:
 *   FADE_HEIGHT_MULTIPLIER — total fade region height (× bar height)
 *   FADE_SOLID_RATIO       — fraction that is a hard cut
 *   FADE_MAX_ALPHA         — peak transparency (0=invisible, 1=no fade)
 */
public class FadingEdgeFrameLayout extends FrameLayout {

    // ── Top fade ──────────────────────────────────────────────────────────────
    private final Paint topMaskPaint;
    private LinearGradient topShader;

    private int   topFadeHeight   = 0;
    private int   topSolidHeight  = 0;
    private float topMaxAlpha     = 0f;

    private int   lastWidth          = -1;
    private int   lastTopFadeHeight  = -1;
    private int   lastTopSolidHeight = -1;
    private float lastTopMaxAlpha    = -1f;

    // ── Bottom fade ───────────────────────────────────────────────────────────
    private final Paint bottomMaskPaint;
    private LinearGradient bottomShader;

    private int   bottomFadeHeight   = 0;
    private int   bottomSolidHeight  = 0;
    private float bottomMaxAlpha     = 0f;

    private int   lastHeight            = -1;
    private int   lastBottomFadeHeight  = -1;
    private int   lastBottomSolidHeight = -1;
    private float lastBottomMaxAlpha    = -1f;

    // ─────────────────────────────────────────────────────────────────────────

    public FadingEdgeFrameLayout(@NonNull Context context) {
        this(context, null);
    }

    public FadingEdgeFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FadingEdgeFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        topMaskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        topMaskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));

        bottomMaskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bottomMaskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));

        // Hardware layer is mandatory: DST_IN composites against this view's
        // own layer, not the entire screen. Without it the effect is invisible.
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    /**
     * Configure the TOP fade (behind status bar).
     *
     * @param fadeHeight  total fade region height in px (solid + gradient)
     * @param solidHeight height of the hard-cut zone at the top (can be 0)
     * @param maxAlpha    peak transparency: 0.0 = invisible, 1.0 = no fade
     */
    public void setFadeParams(@Px int fadeHeight,
                              @Px int solidHeight,
                              @FloatRange(from = 0.0, to = 1.0) float maxAlpha) {
        if (topFadeHeight == fadeHeight && topSolidHeight == solidHeight && topMaxAlpha == maxAlpha) return;
        topFadeHeight  = fadeHeight;
        topSolidHeight = solidHeight;
        topMaxAlpha    = maxAlpha;
        topShader = null;
        invalidate();
    }

    /**
     * Configure the BOTTOM fade (behind system nav bar).
     *
     * @param fadeHeight  total fade region height in px
     * @param solidHeight height of the hard-cut zone at the bottom (can be 0)
     * @param maxAlpha    peak transparency: 0.0 = invisible, 1.0 = no fade
     */
    public void setBottomFadeParams(@Px int fadeHeight,
                                    @Px int solidHeight,
                                    @FloatRange(from = 0.0, to = 1.0) float maxAlpha) {
        if (bottomFadeHeight == fadeHeight && bottomSolidHeight == solidHeight && bottomMaxAlpha == maxAlpha) return;
        bottomFadeHeight  = fadeHeight;
        bottomSolidHeight = solidHeight;
        bottomMaxAlpha    = maxAlpha;
        bottomShader = null;
        invalidate();
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);

        // ── Top fade ──────────────────────────────────────────────────────────
        if (topFadeHeight > 0) {
            if (topShader == null
                    || getWidth()      != lastWidth
                    || topFadeHeight   != lastTopFadeHeight
                    || topSolidHeight  != lastTopSolidHeight
                    || topMaxAlpha     != lastTopMaxAlpha) {

                lastWidth          = getWidth();
                lastTopFadeHeight  = topFadeHeight;
                lastTopSolidHeight = topSolidHeight;
                lastTopMaxAlpha    = topMaxAlpha;

                int peakColor = Color.argb(Math.round(topMaxAlpha * 255), 0, 0, 0);

                if (topSolidHeight > 0 && topSolidHeight < topFadeHeight) {
                    float solidFraction = (float) topSolidHeight / topFadeHeight;
                    topShader = new LinearGradient(
                            0, 0, 0, topFadeHeight,
                            new int[]  { peakColor, peakColor, Color.BLACK },
                            new float[]{ 0f, solidFraction, 1f },
                            Shader.TileMode.CLAMP);
                } else {
                    topShader = new LinearGradient(
                            0, 0, 0, topFadeHeight,
                            peakColor, Color.BLACK,
                            Shader.TileMode.CLAMP);
                }
                topMaskPaint.setShader(topShader);
            }
            canvas.drawRect(0, 0, getWidth(), topFadeHeight, topMaskPaint);
        }

        // ── Bottom fade ───────────────────────────────────────────────────────
        if (bottomFadeHeight > 0) {
            int h = getHeight();
            if (bottomShader == null
                    || getWidth()         != lastWidth
                    || h                  != lastHeight
                    || bottomFadeHeight   != lastBottomFadeHeight
                    || bottomSolidHeight  != lastBottomSolidHeight
                    || bottomMaxAlpha     != lastBottomMaxAlpha) {

                lastWidth             = getWidth();
                lastHeight            = h;
                lastBottomFadeHeight  = bottomFadeHeight;
                lastBottomSolidHeight = bottomSolidHeight;
                lastBottomMaxAlpha    = bottomMaxAlpha;

                // Gradient runs from (h - fadeHeight) → h:
                //   top of fade zone  → fully opaque  (Color.BLACK in DST_IN)
                //   bottom of view    → near-transparent (peakColor in DST_IN)
                int peakColor = Color.argb(Math.round(bottomMaxAlpha * 255), 0, 0, 0);
                float startY = h - bottomFadeHeight;

                if (bottomSolidHeight > 0 && bottomSolidHeight < bottomFadeHeight) {
                    float solidFraction = 1f - (float) bottomSolidHeight / bottomFadeHeight;
                    bottomShader = new LinearGradient(
                            0, startY, 0, h,
                            new int[]  { Color.BLACK, peakColor, peakColor },
                            new float[]{ 0f, solidFraction, 1f },
                            Shader.TileMode.CLAMP);
                } else {
                    bottomShader = new LinearGradient(
                            0, startY, 0, h,
                            Color.BLACK, peakColor,
                            Shader.TileMode.CLAMP);
                }
                bottomMaskPaint.setShader(bottomShader);
            }
            canvas.drawRect(0, getHeight() - bottomFadeHeight, getWidth(), getHeight(), bottomMaskPaint);
        }
    }
}
