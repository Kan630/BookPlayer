package com.driot.bookplayer.settings.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.utils.log.LoggingFragment;

import java.util.concurrent.Executors;

public class PodcastSettingsFragment extends LoggingFragment {

    // CHECKBOXES + ROWS
    private CheckBox chk_podcast_auto_delete;
    private CheckBox chk_podcast_episodes_sort_order;
    private CheckBox chk_podcast_episodes_expand;
    private CheckBox chk_podcast_autodownloaded_at_the_top;
    private CheckBox chk_podcast_open_specific_view;
    private CheckBox chk_podcast_open_favorites_first;
    private CheckBox chk_podcast_add_date_to_episode_name;

    private LinearLayout ll_podcast_auto_delete;
    private LinearLayout ll_podcast_episodes_sort_order;
    private LinearLayout ll_podcast_episodes_expand;
    private LinearLayout ll_podcast_autodownloaded_at_the_top;
    private LinearLayout ll_podcast_open_specific_view;
    private LinearLayout ll_podcast_open_favorites_first;
    private LinearLayout ll_podcast_add_date_to_episode_name;


    // EDIT TEXTS
    private EditText et_podcast_delay_deletion;
    private EditText et_podcast_completion_percentage_deletion;
    private EditText et_podcast_auto_download_last_n_episode;
    private EditText et_auto_download_max_n_podcast;
    private EditText et_auto_download_delay_between_checks_in_min;
    private EditText et_podcast_index_org_api_nb_results;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        // Reuse your existing layout as-is
        View root = inflater.inflate(R.layout.fragment_settings_podcast, container, false);

        // ====== CHECKBOXES ======
        chk_podcast_open_favorites_first = root.findViewById(R.id.chk_podcast_open_favorites_first);
        ll_podcast_open_favorites_first  = root.findViewById(R.id.ll_podcast_open_favorites_first);
        chk_podcast_open_favorites_first.setChecked(Option.getPodcastOpenFavoritesFirst());
        ll_podcast_open_favorites_first.setOnClickListener(v -> chk_podcast_open_favorites_first.toggle());
        chk_podcast_open_favorites_first.setOnCheckedChangeListener((buttonView, isChecked) ->
                Option.setPodcastOpenFavoritesFirst(isChecked));

        chk_podcast_open_specific_view = root.findViewById(R.id.chk_podcast_open_specific_view);
        ll_podcast_open_specific_view  = root.findViewById(R.id.ll_podcast_open_specific_view);
        chk_podcast_open_specific_view.setChecked(Option.getPodcastOpenSpecificView());
        ll_podcast_open_specific_view.setOnClickListener(v -> chk_podcast_open_specific_view.toggle());
        chk_podcast_open_specific_view.setOnCheckedChangeListener((buttonView, isChecked) ->
                Option.setPodcastOpenSpecificView(isChecked));

        chk_podcast_episodes_sort_order = root.findViewById(R.id.chk_podcast_episodes_sort_order);
        ll_podcast_episodes_sort_order  = root.findViewById(R.id.ll_podcast_episodes_sort_order);
        chk_podcast_episodes_sort_order.setChecked(Option.getPodcastEpisodesSortOrder());
        ll_podcast_episodes_sort_order.setOnClickListener(v -> chk_podcast_episodes_sort_order.toggle());
        chk_podcast_episodes_sort_order.setOnCheckedChangeListener((buttonView, isChecked) ->
                Option.setPodcastEpisodesSortOrder(isChecked));

        chk_podcast_episodes_expand = root.findViewById(R.id.chk_podcast_episodes_expand);
        ll_podcast_episodes_expand  = root.findViewById(R.id.ll_podcast_episodes_expand);
        chk_podcast_episodes_expand.setChecked(Option.getPodcastEpisodesDescriptionExpand());
        ll_podcast_episodes_expand.setOnClickListener(v -> chk_podcast_episodes_expand.toggle());
        chk_podcast_episodes_expand.setOnCheckedChangeListener((buttonView, isChecked) ->
                Option.setPodcastEpisodesDescriptionExpand(isChecked));

