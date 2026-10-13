package com.driot.bookplayer.views;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

/**
 * Holds a video surface at the aspect ratio of the stream, letterboxing inside whatever space it
 * is given: a TV stream is 16:9, a phone-shot one can be anything, and a stretched picture looks
 * broken. media3-ui (PlayerView, which brings its own) is deliberately not a dependency of this
 * app, so this is the one piece of it we need.
 *
 * Ratio 0 means "unknown yet": the view then just fills its space, so the black canvas is already
 * the right shape before the first frame is decoded.
 */
public class AspectRatioFrameLayout extends FrameLayout {

    /** width / height of the content, 0 while unknown. */
    private float aspectRatio = 0f;

    public AspectRatioFrameLayout(Context context) {
        super(context);
    }

    public AspectRatioFrameLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public AspectRatioFrameLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setAspectRatio(float ratio) {
        if (ratio != aspectRatio) {
            aspectRatio = ratio;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (aspectRatio <= 0) {
            return;
        }
        int width = getMeasuredWidth();
        int height = getMeasuredHeight();
        if (width == 0 || height == 0) {
            return;
        }
        // Fit inside: keep the full width and shrink the height, unless that would overflow.
        int targetHeight = (int) (width / aspectRatio);
        if (targetHeight <= height) {
            height = targetHeight;
        } else {
            width = (int) (height * aspectRatio);
        }
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }
}
