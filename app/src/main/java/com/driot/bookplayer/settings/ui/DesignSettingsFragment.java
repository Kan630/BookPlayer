package com.driot.bookplayer.settings.ui;

import android.content.res.Configuration;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.util.Executors;
import com.driot.bookplayer.BuildConfig;
import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.driot.bookplayer.utils.log.LoggingFragment;

import java.util.ArrayList;
import java.util.List;

public class DesignSettingsFragment extends LoggingFragment {

    private Spinner spFontFamily;
    private MaterialCheckBox chkNightModeForce;
    private MaterialButtonToggleGroup groupNightMode;

    // local helper identical to your Activity’s inner class
    private static class FontChoice {
        final String key; // e.g., "sans-serif"
        final String label; // e.g., "Sans-serif"

        FontChoice(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }

    private final List<FontChoice> fontChoices = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_settings_design, container, false);

        if (!BuildConfig.DEBUG && !Option.getTechLog()) root.findViewById(R.id.ll_color_viewer_section).setVisibility(View.GONE);

        // Hide the title row when embedded inline
        boolean showLocalTitle = true;
        Bundle args = getArguments();
        if (args != null)
            showLocalTitle = args.getBoolean("ARG_SHOW_LOCAL_TITLE", true);
        View titleContainer = root.findViewById(R.id.ll_title);
        if (titleContainer != null) {
            titleContainer.setVisibility(showLocalTitle ? View.VISIBLE : View.GONE);
        }

        // ===== Font family spinner =====
        spFontFamily = root.findViewById(R.id.sp_font_family);

        // same families you had in SettingsActivity
        fontChoices.add(new FontChoice("sans-serif", "Sans-serif"));
        fontChoices.add(new FontChoice("serif", "Serif"));
        fontChoices.add(new FontChoice("monospace", "Monospace"));
        fontChoices.add(new FontChoice("casual", "Casual"));
        fontChoices.add(new FontChoice("cursive", "Cursive"));
        fontChoices.add(new FontChoice("serif-monospace", "Serif Monospace"));
        fontChoices.add(new FontChoice("sans-serif-condensed", "Sans-serif Condensed"));
        fontChoices.add(new FontChoice("sans-serif-medium", "Sans-serif Medium"));
        fontChoices.add(new FontChoice("sans-serif-smallcaps", "Sans-serif Smallcaps"));

        FontAdapter fontAdapter = new FontAdapter(requireContext(), fontChoices);
        spFontFamily.setAdapter(fontAdapter);

        String savedFamily = Option.getFontFamilyKey();
        int savedIndex = indexOfKey(fontChoices, savedFamily);
        if (savedIndex < 0)
            savedIndex = 0;
        spFontFamily.setSelection(savedIndex, false);

        spFontFamily.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                String key = fontChoices.get(pos).key;
                Option.setFontFamilyKey(key);

                Executors.directExecutor().execute(() -> {
                    // Flag main to refresh + recreate current host activity so theme/typography
                    // applies
                    signalAndRecreate();
                });
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        // theme key → style map (same naming you already use)
        Object[][] themesAndColors = new Object[][] {
                { R.id.btn_color_01, "gray", R.style.Theme_BookPlayer_Gray },
                { R.id.btn_color_02, "purple", R.style.Theme_BookPlayer_Purple },
                { R.id.btn_color_03, "brown", R.style.Theme_BookPlayer_Brown },
                { R.id.btn_color_04, "blue", R.style.Theme_BookPlayer_Blue },
                { R.id.btn_color_05, "cyan", R.style.Theme_BookPlayer_Cyan },
                { R.id.btn_color_06, "turquoise", R.style.Theme_BookPlayer_Turquoise },
                { R.id.btn_color_07, "orange", R.style.Theme_BookPlayer_Orange },
                { R.id.btn_color_08, "yellow", R.style.Theme_BookPlayer_Yellow },
                { R.id.btn_color_09, "yellowDark", R.style.Theme_BookPlayer_YellowDark },
                { R.id.btn_color_10, "red", R.style.Theme_BookPlayer_Red },
                { R.id.btn_color_11, "redDark", R.style.Theme_BookPlayer_RedDark },
                { R.id.btn_color_12, "indigo", R.style.Theme_BookPlayer_Indigo },
                { R.id.btn_color_13, "pinkLight", R.style.Theme_BookPlayer_PinkLight },
                { R.id.btn_color_14, "pink", R.style.Theme_BookPlayer_Pink },
                { R.id.btn_color_15, "pinkDark", R.style.Theme_BookPlayer_PinkDark },
                { R.id.btn_color_16, "greenLight", R.style.Theme_BookPlayer_GreenLight },
                { R.id.btn_color_17, "green", R.style.Theme_BookPlayer_Green },
                { R.id.btn_color_18, "greenDark", R.style.Theme_BookPlayer_GreenDark },
        };

