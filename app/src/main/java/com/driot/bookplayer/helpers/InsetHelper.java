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
 * - Global logging toggle
 * - Display cutout (camera hole) protection (esp. landscape)
 */
public final class InsetHelper {

    private InsetHelper() {}

    // ===== Configs (immutable) =====
    private static final class WindowConfig {
        final boolean edgeToEdge;
        final int statusBarColor;        // -1 => theme
        final int navigationBarColor;
        final boolean softInputAdjustResize;
        final Boolean lightStatusBarIcons; // null => auto
        final Boolean lightNavBarIcons;    // null => auto
        final boolean allowShortEdgeCutout; // true => layout may extend into cutout on short edges

        private WindowConfig(Builder b) {
            this.edgeToEdge = b.edgeToEdge;
            this.statusBarColor = b.statusBarColor;
            this.navigationBarColor = b.navigationBarColor;
            this.softInputAdjustResize = b.softInputAdjustResize;
            this.lightStatusBarIcons = b.lightStatusBarIcons;
            this.lightNavBarIcons = b.lightNavBarIcons;
            this.allowShortEdgeCutout = b.allowShortEdgeCutout;
        }

        static final class Builder {
            boolean edgeToEdge = true;
            int statusBarColor = Color.TRANSPARENT;
            int navigationBarColor = Color.TRANSPARENT;
            boolean softInputAdjustResize = false;
            Boolean lightStatusBarIcons = null;
            Boolean lightNavBarIcons = null;
            boolean allowShortEdgeCutout = false; // default: avoid cutout overlap via padding

            Builder edgeToEdge(boolean v){ this.edgeToEdge = v; return this; }
            Builder statusBarColor(int v){ this.statusBarColor = v; return this; }
            Builder useThemeStatusBarColor(){ this.statusBarColor = -1; return this; }
            Builder navigationBarColor(int v){ this.navigationBarColor = v; return this; }
            Builder softInputAdjustResize(boolean v){ this.softInputAdjustResize = v; return this; }
            Builder lightStatusBarIcons(Boolean v){ this.lightStatusBarIcons = v; return this; }
            Builder lightNavBarIcons(Boolean v){ this.lightNavBarIcons = v; return this; }
            Builder allowShortEdgeCutout(boolean v){ this.allowShortEdgeCutout = v; return this; }
            WindowConfig build(){ return new WindowConfig(this); }
        }
    }

    private static final class PaddingConfig {
        final boolean top;
        final boolean bottom;
        final boolean left;
        final boolean right;
        final boolean handleIME;
        final boolean addToPadding;
        final boolean handleCutout; // NEW: also pad for display cutout

        private PaddingConfig(Builder b) {
            this.top = b.top;
            this.bottom = b.bottom;
            this.left = b.left;
            this.right = b.right;
            this.handleIME = b.handleIME;
            this.addToPadding = b.addToPadding;
            this.handleCutout = b.handleCutout;
        }

        static final class Builder {
            boolean top = true;
            boolean bottom = true;
            boolean left = false;
            boolean right = false;
            boolean handleIME = false;
            boolean addToPadding = false;
            boolean handleCutout = true; // default ON to protect against camera hole

            Builder top(boolean v){ this.top = v; return this; }
            Builder bottom(boolean v){ this.bottom = v; return this; }
            Builder sides(boolean v){ this.left = v; this.right = v; return this; }
            Builder handleIME(boolean v){ this.handleIME = v; return this; }
            Builder addToPadding(boolean v){ this.addToPadding = v; return this; }
            Builder handleCutout(boolean v){ this.handleCutout = v; return this; }

            Builder onlyTop(){ this.top = true; this.bottom = false; this.left = false; this.right = false; return this; }
            Builder topAndBottom(){ this.top = true; this.bottom = true; this.left = false; this.right = false; return this; }

            PaddingConfig build(){ return new PaddingConfig(this); }
        }
    }

    // ===== Public API =====

    /** Classic fitSystemWindows behavior with IME support. */
    public static void apply(@NonNull Activity activity) {
        View root = activity.findViewById(android.R.id.content);
        if (root == null) {
            myLogEE(null,"apply(): root content view is NULL, aborting insets setup.");
            return;
        }
        if (KanLogger.LOG_INSETS) myLogD("apply() on root");
        applyInsets(activity, root,
                new WindowConfig.Builder()
                        .softInputAdjustResize(true)
                        .allowShortEdgeCutout(true)
                        .build(),
                new PaddingConfig.Builder()
                        .topAndBottom()
                        .handleIME(true)
                        .handleCutout(true)
                        .sides(true)
                        .build(),
                /*consume*/ true);
    }

