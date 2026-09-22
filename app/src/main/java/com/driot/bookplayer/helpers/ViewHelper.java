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
            int position = parent.getChildAdapterPosition(v);
            if (position == 0) {
                // outRect.set(space, 0, space, space);
                outRect.set(space, space, space, space);
            } else {
                outRect.set(space, space, space, space);
            }
        }
    }

    public static int dp(Context c, int v) {
        return Math.round(c.getResources().getDisplayMetrics().density * v);
    }

    /**
     * Walks up the View hierarchy from {@code view} (exclusive) and returns the nearest ancestor
     * of type {@code type}, or null if the view isn't nested inside one. Use this instead of
     * reaching for the hosting Activity to find a sibling/ancestor view by id - it works
     * regardless of whether the view ends up Activity- or Fragment-hosted, and doesn't depend on
     * getContext() actually being an Activity (e.g. Hilt wraps fragment contexts in
     * ViewComponentManager$FragmentContextWrapper for DI, which isn't one).
     */
    @androidx.annotation.Nullable
    public static <T extends View> T findAncestor(View view, Class<T> type) {
        android.view.ViewParent parent = view.getParent();
        while (parent instanceof View) {
            if (type.isInstance(parent)) {
                return type.cast(parent);
            }
            parent = parent.getParent();
        }
        return null;
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

    public static void hideKeyboard(Context context, View view) {
        if (view != null) {
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) context
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }

    public static void showKeyboard(Context context, View view) {
        if (view != null) {
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) context
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                if (view.isAttachedToWindow()) {
                    imm.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                } else {
                    view.post(() -> imm.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT));
                }
            }
        }
    }
}
