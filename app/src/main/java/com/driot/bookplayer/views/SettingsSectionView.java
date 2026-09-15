package com.driot.bookplayer.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageButton;

import androidx.annotation.Nullable;

import com.driot.bookplayer.R;

/**
 * A tappable row in the top-level settings list: icon, title and a trailing chevron
 * indicating that tapping it opens that category's settings screen.
 */
public class SettingsSectionView extends LinearLayout {

    private TextView tvTitle;
    private ImageButton ivIcon;
    private View headerClickable;

    public SettingsSectionView(Context context) {
        this(context, null);
    }

    public SettingsSectionView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SettingsSectionView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.view_settings_section, this, true);

        headerClickable = findViewById(R.id.headerCard);
        tvTitle = findViewById(R.id.tvTitle);
        ivIcon = findViewById(R.id.ivIcon);

        headerClickable.setId(View.generateViewId());

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SettingsSectionView);
            CharSequence title = a.getText(R.styleable.SettingsSectionView_sectionTitle);
            int iconRes = a.getResourceId(R.styleable.SettingsSectionView_sectionIcon, 0);
            a.recycle();

            if (title != null)
                tvTitle.setText(title);
            if (iconRes != 0)
                ivIcon.setImageResource(iconRes);
            else
                ivIcon.setVisibility(GONE);
        }
    }

    public View getHeaderView() {
        return headerClickable;
    }

    public CharSequence getTitle() {
        return tvTitle.getText();
    }

    public void setTitle(CharSequence title) {
        tvTitle.setText(title);
    }

    public void setIcon(int resId) {
        if (resId != 0) {
            ivIcon.setVisibility(VISIBLE);
            ivIcon.setImageResource(resId);
        } else
            ivIcon.setVisibility(GONE);
    }
}