        int currentThemeResId = Option.getThemeColor();
        float density = getResources().getDisplayMetrics().density;
        int cornerRadiusPx = (int) (8 * density);
        int strokeWidthPx = (int) (3 * density);
        int strokeColor = resolveColorOnSurface(requireContext());

        for (Object[] entry : themesAndColors) {
            int btnId = (int) entry[0];
            String themeKey = (String) entry[1];
            int themeResId = (int) entry[2];

            ImageButton b = root.findViewById(btnId);
            int mainColor = getPrimaryColorFromTheme(requireContext(), themeResId);

            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.RECTANGLE);
            gd.setCornerRadius(cornerRadiusPx);
            gd.setColor(mainColor);
            boolean selected = (themeResId == currentThemeResId);
            if (selected) {
                gd.setStroke(strokeWidthPx, strokeColor);
            }
            b.setBackground(gd);
            b.setOnClickListener(v -> changeBaseTheme(themeKey));
        }

        // ===== Theme mode: override system checkbox + Light/Dark toggle =====
        chkNightModeForce = root.findViewById(R.id.chk_night_mode_force);
        groupNightMode = root.findViewById(R.id.group_night_mode);

        String nightMode = Option.getNightMode();
        boolean forceMode = !"SYSTEM".equals(nightMode);
        chkNightModeForce.setChecked(forceMode);
        groupNightMode.setEnabled(forceMode);

        updateThemeModeSelection(nightMode);

        // Defer attaching listeners so we don't react to the programmatic check(id)
        // above.
        // check(id) runs synchronously and would fire addOnButtonCheckedListener
        // (uncheck + check);
        // by posting, we attach listeners only after that has finished, so only real
        // user taps trigger them.
        // WORKS with 0 POST DELAY, BUT I PUT 500 - better safe than sorry.
        // if not posted, cause infinite loop in some case after user changes theme
        // mode.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            chkNightModeForce.setOnCheckedChangeListener((buttonView, isChecked) -> {
                myLogI("----- user clicks checkbox Force Night Theme - IsThemeForced=" + isChecked);

                if (isChecked) {
                    groupNightMode.setEnabled(true);
                    String current = Option.getNightMode();
                    if ("SYSTEM".equals(current)) {
                        current = isSystemDarkMode() ? "DARK" : "LIGHT";
                        Option.setNightMode(current);
                        Option.applyNightMode();
                    }
                    updateThemeModeSelection(current);
                    // signalAndRecreate();
                } else {
                    Option.setNightMode("SYSTEM");
                    Option.applyNightMode();
                    groupNightMode.setEnabled(false);
                    updateThemeModeSelection("SYSTEM");
                    // signalAndRecreate();
                }
            });

            groupNightMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                myLog("----- user select Night Theme - checkedId=" + checkedId + ":" + isChecked);
                if (!isChecked)
                    return;
                String chosen = (checkedId == R.id.btn_theme_light) ? "LIGHT" : "DARK";
                if (!chosen.equals(Option.getNightMode())) {
                    Option.setNightMode(chosen);
                    Option.applyNightMode();
                    // signalAndRecreate();
                }
            });
        }, 500);

        return root;
    }


    // ---------------- helpers ----------------

    private int indexOfKey(List<FontChoice> list, String key) {
        for (int i = 0; i < list.size(); i++)
            if (list.get(i).key.equalsIgnoreCase(key))
                return i;
        return -1;
    }

    private int getPrimaryColorFromTheme(Context context, int themeResId) {
        Resources.Theme theme = context.getResources().newTheme();
        theme.applyStyle(themeResId, true);
        TypedArray ta = theme.obtainStyledAttributes(new int[] { androidx.appcompat.R.attr.colorPrimary });
        int color = ta.getColor(0, ContextCompat.getColor(context, android.R.color.black));
        ta.recycle();
        return color;
    }

    private int resolveColorOnSurface(Context context) {
        TypedArray ta = context.getTheme().obtainStyledAttributes(new int[] { android.R.attr.textColorPrimary });
        int color = ta.getColor(0, ContextCompat.getColor(context, android.R.color.black));
        ta.recycle();
        return color;
    }

    private void changeBaseTheme(String newBase) {
        myLog("new Base theme is [" + newBase + "]");
        Option.setThemeColor(newBase);
        signalAndRecreate();
    }

    private boolean isSystemDarkMode() {
        int uiMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return uiMode == Configuration.UI_MODE_NIGHT_YES;
    }

    private void updateThemeModeSelection(String mode) {
        int id = "LIGHT".equals(mode) ? R.id.btn_theme_light
                : ("DARK".equals(mode) ? R.id.btn_theme_dark
                        : (isSystemDarkMode() ? R.id.btn_theme_dark : R.id.btn_theme_light));
        groupNightMode.check(id);
    }

    private void signalAndRecreate() {
        requireContext()
                .getSharedPreferences(Option.SHARED_PREFERENCES_OPTIONS, Context.MODE_PRIVATE)
                .edit().putBoolean("ACTIVITY_OPTION_HAS_RESULT", true).apply();
        requireActivity().recreate();
    }

    private static class FontAdapter extends ArrayAdapter<FontChoice> {

        private final LayoutInflater inflater;

        FontAdapter(@NonNull Context ctx, @NonNull List<FontChoice> items) {
            super(ctx, 0, items);
            this.inflater = LayoutInflater.from(ctx);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            // Collapsed spinner view
            View v = (convertView != null) ? convertView
                    : inflater.inflate(R.layout.spinner_item, parent, false);
            bind(position, v);
            return v;
        }

        @NonNull
        @Override
        public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            // Dropdown rows
            View v = (convertView != null) ? convertView
                    : inflater.inflate(R.layout.spinner_item, parent, false);
            bind(position, v);
            return v;
        }

        private void bind(int position, View v) {
            TextView tv = (TextView) v; // spinner_item is a TextView root
            FontChoice item = getItem(position);
            if (item == null)
                return;

            tv.setText(item.label);

            // Try to create a Typeface from the family key; fall back gracefully.
            Typeface tf = resolveTypeface(item.key);
            if (tf != null) {
                tv.setTypeface(tf);
            } else {
                // fallback for odd/unavailable families
                tv.setTypeface(Typeface.SANS_SERIF);
            }

            // Optional: make preview a tad bigger for clarity
            // tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        }

        private @Nullable Typeface resolveTypeface(String key) {
            // Works for system families like "sans-serif", "serif", "monospace",
            // "casual", "cursive", "serif-monospace", "sans-serif-condensed",
            // "sans-serif-medium", "sans-serif-smallcaps" (if present on the device).
            try {
                Typeface t = Typeface.create(key, Typeface.NORMAL);
                // Some vendors return default instead of null; add a light sanity check:
                // If you want stricter fallback detection, you can compare style metrics, but
                // generally create(key, ...) is fine across API levels.
                return t;
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

}
