package com.driot.bookplayer.utils;

import static com.driot.bookplayer.global.Var.SOURCE_LOCATION_LIBRIVOX;
import static com.driot.bookplayer.global.Var.SOURCE_LOCATION_PODCAST;
import static com.driot.bookplayer.utils.TextOptions.parseMaybeHtml;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.widget.ImageView;

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
