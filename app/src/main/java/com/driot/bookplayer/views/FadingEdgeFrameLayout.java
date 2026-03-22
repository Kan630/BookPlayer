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
 * Uses DST_IN Porter-Duff on its own hardware layer — so the gradient
 * punches alpha out of the composited children rather than painting a color.
 * Result: content fades out near the top, revealing whatever is behind
 * this view (your background), regardless of light/dark theme.
 *
 * Usage: call {@link #setFadeHeight(int)} with the real status bar height
 * (or a multiple of it) once WindowInsets are available.
 */
public class FadingEdgeFrameLayout extends FrameLayout {

    private final Paint maskPaint;
    private LinearGradient shader;

    private int fadeHeight = 0;
    private int lastWidth  = -1;
    private int lastShaderHeight = -1;

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
     * Sets how tall the fade region should be in pixels.
     * Typically: statusBarHeight * multiplier (e.g. ×2 for a soft fade).
     * Call this from your insets callback once the real height is known.
     */
    public void setFadeHeight(@Px int fadeHeight) {
        if (this.fadeHeight == fadeHeight) return;
        this.fadeHeight = fadeHeight;
        shader = null; // invalidate cached shader
        invalidate();
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        // Draw children normally first.
        super.dispatchDraw(canvas);

        if (fadeHeight <= 0) return;

        // Rebuild shader only when dimensions or fadeHeight change.
        if (shader == null || getWidth() != lastWidth || fadeHeight != lastShaderHeight) {
            lastWidth       = getWidth();
            lastShaderHeight = fadeHeight;
            shader = new LinearGradient(
                    0, 0,           // top
                    0, fadeHeight,  // bottom of fade zone
                    Color.TRANSPARENT,  // top   → alpha=0, content invisible
                    Color.BLACK,        // bottom → alpha=255, content fully visible
                    Shader.TileMode.CLAMP);
            maskPaint.setShader(shader);
        }

        // DST_IN: draws the gradient as an alpha mask over the already-drawn children.
        canvas.drawRect(0, 0, getWidth(), fadeHeight, maskPaint);
    }
}
