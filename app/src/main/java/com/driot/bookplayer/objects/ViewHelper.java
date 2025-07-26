package com.driot.bookplayer.objects;

import static com.driot.bookplayer.utils.TextOptions.parseMaybeHtml;

import android.app.AlertDialog;
import android.content.Context;

import com.driot.bookplayer.R;

public class ViewHelper {

    public static void showAlterDialogToDisplayText(Context context, String fullText, String title) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setMessage(parseMaybeHtml(fullText));
        builder.setPositiveButton(context.getString(R.string.Close), null);
        builder.show();
    }

}
