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
import com.driot.bookplayer.views.FadingEdgeFrameLayout;

/**
 * Edge-to-edge + insets helper (API 26+).
 * Handles status/nav bar colors, display cutout, IME padding,
 * and an optional top fade mask via {@link FadingEdgeFrameLayout}.
 */
public final class InsetHelper {

    private InsetHelper() {}

    // Total height of the fade region = statusBarHeight × this value.
    // 2.0 = fade extends twice the status bar height down into the content.
    private static final float FADE_HEIGHT_MULTIPLIER = 1.5f;

    // Fraction of the fade region that is a hard fully-faded cut before the gradient begins.
    // 0.0 = no solid zone, gradient starts immediately at the top.
    // 0.5 = top half is solid fade, gradient fills the bottom half.
    private static final float FADE_SOLID_RATIO = 0.5f;

    // How transparent the content is at the strongest point of the fade.
    // 0.0 = fully hidden (content completely invisible at peak).
    // 0.2 = 80% faded — content is still faintly visible, nothing disappears entirely.
    // 1.0 = no fade at all (pointless but valid).
    private static final float FADE_MAX_ALPHA = 0.3f;

    // ===== Public API =====

    /** Classic fitSystemWindows: pads all sides + IME on the root content view. */
    public static void apply(@NonNull Activity activity) {
        View root = activity.findViewById(android.R.id.content);
        if (root == null) {
            myLogEE(null, "apply(): root content view is NULL, aborting.");
            return;
        }
        applyInsets(activity, root,
                /* padTop */              true,
                /* padBottom */           true,
                /* padSides */            true,
                /* handleIME */           true,
                /* handleCutout */        true,
                /* allowShortEdgeCutout */true,
                /* addToPadding */        false,
                /* consume */             true,
                /* fadingParent */        null);
    }

    /**
     * Scrollable full-screen view: draws behind nav bar with proper padding.
     * Use this for standalone activities (not inside FullActivity chrome).
     * If {@code scrollableView}'s parent is a {@link FadingEdgeFrameLayout},
     * its fade params are updated from the real status bar inset automatically.
     */
    public static void applyInsetsForScrollableBehindNavBar(@NonNull Activity activity,
                                                            @NonNull View scrollableView) {
        FadingEdgeFrameLayout fading = null;
        if (scrollableView.getParent() instanceof FadingEdgeFrameLayout) {
            fading = (FadingEdgeFrameLayout) scrollableView.getParent();
        }
        applyInsets(activity, scrollableView,
                /* padTop */              true,
                /* padBottom */           true,
                /* padSides */            true,
                /* handleIME */           true,
                /* handleCutout */        true,
                /* allowShortEdgeCutout */true,
                /* addToPadding */        false,
                /* consume */             true,
                /* fadingParent */        fading);
    }

    /**
     * Scrollable view inside a FullActivity (has miniNowPlaying + bottomNav chrome).
     * Applies top and side insets only — bottom is already handled by the constraint
     * chain (base_content is constrained above miniNowPlaying, never reaching the
     * system nav bar). Applying sys.bottom here would create a white-space gap.
     * If {@code scrollableView}'s parent is a {@link FadingEdgeFrameLayout},
     * its fade params are updated from the real status bar inset automatically.
     */
    public static void applyInsetsForScrollableInFullActivity(@NonNull Activity activity,
                                                              @NonNull View scrollableView) {
        FadingEdgeFrameLayout fading = null;
        if (scrollableView.getParent() instanceof FadingEdgeFrameLayout) {
            fading = (FadingEdgeFrameLayout) scrollableView.getParent();
        }
        applyInsets(activity, scrollableView,
                /* padTop */              true,
                /* padBottom */           false,
                /* padSides */            true,
                /* handleIME */           true,
                /* handleCutout */        true,
                /* allowShortEdgeCutout */true,
                /* addToPadding */        false,
                /* consume */             true,
                /* fadingParent */        fading);
    }

    /** Status-bar height only — no side or cutout padding (avoids dead space). */
    public static void applyTopInsetsOnlyTo(@NonNull Activity activity,
                                            @NonNull View targetView) {
        applyInsets(activity, targetView,
                /* padTop */              true,
                /* padBottom */           false,
                /* padSides */            false,
                /* handleIME */           false,
                /* handleCutout */        false,
                /* allowShortEdgeCutout */false,
                /* addToPadding */        true,
                /* consume */             false,
                /* fadingParent */        null);
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
                                    boolean consume,
                                    @androidx.annotation.Nullable FadingEdgeFrameLayout fadingParent) {
        try {
            final Window window = activity.getWindow();

            WindowCompat.setDecorFitsSystemWindows(window, false);

            if (android.os.Build.VERSION.SDK_INT >= 28) {
                window.getAttributes().layoutInDisplayCutoutMode = allowShortEdgeCutout
                        ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                        : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER;
                window.setAttributes(window.getAttributes());
            }

            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

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
                        + " handleCutout=" + handleCutout
                        + " hasFadingParent=" + (fadingParent != null)
                        + " orientation=" + orientationString(activity));

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

                if (fadingParent != null) {
                    int fadeHeight  = Math.round(sys.top * FADE_HEIGHT_MULTIPLIER);
                    int solidHeight = Math.round(fadeHeight * FADE_SOLID_RATIO);
                    fadingParent.setFadeParams(fadeHeight, solidHeight, FADE_MAX_ALPHA);
                    if (KanLogger.LOG_INSETS)
                        myLogD("setFadeParams: fadeHeight=" + fadeHeight
                                + " solidHeight=" + solidHeight
                                + " maxAlpha=" + FADE_MAX_ALPHA
                                + " (statusBar=" + sys.top + ")");
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
