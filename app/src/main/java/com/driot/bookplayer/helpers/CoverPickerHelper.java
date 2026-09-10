package com.driot.bookplayer.helpers;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.NestedScrollView;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

/**
 * Shared "change cover" action menu, used from both the pre-import preview screen
 * (ImportBookSingleActivity - no Folder DB row exists yet) and the post-import editor
 * (ModifyFolderActivity - a real Folder row already exists). The two contexts persist a chosen
 * cover very differently (temp file vs. versioned per-folder file + DB write), so this class only
 * owns the shared UI (menu + candidate picker); each caller supplies its own persistence via
 * Actions.
 */
public class CoverPickerHelper {

    public interface Actions {
        /** User picked one of the images found alongside the book/folder itself. */
        void onCandidateChosen(String path);

        /** User wants to pick an image from the device. */
        void onUploadRequested();

        /** User wants to search the web for a cover. Only invoked when webSearchSupported was
         * true in showCoverOptionsMenu(). */
        default void onWebSearchRequested() {
        }

        /** User wants to generate an initials-based cover. */
        void onGenerateRequested();
    }

    public static void showCoverOptionsMenu(Activity activity, View anchor,
            @Nullable List<String> candidatePaths, boolean webSearchSupported, Actions actions) {
        PopupMenu menu = new PopupMenu(activity, anchor);
        final int idCandidates = 1, idUpload = 2, idWebSearch = 3, idGenerate = 4;
        int candidateCount = candidatePaths == null ? 0 : candidatePaths.size();

        if (candidateCount > 1) {
            menu.getMenu().add(0, idCandidates, 0,
                    activity.getString(R.string.action_choose_from_candidates, candidateCount));
        }
        menu.getMenu().add(0, idUpload, 0, activity.getString(R.string.action_change));
        if (webSearchSupported) {
            menu.getMenu().add(0, idWebSearch, 0, activity.getString(R.string.action_web_search));
        }
        menu.getMenu().add(0, idGenerate, 0, activity.getString(R.string.action_generate));

        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == idCandidates) {
                showCandidatePickerDialog(activity, candidatePaths, actions::onCandidateChosen);
            } else if (id == idUpload) {
                actions.onUploadRequested();
            } else if (id == idWebSearch) {
                actions.onWebSearchRequested();
            } else if (id == idGenerate) {
                actions.onGenerateRequested();
            }
            return true;
        });
        menu.show();
    }

    public interface OnPathChosen {
        void onChosen(String path);
    }

    public static void showCandidatePickerDialog(Activity activity, @Nullable List<String> candidatePaths,
            OnPathChosen callback) {
        if (candidatePaths == null || candidatePaths.isEmpty()) {
            return;
        }

        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.cover_candidates_dialog_title)
                .setView(wrapScrollable(activity, list))
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        for (String path : candidatePaths) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int rowPad = dp(activity, 8);
            row.setPadding(rowPad, rowPad, rowPad, rowPad);
            row.setClickable(true);
            row.setFocusable(true);

            ImageView thumb = new ImageView(activity);
            int thumbSize = dp(activity, 56);
            LinearLayout.LayoutParams thumbParams = new LinearLayout.LayoutParams(thumbSize, thumbSize);
            thumbParams.setMarginEnd(dp(activity, 12));
            thumb.setLayoutParams(thumbParams);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Glide.with(activity).load(path).error(R.drawable.no_image_icon).into(thumb);
            row.addView(thumb);

            TextView label = new TextView(activity);
            label.setText(fileLabel(path));
            row.addView(label);

            row.setOnClickListener(v -> {
                callback.onChosen(path);
                dialog.dismiss();
            });

            list.addView(row);
        }

        dialog.show();
    }

    private static View wrapScrollable(Context context, View content) {
        NestedScrollView scroll = new NestedScrollView(context);
        scroll.addView(content);
        return scroll;
    }

    private static String fileLabel(String path) {
        Uri uri = Uri.parse(path);
        String last = uri.getLastPathSegment();
        return last != null ? last : path;
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density);
    }

    /**
     * In-memory pastel-initials preview, used as the displayed cover when nothing real has been
     * found yet. Purely visual - callers decide separately whether/how to persist it (e.g. only
     * once the user actually confirms the import, or taps "Generate" to keep it for real).
     */
    public static Bitmap generateFallbackPreview(String title, int sizePx) {
        return ImageHelper.createInitialsBitmap(title, sizePx, true);
    }
}