    /** Scrollable view draws behind nav bar with proper bottom padding. */
    public static void applyInsetsForScrollableBehindNavBar(@NonNull Activity activity, @NonNull View scrollableView) {
        if (KanLogger.LOG_INSETS) myLogD("applyInsetsForScrollableBehindNavBar()");
        if (scrollableView == null) { //can be null at runtime, just a compiler check
            myLogE("applyInsetsForScrollableBehindNavBar(): scrollableView is NULL; falling back to root. for " + (activity!=null ? activity.getLocalClassName() : "null activity"));
            View root = activity.findViewById(android.R.id.content);
            if (root == null) {
                myLogEE(null,"applyInsetsForScrollableBehindNavBar(): root also NULL; aborting insets setup. for " + (activity!=null ? activity.getLocalClassName() : "null activity"));
                return;
            }
            scrollableView = root; // fallback
        }
        applyInsets(activity, scrollableView,
                new WindowConfig.Builder()
                        .softInputAdjustResize(true)
                        .allowShortEdgeCutout(true)
                        .build(),
                new PaddingConfig.Builder()
                        .topAndBottom()   //maybe change to bottom only...
                        .handleIME(true)
                        .handleCutout(true)
                        .sides(true)
                        .build(),
                /*consume*/ true);
    }


    // 1) Ensure a view starts *below* the status bar / cutout.
//    Keeps edge-to-edge on, but adds only TOP padding (and left/right if you wish).
    public static void applyTopInsetsTo(@NonNull Activity activity, @NonNull View targetView) {
        if (KanLogger.LOG_INSETS) myLogD("applyTopInsetsTo()");
        applyInsets(activity, targetView,
                new WindowConfig.Builder()
                        .softInputAdjustResize(true)
                        .allowShortEdgeCutout(false) // safer for headers
                        .build(),
                new PaddingConfig.Builder()
                        .onlyTop()
                        .handleCutout(true)
                        .sides(true)          // set to false if you don’t want side padding
                        .addToPadding(true)   // preserve existing margins/padding
                        .build(),
                /*consume*/ false);
    }

    // 2) Scrollable list behind nav bar, with IME lift.
//    Only bottom padding + optional side padding. No top padding here.
    public static void applyBottomInsetsForScrollable(@NonNull Activity activity, @NonNull View scrollableView) {
        if (KanLogger.LOG_INSETS) myLogD("applyBottomInsetsForScrollable()");
        applyInsets(activity, scrollableView,
                new WindowConfig.Builder()
                        .softInputAdjustResize(true)
                        .allowShortEdgeCutout(true)
                        .build(),
                new PaddingConfig.Builder()
                        .top(false)          // <-- important: no top padding here
                        .bottom(true)
                        .sides(true)
                        .handleIME(true)
                        .handleCutout(true)
                        .addToPadding(false) // replace padding; your RV already has clipToPadding="false"
                        .build(),
                /*consume*/ false);
    }

    // ===== Core =====

    private static void applyInsets(@NonNull Activity activity,
                                    @NonNull View targetView,
                                    @NonNull WindowConfig windowCfg,
                                    @NonNull PaddingConfig padCfg,
                                    boolean consume) {
        try {
            final Window window = activity.getWindow();

            // Window setup
            WindowCompat.setDecorFitsSystemWindows(window, !windowCfg.edgeToEdge);

            if (android.os.Build.VERSION.SDK_INT >= 28) {
                window.getAttributes().layoutInDisplayCutoutMode = windowCfg.allowShortEdgeCutout
                        ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                        : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER;
                window.setAttributes(window.getAttributes());
            }

            final int actualStatus = windowCfg.statusBarColor == -1
                    ? resolveAttr(activity, android.R.attr.statusBarColor, Color.BLACK)
                    : windowCfg.statusBarColor;
            window.setStatusBarColor(actualStatus);
            window.setNavigationBarColor(windowCfg.navigationBarColor);

            if (windowCfg.softInputAdjustResize) {
                window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }

            if (KanLogger.LOG_INSETS) myLogD("applyInsets(): edgeToEdge=" + windowCfg.edgeToEdge
                    + ", statusBarColor=" + colorHex(actualStatus)
                    + " (requested=" + colorHex(windowCfg.statusBarColor) + ")"
                    + ", navBarColor=" + colorHex(windowCfg.navigationBarColor)
                    + ", adjustResize=" + windowCfg.softInputAdjustResize
                    + ", allowShortEdgeCutout=" + windowCfg.allowShortEdgeCutout
                    + ", orientation=" + orientationString(activity));

            configureBars(activity, window, windowCfg, actualStatus);

            ViewCompat.setOnApplyWindowInsetsListener(targetView, (v, insets) -> {
                final Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                final Insets cut = padCfg.handleCutout ? insets.getInsets(WindowInsetsCompat.Type.displayCutout()) : Insets.NONE;
                final Insets ime = padCfg.handleIME ? insets.getInsets(WindowInsetsCompat.Type.ime()) : Insets.NONE;

                int left = padCfg.left ? Math.max(sys.left, cut.left) : 0;
                int top = padCfg.top ? Math.max(sys.top, cut.top) : 0;
                int right = padCfg.right ? Math.max(sys.right, cut.right) : 0;
                int bottom = padCfg.bottom ? Math.max(sys.bottom, cut.bottom) : 0;

                if (padCfg.handleIME) bottom = Math.max(bottom, ime.bottom);

                if (padCfg.addToPadding) {
                    v.setPadding(v.getPaddingLeft() + left,
                            v.getPaddingTop() + top,
                            v.getPaddingRight() + right,
                            v.getPaddingBottom() + bottom);
                } else {
                    v.setPadding(left, top, right, bottom);
                }

                if (KanLogger.LOG_INSETS) myLogD("onApplyWindowInsets -> sys=" + insetsToString(sys)
                        + (padCfg.handleCutout ? (", cut=" + insetsToString(cut)) : "")
                        + (padCfg.handleIME ? (", ime=" + insetsToString(ime)) : "")
                        + ", applied(L/T/R/B)=" + left + "/" + top + "/" + right + "/" + bottom
                        + ", addToPadding=" + padCfg.addToPadding
                        + ", consume=" + consume);

                return consume ? WindowInsetsCompat.CONSUMED : insets;
            });

            requestApplyInsetsSafely(targetView);
        } catch (Exception e) {
            myLogEE(e, "applyInsets() failed unexpectedly, skipped insets setup.");
        }
    }

