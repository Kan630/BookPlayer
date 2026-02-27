package com.driot.bookplayer.helpers;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Rect;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.widget.AppCompatAutoCompleteTextView;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.utils.MsgBox;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

public class ViewHelper {

    public static void showAlertDialogText(Context context, CharSequence text, CharSequence title) {
        MsgBox.info(context, title != null ? title : "", text != null ? text : "");
    }

    public static class SpacesItemDecoration extends RecyclerView.ItemDecoration {
        private final int space;

        public SpacesItemDecoration(int space) {
            this.space = space;
        }

        @Override
        public void getItemOffsets(Rect outRect, View v, RecyclerView parent, RecyclerView.State s) {
            outRect.set(space, space, space, space);
        }
    }

    public static int dp(Context c, int v) {
        return Math.round(c.getResources().getDisplayMetrics().density * v);
    }

    public static void pasteClipboard(Context context, AppCompatAutoCompleteTextView editText) {
        try {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                ClipData clip = clipboard.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0) {
                    CharSequence pasteData = clip.getItemAt(0).coerceToText(context);
                    if (!TextUtils.isEmpty(pasteData)) {
                        editText.setText(pasteData);
                        editText.setSelection(pasteData.length());
                        editText.showDropDown(); // refresh suggestions contextually
                    }
                }
            } else {
                Toast.makeText(context, context.getString(R.string.Clipboard_is_empty), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            myToastEE(e, context.getString(R.string.error) + " : " + e.getMessage());
        }
    }

}
