package com.driot.bookplayer.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.Nullable;

import com.driot.bookplayer.activities.MsgBoxActivity;

public final class MsgBox {

    private MsgBox() {
    }

    /**
     * Starts the dialog over the current screen. Fragments hand out a wrapped context (Hilt's
     * FragmentContextWrapper), not the Activity itself: taken for a non-Activity context, that
     * added FLAG_ACTIVITY_NEW_TASK and - MsgBoxActivity having taskAffinity="" - opened the
     * translucent dialog in a task of its own, over black instead of over the app.
     */
    public static void start(Context ctx, Intent i) {
        Activity activity = activityOf(ctx);
        if (activity != null) {
            activity.startActivity(i);
        } else {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // really no screen (service, receiver...)
            ctx.startActivity(i);
        }
    }

    @Nullable
    public static Activity activityOf(Context ctx) {
        while (ctx instanceof android.content.ContextWrapper) {
            if (ctx instanceof Activity)
                return (Activity) ctx;
            ctx = ((android.content.ContextWrapper) ctx).getBaseContext();
        }
        return null;
    }

    // ==== INFO ====
    public static void info(Context ctx, CharSequence title, CharSequence message) {
        info(ctx, title, message, null);
    }

    public static void info(Context ctx, CharSequence title, CharSequence message, @Nullable CharSequence details) {
        Intent i = MsgBoxActivity.buildInfo(ctx, title, message, details);
        start(ctx, i);
    }

    // ==== ALERT ====
    public static void alert(Context ctx, CharSequence title, CharSequence message) {
        alert(ctx, title, message, null);
    }

    public static void alert(Context ctx, CharSequence title, CharSequence message, @Nullable CharSequence details) {
        Intent i = MsgBoxActivity.buildAlert(ctx, title, message, details);
        start(ctx, i);
    }

    public static void alertWithNeutral(Context ctx,
            CharSequence title,
            CharSequence message,
            @Nullable CharSequence details,
            CharSequence neutralText,
            @Nullable Intent neutralIntent) {
        Intent i = MsgBoxActivity.buildAlert(ctx, title, message, details);
        i.putExtra(MsgBoxActivity.EXTRA_NEUTRAL, neutralText);
        if (neutralIntent != null) {
            i.putExtra(MsgBoxActivity.EXTRA_NEUTRAL_INTENT, neutralIntent);
        }
        start(ctx, i);
    }

    // ==== QUESTION ====
    /**
     * Démarre un MsgBoxActivity de type QUESTION.
     * Utilise startActivityForResult, donc appelle depuis une Activity !
     */
    public static void ask(Activity activity,
            CharSequence title,
            CharSequence message,
            @Nullable CharSequence details,
            @Nullable CharSequence positiveText,
            @Nullable CharSequence negativeText,
            int requestCode) {
        Intent i = MsgBoxActivity.buildQuestion(activity, title, message, details, positiveText, negativeText);
        activity.startActivityForResult(i, requestCode);
    }

    public static void ask(androidx.fragment.app.Fragment fragment,
            CharSequence title,
            CharSequence message,
            @Nullable CharSequence details,
            @Nullable CharSequence positiveText,
            @Nullable CharSequence negativeText,
            int requestCode) {
        Intent i = MsgBoxActivity.buildQuestion(fragment.requireContext(), title, message, details, positiveText,
                negativeText);
        fragment.startActivityForResult(i, requestCode);
    }
}
