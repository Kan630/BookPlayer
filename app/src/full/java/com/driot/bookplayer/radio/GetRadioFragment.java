package com.driot.bookplayer.radio;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.Navigation;

import com.driot.bookplayer.R;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.activities.MainActivity;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.settings.ui.RadioSettingsFragment;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingFragment;
import com.driot.bookplayer.views.EditText1lineWithSearch;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Entry screen to browse/search internet radios (Radio Browser). Start destination of
 * radio_nav_graph.xml, hosted by RadioHostActivity. Converted from the former
 * GetRadioActivity - see [[radio_deeplink_applinks_fix]] for why.
 */
@AndroidEntryPoint
public class GetRadioFragment extends LoggingFragment {

    public static final String EXTRA_RADIO_STATION_SEARCH_MODE = "EXTRA_RADIO_STATION_SEARCH_MODE";

    EditText1lineWithSearch etRadio;
    Button bFavorite;
    Button bHistory;
    ImageButton ibSettings;
    Button bTopClick, bTopVote, bLastClick, bLastChange;

    String query, lang, country, tag;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_get_radio, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bTopClick = view.findViewById(R.id.bRadioTopClicked);
        bTopVote = view.findViewById(R.id.bRadioTopVoted);
        bLastClick = view.findViewById(R.id.bRadioLastClicked);
        bLastChange = view.findViewById(R.id.bRadioLastChanged);

        etRadio = view.findViewById(R.id.etRadio);
        bFavorite = view.findViewById(R.id.bFavorite);
        bHistory = view.findViewById(R.id.bHistory);
        ibSettings = view.findViewById(R.id.ibSettings);

        // ---- open recyclerviews ----
        bFavorite.setOnClickListener(v -> clickFavorite(view));
        bHistory.setOnClickListener(v -> clickHistory(view));
        ibSettings.setOnClickListener(v -> clickSettings());

        view.findViewById(R.id.bRadioByTag).setOnClickListener(v -> {
            myLogI("---- user clicks BY TAG ---");
            if (!NetworkHelper.getCheckInternetForAction(requireContext()))
                return;
            FirebaseAnalyticsHelper.tellAnalyticsRadioBy("tag");
            navigateToCardList(view, GetRadioCardListFragment.MODE_TAG);
        });
        view.findViewById(R.id.bRadioByCountry).setOnClickListener(v -> {
            myLogI("---- user clicks BY COUNTRY ---");
            if (!NetworkHelper.getCheckInternetForAction(requireContext()))
                return;
            FirebaseAnalyticsHelper.tellAnalyticsRadioBy("country");
            navigateToCardList(view, GetRadioCardListFragment.MODE_COUNTRY);
        });
        view.findViewById(R.id.bRadioByLang).setOnClickListener(v -> {
            myLogI("---- user clicks BY LANGUAGE ---");
            if (!NetworkHelper.getCheckInternetForAction(requireContext()))
                return;
            FirebaseAnalyticsHelper.tellAnalyticsRadioBy("lang");
            navigateToCardList(view, GetRadioCardListFragment.MODE_LANGUAGE);
        });

        etRadio.setHistoryKey("radio_search"); // keep histories separate
        etRadio.setCompletionThreshold(1);
        etRadio.setSuggestOnFocus(true);

        bTopClick.setOnClickListener(v -> {
            myLogI("---- user clicks TOP CLICK ---");
            if (!NetworkHelper.getCheckInternetForAction(requireContext()))
                return;
            Bundle args = new Bundle();
            args.putString(EXTRA_RADIO_STATION_SEARCH_MODE, "MODE_TOP_CLICK");
            Navigation.findNavController(view).navigate(R.id.radioResultsFragment, args);
            FirebaseAnalyticsHelper.tellAnalyticsRadioBy("topClick");
        });

        bTopVote.setOnClickListener(v -> {
            myLogI("---- user clicks TOP VOTE ---");
            if (!NetworkHelper.getCheckInternetForAction(requireContext()))
                return;
            Bundle args = new Bundle();
            args.putString(EXTRA_RADIO_STATION_SEARCH_MODE, "MODE_TOP_VOTE");
            Navigation.findNavController(view).navigate(R.id.radioResultsFragment, args);
            FirebaseAnalyticsHelper.tellAnalyticsRadioBy("topVote");
        });

