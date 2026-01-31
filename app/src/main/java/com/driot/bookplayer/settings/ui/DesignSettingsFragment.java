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
import android.view.ViewTreeObserver;
import android.widget.ScrollView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.SeekBar; // Added for Custom Theme
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.bumptech.glide.util.Executors;
import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.driot.bookplayer.utils.log.LoggingFragment;

import java.util.ArrayList;
import java.util.List;

public class DesignSettingsFragment extends LoggingFragment {

    private Spinner spFontFamily;
    private MaterialCheckBox chkThemeModeForce;
    private MaterialButtonToggleGroup groupThemeMode;
    private MaterialButton btnThemeLight;
    private MaterialButton btnThemeDark;

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

    // Keep references for color buttons to paint them with theme primary color
    private ImageButton[] colorButtons;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_settings_design, container, false);

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

        // ===== Theme color grid (18 buttons) =====
        colorButtons = new ImageButton[] {
                root.findViewById(R.id.btn_color_01),
                root.findViewById(R.id.btn_color_02),
                root.findViewById(R.id.btn_color_03),
                root.findViewById(R.id.btn_color_04),
                root.findViewById(R.id.btn_color_05),
                root.findViewById(R.id.btn_color_06),
                root.findViewById(R.id.btn_color_07),
                root.findViewById(R.id.btn_color_08),
                root.findViewById(R.id.btn_color_09),
                root.findViewById(R.id.btn_color_10),
                root.findViewById(R.id.btn_color_11),
                root.findViewById(R.id.btn_color_12),
                root.findViewById(R.id.btn_color_13),
                root.findViewById(R.id.btn_color_14),
                root.findViewById(R.id.btn_color_15),
                root.findViewById(R.id.btn_color_16),
                root.findViewById(R.id.btn_color_17),
                root.findViewById(R.id.btn_color_18)
        };

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
        chkThemeModeForce = root.findViewById(R.id.chk_theme_mode_force);
        groupThemeMode = root.findViewById(R.id.group_theme_mode);
        btnThemeLight = root.findViewById(R.id.btn_theme_light);
        btnThemeDark = root.findViewById(R.id.btn_theme_dark);

        String nightMode = Option.getNightMode();
        boolean forceMode = !"SYSTEM".equals(nightMode);
        chkThemeModeForce.setChecked(forceMode);
        groupThemeMode.setEnabled(forceMode);

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
            chkThemeModeForce.setOnCheckedChangeListener((buttonView, isChecked) -> {
                myLog("chkThemeModeForce.setOnCheckedChangeListener - themeForce=" + isChecked);

                if (isChecked) {
                    groupThemeMode.setEnabled(true);
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
                    groupThemeMode.setEnabled(false);
                    updateThemeModeSelection("SYSTEM");
                    // signalAndRecreate();
                }
            });

            groupThemeMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                myLog("groupThemeMode.addOnButtonCheckedListener - checkedId=" + checkedId + ":" + isChecked);
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

        // ===== Custom Buttons & Sliders =====
        setupCustomThemeLogic(root);

        return root;
    }

    // ---------------- Custom Logic ----------------
    private LinearLayout llColorPicker;
    private SeekBar sbRed, sbGreen, sbBlue;
    private TextView tvRed, tvGreen, tvBlue;
    private MaterialButton btnCustom1, btnCustom2, btnCustom3;
    // 6 preview boxes references
    private TextView boxPrimary, boxSecondary, boxPrimaryV, boxSurface, boxSurfaceV, boxOutline;
    private TextView[] allBoxes;
    private String[] boxKeys = { "primary", "secondary", "primary_v", "surface", "surface_v", "outline" };
    private int[] defaultColors;

    private int currentCustomThemeIdx = -1; // -1 = standard, 1,2,3 = custom
    private String currentSelectedColorKey = "primary"; // default selection

    private void setupCustomThemeLogic(View root) {
        btnCustom1 = root.findViewById(R.id.btn_custom_1);
        btnCustom2 = root.findViewById(R.id.btn_custom_2);
        btnCustom3 = root.findViewById(R.id.btn_custom_3);

        llColorPicker = root.findViewById(R.id.ll_color_picker);
        sbRed = root.findViewById(R.id.seekbar_red);
        sbGreen = root.findViewById(R.id.seekbar_green);
        sbBlue = root.findViewById(R.id.seekbar_blue);
        tvRed = root.findViewById(R.id.tv_red_val);
        tvGreen = root.findViewById(R.id.tv_green_val);
        tvBlue = root.findViewById(R.id.tv_blue_val);

        boxPrimary = root.findViewById(R.id.box_primary);
        boxSecondary = root.findViewById(R.id.box_secondary);
        boxPrimaryV = root.findViewById(R.id.box_primary_v);
        boxSurface = root.findViewById(R.id.box_surface);
        boxSurfaceV = root.findViewById(R.id.box_surface_v);
        boxOutline = root.findViewById(R.id.box_outline);

        allBoxes = new TextView[] { boxPrimary, boxSecondary, boxPrimaryV, boxSurface, boxSurfaceV, boxOutline };

        // Listeners for Custom Buttons
        View.OnClickListener customBtnListener = v -> {
            int idx = 1;
            if (v == btnCustom2)
                idx = 2;
            else if (v == btnCustom3)
                idx = 3;
            setCustomThemeMode(idx);
        };
        btnCustom1.setOnClickListener(customBtnListener);
        btnCustom2.setOnClickListener(customBtnListener);
        btnCustom3.setOnClickListener(customBtnListener);

        // Listeners for Boxes
        for (int i = 0; i < allBoxes.length; i++) {
            String key = boxKeys[i];
            View box = allBoxes[i];
            box.setOnClickListener(v -> {
                if (currentCustomThemeIdx != -1) {
                    selectBox(key);
                }
            });
        }

        // Listeners for SeekBars
        SeekBar.OnSeekBarChangeListener sbListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && currentCustomThemeIdx != -1) {
                    updateColorFromSliders();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        };
        sbRed.setOnSeekBarChangeListener(sbListener);
        sbGreen.setOnSeekBarChangeListener(sbListener);
        sbBlue.setOnSeekBarChangeListener(sbListener);
    }

    private void setCustomThemeMode(int idx) {
        currentCustomThemeIdx = idx;
        llColorPicker.setVisibility(View.VISIBLE);
        // Highlight active button (optional visual feedback, simple toggle for now)
        btnCustom1.setChecked(idx == 1);
        btnCustom2.setChecked(idx == 2);
        btnCustom3.setChecked(idx == 3);

        // Deselect standard color buttons (remove strokes)
        float density = getResources().getDisplayMetrics().density;
        if (colorButtons != null) {
            for (ImageButton b : colorButtons) {
                ViewCompat.setBackgroundTintList(b, null); // Just in case tint interferes
                GradientDrawable gd = (GradientDrawable) b.getBackground();
                if (gd != null)
                    gd.setStroke(0, 0); // clear stroke
            }
        }

        reloadCustomColorsForPreview();
        selectBox("primary"); // default selection
    }

    private void reloadCustomColorsForPreview() {
        if (defaultColors == null) {
            // Lazy load defaults from Purple theme as fallback
            defaultColors = new int[6];
            Context ctx = requireContext();

            // Resolve standard theme colors
            Resources.Theme theme = ctx.getResources().newTheme();
            theme.applyStyle(R.style.Theme_BookPlayer_Purple, true);
            defaultColors[0] = getColor(theme, R.attr.colorPrimary);
            defaultColors[1] = getColor(theme, R.attr.colorSecondary);
            defaultColors[2] = getColor(theme, R.attr.colorPrimaryVariant);
            defaultColors[3] = getColor(theme, com.google.android.material.R.attr.colorSurface);
            defaultColors[4] = getColor(theme, com.google.android.material.R.attr.colorSurfaceVariant);
            defaultColors[5] = getColor(theme, com.google.android.material.R.attr.colorOutline);
        }

        for (int i = 0; i < allBoxes.length; i++) {
            int color = Option.getCustomColor(currentCustomThemeIdx, boxKeys[i], defaultColors[i]);
            setBoxColor(allBoxes[i], color, false);
        }
    }

    private int getColor(Resources.Theme theme, int attr) {
        TypedArray ta = theme.obtainStyledAttributes(new int[] { attr });
        int color = ta.getColor(0, 0xFF777777);
        ta.recycle();
        return color;
    }

    private void selectBox(String key) {
        currentSelectedColorKey = key;

        // Find stored color
        int boxIdx = 0;
        for (int i = 0; i < boxKeys.length; i++)
            if (boxKeys[i].equals(key))
                boxIdx = i;

        int color = Option.getCustomColor(currentCustomThemeIdx, key,
                defaultColors != null ? defaultColors[boxIdx] : 0xFF000000);

        // Update Sliders
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = (color) & 0xFF;
        sbRed.setProgress(r);
        sbGreen.setProgress(g);
        sbBlue.setProgress(b);
        updateSliderText(r, g, b);

        // Update Contours
        for (int i = 0; i < allBoxes.length; i++) {
            int c = Option.getCustomColor(currentCustomThemeIdx, boxKeys[i], defaultColors[i]);
            setBoxColor(allBoxes[i], c, boxKeys[i].equals(key));
        }
    }

    private void updateColorFromSliders() {
        int r = sbRed.getProgress();
        int g = sbGreen.getProgress();
        int b = sbBlue.getProgress();
        updateSliderText(r, g, b);

        int color = 0xFF000000 | (r << 16) | (g << 8) | b;

        // Save
        Option.setCustomColor(currentCustomThemeIdx, currentSelectedColorKey, color);

        // Update View
        int boxIdx = 0;
        for (int i = 0; i < boxKeys.length; i++)
            if (boxKeys[i].equals(currentSelectedColorKey))
                boxIdx = i;
        setBoxColor(allBoxes[boxIdx], color, true);
    }

    private void updateSliderText(int r, int g, int b) {
        tvRed.setText(String.valueOf(r));
        tvGreen.setText(String.valueOf(g));
        tvBlue.setText(String.valueOf(b));
    }

    private void setBoxColor(TextView box, int color, boolean selected) {
        float density = getResources().getDisplayMetrics().density;
        int cornerRadiusPx = (int) (8 * density);
        int strokeWidthPx = (int) (3 * density);

        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(cornerRadiusPx);
        gd.setColor(color);

        if (selected) {
            int strokeColor = resolveColorOnSurface(requireContext());
            gd.setStroke(strokeWidthPx, strokeColor);
        }

        box.setBackground(gd);

        // Auto text color (white or black depending on luminance)
        if (androidx.core.graphics.ColorUtils.calculateLuminance(color) > 0.5) {
            box.setTextColor(0xFF000000);
        } else {
            box.setTextColor(0xFFFFFFFF);
        }
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
        groupThemeMode.check(id);
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
