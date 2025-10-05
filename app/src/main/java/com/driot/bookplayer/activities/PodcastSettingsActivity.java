package com.driot.bookplayer.activities;

import android.os.Bundle;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.utils.log.LoggingActivity;

public class PodcastSettingsActivity extends LoggingActivity {

    CheckBox chk_podcast_auto_delete;
    CheckBox chk_podcast_episodes_sort_order;
    CheckBox chk_podcast_episodes_expand;
    CheckBox chk_podcast_autodownloaded_at_the_top;
    CheckBox chk_podcast_open_specific_view;

    EditText et_podcast_delay_deletion, et_podcast_completion_percentage_deletion;
    EditText et_podcast_auto_download_last_n_episode, et_auto_download_max_n_podcast, et_auto_download_delay_between_checks_in_min;
    EditText et_podcast_index_org_api_nb_results;


    LinearLayout ll_podcast_auto_delete, ll_podcast_episodes_sort_order, ll_podcast_episodes_expand, ll_podcast_autodownloaded_at_the_top, ll_podcast_open_specific_view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_podcast_settings);
        InsetHelper.apply(this);


///  PODCASTS
        chk_podcast_open_specific_view = findViewById(R.id.chk_podcast_open_specific_view);
        ll_podcast_open_specific_view = findViewById(R.id.ll_podcast_open_specific_view);
        chk_podcast_open_specific_view.setChecked(Option.getPodcastOpenSpecificView());
        ll_podcast_open_specific_view.setOnClickListener(v -> chk_podcast_open_specific_view.toggle());
        chk_podcast_open_specific_view.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setPodcastOpenSpecificView(isChecked));

        chk_podcast_episodes_sort_order = findViewById(R.id.chk_podcast_episodes_sort_order);
        ll_podcast_episodes_sort_order = findViewById(R.id.ll_podcast_episodes_sort_order);
        chk_podcast_episodes_sort_order.setChecked(Option.getPodcastEpisodesSortOrder());
        ll_podcast_episodes_sort_order.setOnClickListener(v -> chk_podcast_episodes_sort_order.toggle());
        chk_podcast_episodes_sort_order.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setPodcastEpisodesSortOrder(isChecked));

        chk_podcast_episodes_expand = findViewById(R.id.chk_podcast_episodes_expand);
        ll_podcast_episodes_expand = findViewById(R.id.ll_podcast_episodes_expand);
        chk_podcast_episodes_expand.setChecked(Option.getPodcastEpisodesDescriptionExpand());
        ll_podcast_episodes_expand.setOnClickListener(v -> chk_podcast_episodes_expand.toggle());
        chk_podcast_episodes_expand.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setPodcastEpisodesDescriptionExpand(isChecked));

        et_podcast_index_org_api_nb_results = findViewById(R.id.et_podcast_index_org_api_nb_results);
        et_podcast_index_org_api_nb_results.setText(String.valueOf(Option.getPodcastIndexOrgApiNbResults()));

        et_podcast_auto_download_last_n_episode = findViewById(R.id.et_auto_download_last_n_episode);
        et_podcast_auto_download_last_n_episode.setText(String.valueOf(Option.getPodcastAutoDownloadLastNbEpisode()));

        et_auto_download_max_n_podcast = findViewById(R.id.et_auto_download_max_n_podcast);
        et_auto_download_max_n_podcast.setText(String.valueOf(Option.getPodcastAutoDownloadMaxNbPodcast()));

        et_auto_download_delay_between_checks_in_min = findViewById(R.id.et_auto_download_delay_between_checks_in_min);
        et_auto_download_delay_between_checks_in_min.setText(String.valueOf(Option.getPodcastAutoDownloadDelayBetweenChecks()));

        chk_podcast_autodownloaded_at_the_top = findViewById(R.id.chk_podcast_autodownloaded_at_the_top);
        ll_podcast_autodownloaded_at_the_top = findViewById(R.id.ll_podcast_autodownloaded_at_the_top);
        chk_podcast_autodownloaded_at_the_top.setChecked(Option.getPodcastAutoDownloadedAtTheTop());
        ll_podcast_autodownloaded_at_the_top.setOnClickListener(v -> chk_podcast_autodownloaded_at_the_top.toggle());
        chk_podcast_autodownloaded_at_the_top.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setPodcastAutoDownloadedAtTheTop(isChecked));


        chk_podcast_auto_delete = findViewById(R.id.chk_podcast_auto_delete);
        ll_podcast_auto_delete = findViewById(R.id.ll_podcast_auto_delete);
        chk_podcast_auto_delete.setChecked(Option.getPodcastAutoDelete());
        ll_podcast_auto_delete.setOnClickListener(v -> chk_podcast_auto_delete.toggle());
        chk_podcast_auto_delete.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setPodcastAutoDelete(isChecked));

        et_podcast_delay_deletion = findViewById(R.id.et_delay_deletion);
        et_podcast_delay_deletion.setText(String.valueOf(Option.getPodcastAutoDeleteDelay()));

        et_podcast_completion_percentage_deletion = findViewById(R.id.et_percentage_deletion);
        et_podcast_completion_percentage_deletion.setText(String.valueOf(Option.getPodcastAutoDeleteCompletionPercentage()));


        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN); // Avoid keyboard on opening
    }

    private void saveEditTextValues() {
        if (et_podcast_delay_deletion != null ) {
            int value1 = clampInt(et_podcast_delay_deletion, 0, 365, Option.DEFAULT_PODCAST_DELAY_AUTO_DELETE,
                    () -> myLongToast(getString(R.string.delay_for_auto_deletion) + " " + getString(R.string.too_low)),
                    () -> myLongToast(getString(R.string.delay_for_auto_deletion) + " " + getString(R.string.too_high)));
            Option.setPodcastAutoDeleteDelay(value1);
        }
        if (et_podcast_completion_percentage_deletion != null ) {
            int value2 = clampInt(et_podcast_completion_percentage_deletion, 10, 100, Option.DEFAULT_PODCAST_COMPLETION_PERCENTAGE_AUTO_DELETE,
                    () -> myLongToast(getString(R.string.completion_percentage_for_auto_deletion) + " " + getString(R.string.too_low)),
                    () -> myLongToast(getString(R.string.completion_percentage_for_auto_deletion) + " " + getString(R.string.too_high)));
            Option.setPodcastAutoDeleteCompletionPercentage(value2);
        }
        if (et_podcast_auto_download_last_n_episode != null ) {
            int value3 = clampInt(et_podcast_auto_download_last_n_episode, 1, 100, Option.DEFAULT_PODCAST_AUTO_DOWNLOAD_LAST_N_EPISODES,
                    () -> myLongToast(getString(R.string.auto_download_last_n_episode) + " " + getString(R.string.too_low)),
                    () -> myLongToast(getString(R.string.auto_download_last_n_episode) + " " + getString(R.string.too_high)));
            Option.setPodcastAutoDownloadLastNbEpisode(value3);
        }
        if (et_auto_download_max_n_podcast != null ) {
            int value4 = clampInt(et_auto_download_max_n_podcast, 1, 100, Option.DEFAULT_PODCAST_AUTO_DOWNLOAD_MAX_N_PODCASTS,
                    () -> myLongToast(getString(R.string.auto_download_max_n_podcast) + " " + getString(R.string.too_low)),
                    () -> myLongToast(getString(R.string.auto_download_max_n_podcast) + " " + getString(R.string.too_high)));
            Option.setPodcastAutoDownloadMaxNbPodcast(value4);
        }
        if (et_auto_download_delay_between_checks_in_min != null ) {
            int value5 = clampInt(et_auto_download_delay_between_checks_in_min,15, 60*24, Option.DEFAULT_PODCAST_AUTO_DOWNLOAD_DELAY_BETWEEN_CHECKS_IN_MIN,
                    () -> myLongToast(getString(R.string.auto_download_delay_between_checks_in_min) + " " + getString(R.string.too_low)),
                    () -> myLongToast(getString(R.string.auto_download_delay_between_checks_in_min) + " " + getString(R.string.too_high)));
            Option.setPodcastAutoDownloadDelayBetweenChecks(value5);
        }
        if (et_podcast_index_org_api_nb_results != null ) {
            int value6 = clampInt(et_podcast_index_org_api_nb_results, Var.PODCAST_INDEX_ORG_API_MIN_RESULTS_FOR_PODCASTS, Var.PODCAST_INDEX_ORG_API_MAX_RESULTS_FOR_PODCASTS, Option.DEFAULT_PODCAST_INDEX_ORG_API_NB_RESULTS,
                    () -> myLongToast(getString(R.string.minimum_number_of_results_for_) + getString(R.string.podcasts) + " " + getString(R.string.too_low)),
                    () -> myLongToast(getString(R.string.maximum_number_of_results_for_) + getString(R.string.podcasts) + " " + getString(R.string.too_high)));
            Option.setPodcastIndexOrgApiNbResults(value6);
        }

    }


    @Override
    protected void onDestroy() {
        saveEditTextValues();
        super.onDestroy();
    }

    public static int clampInt(EditText et, int min, int max, int def, Runnable onTooLow, Runnable onTooHigh) {
        if (et == null) return def;
        String str = et.getText().toString().trim();
        int val;
        try {
            val = Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return def;
        }

        if (val < min) {
            if (onTooLow != null) onTooLow.run();
            return min;
        } else if (val > max) {
            if (onTooHigh != null) onTooHigh.run();
            return max;
        }
        return val;
    }

}