        bLastClick.setOnClickListener(v -> {
            myLogI("---- user clicks LAST CLICK ---");
            if (!NetworkHelper.getCheckInternetForAction(requireContext()))
                return;
            Bundle args = new Bundle();
            args.putString(EXTRA_RADIO_STATION_SEARCH_MODE, "MODE_LAST_CLICK");
            Navigation.findNavController(view).navigate(R.id.radioResultsFragment, args);
            FirebaseAnalyticsHelper.tellAnalyticsRadioBy("lastClick");
        });

        bLastChange.setOnClickListener(v -> {
            myLogI("---- user clicks LAST CHANGE ---");
            if (!NetworkHelper.getCheckInternetForAction(requireContext()))
                return;
            Bundle args = new Bundle();
            args.putString(EXTRA_RADIO_STATION_SEARCH_MODE, "MODE_LAST_CHANGE");
            Navigation.findNavController(view).navigate(R.id.radioResultsFragment, args);
            FirebaseAnalyticsHelper.tellAnalyticsRadioBy("lastChange");
        });

        etRadio.getSearchButton().setOnClickListener(v -> {
            myLogI("--- User clicks RADIO SEARCH ---");
            etRadio.saveCurrentTextToHistory();
            doSearch(view);
        });

        etRadio.getEditText().setOnEditorActionListener((v, actionId, event) -> {
            boolean isEnterKey = event != null
                    && event.getAction() == android.view.KeyEvent.ACTION_DOWN
                    && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER;

            if (actionId == EditorInfo.IME_ACTION_SEARCH || isEnterKey) {
                myLogI("--- User clicks RADIO SEARCH --- (via keyboard)");
                etRadio.saveCurrentTextToHistory();
                doSearch(view);
                etRadio.getEditText().dismissDropDown();
                return true;
            }
            return false;
        });
    }

    private void navigateToCardList(View view, @GetRadioCardListFragment.FacetMode int mode) {
        Bundle args = new Bundle();
        args.putInt(GetRadioCardListFragment.EXTRA_FACET_MODE, mode);
        Navigation.findNavController(view).navigate(R.id.getRadioCardListFragment, args);
    }

    private void clickFavorite(View view) {
        myLogI("--- User clicks RADIO FAVORITES ---");
        Bundle args = new Bundle();
        args.putBoolean(Intents.EXTRA_START_IN_FAVORITES, true);
        Navigation.findNavController(view).navigate(R.id.radioFavoritesFragment, args);
    }

    private void clickHistory(View view) {
        myLogI("--- User clicks RADIO HISTORY ---");
        Bundle args = new Bundle();
        args.putBoolean(Intents.EXTRA_START_IN_HISTORY, true);
        Navigation.findNavController(view).navigate(R.id.radioFavoritesFragment, args);
    }

    private void clickSettings() {
        myLogI("--- User clicks RADIO SETTINGS ---");
        MainActivity.startSettings(requireContext(), RadioSettingsFragment.class, true, R.string.Radio_Settings);
    }

    private void doSearch(View view) {
        if (!NetworkHelper.getCheckInternetForAction(requireContext())) {
            return;
        }
        query = Tonio.cleanSearchString(etRadio.getText());
        if (query.isEmpty()) {
            myToast(getString(R.string.please_type_a_search_string));
            return;
        }

        lang = null;
        country = null;
        tag = null;

        Bundle args = new Bundle();
        args.putString(EXTRA_RADIO_STATION_SEARCH_MODE, "MODE_SEARCH");
        args.putString("query", query);
        args.putString("lang", lang);
        args.putString("country", country);
        args.putString("tag", tag);
        Navigation.findNavController(view).navigate(R.id.radioResultsFragment, args);
        FirebaseAnalyticsHelper.tellAnalyticsRadioSearch(query, lang, country, tag);
    }

}
