package com.driot.bookplayer.activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.librivox.LibrivoxLanguageItem;
import com.driot.bookplayer.librivox.LibrivoxLanguageStore;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.settings.ui.RepositoriesSettingsFragment;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingFragment;
import com.driot.bookplayer.views.EditText1lineWithSearch;

import java.util.List;
import java.util.stream.Collectors;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class GetLibrivoxFragment extends LoggingFragment {

    Spinner spinnerLibrivox;
    EditText1lineWithSearch etLibrivoxSearch;
    Button bFavorite;
    Button bLibrivoxTrending, bLibrivoxLastAdded;
    Button bLibrivoxByGenre;
    View cardFavorites;
    LibrivoxResultsViewModel favoritesViewModel;

    String query;
    LibrivoxLanguageItem selectedLanguageItem;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_get_librivox, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bLibrivoxTrending = view.findViewById(R.id.bLibrivoxTrending);
        bLibrivoxLastAdded = view.findViewById(R.id.bLibrivoxLastAdded);
        bLibrivoxByGenre = view.findViewById(R.id.bLibrivoxByGenre);
        spinnerLibrivox = view.findViewById(R.id.spinnerLibrivox);
        etLibrivoxSearch = view.findViewById(R.id.etLibrivoxSearch);
        bFavorite = view.findViewById(R.id.bFavorite);
        cardFavorites = view.findViewById(R.id.cardFavorites);

        bFavorite.setOnClickListener(v -> clickFavorite(view));
        view.findViewById(R.id.ibSettings).setOnClickListener(v -> clickSettings());

        favoritesViewModel = new ViewModelProvider(this).get(LibrivoxResultsViewModel.class);
        favoritesViewModel.getFavoriteBookSourcesLive().observe(getViewLifecycleOwner(), favorites ->
                cardFavorites.setVisibility(favorites == null || favorites.isEmpty() ? View.GONE : View.VISIBLE));

        etLibrivoxSearch.setHistoryKey("librivox_search"); // keep histories separate
        etLibrivoxSearch.setCompletionThreshold(1); // suggestions after 1 char
        etLibrivoxSearch.setSuggestOnFocus(true); // show dropdown on focus if empty

        refreshLanguageSpinner();

        bLibrivoxTrending.setOnClickListener(v -> {
            myLogI("--- User clicks MOST DOWNLOADED ---");
            if (!NetworkHelper.isConnected(requireContext())) {
                myToastE(getString(R.string.no_internet_connection));
                return;
            }
            query = "";
            if (!checkLangFromSpinner())
                return;
            openLibrivoxResultsTrending(view);
            FirebaseAnalyticsHelper.tellAnalyticsLibrivoxQuickList(query, selectedLanguageItem.name, "most_download");
        });

        bLibrivoxLastAdded.setOnClickListener(v -> {
            myLogI("--- User clicks LAST ADDED ---");
            if (!NetworkHelper.isConnected(requireContext())) {
                myToastE(getString(R.string.no_internet_connection));
                return;
            }
            query = "";
            if (!checkLangFromSpinner())
                return;
            openLibrivoxResultsLastAdded(view);
            FirebaseAnalyticsHelper.tellAnalyticsLibrivoxQuickList(query, selectedLanguageItem.name, "last_added");
        });

        bLibrivoxByGenre.setOnClickListener(v -> {
            myLogI("--- User clicks BY GENRE ---");
            if (!NetworkHelper.isConnected(requireContext())) {
                myToastE(getString(R.string.no_internet_connection));
                return;
            }
            if (!checkLangFromSpinner())
                return;
            Bundle facetArgs = new Bundle();
            facetArgs.putInt(com.driot.bookplayer.librivox.GetLibrivoxFacetListFragment.EXTRA_FACET_MODE,
                    com.driot.bookplayer.librivox.GetLibrivoxFacetListFragment.MODE_GENRE);
            facetArgs.putSerializable(Intents.EXTRA_LIBRIVOX_LANGUAGE_ITEM, selectedLanguageItem);
            Navigation.findNavController(view).navigate(R.id.getLibrivoxFacetListFragment, facetArgs);
            FirebaseAnalyticsHelper.tellAnalyticsLibrivoxQuickList("", selectedLanguageItem.name, "by_genre");
        });

        etLibrivoxSearch.getSearchButton().setOnClickListener(v -> {
            myLogI("--- User clicks SEARCH ---");
            if (!checkLangFromSpinner())
                return;
            doSearch(view);
        });

        etLibrivoxSearch.getEditText().setOnEditorActionListener((v, actionId, event) -> {
            boolean isEnterKey = event != null
                    && event.getAction() == android.view.KeyEvent.ACTION_DOWN
                    && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER;

            if (actionId == EditorInfo.IME_ACTION_SEARCH || isEnterKey) {
                myLogI("--- User clicks SEARCH --- (via keyboard)");
                if (!checkLangFromSpinner())
                    return true;
                doSearch(view);

                etLibrivoxSearch.getEditText().dismissDropDown();

                return true;
            }
            return false;
        });

    }

    ////////////////////////////////
    ////////////////////////////////
    private void clickFavorite(View view) {
        myLogI("--- User clicks FAVORITES ---");
        Navigation.findNavController(view).navigate(R.id.librivoxFavoritesFragment);
    }

    private void clickSettings() {
        myLogI("--- User clicks SETTINGS ---");
        SettingsHostActivity.start(requireContext(), RepositoriesSettingsFragment.class, true, R.string.repositories_settings);
    }

    private void doSearch(View view) {
        query = Tonio.cleanSearchString(etLibrivoxSearch.getText());
        etLibrivoxSearch.saveCurrentTextToHistory();
        if (!NetworkHelper.isConnected(requireContext())) {
            myToastE(getString(R.string.no_internet_connection));
            return;
        }
        openLibrivoxResultsActivity(view);
        FirebaseAnalyticsHelper.tellAnalyticsLibrivoxSearch(query, selectedLanguageItem.name);
    }

    private void openLibrivoxResultsActivity(View view) {
        Bundle args = new Bundle();
        args.putString("query", query);
        args.putSerializable(Intents.EXTRA_LIBRIVOX_LANGUAGE_ITEM, selectedLanguageItem);
        Navigation.findNavController(view).navigate(R.id.librivoxResultsFragment, args);
    }

    private void openLibrivoxResultsTrending(View view) {
        Bundle args = new Bundle();
        args.putString("mode", "MODE_TRENDING");
        args.putString("query", ""); // query not used in TRENDING
        args.putSerializable(Intents.EXTRA_LIBRIVOX_LANGUAGE_ITEM, selectedLanguageItem);
        Navigation.findNavController(view).navigate(R.id.librivoxResultsFragment, args);
    }

    private void openLibrivoxResultsLastAdded(View view) {
        Bundle args = new Bundle();
        args.putString("mode", "MODE_LAST_ADDED");
        args.putString("query", ""); // query not used in TRENDING
        args.putSerializable(Intents.EXTRA_LIBRIVOX_LANGUAGE_ITEM, selectedLanguageItem);
        Navigation.findNavController(view).navigate(R.id.librivoxResultsFragment, args);
    }

    private boolean checkLangFromSpinner() {
        selectedLanguageItem = (LibrivoxLanguageItem) spinnerLibrivox.getSelectedItem();
        if (selectedLanguageItem == null) {
            myLogE("Selected language item is null!");
            myToastEE(null, getString(R.string.selected_language_error));
            return false;
        }
        String lang = selectedLanguageItem.code3.toLowerCase();
        if (lang.isEmpty()) {
            myLogE(selectedLanguageItem.toString());
            String errStr = getString(R.string.unsupported_language) + " : [" + selectedLanguageItem.name + "]";
            myToastE(errStr);
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshLanguageSpinner();
    }

    private void refreshLanguageSpinner() {
        if (spinnerLibrivox == null)
            return;
        LibrivoxLanguageStore store = new LibrivoxLanguageStore(requireContext());
        List<LibrivoxLanguageItem> librivox_languages = store.loadLanguages(R.raw.librivox_languages);
        List<LibrivoxLanguageItem> spinnerItems = librivox_languages.stream()
                .filter(l -> l.completed > 0)
                .sorted((a, b) -> Integer.compare(b.completed, a.completed)) // DESC
                .collect(Collectors.toList());

        LibrivoxLanguageStore.setupLanguageSpinner(
                requireContext(),
                spinnerLibrivox,
                Pref.get_Audio_Language_Librivox(requireContext()),
                spinnerItems,
                lli -> Pref.set_Audio_Language_Librivox(requireContext(), lli.name));
    }

}
