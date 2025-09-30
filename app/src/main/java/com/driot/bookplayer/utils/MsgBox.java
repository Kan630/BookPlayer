package com.driot.bookplayer.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.Nullable;

import com.driot.bookplayer.activities.MsgBoxActivity;

public final class MsgBox {

    private MsgBox() {}

    // ==== INFO ====
    public static void info(Context ctx, String title, String message) {
        info(ctx, title, message, null);
    }

    public static void info(Context ctx, String title, String message, @Nullable String details) {
        Intent i = MsgBoxActivity.buildInfo(ctx, title, message, details);
        ctx.startActivity(i);
    }

    // ==== ALERT ====
    public static void alert(Context ctx, String title, String message) {
        alert(ctx, title, message, null);
    }

    public static void alert(Context ctx, String title, String message, @Nullable String details) {
        Intent i = MsgBoxActivity.buildAlert(ctx, title, message, details);
        ctx.startActivity(i);
    }

    public static void alertWithNeutral(Context ctx,
                                        String title,
                                        String message,
                                        @Nullable String details,
                                        String neutralText,
                                        @Nullable Intent neutralIntent) {
        Intent i = MsgBoxActivity.buildAlert(ctx, title, message, details);
        i.putExtra(MsgBoxActivity.EXTRA_NEUTRAL, neutralText);
        if (neutralIntent != null) {
            i.putExtra(MsgBoxActivity.EXTRA_NEUTRAL_INTENT, neutralIntent);
        }
        ctx.startActivity(i);
    }

    // ==== QUESTION ====
    /**
     * Démarre un MsgBoxActivity de type QUESTION.
     * Utilise startActivityForResult, donc appelle depuis une Activity !
     */
    public static void ask(Activity activity,
                           String title,
                           String message,
                           @Nullable String details,
                           @Nullable String positiveText,
                           @Nullable String negativeText,
                           int requestCode) {
        Intent i = MsgBoxActivity.buildQuestion(activity, title, message, details, positiveText, negativeText);
        activity.startActivityForResult(i, requestCode);
    }
}
