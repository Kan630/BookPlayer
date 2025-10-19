package com.driot.bookplayer.settings.ui;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.util.Executors;
import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.utils.log.LoggingFragment;

import java.util.ArrayList;
import java.util.List;

public class DesignSettingsFragment extends LoggingFragment {

    private Spinner spFontFamily;
    private Button btnNightMode;

    // local helper identical to your Activity’s inner class
    private static class FontChoice {
        final String key;   // e.g., "sans-serif"
        final String label; // e.g., "Sans-serif"
        FontChoice(String key, String label) { this.key = key; this.label = label; }
    }
    private final List<FontChoice> fontChoices = new ArrayList<>();

    // Keep references for color buttons to paint them with theme primary color
    private ImageButton[] colorButtons;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_design_settings, container, false);

        // Hide the title row when embedded inline
        boolean showLocalTitle = true;
        Bundle args = getArguments();
        if (args != null) showLocalTitle = args.getBoolean("ARG_SHOW_LOCAL_TITLE", true);
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

        ArrayAdapter<String> fontAdapter = new ArrayAdapter<>(
                requireContext(),
                R.layout.spinner_item,
                toLabels(fontChoices)
        );
        fontAdapter.setDropDownViewResource(R.layout.spinner_item);
        spFontFamily.setAdapter(fontAdapter);

        String savedFamily = Option.getFontFamilyKey();
        int savedIndex = indexOfKey(fontChoices, savedFamily);
        if (savedIndex < 0) savedIndex = 0;
        spFontFamily.setSelection(savedIndex, false);

        spFontFamily.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                String key = fontChoices.get(pos).key;
                Option.setFontFamilyKey(key);

                Executors.directExecutor().execute(() -> {
                    // Flag main to refresh + recreate current host activity so theme/typography applies
                    requireContext()
                            .getSharedPreferences(Option.SHARED_PREFERENCES_OPTIONS, Context.MODE_PRIVATE)
                            .edit().putBoolean("ACTIVITY_OPTION_HAS_RESULT", true).apply();
                    requireActivity().recreate();
                        });
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
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
                { R.id.btn_color_01, "gray",       R.style.Theme_BookPlayer_Gray },
                { R.id.btn_color_02, "purple",     R.style.Theme_BookPlayer_Purple },
                { R.id.btn_color_03, "brown",      R.style.Theme_BookPlayer_Brown },
                { R.id.btn_color_04, "blue",       R.style.Theme_BookPlayer_Blue },
                { R.id.btn_color_05, "cyan",       R.style.Theme_BookPlayer_Cyan },
                { R.id.btn_color_06, "turquoise",  R.style.Theme_BookPlayer_Turquoise },
                { R.id.btn_color_07, "orange",     R.style.Theme_BookPlayer_Orange },
                { R.id.btn_color_08, "yellow",     R.style.Theme_BookPlayer_Yellow },
                { R.id.btn_color_09, "yellowDark", R.style.Theme_BookPlayer_YellowDark },
                { R.id.btn_color_10,"red",         R.style.Theme_BookPlayer_Red },
                { R.id.btn_color_11,"redDark",     R.style.Theme_BookPlayer_RedDark },
                { R.id.btn_color_12,"indigo",      R.style.Theme_BookPlayer_Indigo },
                { R.id.btn_color_13,"pinkLight",   R.style.Theme_BookPlayer_PinkLight },
                { R.id.btn_color_14,"pink",        R.style.Theme_BookPlayer_Pink },
                { R.id.btn_color_15,"pinkDark",    R.style.Theme_BookPlayer_PinkDark },
                { R.id.btn_color_16,"greenLight",  R.style.Theme_BookPlayer_GreenLight },
                { R.id.btn_color_17,"green",       R.style.Theme_BookPlayer_Green },
                { R.id.btn_color_18,"greenDark",   R.style.Theme_BookPlayer_GreenDark },
        };

        for (Object[] entry : themesAndColors) {
            int btnId = (int) entry[0];
            String themeKey = (String) entry[1];
            int themeResId = (int) entry[2];

            ImageButton b = root.findViewById(btnId);
            int mainColor = getPrimaryColorFromTheme(requireContext(), themeResId);
            b.setBackgroundColor(mainColor);
            b.setOnClickListener(v -> changeBaseTheme(themeKey));
        }

        // ===== Night mode row =====
        btnNightMode = root.findViewById(R.id.btn_night_mode);
        setNightModeButtonLabel(btnNightMode, Option.getNightMode());

        View nightModeRow = root.findViewById(R.id.ll_night_mode);
        nightModeRow.setOnClickListener(v -> showNightModeChooser());
        btnNightMode.setOnClickListener(v -> showNightModeChooser());

        return root;
    }

    // ---------------- helpers ----------------

    private List<String> toLabels(List<FontChoice> list) {
        List<String> labels = new ArrayList<>();
        for (FontChoice f : list) labels.add(f.label);
        return labels;
    }

    private int indexOfKey(List<FontChoice> list, String key) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).key.equalsIgnoreCase(key)) return i;
        return -1;
    }

    private int getPrimaryColorFromTheme(Context context, int themeResId) {
        Resources.Theme theme = context.getResources().newTheme();
        theme.applyStyle(themeResId, true);
        TypedArray ta = theme.obtainStyledAttributes(new int[]{ androidx.appcompat.R.attr.colorPrimary });
        int color = ta.getColor(0, ContextCompat.getColor(context, android.R.color.black));
        ta.recycle();
        return color;
    }

    private void changeBaseTheme(String newBase) {
        myLog("new Base theme is [" + newBase + "]");
        Option.setThemeColor(newBase);

        // Signal and recreate the host so the theme takes effect across activities
        requireContext()
                .getSharedPreferences(Option.SHARED_PREFERENCES_OPTIONS, Context.MODE_PRIVATE)
                .edit().putBoolean("ACTIVITY_OPTION_HAS_RESULT", true).apply();
        requireActivity().recreate();
    }

    private void showNightModeChooser() {
        final String current = Option.getNightMode();
        final CharSequence[] items = new CharSequence[] {
                getString(R.string.option_night_mode_follow_system),
                getString(R.string.option_night_mode_light),
                getString(R.string.option_night_mode_dark)
        };
        int checked = (current.equals("LIGHT")) ? 1 : (current.equals("DARK") ? 2 : 0);

        new android.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.option_night_mode_dialog_title)
                .setSingleChoiceItems(items, checked, (dlg, which) -> {
                    String chosen = (which == 1) ? "LIGHT"
                            : (which == 2) ? "DARK"
                            : "SYSTEM";

                    if (!chosen.equals(current)) {
                        Option.setNightMode(chosen);
                        Option.applyNightMode();
                        setNightModeButtonLabel(btnNightMode, chosen);

                        requireContext()
                                .getSharedPreferences(Option.SHARED_PREFERENCES_OPTIONS, Context.MODE_PRIVATE)
                                .edit().putBoolean("ACTIVITY_OPTION_HAS_RESULT", true).apply();
                        requireActivity().recreate();
                    }
                    dlg.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void setNightModeButtonLabel(Button btn, String modeKey) {
        // Show raw key or map to pretty if you prefer
        btn.setText(modeKey); // You can map to strings if you want localized text.
    }
}
