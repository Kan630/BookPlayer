package com.driot.bookplayer.views;

import android.content.Context;
import android.util.AttributeSet;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

/**
 * Custom TextView for TTS that overrides performClick to satisfy accessibility
 * lint.
 */
public class TtsTextView extends AppCompatTextView {
    public TtsTextView(Context context) {
        super(context);
    }

    public TtsTextView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public TtsTextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
