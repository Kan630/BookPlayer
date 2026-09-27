package com.driot.bookplayer.podcasts;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.utils.Tonio;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import java.util.List;

/**
 * Quick bulk removal of a podcast's downloaded episodes, opened by a long press on the podcast in
 * the Clean screen: already listened, never played, or untouched for a chosen period. Each choice
 * shows what it would remove and asks before deleting; deletion goes through the same path as
 * AutoDelete (PodcastHelper.deleteDownloadedEpisodes), so listening progress and stats are kept.
 * Posts PodcastHelper.EPISODE_CLEANUP_RESULT_KEY to its parent fragment manager after a deletion.
 */
public class PodcastEpisodeCleanupSheet extends BottomSheetDialogFragment {

    public static final String TAG = "PodcastEpisodeCleanupSheet";

    private static final String ARG_FOLDER_ID = "folderId";
    private static final String ARG_NAME = "name";
    private static final String ARG_IMAGE = "image";
    private static final String ARG_RECORDINGS = "recordings";
    // Recordings have no auto-delete setting to borrow the "listened" threshold from.
    private static final int RECORDING_LISTENED_PERCENT = 95;
    private static final int[] UNTOUCHED_MONTHS = { 1, 3, 6, 12 };
    private static final int DEFAULT_UNTOUCHED_MONTHS = 3;

    private long folderId;
    private boolean recordings; // radio recordings folder: same choices, recordings wording
    private int untouchedMonths = DEFAULT_UNTOUCHED_MONTHS;
    private boolean deleting = false;
    private int loadGeneration = 0;

    private TextView summary;
    private OptionViews listened, neverPlayed, untouched;

    /** The views of one included item_cleanup_option card. */
    private static final class OptionViews {
        final TextView count;
        final MaterialButton button;
        final ViewGroup extra;
        List<Long> ids;
        long bytes;

        OptionViews(View card, @DrawableRes int icon, CharSequence title, CharSequence desc) {
            ((ImageView) card.findViewById(R.id.cleanup_option_icon)).setImageResource(icon);
            ((TextView) card.findViewById(R.id.cleanup_option_title)).setText(title);
            ((TextView) card.findViewById(R.id.cleanup_option_desc)).setText(desc);
            count = card.findViewById(R.id.cleanup_option_count);
            button = card.findViewById(R.id.cleanup_option_button);
            extra = card.findViewById(R.id.cleanup_option_extra);
        }
    }

    public static PodcastEpisodeCleanupSheet newInstance(long folderId, String name, @Nullable String image,
            boolean recordings) {
        Bundle args = new Bundle();
        args.putLong(ARG_FOLDER_ID, folderId);
        args.putBoolean(ARG_RECORDINGS, recordings);
        args.putString(ARG_NAME, name);
        args.putString(ARG_IMAGE, image);
        PodcastEpisodeCleanupSheet sheet = new PodcastEpisodeCleanupSheet();
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_podcast_episode_cleanup, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = requireArguments();
        folderId = args.getLong(ARG_FOLDER_ID);
        recordings = args.getBoolean(ARG_RECORDINGS);
        if (recordings)
            ((TextView) view.findViewById(R.id.cleanup_title)).setText(R.string.recording_cleanup_title);
        ((TextView) view.findViewById(R.id.cleanup_podcast_name)).setText(args.getString(ARG_NAME));
        String image = args.getString(ARG_IMAGE);
        if (image != null && !image.isEmpty())
            Glide.with(this).load(image).placeholder(R.drawable.ic_podcast_24)
                    .into((ImageView) view.findViewById(R.id.cleanup_cover));
        summary = view.findViewById(R.id.cleanup_summary);

        listened = new OptionViews(view.findViewById(R.id.cleanup_listened), R.drawable.ic_done_all_24,
                getString(R.string.podcast_cleanup_listened_title), "…");
        neverPlayed = new OptionViews(view.findViewById(R.id.cleanup_never_played), R.drawable.ic_hourglass_24,
                getString(R.string.podcast_cleanup_never_title), getString(recordings
                        ? R.string.recording_cleanup_never_desc : R.string.podcast_episode_status_never_played));
        untouched = new OptionViews(view.findViewById(R.id.cleanup_untouched), R.drawable.ic_schedule_24,
                getString(R.string.podcast_cleanup_untouched_title), getString(R.string.podcast_cleanup_untouched_desc));
        addPeriodChips(untouched.extra);

        listened.button.setOnClickListener(v -> confirmAndDelete(listened));
        neverPlayed.button.setOnClickListener(v -> confirmAndDelete(neverPlayed));
        untouched.button.setOnClickListener(v -> confirmAndDelete(untouched));

        loadPreview();
    }

