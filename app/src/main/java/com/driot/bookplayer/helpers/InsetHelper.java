package com.driot.bookplayer.helpers;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;
import com.driot.bookplayer.utils.log.KanLogger;

/**
 * Edge-to-edge + insets helper (API 26+).
 * Handles status/nav bar colors, display cutout, and IME padding.
 */
public final class InsetHelper {

    private InsetHelper() {}

    // ===== Public API =====

    /** Classic fitSystemWindows: pads all sides + IME on the root content view. */
    public static void apply(@NonNull Activity activity) {
        View root = activity.findViewById(android.R.id.content);
        if (root == null) {
            myLogEE(null, "apply(): root content view is NULL, aborting.");
            return;
        }
        applyInsets(activity, root,
                /* padTop */    true,
                /* padBottom */ true,
                /* padSides */  true,
                /* handleIME */ true,
                /* handleCutout */ true,
                /* allowShortEdgeCutout */ true,
                /* addToPadding */ false,
                /* consume */   true);
    }

    /** Scrollable view draws behind the nav bar with proper bottom padding. */
    public static void applyInsetsForScrollableBehindNavBar(@NonNull Activity activity,
                                                            @NonNull View scrollableView) {
        applyInsets(activity, scrollableView,
                /* padTop */    true,
                /* padBottom */ true,
                /* padSides */  true,
                /* handleIME */ true,
                /* handleCutout */ true,
                /* allowShortEdgeCutout */ true,
                /* addToPadding */ false,
                /* consume */   true);
    }

    /** Status-bar height only — no side or cutout padding (avoids dead space). */
    public static void applyTopInsetsOnlyTo(@NonNull Activity activity,
                                            @NonNull View targetView) {
        applyInsets(activity, targetView,
                /* padTop */    true,
                /* padBottom */ false,
                /* padSides */  false,
                /* handleIME */ false,
                /* handleCutout */ false,
                /* allowShortEdgeCutout */ false,
                /* addToPadding */ true,
                /* consume */   false);
    }

    // ===== Core =====

    private static void applyInsets(@NonNull Activity activity,
                                    @NonNull View target,
                                    boolean padTop,
                                    boolean padBottom,
                                    boolean padSides,
                                    boolean handleIME,
                                    boolean handleCutout,
                                    boolean allowShortEdgeCutout,
                                    boolean addToPadding,
                                    boolean consume) {
        try {
            final Window window = activity.getWindow();

            // Edge-to-edge
            WindowCompat.setDecorFitsSystemWindows(window, false);

            // Display cutout mode
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                window.getAttributes().layoutInDisplayCutoutMode = allowShortEdgeCutout
                        ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                        : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER;
                window.setAttributes(window.getAttributes());
            }

            // Bar colors
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

            // Light/dark bar icons based on surface color
            WindowInsetsControllerCompat controller =
                    WindowCompat.getInsetsController(window, window.getDecorView());
            if (controller != null) {
                boolean lightSurface = isLightSurface(activity);
                controller.setAppearanceLightStatusBars(lightSurface);
                controller.setAppearanceLightNavigationBars(lightSurface);
            } else {
                myLogW("applyInsets(): no WindowInsetsControllerCompat available.");
            }

            if (KanLogger.LOG_INSETS)
                myLogD("applyInsets(): padTop=" + padTop + " padBottom=" + padBottom
                        + " padSides=" + padSides + " handleIME=" + handleIME
                        + " handleCutout=" + handleCutout + " consume=" + consume
                        + " orientation=" + orientationString(activity));

            // Snapshot initial padding for addToPadding mode
            final int initLeft   = target.getPaddingLeft();
            final int initTop    = target.getPaddingTop();
            final int initRight  = target.getPaddingRight();
            final int initBottom = target.getPaddingBottom();

            ViewCompat.setOnApplyWindowInsetsListener(target, (v, insets) -> {
                final Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                final Insets cut = handleCutout
                        ? insets.getInsets(WindowInsetsCompat.Type.displayCutout())
                        : Insets.NONE;
                final Insets ime = handleIME
                        ? insets.getInsets(WindowInsetsCompat.Type.ime())
                        : Insets.NONE;

                int left   = padSides  ? Math.max(sys.left,   cut.left)   : 0;
                int top    = padTop    ? Math.max(sys.top,    cut.top)    : 0;
                int right  = padSides  ? Math.max(sys.right,  cut.right)  : 0;
                int bottom = padBottom ? Math.max(sys.bottom, cut.bottom) : 0;

                if (handleIME) bottom = Math.max(bottom, ime.bottom);

                if (addToPadding) {
                    v.setPadding(initLeft + left, initTop + top, initRight + right, initBottom + bottom);
                } else {
                    v.setPadding(left, top, right, bottom);
                }

                if (KanLogger.LOG_INSETS)
                    myLogD("onApplyWindowInsets -> sys=" + fmt(sys)
                            + (handleCutout ? " cut=" + fmt(cut) : "")
                            + (handleIME    ? " ime=" + fmt(ime) : "")
                            + " applied(L/T/R/B)=" + left + "/" + top + "/" + right + "/" + bottom);

                return consume ? WindowInsetsCompat.CONSUMED : insets;
            });

            ViewCompat.requestApplyInsets(target);

        } catch (Exception e) {
            myLogEE(e, "applyInsets() failed unexpectedly.");
        }
    }

    // ===== Helpers =====

    private static boolean isLightSurface(@NonNull Context ctx) {
        TypedValue tv = new TypedValue();
        int bg;
        if (ctx.getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurface, tv, true)) {
            bg = tv.data;
        } else if (ctx.getTheme().resolveAttribute(android.R.attr.windowBackground, tv, true)) {
            bg = tv.data;
        } else {
            myLogW("isLightSurface(): no background attr found; assuming dark.");
            return false;
        }
        // Respect explicit windowLightStatusBar if set
        if (ctx.getTheme().resolveAttribute(android.R.attr.windowLightStatusBar, tv, true) && tv.data != 0)
            return true;
        return isColorLight(bg);
    }

    private static boolean isColorLight(@ColorInt int color) {
        double luminance = (0.299 * Color.red(color)
                + 0.587 * Color.green(color)
                + 0.114 * Color.blue(color)) / 255.0;
        return luminance > 0.5;
    }

    private static String fmt(@NonNull Insets i) {
        return "L" + i.left + " T" + i.top + " R" + i.right + " B" + i.bottom;
    }

    private static String orientationString(@NonNull Context ctx) {
        int o = ctx.getResources().getConfiguration().orientation;
        return o == Configuration.ORIENTATION_LANDSCAPE ? "landscape"
                : o == Configuration.ORIENTATION_PORTRAIT  ? "portrait" : "undefined";
    }
}