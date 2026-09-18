package com.driot.bookplayer.podcasts;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.Navigation;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.SettingsHostActivity;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.objects.LanguageItem;
import com.driot.bookplayer.helpers.LanguageHelper;
import com.driot.bookplayer.settings.ui.PodcastSettingsFragment;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingFragment;
import com.driot.bookplayer.views.EditText1lineWithSearch;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Entry screen to browse/search podcasts. Start destination of podcast_nav_graph.xml,
 * hosted by PodcastHostActivity. Converted from the former GetPodcastActivity - see
 * [[radio_deeplink_applinks_fix]] for why.
 */
@AndroidEntryPoint
public class GetPodcastFragment extends LoggingFragment {

    String query, lang;
    EditText1lineWithSearch editTextPodcast;
    Button bFavorite;
    Button bHistory;
    ImageButton ibSettings;
    Button buttonTrending;
    Spinner spinnerLang;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_get_podcast, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        editTextPodcast = view.findViewById(R.id.etPodcast);
        bFavorite = view.findViewById(R.id.bFavorite);
        bHistory = view.findViewById(R.id.bHistory);
        ibSettings = view.findViewById(R.id.ibSettings);
        buttonTrending = view.findViewById(R.id.bPodcastTrending);

        spinnerLang = view.findViewById(R.id.spinnerLang);
        spinnerLang.setAdapter(new ArrayAdapter<>(requireContext(),
                R.layout.spinner_language_item, R.id.textViewLanguage, new String[]{ getString(R.string.loading)}));
        spinnerLang.post(() ->
                LanguageHelper.setupLanguageSpinner(
                        requireContext(),
                        spinnerLang,
                        Pref.get_Audio_Language_Podcast(requireContext()),
                        LanguageHelper.getPodcastLanguages(),
                        selected -> Pref.set_Audio_Language_Podcast(requireContext(), selected.twoLetterCode),
                        false));

        bFavorite.setOnClickListener(v -> clickFavorite(view));
        bHistory.setOnClickListener(v -> clickHistory(view));
        ibSettings.setOnClickListener(v -> clickSettings());

        editTextPodcast.post(() -> { // async because takes ages
            editTextPodcast.setHistoryKey("podcast_search");
            editTextPodcast.setCompletionThreshold(1);
            editTextPodcast.setSuggestOnFocus(true);
        });

        editTextPodcast.getSearchButton().setOnClickListener(v -> {
            myLogI("--- User clicks SEARCH ---");
            doSearch(view);
            editTextPodcast.saveCurrentTextToHistory();
        });

        buttonTrending.setOnClickListener(v -> {
            myLogI("--- User clicks TRENDING ---");
            if (!NetworkHelper.isConnected(requireContext())) {
                myToastE(getString(R.string.no_internet_connection));
                return;
            }
            query = "";
            LanguageItem selectedLang = (LanguageItem) spinnerLang.getSelectedItem();
            lang = selectedLang.getTwoLetterCode().toLowerCase();

            Bundle args = new Bundle();
            args.putString("query", query);
            args.putString("lang", lang);
            Navigation.findNavController(view).navigate(R.id.podcastSearchResultsFragment, args);
            FirebaseAnalyticsHelper.tellAnalyticsPodcastTrending(query, lang);
        });
        // Keyboard "done/search"
        editTextPodcast.getEditText().setOnEditorActionListener((v, actionId, event) -> {
            boolean isEnterKey = event != null
                    && event.getAction() == android.view.KeyEvent.ACTION_DOWN
                    && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER;

            if (actionId == EditorInfo.IME_ACTION_SEARCH || isEnterKey) {
                doSearch(view);
                editTextPodcast.saveCurrentTextToHistory();
                editTextPodcast.getEditText().dismissDropDown();
                return true;
            }
            return false;
        });

    }

    private void clickFavorite(View view) {
        myLogI("--- User clicks FAVORITES ---");
        Navigation.findNavController(view).navigate(R.id.podcastFavoritesFragment);
    }

    private void clickHistory(View view) {
        myLogI("--- User clicks PODCAST HISTORY ---");
        Bundle args = new Bundle();
        args.putBoolean(Intents.EXTRA_START_IN_HISTORY, true);
        Navigation.findNavController(view).navigate(R.id.podcastFavoritesFragment, args);
    }

    private void clickSettings() {
        myLogI("--- User clicks SETTINGS ---");
        SettingsHostActivity.start(requireContext(), PodcastSettingsFragment.class, true, R.string.Podcast_Settings);
    }

    private void doSearch(View view) {
        if (!NetworkHelper.isConnected(requireContext())) {
            myToastE(getString(R.string.no_internet_connection));
            return;
        }
        query = Tonio.cleanSearchString(editTextPodcast.getText());
        lang = spinnerLang.getSelectedItem().toString().toLowerCase();

        if (lang.isEmpty()) {
            myToast(getString(R.string.selected_language_error));
            return;
        }

        Bundle args = new Bundle();
        args.putString("query", query);
        args.putString("lang", lang);
        Navigation.findNavController(view).navigate(R.id.podcastSearchResultsFragment, args);

        FirebaseAnalyticsHelper.tellAnalyticsPodcastSearch(query, lang);
    }

}