        chk_podcast_autodownloaded_at_the_top = root.findViewById(R.id.chk_podcast_autodownloaded_at_the_top);
        ll_podcast_autodownloaded_at_the_top  = root.findViewById(R.id.ll_podcast_autodownloaded_at_the_top);
        chk_podcast_autodownloaded_at_the_top.setChecked(Option.getPodcastAutoDownloadedAtTheTop());
        ll_podcast_autodownloaded_at_the_top.setOnClickListener(v -> chk_podcast_autodownloaded_at_the_top.toggle());
        chk_podcast_autodownloaded_at_the_top.setOnCheckedChangeListener((buttonView, isChecked) ->
                Option.setPodcastAutoDownloadedAtTheTop(isChecked));

        chk_podcast_auto_delete = root.findViewById(R.id.chk_podcast_auto_delete);
        ll_podcast_auto_delete  = root.findViewById(R.id.ll_podcast_auto_delete);
        chk_podcast_auto_delete.setChecked(Option.getPodcastAutoDelete());
        ll_podcast_auto_delete.setOnClickListener(v -> chk_podcast_auto_delete.toggle());
        chk_podcast_auto_delete.setOnCheckedChangeListener((buttonView, isChecked) ->
                Option.setPodcastAutoDelete(isChecked));

        chk_podcast_add_date_to_episode_name = root.findViewById(R.id.chk_podcast_add_date_to_episode_name);
        ll_podcast_add_date_to_episode_name  = root.findViewById(R.id.ll_podcast_add_date_to_episode_name);
        chk_podcast_add_date_to_episode_name.setChecked(Option.getPodcastAddDateToEpisodeName());
        ll_podcast_add_date_to_episode_name.setOnClickListener(v -> chk_podcast_add_date_to_episode_name.toggle());
        chk_podcast_add_date_to_episode_name.setOnCheckedChangeListener((buttonView, isChecked) ->
                Option.setPodcastAddDateToEpisodeName(isChecked));

        // ====== EDIT TEXTS ======
        et_podcast_index_org_api_nb_results = root.findViewById(R.id.et_librivox_api_nb_results);
        et_podcast_index_org_api_nb_results.setText(String.valueOf(Option.getPodcastIndexOrgApiNbResults()));

        et_podcast_auto_download_last_n_episode = root.findViewById(R.id.et_auto_download_last_n_episode);
        et_podcast_auto_download_last_n_episode.setText(String.valueOf(Option.getPodcastAutoDownloadLastNbEpisode()));

        et_auto_download_max_n_podcast = root.findViewById(R.id.et_auto_download_max_n_podcast);
        et_auto_download_max_n_podcast.setText(String.valueOf(Option.getPodcastAutoDownloadMaxNbPodcast()));

        et_auto_download_delay_between_checks_in_min = root.findViewById(R.id.et_auto_download_delay_between_checks_in_min);
        et_auto_download_delay_between_checks_in_min.setText(String.valueOf(Option.getPodcastAutoDownloadDelayBetweenChecks()));

        et_podcast_delay_deletion = root.findViewById(R.id.et_delay_deletion);
        et_podcast_delay_deletion.setText(String.valueOf(Option.getPodcastAutoDeleteDelay()));

        et_podcast_completion_percentage_deletion = root.findViewById(R.id.et_percentage_deletion);
        et_podcast_completion_percentage_deletion.setText(String.valueOf(Option.getPodcastAutoDeleteCompletionPercentage()));

        // Optional: if you support inline/host title toggling like Librivox,
        // you can read ARG_SHOW_LOCAL_TITLE and toggle a title container here
        // (only if your layout contains a top title block with an id, e.g., R.id.ll_title).
        View titleContainer = root.findViewById(R.id.ll_title);
        if (titleContainer != null) {
            boolean showLocalTitle = true;
            Bundle args = getArguments();
            if (args != null) showLocalTitle = args.getBoolean("ARG_SHOW_LOCAL_TITLE", true);
            titleContainer.setVisibility(showLocalTitle ? View.VISIBLE : View.GONE);
        }

