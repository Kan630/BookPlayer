package com.driot.bookplayer.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * A "label [number box] label" settings row (e.g. "last [5] episodes"), with optional
 * Min/Max helper lines below. Centralizes the box sizing, text size and label-wrap
 * weighting used throughout Settings, so screens stay visually consistent and long
 * translations wrap instead of overflowing off screen.
 */
public class SettingsNumberField extends LinearLayout {

    private TextView tvBefore, tvAfter, tvMin, tvMax;
    private TextInputEditText etField;

    public SettingsNumberField(Context context) {
        this(context, null);
    }

    public SettingsNumberField(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SettingsNumberField(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.view_settings_number_field, this, true);

        tvBefore = findViewById(R.id.tvBefore);
        etField = findViewById(R.id.etField);
        tvAfter = findViewById(R.id.tvAfter);
        tvMin = findViewById(R.id.tvMin);
        tvMax = findViewById(R.id.tvMax);

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SettingsNumberField);

            setBeforeText(a.getText(R.styleable.SettingsNumberField_beforeText));
            setAfterText(a.getText(R.styleable.SettingsNumberField_afterText));
            setMinText(a.getText(R.styleable.SettingsNumberField_minText));
            setMaxText(a.getText(R.styleable.SettingsNumberField_maxText));

            float fieldTextSizePx = a.getDimension(R.styleable.SettingsNumberField_fieldTextSize, -1);
            if (fieldTextSizePx > 0) {
                tvBefore.setTextSize(TypedValue.COMPLEX_UNIT_PX, fieldTextSizePx);
                tvAfter.setTextSize(TypedValue.COMPLEX_UNIT_PX, fieldTextSizePx);
                etField.setTextSize(TypedValue.COMPLEX_UNIT_PX, fieldTextSizePx);
            }

            float boxWidthPx = a.getDimension(R.styleable.SettingsNumberField_numberBoxWidth, -1);
            if (boxWidthPx > 0) {
                TextInputLayout boxLayout = findViewById(R.id.fieldBoxLayout);
                LayoutParams lp = (LayoutParams) boxLayout.getLayoutParams();
                lp.width = (int) boxWidthPx;
                boxLayout.setLayoutParams(lp);
            }

            a.recycle();
        }
    }

    public TextInputEditText getEditText() {
        return etField;
    }

    public String getText() {
        return etField.getText() == null ? "" : etField.getText().toString();
    }

    public void setText(CharSequence text) {
        etField.setText(text);
    }

    public void setBeforeText(@Nullable CharSequence text) {
        tvBefore.setText(text);
    }

    /** Hides the trailing unit label (and gives the leading label its full width) when empty. */
    public void setAfterText(@Nullable CharSequence text) {
        boolean has = !TextUtils.isEmpty(text);
        tvAfter.setText(text);
        tvAfter.setVisibility(has ? VISIBLE : GONE);
        LayoutParams lp = (LayoutParams) tvBefore.getLayoutParams();
        lp.weight = has ? 2f : 5f;
        tvBefore.setLayoutParams(lp);
    }

    public void setMinText(@Nullable CharSequence text) {
        tvMin.setText(text);
        tvMin.setVisibility(TextUtils.isEmpty(text) ? GONE : VISIBLE);
    }

    public void setMaxText(@Nullable CharSequence text) {
        tvMax.setText(text);
        tvMax.setVisibility(TextUtils.isEmpty(text) ? GONE : VISIBLE);
    }
}