    private static void configureBars(@NonNull Activity activity,
                                      @NonNull Window window,
                                      @NonNull WindowConfig cfg,
                                      @ColorInt int actualStatusColor) {
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(window, window.getDecorView());
        if (c == null) {
            myLogW("No WindowInsetsControllerCompat available.");
            return;
        }

        final boolean statusLight;
        if (cfg.lightStatusBarIcons != null) {
            statusLight = cfg.lightStatusBarIcons;
        } else if (cfg.statusBarColor == Color.TRANSPARENT) {
            statusLight = isLightSurface(activity);
        } else {
            statusLight = isColorLight(actualStatusColor);
        }

        final boolean navLight = (cfg.lightNavBarIcons != null)
                ? cfg.lightNavBarIcons
                : isLightSurface(activity);

        c.setAppearanceLightStatusBars(statusLight);
        c.setAppearanceLightNavigationBars(navLight);
/*
        myLogD("configureBars(): lightStatus=" + statusLight
                + ", lightNav=" + navLight
                + ", statusBarColor(actual)=" + colorHex(actualStatusColor));

 */
    }

    // ===== Helpers =====

    private static boolean isColorLight(@ColorInt int color) {
        double L = (0.299 * Color.red(color) +
                0.587 * Color.green(color) +
                0.114 * Color.blue(color)) / 255d;
        return L > 0.5;
    }

    private static boolean isLightSurface(@NonNull Context ctx) {
        TypedValue tv = new TypedValue();
        int bg;

        if (ctx.getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurface, tv, true)) {
            bg = tv.data;
        } else if (ctx.getTheme().resolveAttribute(android.R.attr.windowBackground, tv, true)) {
            bg = tv.data;
        } else if (ctx.getTheme().resolveAttribute(android.R.attr.colorBackground, tv, true)) {
            bg = tv.data;
        } else {
            myLogW("isLightSurface(): no background attr found; assuming dark.");
            return false;
        }

        boolean explicitLight = false;
        if (ctx.getTheme().resolveAttribute(android.R.attr.windowLightStatusBar, tv, true)) {
            explicitLight = tv.data != 0;
        }

        boolean result = explicitLight || isColorLight(bg);
        /*
        myLogD("isLightSurface(): bg=" + colorHex(bg)
                + ", explicitLight=" + explicitLight
                + ", computedLight=" + isColorLight(bg)
                + " -> " + result);

         */
        return result;
    }

    private static int resolveAttr(@NonNull Context ctx, int attrRes, int defVal) {
        TypedValue tv = new TypedValue();
        if (ctx.getTheme().resolveAttribute(attrRes, tv, true)) {
            return tv.data;
        }
        myLogW("resolveAttr(): attrRes=" + attrRes + " not found, using default=" + colorHex(defVal));
        return defVal;
    }

    private static void requestApplyInsetsSafely(@NonNull View v) {
        try {
            ViewCompat.requestApplyInsets(v);
            //myLogD("requestApplyInsetsSafely() posted for view=" + v.getClass().getSimpleName());
        } catch (Throwable t) {
            myLogEE(t, "requestApplyInsetsSafely() failed");
        }
    }

    private static String insetsToString(@NonNull Insets i) {
        return "L" + i.left + " T" + i.top + " R" + i.right + " B" + i.bottom;
    }

    private static String colorHex(int c) {
        if (c == -1) return "THEME";
        return String.format("#%08X", (c));
    }

    private static String orientationString(@NonNull Context ctx) {
        int o = ctx.getResources().getConfiguration().orientation;
        return (o == Configuration.ORIENTATION_LANDSCAPE) ? "landscape"
                : (o == Configuration.ORIENTATION_PORTRAIT) ? "portrait" : "undefined";
    }
}
