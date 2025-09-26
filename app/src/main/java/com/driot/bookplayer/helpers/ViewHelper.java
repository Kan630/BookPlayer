package com.driot.bookplayer.helpers;

import static com.driot.bookplayer.utils.TextOptions.parseMaybeHtml;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Rect;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;

public class ViewHelper {

    public static void showAlterDialogToDisplayText(Context context, String fullText, String title) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setMessage(parseMaybeHtml(fullText));
        builder.setPositiveButton(context.getString(R.string.Close), null);
        builder.show();
    }


    public static void animateView(final View view, final int toVisibility, float toAlpha, int duration) {
        boolean show = toVisibility == View.VISIBLE;
        if (show) {
            view.setAlpha(0);
        }
        view.setVisibility(View.VISIBLE);
        view.animate()
                .setDuration(duration)
                .alpha(show ? toAlpha : 0)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        view.setVisibility(toVisibility);
                    }
                });
    }

    public static class SpacesItemDecoration extends RecyclerView.ItemDecoration {
        private final int space;
        public SpacesItemDecoration(int space) { this.space = space; }
        @Override public void getItemOffsets(Rect outRect, View v, RecyclerView parent, RecyclerView.State s) {
            outRect.set(space, space, space, space);
        }
    }
    public static int dp(Context c, int v){ return Math.round(c.getResources().getDisplayMetrics().density * v); }

}