    @Override
    public void onStart() {
        super.onStart();
        // Open fully: the three choices must be visible at once, not behind a half-height peek.
        if (getDialog() instanceof BottomSheetDialog) {
            BottomSheetBehavior<?> behavior = ((BottomSheetDialog) getDialog()).getBehavior();
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    private void addPeriodChips(ViewGroup container) {
        ChipGroup group = new ChipGroup(requireContext());
        group.setSingleSelection(true);
        group.setSelectionRequired(true);
        for (int months : UNTOUCHED_MONTHS) {
            Chip chip = new Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle);
            chip.setCheckable(true);
            chip.setId(View.generateViewId());
            chip.setText(months == 12 ? getString(R.string.podcast_cleanup_one_year)
                    : getResources().getQuantityString(R.plurals.podcast_cleanup_months, months, months));
            chip.setChecked(months == untouchedMonths);
            chip.setOnCheckedChangeListener((c, checked) -> {
                if (checked && untouchedMonths != months) {
                    untouchedMonths = months;
                    loadPreview();
                }
            });
            group.addView(chip);
        }
        container.addView(group);
        container.setVisibility(View.VISIBLE);
    }

    private void loadPreview() {
        int generation = ++loadGeneration; // a quick chip change must not be overwritten by an older result
        Context app = requireContext().getApplicationContext();
        int months = untouchedMonths;
        setButtonsEnabled(false);
        AppDatabase.databaseReadExecutor.execute(() -> {
            PodcastHelper.EpisodeCleanupPreview p = recordings
                    ? PodcastHelper.previewEpisodeCleanup(app, folderId, months, RECORDING_LISTENED_PERCENT)
                    : PodcastHelper.previewEpisodeCleanup(app, folderId, months);
            View root = getView();
            if (root == null)
                return;
            root.post(() -> {
                if (!isAdded() || generation != loadGeneration)
                    return;
                summary.setText(getResources().getQuantityString(recordings ? R.plurals.recording_cleanup_summary : R.plurals.podcast_cleanup_summary,
                        p.episodeCount, p.episodeCount, Tonio.getReadableSize(p.totalBytes)));
                ((TextView) requireView().findViewById(R.id.cleanup_listened)
                        .findViewById(R.id.cleanup_option_desc))
                        .setText(getString(recordings ? R.string.recording_cleanup_listened_desc
                                : R.string.podcast_cleanup_listened_desc, p.listenedPercent));
                show(listened, p.listened);
                show(neverPlayed, p.neverPlayed);
                show(untouched, p.untouched);
            });
        });
    }

    private void show(OptionViews option, PodcastHelper.EpisodeCleanupGroup group) {
        option.ids = group.zikFileIds;
        option.bytes = group.bytes;
        int n = group.zikFileIds.size();
        option.count.setText(n == 0 ? getString(R.string.podcast_cleanup_nothing)
                : getResources().getQuantityString(
                        recordings ? R.plurals.recording_cleanup_count : R.plurals.podcast_cleanup_count, n, n,
                        Tonio.getReadableSize(group.bytes)));
        option.button.setEnabled(n > 0 && !deleting);
    }

    private void setButtonsEnabled(boolean enabled) {
        for (OptionViews o : new OptionViews[] { listened, neverPlayed, untouched })
            o.button.setEnabled(enabled && o.ids != null && !o.ids.isEmpty());
    }

    private void confirmAndDelete(OptionViews option) {
        if (option.ids == null || option.ids.isEmpty() || deleting)
            return;
        List<Long> ids = new java.util.ArrayList<>(option.ids);
        long bytes = option.bytes;
        new MaterialAlertDialogBuilder(requireContext())
                .setIcon(R.drawable.ic_delete_24)
                .setTitle(getResources().getQuantityString(recordings ? R.plurals.recording_cleanup_confirm_title
                        : R.plurals.podcast_cleanup_confirm_title, ids.size(), ids.size()))
                .setMessage(getString(recordings ? R.string.recording_cleanup_confirm_message
                        : R.string.podcast_cleanup_confirm_message, Tonio.getReadableSize(bytes)))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.Delete, (d, w) -> delete(ids))
                .show();
    }

    private void delete(List<Long> ids) {
        myLogI("--- USER deletes " + ids.size() + " podcast episode(s) from folder " + folderId + " ---");
        deleting = true;
        setButtonsEnabled(false);
        Context app = requireContext().getApplicationContext();
        AppDatabase.databaseWriteExecutor.execute(() -> {
            PodcastHelper.EpisodeCleanupResult r = PodcastHelper.deleteDownloadedEpisodes(app, folderId, ids);
            View root = getView();
            if (root == null)
                return;
            root.post(() -> {
                deleting = false;
                if (!isAdded())
                    return;
                Toast.makeText(app, getResources().getQuantityString(
                        recordings ? R.plurals.recording_cleanup_done : R.plurals.podcast_cleanup_done, r.removed,
                        r.removed, Tonio.getReadableSize(r.freedBytes)), Toast.LENGTH_LONG).show();
                getParentFragmentManager().setFragmentResult(PodcastHelper.EPISODE_CLEANUP_RESULT_KEY, new Bundle());
                loadPreview();
            });
        });
    }
}
