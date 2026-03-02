package com.driot.bookplayer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Spinner;

import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.librivox.GetLibrivoxFacetListActivity;
import com.driot.bookplayer.librivox.LibrivoxLanguageItem;
import com.driot.bookplayer.librivox.LibrivoxLanguageStore;
import com.driot.bookplayer.nav.BaseBottomNavActivity;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.settings.ui.RepositoriesSettingsFragment;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.views.EditText1lineWithSearch;

import java.util.List;
import java.util.stream.Collectors;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class GetLibrivoxActivity extends BaseBottomNavActivity {

    Spinner spinnerLibrivox;
    EditText1lineWithSearch etLibrivoxSearch;
    Button bFavorite;
    ImageButton ibFavorite;
    Button bLibrivoxTrending, bLibrivoxLastAdded;
    Button bLibrivoxByGenre;
    Button buttonByAuthor;

    String query;
    LibrivoxLanguageItem selectedLanguageItem;

    @Override
    protected int getNavId() {
        return R.id.nav_add;
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_get_librivox;
    }

    @Override
    protected boolean enableOngoingTaskOverlay() {
        return true;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        bLibrivoxTrending = findViewById(R.id.bLibrivoxTrending);
        bLibrivoxLastAdded = findViewById(R.id.bLibrivoxLastAdded);
        bLibrivoxByGenre = findViewById(R.id.bLibrivoxByGenre);
        spinnerLibrivox = findViewById(R.id.spinnerLibrivox);
        etLibrivoxSearch = findViewById(R.id.etLibrivoxSearch);
        bFavorite = findViewById(R.id.bFavorite);
        ibFavorite = findViewById(R.id.ibFavorite);

        bFavorite.setOnClickListener(v -> clickFavorite());
        ibFavorite.setOnClickListener(v -> clickFavorite());
        findViewById(R.id.ibSettings).setOnClickListener(v -> clickSettings());

        etLibrivoxSearch.setHistoryKey("librivox_search"); // keep histories separate
        etLibrivoxSearch.setCompletionThreshold(1); // suggestions after 1 char
        etLibrivoxSearch.setSuggestOnFocus(true); // show dropdown on focus if empty

        refreshLanguageSpinner();

        bLibrivoxTrending.setOnClickListener(v -> {
            myLogI("--- User clicks MOST DOWNLOADED ---");
            if (!NetworkHelper.isConnected(this)) {
                myToastE(getString(R.string.no_internet_connection));
                return;
            }
            query = "";
            if (!checkLangFromSpinner())
                return;
            openLibrivoxResultsTrending();
            FirebaseAnalyticsHelper.tellAnalyticsLibrivoxQuickList(query, selectedLanguageItem.name, "most_download");
        });

        bLibrivoxLastAdded.setOnClickListener(v -> {
            myLogI("--- User clicks LAST ADDED ---");
            if (!NetworkHelper.isConnected(this)) {
                myToastE(getString(R.string.no_internet_connection));
                return;
            }
            query = "";
            if (!checkLangFromSpinner())
                return;
            openLibrivoxResultsLastAdded();
            FirebaseAnalyticsHelper.tellAnalyticsLibrivoxQuickList(query, selectedLanguageItem.name, "last_added");
        });

        bLibrivoxByGenre.setOnClickListener(v -> {
            myLogI("--- User clicks BY GENRE ---");
            if (!NetworkHelper.isConnected(this)) {
                myToastE(getString(R.string.no_internet_connection));
                return;
            }
            if (!checkLangFromSpinner())
                return;
            GetLibrivoxFacetListActivity.startForGenres(this, selectedLanguageItem);
            FirebaseAnalyticsHelper.tellAnalyticsLibrivoxQuickList("", selectedLanguageItem.name, "by_genre");
        });

        etLibrivoxSearch.getSearchButton().setOnClickListener(v -> {
            myLogI("--- User clicks SEARCH ---");
            if (!checkLangFromSpinner())
                return;
            doSearch();
        });

        etLibrivoxSearch.getEditText().setOnEditorActionListener((v, actionId, event) -> {
            boolean isEnterKey = event != null
                    && event.getAction() == android.view.KeyEvent.ACTION_DOWN
                    && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER;

            if (actionId == EditorInfo.IME_ACTION_SEARCH || isEnterKey) {
                myLogI("--- User clicks SEARCH --- (via keyboard)");
                // 2) run your existing search
                if (!checkLangFromSpinner())
                    return true;
                doSearch();

                // 3) optional: close suggestions dropdown
                etLibrivoxSearch.getEditText().dismissDropDown();

                return true;
            }
            return false;
        });

    }

    ////////////////////////////////
    ////////////////////////////////
    private void clickFavorite() {
        myLogI("--- User clicks FAVORITES ---");
        Intent intent = new Intent(this, LibrivoxFavoritesActivity.class);
        startActivity(intent);
    }

    private void clickSettings() {
        myLogI("--- User clicks SETTINGS ---");
        SettingsHostActivity.start(this, RepositoriesSettingsFragment.class, true, R.string.repositories_settings);
    }

    private void doSearch() {
        query = Tonio.cleanSearchString(etLibrivoxSearch.getText());
        etLibrivoxSearch.saveCurrentTextToHistory();
        if (!NetworkHelper.isConnected(this)) {
            myToastE(getString(R.string.no_internet_connection));
            return;
        }
        openLibrivoxResultsActivity();
        FirebaseAnalyticsHelper.tellAnalyticsLibrivoxSearch(query, selectedLanguageItem.name);
    }

    private void openLibrivoxResultsActivity() {
        Intent intent = new Intent(this, LibrivoxResultsActivity.class);
        intent.putExtra("query", query);
        intent.putExtra(Intents.EXTRA_LIBRIVOX_LANGUAGE_ITEM, selectedLanguageItem);
        startActivity(intent);
    }

    private void openLibrivoxResultsTrending() {
        Intent intent = new Intent(this, LibrivoxResultsActivity.class);
        intent.putExtra("mode", "MODE_TRENDING");
        intent.putExtra("query", ""); // query not used in TRENDING
        intent.putExtra(Intents.EXTRA_LIBRIVOX_LANGUAGE_ITEM, selectedLanguageItem);
        startActivity(intent);
    }

    private void openLibrivoxResultsLastAdded() {
        Intent intent = new Intent(this, LibrivoxResultsActivity.class);
        intent.putExtra("mode", "MODE_LAST_ADDED");
        intent.putExtra("query", ""); // query not used in TRENDING
        intent.putExtra(Intents.EXTRA_LIBRIVOX_LANGUAGE_ITEM, selectedLanguageItem);
        startActivity(intent);
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
    protected void onResume() {
        super.onResume();
        refreshLanguageSpinner();
    }

    private void refreshLanguageSpinner() {
        if (spinnerLibrivox == null)
            return;
        LibrivoxLanguageStore store = new LibrivoxLanguageStore(this);
        List<LibrivoxLanguageItem> librivox_languages = store.loadLanguages(R.raw.librivox_languages);
        List<LibrivoxLanguageItem> spinnerItems = librivox_languages.stream()
                .filter(l -> l.completed > 0)
                .sorted((a, b) -> Integer.compare(b.completed, a.completed)) // DESC
                .collect(Collectors.toList());

        LibrivoxLanguageStore.setupLanguageSpinner(
                this,
                spinnerLibrivox,
                Pref.get_Audio_Language_Librivox(this),
                spinnerItems,
                lli -> Pref.set_Audio_Language_Librivox(this, lli.name));
    }

}
