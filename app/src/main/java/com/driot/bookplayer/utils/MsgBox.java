package com.driot.bookplayer.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.Nullable;

import com.driot.bookplayer.activities.MsgBoxActivity;

public final class MsgBox {

    private MsgBox() {
    }

    // ==== INFO ====
    public static void info(Context ctx, CharSequence title, CharSequence message) {
        info(ctx, title, message, null);
    }

    public static void info(Context ctx, CharSequence title, CharSequence message, @Nullable CharSequence details) {
        Intent i = MsgBoxActivity.buildInfo(ctx, title, message, details);
        if (!(ctx instanceof Activity)) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        ctx.startActivity(i);
    }

    // ==== ALERT ====
    public static void alert(Context ctx, CharSequence title, CharSequence message) {
        alert(ctx, title, message, null);
    }

    public static void alert(Context ctx, CharSequence title, CharSequence message, @Nullable CharSequence details) {
        Intent i = MsgBoxActivity.buildAlert(ctx, title, message, details);
        if (!(ctx instanceof Activity)) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        ctx.startActivity(i);
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
        if (!(ctx instanceof Activity)) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        ctx.startActivity(i);
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
