package com.driot.bookplayer.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.helpers.BookSpaceHelper;
import com.driot.bookplayer.podcasts.PodcastHelper;
import com.driot.bookplayer.redownload.RedownloadHelper;
import com.driot.bookplayer.utils.Tonio;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Quick actions of a book in the Clean screen (long press; podcasts and radio recordings have
 * their own PodcastEpisodeCleanupSheet): free up space but keep the book, remove listened tracks,
 * download again, move to the other storage - only the ones that apply (BookSpaceHelper). For a
 * folder no book points to, shows what it contains. Posts PodcastHelper.EPISODE_CLEANUP_RESULT_KEY
 * to its parent fragment manager after a deletion, so the Clean list refreshes.
 */
public class CleanItemSheet extends BottomSheetDialogFragment {

    public static final String TAG = "CleanItemSheet";

    private static final String ARG_FOLDER_ID = "folderId";
    private static final String ARG_NAME = "name";
    private static final String ARG_IMAGE = "image";
    private static final String ARG_DIR = "dir";
    private static final String ARG_SIZE = "size";
    private static final int MAX_FILES_LISTED = 50;

    private long folderId;
    private long sizeBytes; // shown in the header, lowered by each deletion
    private boolean busy = false;

    public static CleanItemSheet newInstance(long folderId, String name, @Nullable String image, File dir,
            long sizeBytes) {
        Bundle args = new Bundle();
        args.putLong(ARG_FOLDER_ID, folderId);
        args.putString(ARG_NAME, name);
        args.putString(ARG_IMAGE, image);
        args.putString(ARG_DIR, dir.getAbsolutePath());
        args.putLong(ARG_SIZE, sizeBytes);
        CleanItemSheet sheet = new CleanItemSheet();
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_clean_item, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = requireArguments();
        folderId = args.getLong(ARG_FOLDER_ID);
        sizeBytes = args.getLong(ARG_SIZE);
        ((TextView) view.findViewById(R.id.clean_item_name)).setText(args.getString(ARG_NAME));
        String image = args.getString(ARG_IMAGE);
        if (image != null && !image.isEmpty())
            Glide.with(this).load(image).placeholder(R.drawable.ic_menu_book_24)
                    .into((ImageView) view.findViewById(R.id.clean_item_cover));
        load();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() instanceof BottomSheetDialog) {
            BottomSheetBehavior<?> behavior = ((BottomSheetDialog) getDialog()).getBehavior();
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    private void load() {
        Context app = requireContext().getApplicationContext();
        File dir = new File(requireArguments().getString(ARG_DIR));
        AppDatabase.databaseReadExecutor.execute(() -> {
            BookSpaceHelper.Preview p = folderId > 0 ? BookSpaceHelper.preview(app, folderId) : null;
            File[] files = p == null ? dir.listFiles(File::isFile) : null;
            View root = getView();
            if (root == null)
                return;
            root.post(() -> {
                if (!isAdded())
                    return;
                if (p != null)
                    showBook(p);
                else
                    showUnknownFolder(files);
            });
        });
    }

    private void showBook(BookSpaceHelper.Preview p) {
        View root = requireView();
        ((TextView) root.findViewById(R.id.clean_item_summary)).setText(getResources().getQuantityString(
                R.plurals.clean_item_summary, p.trackCount, p.trackCount,
                Tonio.getReadableSize(sizeBytes)));
        LinearLayout actions = root.findViewById(R.id.clean_item_actions);
        actions.removeAllViews();

        if (p.redownloadable && !p.ownedIds.isEmpty()) {
            addAction(actions, R.drawable.ic_cloud_24, getString(R.string.clean_item_free_title),
                    getString(R.string.clean_item_free_desc), filesCount(p.ownedIds.size(), p.ownedBytes),
                    getString(R.string.clean_item_free_button), R.drawable.ic_delete_24,
                    v -> confirmAndDelete(p.ownedIds, p.ownedBytes));
        }
        // Only when it differs from freeing everything (some tracks are not finished yet).
        if (p.redownloadable && p.trackCount >= 2 && !p.listenedIds.isEmpty()
                && p.listenedIds.size() < p.ownedIds.size()) {
            addAction(actions, R.drawable.ic_done_all_24, getString(R.string.clean_item_listened_title),
                    getString(R.string.clean_item_listened_desc), filesCount(p.listenedIds.size(), p.listenedBytes),
                    getString(R.string.Delete), R.drawable.ic_delete_24,
                    v -> confirmAndDelete(p.listenedIds, p.listenedBytes));
        }
        if (p.redownloadable && p.missingCount > 0) {
            addAction(actions, R.drawable.ic_download_action_24, getString(R.string.clean_item_download_title),
                    getResources().getQuantityString(R.plurals.clean_item_download_desc, p.missingCount, p.missingCount),
                    null, getString(R.string.clean_item_download_button), R.drawable.ic_download_action_24, v -> {
                        myLogI("--- USER downloads again folder " + folderId + " (Clean quick actions) ---");
                        RedownloadHelper.start(requireContext(), folderId);
                        Toast.makeText(requireContext(), R.string.redownload_started, Toast.LENGTH_LONG).show();
                        dismiss();
                    });
        }
        if (p.sdCardPresent && p.folder != null) {
            addAction(actions, R.drawable.ic_memory_sdcard, getString(R.string.clean_item_move_title),
                    getString(R.string.clean_item_move_desc), null, getString(R.string.clean_item_move_button),
                    R.drawable.ic_memory_sdcard, v -> {
                        startActivity(new Intent(requireContext(), MoveBookActivity.class)
                                .putExtra(Intents.EXTRA_FOLDER, p.folder));
                        dismiss();
                    });
        }
        TextView note = root.findViewById(R.id.clean_item_note);
        note.setVisibility(actions.getChildCount() == 0 ? View.VISIBLE : View.GONE);
        note.setText(R.string.clean_item_nothing);
    }

    private void showUnknownFolder(@Nullable File[] files) {
        View root = requireView();
        ((TextView) root.findViewById(R.id.clean_item_label)).setText(R.string.clean_item_orphan_title);
        TextView note = root.findViewById(R.id.clean_item_note);
        note.setText(R.string.clean_item_orphan_desc);
        note.setVisibility(View.VISIBLE);

        List<File> list = files == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(files));
        list.sort((a, b) -> Long.compare(b.length(), a.length()));
        long total = 0;
        for (File f : list)
            total += f.length();
        ((TextView) root.findViewById(R.id.clean_item_summary)).setText(
                getResources().getQuantityString(R.plurals.clean_item_files, list.size(), list.size(),
                        Tonio.getReadableSize(total)));

        LinearLayout box = root.findViewById(R.id.clean_item_files);
        box.removeAllViews();
        for (int i = 0; i < Math.min(list.size(), MAX_FILES_LISTED); i++)
            box.addView(fileRow(list.get(i).getName(), Tonio.getReadableSize(list.get(i).length())));
        if (list.size() > MAX_FILES_LISTED) {
            int more = list.size() - MAX_FILES_LISTED;
            box.addView(fileRow(getResources().getQuantityString(R.plurals.clean_item_more_files, more, more), ""));
        }
        box.setVisibility(View.VISIBLE);
    }

