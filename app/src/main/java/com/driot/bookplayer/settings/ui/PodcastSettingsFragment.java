package com.driot.bookplayer.settings.ui;

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

    private LinearLayout ll_podcast_auto_delete;
    private LinearLayout ll_podcast_episodes_sort_order;
    private LinearLayout ll_podcast_episodes_expand;
    private LinearLayout ll_podcast_autodownloaded_at_the_top;
    private LinearLayout ll_podcast_open_specific_view;

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
        View root = inflater.inflate(R.layout.activity_podcast_settings, container, false);

        // ====== CHECKBOXES ======
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

        // ====== EDIT TEXTS ======
        et_podcast_index_org_api_nb_results = root.findViewById(R.id.et_podcast_index_org_api_nb_results);
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
        // mirror the Librivox fragment style: persist on a background thread
        Executors.newSingleThreadExecutor().execute(() -> {
            if (getContext() == null) return;

            if (et_podcast_delay_deletion != null) {
                int v = Option.clampInt(
                        getContext(),
                        et_podcast_delay_deletion,
                        /* min */ 0,
                        /* max */ 365,
                        /* def */ Option.DEFAULT_PODCAST_DELAY_AUTO_DELETE,
                        getString(R.string.delay_for_auto_deletion)
                );
                Option.setPodcastAutoDeleteDelay(v);
            }

            if (et_podcast_completion_percentage_deletion != null) {
                int v = Option.clampInt(
                        getContext(),
                        et_podcast_completion_percentage_deletion,
                        /* min */ 10,
                        /* max */ 100,
                        /* def */ Option.DEFAULT_PODCAST_COMPLETION_PERCENTAGE_AUTO_DELETE,
                        getString(R.string.completion_percentage_for_auto_deletion)
                );
                Option.setPodcastAutoDeleteCompletionPercentage(v);
            }

            if (et_podcast_auto_download_last_n_episode != null) {
                int v = Option.clampInt(
                        getContext(),
                        et_podcast_auto_download_last_n_episode,
                        /* min */ 1,
                        /* max */ 100,
                        /* def */ Option.DEFAULT_PODCAST_AUTO_DOWNLOAD_LAST_N_EPISODES,
                        getString(R.string.auto_download_last_n_episode)
                );
                Option.setPodcastAutoDownloadLastNbEpisode(v);
            }

            if (et_auto_download_max_n_podcast != null) {
                int v = Option.clampInt(
                        getContext(),
                        et_auto_download_max_n_podcast,
                        /* min */ 1,
                        /* max */ 100,
                        /* def */ Option.DEFAULT_PODCAST_AUTO_DOWNLOAD_MAX_N_PODCASTS,
                        getString(R.string.auto_download_max_n_podcast)
                );
                Option.setPodcastAutoDownloadMaxNbPodcast(v);
            }

            if (et_auto_download_delay_between_checks_in_min != null) {
                int v = Option.clampInt(
                        getContext(),
                        et_auto_download_delay_between_checks_in_min,
                        /* min */ 15,
                        /* max */ 60 * 24,
                        /* def */ Option.DEFAULT_PODCAST_AUTO_DOWNLOAD_DELAY_BETWEEN_CHECKS_IN_MIN,
                        getString(R.string.auto_download_delay_between_checks_in_min)
                );
                Option.setPodcastAutoDownloadDelayBetweenChecks(v);
            }

            if (et_podcast_index_org_api_nb_results != null) {
                int v = Option.clampInt(
                        getContext(),
                        et_podcast_index_org_api_nb_results,
                        Var.PODCAST_INDEX_ORG_API_MIN_RESULTS_FOR_PODCASTS,
                        Var.PODCAST_INDEX_ORG_API_MAX_RESULTS_FOR_PODCASTS,
                        Option.DEFAULT_PODCAST_INDEX_ORG_API_NB_RESULTS,
                        getString(R.string.podcasts) // label token for your clamp/toast
                );
                Option.setPodcastIndexOrgApiNbResults(v);
            }
        });
    }
}
