package com.driot.bookplayer.helpers;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;

import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/** One-liner edge-to-edge paddings for top & bottom. */
public final class InsetHelper {
    private InsetHelper() {}

    /**
     * Enables edge-to-edge and applies insets:
     * - Adds status bar height to topContainer's top padding
     * - Adds max(nav bar, IME) height to bottomContainer's bottom padding
     * - Optionally pads contentContainer horizontally for gesture insets
     *
     * Pass null for any container you don't want adjusted.
     */
    public static void applyEdgeToEdge(Activity activity,
                                       @Nullable View topContainer,
                                       @Nullable View bottomContainer,
                                       @Nullable View contentContainerForSides) {

        final Window window = activity.getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);

        // Make bars transparent so content can draw behind
        //not sure if really needed...
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        final View root = activity.findViewById(android.R.id.content);
/*
        // fuck with top system bar...
        // Optional: light/dark icons depending on your theme (set to true if your background is light)
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, root);
        // Example: light status bar icons off (use your own condition/theme here)
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);

 */

        // Capture initial paddings so we don’t stack them on every inset dispatch
        final int topInitPadTop = topContainer != null ? topContainer.getPaddingTop() : 0;
        final int topInitPadLeft = topContainer != null ? topContainer.getPaddingLeft() : 0;
        final int topInitPadRight = topContainer != null ? topContainer.getPaddingRight() : 0;
        final int topInitPadBottom = topContainer != null ? topContainer.getPaddingBottom() : 0;

        final int bottomInitPadTop = bottomContainer != null ? bottomContainer.getPaddingTop() : 0;
        final int bottomInitPadLeft = bottomContainer != null ? bottomContainer.getPaddingLeft() : 0;
        final int bottomInitPadRight = bottomContainer != null ? bottomContainer.getPaddingRight() : 0;
        final int bottomInitPadBottom = bottomContainer != null ? bottomContainer.getPaddingBottom() : 0;

        final int contentInitPadLeft = contentContainerForSides != null ? contentContainerForSides.getPaddingLeft() : 0;
        final int contentInitPadRight = contentContainerForSides != null ? contentContainerForSides.getPaddingRight() : 0;
        final int contentInitPadTop = contentContainerForSides != null ? contentContainerForSides.getPaddingTop() : 0;
        final int contentInitPadBottom = contentContainerForSides != null ? contentContainerForSides.getPaddingBottom() : 0;

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());

            // Top container → pad for status bar height
            if (topContainer != null) {
                topContainer.setPadding(
                        topInitPadLeft + sys.left,
                        topInitPadTop + sys.top,
                        topInitPadRight + sys.right,
                        topInitPadBottom
                );
            }

            // Bottom container → pad for whichever is taller: nav bar or IME
            int bottomInset = Math.max(sys.bottom, ime.bottom);
            if (bottomContainer != null) {
                bottomContainer.setPadding(
                        bottomInitPadLeft + sys.left,
                        bottomInitPadTop,
                        bottomInitPadRight + sys.right,
                        bottomInitPadBottom + bottomInset
                );
            }

            // Optional: pad content sides for gesture nav (and keep its original top/bottom)
            if (contentContainerForSides != null) {
                contentContainerForSides.setPadding(
                        contentInitPadLeft + sys.left,
                        contentInitPadTop,
                        contentInitPadRight + sys.right,
                        contentInitPadBottom
                );
            }

            // Return the same insets so children may also consume them if needed
            return insets;
        });
    }
}