    private View fileRow(String name, String size) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 6, 0, 6);
        TextView tvName = new TextView(requireContext());
        tvName.setText(name);
        tvName.setMaxLines(1);
        tvName.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        tvName.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        row.addView(tvName, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView tvSize = new TextView(requireContext());
        tvSize.setText(size);
        tvSize.setPadding(16, 0, 0, 0);
        tvSize.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        row.addView(tvSize);
        return row;
    }

    private String filesCount(int n, long bytes) {
        return getResources().getQuantityString(R.plurals.clean_item_files, n, n, Tonio.getReadableSize(bytes));
    }

    private void addAction(LinearLayout parent, @DrawableRes int icon, CharSequence title, CharSequence desc,
            @Nullable CharSequence count, CharSequence buttonText, @DrawableRes int buttonIcon,
            View.OnClickListener onClick) {
        View card = LayoutInflater.from(requireContext()).inflate(R.layout.item_cleanup_option, parent, false);
        ((ImageView) card.findViewById(R.id.cleanup_option_icon)).setImageResource(icon);
        ((TextView) card.findViewById(R.id.cleanup_option_title)).setText(title);
        ((TextView) card.findViewById(R.id.cleanup_option_desc)).setText(desc);
        TextView tvCount = card.findViewById(R.id.cleanup_option_count);
        tvCount.setText(count != null ? count : "");
        MaterialButton button = card.findViewById(R.id.cleanup_option_button);
        button.setText(buttonText);
        button.setIconResource(buttonIcon);
        button.setEnabled(true);
        button.setOnClickListener(onClick);
        parent.addView(card);
    }

    private void confirmAndDelete(List<Long> ids, long bytes) {
        if (busy || ids.isEmpty())
            return;
        List<Long> copy = new ArrayList<>(ids);
        new MaterialAlertDialogBuilder(requireContext())
                .setIcon(R.drawable.ic_delete_24)
                .setTitle(getResources().getQuantityString(R.plurals.clean_item_confirm_title, copy.size(), copy.size()))
                .setMessage(getString(R.string.clean_item_confirm_message, Tonio.getReadableSize(bytes)))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.Delete, (d, w) -> delete(copy))
                .show();
    }

    private void delete(List<Long> ids) {
        myLogI("--- USER deletes " + ids.size() + " track file(s) of folder " + folderId + " (book kept) ---");
        busy = true;
        Context app = requireContext().getApplicationContext();
        AppDatabase.databaseWriteExecutor.execute(() -> {
            BookSpaceHelper.Result r = BookSpaceHelper.deleteTrackFiles(app, ids);
            View root = getView();
            if (root == null)
                return;
            root.post(() -> {
                busy = false;
                sizeBytes = Math.max(0, sizeBytes - r.freedBytes);
                if (!isAdded())
                    return;
                Toast.makeText(app, getResources().getQuantityString(R.plurals.clean_item_done, r.deleted,
                        r.deleted, Tonio.getReadableSize(r.freedBytes)), Toast.LENGTH_LONG).show();
                getParentFragmentManager().setFragmentResult(PodcastHelper.EPISODE_CLEANUP_RESULT_KEY, new Bundle());
                load();
            });
        });
    }
}