        return root;
    }

    @Override
    public void onPause() {
        super.onPause();
        saveEditTextValues();
    }

    private void saveEditTextValues() {
        // Make sure fragment is attached before touching context / views
        if (!isAdded()) {
            return;
        }

        final Context ctx = requireContext();

        // --- Read & validate on UI thread ---

        final Integer delayDeletion;
        if (et_podcast_delay_deletion != null) {
            delayDeletion = Option.clampInt(
                    ctx,
                    et_podcast_delay_deletion,
                    /* min */ 0,
                    /* max */ 365,
                    /* def */ Option.DEFAULT_PODCAST_DELAY_AUTO_DELETE,
                    ctx.getString(R.string.delay_for_auto_deletion)
            );
        } else {
            delayDeletion = null;
        }

        final Integer completionPercentageDeletion;
        if (et_podcast_completion_percentage_deletion != null) {
            completionPercentageDeletion = Option.clampInt(
                    ctx,
                    et_podcast_completion_percentage_deletion,
                    /* min */ 10,
                    /* max */ 100,
                    /* def */ Option.DEFAULT_PODCAST_COMPLETION_PERCENTAGE_AUTO_DELETE,
                    ctx.getString(R.string.completion_percentage_for_auto_deletion)
            );
        } else {
            completionPercentageDeletion = null;
        }

        final Integer autoDownloadLastNEpisode;
        if (et_podcast_auto_download_last_n_episode != null) {
            autoDownloadLastNEpisode = Option.clampInt(
                    ctx,
                    et_podcast_auto_download_last_n_episode,
                    /* min */ 1,
                    /* max */ 100,
                    /* def */ Option.DEFAULT_PODCAST_AUTO_DOWNLOAD_LAST_N_EPISODES,
                    ctx.getString(R.string.auto_download_last_n_episode)
            );
        } else {
            autoDownloadLastNEpisode = null;
        }

        final Integer autoDownloadMaxNPodcast;
        if (et_auto_download_max_n_podcast != null) {
            autoDownloadMaxNPodcast = Option.clampInt(
                    ctx,
                    et_auto_download_max_n_podcast,
                    /* min */ 1,
                    /* max */ 100,
                    /* def */ Option.DEFAULT_PODCAST_AUTO_DOWNLOAD_MAX_N_PODCASTS,
                    ctx.getString(R.string.auto_download_max_n_podcast)
            );
        } else {
            autoDownloadMaxNPodcast = null;
        }

        final Integer autoDownloadDelayBetweenChecks;
        if (et_auto_download_delay_between_checks_in_min != null) {
            autoDownloadDelayBetweenChecks = Option.clampInt(
                    ctx,
                    et_auto_download_delay_between_checks_in_min,
                    /* min */ 15,
                    /* max */ 60 * 24,
                    /* def */ Option.DEFAULT_PODCAST_AUTO_DOWNLOAD_DELAY_BETWEEN_CHECKS_IN_MIN,
                    ctx.getString(R.string.auto_download_delay_between_checks_in_min)
            );
        } else {
            autoDownloadDelayBetweenChecks = null;
        }

        final Integer podcastIndexOrgApiNbResults;
        if (et_podcast_index_org_api_nb_results != null) {
            podcastIndexOrgApiNbResults = Option.clampInt(
                    ctx,
                    et_podcast_index_org_api_nb_results,
                    Var.PODCAST_INDEX_ORG_API_MIN_RESULTS_FOR_PODCASTS,
                    Var.PODCAST_INDEX_ORG_API_MAX_RESULTS_FOR_PODCASTS,
                    Option.DEFAULT_PODCAST_INDEX_ORG_API_NB_RESULTS,
                    ctx.getString(R.string.podcasts) // label token for your clamp/toast
            );
        } else {
            podcastIndexOrgApiNbResults = null;
        }

        // --- Persist off the UI thread ---
        Executors.newSingleThreadExecutor().execute(() -> {
            if (delayDeletion != null) {
                Option.setPodcastAutoDeleteDelay(delayDeletion);
            }
            if (completionPercentageDeletion != null) {
                Option.setPodcastAutoDeleteCompletionPercentage(completionPercentageDeletion);
            }
            if (autoDownloadLastNEpisode != null) {
                Option.setPodcastAutoDownloadLastNbEpisode(autoDownloadLastNEpisode);
            }
            if (autoDownloadMaxNPodcast != null) {
                Option.setPodcastAutoDownloadMaxNbPodcast(autoDownloadMaxNPodcast);
            }
            if (autoDownloadDelayBetweenChecks != null) {
                Option.setPodcastAutoDownloadDelayBetweenChecks(autoDownloadDelayBetweenChecks);
            }
            if (podcastIndexOrgApiNbResults != null) {
                Option.setPodcastIndexOrgApiNbResults(podcastIndexOrgApiNbResults);
            }
        });
    }
}
