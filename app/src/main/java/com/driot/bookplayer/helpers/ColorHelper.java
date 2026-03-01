package com.driot.bookplayer.helpers;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.TypedValue;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.driot.bookplayer.global.Option;

public class ColorHelper {

    private ColorHelper() {
    }



    public static int getColorPrimaryForTtsCursor(Context context) {
        int themeResId = Option.getThemeColor();
        return getColorPrimaryFromTheme(context, themeResId);
    }
    public static int getColorOnPrimaryForTtsCursor(Context context) {
        int themeResId = Option.getThemeColor();
        return getColorOnPrimaryFromTheme(context, themeResId);
    }


    private static int getColorPrimaryFromTheme(Context context, int themeResId) {
        Resources.Theme theme = context.getResources().newTheme();
        theme.applyStyle(themeResId, true);
        TypedArray ta = theme.obtainStyledAttributes(new int[]{ androidx.appcompat.R.attr.colorPrimary });
        int color = ta.getColor(0,, ContextCompat.getColor(context, android.R.color.white));
        ta.recycle();
        return color;
    }
    private static int getColorOnPrimaryFromTheme(Context context, int themeResId) {
        Resources.Theme theme = context.getResources().newTheme();
        theme.applyStyle(themeResId, true);
        TypedArray ta = theme.obtainStyledAttributes(new int[]{ com.google.android.material.R.attr.colorOnPrimary });
        int color = ta.getColor(0, ContextCompat.getColor(context, android.R.color.black));
        ta.recycle();
        return color;
    }



    /**
     * Resolves a color from a theme attribute using the context's current theme.
     */
    @ColorInt
    public static int resolveThemeColor(@NonNull Context context, @AttrRes int attrRes, int defaultColor) {
        return resolveColor(context.getTheme(), attrRes, defaultColor);
    }
    /**
     * Resolves a color from a theme attribute for a specific theme.
     */
    @ColorInt
    public static int resolveColor(@NonNull Resources.Theme theme, @AttrRes int attrRes, int defaultColor) {
        TypedValue typedValue = new TypedValue();
        if (theme.resolveAttribute(attrRes, typedValue, true)) {
            return typedValue.data;
        }
        return defaultColor;
    }

    /**
     * Sets the alpha component of a color.
     * 
     * @param color The color to modify.
     * @param alpha The alpha value (0-255).
     * @return The color with the specified alpha.
     */
    @ColorInt
    public static int withAlpha(@ColorInt int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }
}
