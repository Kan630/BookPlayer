// src/.../views/SettingSwitchRow.java
package com.driot.bookplayer.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.driot.bookplayer.R;
import com.google.android.material.materialswitch.MaterialSwitch;

public class SettingSwitchRow extends ConstraintLayout {

    private TextView tvTitle, tvSubtitle;
    private MaterialSwitch sw;

    public SettingSwitchRow(Context context) {
        this(context, null);
    }

    public SettingSwitchRow(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SettingSwitchRow(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        LayoutInflater.from(context).inflate(R.layout.view_setting_switch_row, this, true);
        tvTitle = findViewById(R.id.title);
        tvSubtitle = findViewById(R.id.subtitle);
        sw = findViewById(R.id.switcher);

        if (attrs != null) {
            var a = context.obtainStyledAttributes(attrs, R.styleable.SettingSwitchRow, defStyleAttr, 0);
            CharSequence title = a.getText(R.styleable.SettingSwitchRow_title);
            CharSequence subtitle = a.getText(R.styleable.SettingSwitchRow_subtitle);
            boolean checked = a.getBoolean(R.styleable.SettingSwitchRow_checked, false);
            a.recycle();

            if (title != null) tvTitle.setText(title);
            if (subtitle != null) {
                tvSubtitle.setText(subtitle);
                tvSubtitle.setVisibility(VISIBLE);
            } else {
                tvSubtitle.setVisibility(GONE);
            }
            sw.setChecked(checked);
        }

        // Whole row toggles the switch
        setOnClickListener(v -> sw.toggle());
    }

    // --- Public API ---
    public void setTitle(CharSequence title) { tvTitle.setText(title); }
    public void setSubtitle(@Nullable CharSequence subtitle) {
        if (subtitle == null || subtitle.length() == 0) {
            tvSubtitle.setText(null);
            tvSubtitle.setVisibility(GONE);
        } else {
            tvSubtitle.setText(subtitle);
            tvSubtitle.setVisibility(VISIBLE);
        }
    }
    public boolean isChecked() { return sw.isChecked(); }
    public void setChecked(boolean checked) { sw.setChecked(checked); }
    public void setOnCheckedChangeListener(MaterialSwitch.OnCheckedChangeListener l) {
        sw.setOnCheckedChangeListener(l);
    }
    public MaterialSwitch getSwitch() { return sw; } // if you need more control
}
