package com.driot.bookplayer.helpers;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.MsgBoxActivity;
import com.driot.bookplayer.global.Pref;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import java.util.HashSet;
import java.util.Set;

/**
 * Contextual tips: a small info dialog with a "Don't show again" checkbox. MsgBoxActivity itself
 * stores the checkbox (EXTRA_HIDE_TIP_KEY), so a tip also works from a screen that closes right
 * after showing it. Settings > Utilities > "Show tips again" brings them all back (resetAll()).
 * Never shown during instrumented tests: a dialog would cover the screen a test is driving.
 */
public final class TipHelper {

    public enum Tip {
        // Every visit to the Clean screen showing a podcast (key kept from its first version).
        CLEAN_PODCAST_LONG_PRESS("CLEAN_PODCAST_TIP_HIDDEN", 0, R.string.clean_podcast_tip_message, false, 0),
        // Every time the track order mode starts: it is the how-to for that screen.
        TRACK_ORDER("TIP_HIDDEN_TRACK_ORDER", R.string.ChangeTrackOrder_Title, R.string.ChangeTrackOrder_Text, false, 0),
        // Frequently opened screens: at most once per app session.
        // The library is the first screen a new user sees: not before their 6th launch.
        LIBRARY_LONG_PRESS("TIP_HIDDEN_LIBRARY_LONG_PRESS", 0, R.string.tip_library_long_press, true, 5),
        TRACKS_LONG_PRESS("TIP_HIDDEN_TRACKS_LONG_PRESS", 0, R.string.tip_tracks_long_press, true, 0),
        TEXT_READER_GESTURES("TIP_HIDDEN_TEXT_READER_GESTURES", 0, R.string.tip_text_reader_gestures, true, 0),
        PODCAST_COVER_TAP("TIP_HIDDEN_PODCAST_COVER_TAP", 0, R.string.tip_podcast_cover_tap, true, 0);

        final String prefKey;
        @StringRes final int title; // 0 = generic "Tip"
        @StringRes final int message;
        final boolean oncePerSession;
        final int afterLaunches; // not shown until the app was opened more than this many times

        Tip(String prefKey, @StringRes int title, @StringRes int message, boolean oncePerSession,
                int afterLaunches) {
            this.prefKey = prefKey;
            this.title = title;
            this.message = message;
            this.oncePerSession = oncePerSession;
            this.afterLaunches = afterLaunches;
        }
    }

    private static final Set<Tip> shownThisSession = new HashSet<>();
    private static Boolean underInstrumentation = null;

    private TipHelper() {
    }

    public static boolean isHidden(Tip tip) {
        return Pref.getTipHidden(tip.prefKey);
    }

    /** Shows the first of these tips that is due (not hidden, not already shown this session when
     * it is a once-per-session one). One tip at a time: returns whether one was shown. */
    public static boolean maybeShow(Context context, Tip... tips) {
        return maybeShowWithDetails(context, null, tips);
    }

    /** Same, with an extra line under the message (e.g. a situational warning). */
    public static boolean maybeShowWithDetails(Context context, @Nullable CharSequence details, Tip... tips) {
        if (isUnderInstrumentation())
            return false;
        for (Tip tip : tips) {
            if (isHidden(tip) || (tip.oncePerSession && shownThisSession.contains(tip))
                    || Pref.getAppLaunchCount() <= tip.afterLaunches)
                continue;
            shownThisSession.add(tip);
            myLogI("Tip shown: " + tip);
            Intent i = MsgBoxActivity.buildInfo(context,
                    context.getString(tip.title != 0 ? tip.title : R.string.tip_title),
                    context.getString(tip.message), details)
                    .putExtra(MsgBoxActivity.EXTRA_CHECKBOX_TEXT, context.getString(R.string.dont_show_again))
                    .putExtra(MsgBoxActivity.EXTRA_HIDE_TIP_KEY, tip.prefKey);
            if (!(context instanceof android.app.Activity))
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
            return true;
        }
        return false;
    }

    /** Settings > Utilities > "Show tips again". */
    public static void resetAll() {
        for (Tip tip : Tip.values())
            Pref.setTipHidden(tip.prefKey, false);
        shownThisSession.clear();
    }

    private static boolean isUnderInstrumentation() {
        if (underInstrumentation == null) {
            try {
                Class.forName("androidx.test.platform.app.InstrumentationRegistry");
                underInstrumentation = true;
            } catch (ClassNotFoundException e) {
                underInstrumentation = false;
            }
        }
        return underInstrumentation;
    }
}
