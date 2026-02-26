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
import com.driot.bookplayer.settings.ui.LibrivoxSettingsFragment;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.views.EditText1lineWithSearch;

import java.util.List;
import java.util.stream.Collectors;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class GetLibrivoxActivity extends BaseBottomNavActivity {

    Spinner spinnerLibrivox;
    EditText1lineWithSearch editTextLibrivox;
    Button bFavorite;
    ImageButton ibFavorite;
    Button buttonTrending, bLibrivoxLastAdded;
    Button buttonByGenre;
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

        buttonTrending = findViewById(R.id.bLibrivoxTrending);
        bLibrivoxLastAdded = findViewById(R.id.bLibrivoxLastAdded);
        buttonByGenre = findViewById(R.id.bByGenre);
        buttonByAuthor = findViewById(R.id.bByAuthor);
        spinnerLibrivox = findViewById(R.id.spinnerLibrivox);
        editTextLibrivox = findViewById(R.id.etLibrivox);
        bFavorite = findViewById(R.id.bFavorite);
        ibFavorite = findViewById(R.id.ibFavorite);

        bFavorite.setOnClickListener(v -> clickFavorite());
        ibFavorite.setOnClickListener(v -> clickFavorite());
        findViewById(R.id.ibSettings).setOnClickListener(v -> clickSettings());

        editTextLibrivox.setHistoryKey("librivox_search"); // keep histories separate
        editTextLibrivox.setCompletionThreshold(1); // suggestions after 1 char
        editTextLibrivox.setSuggestOnFocus(true); // show dropdown on focus if empty

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

        buttonTrending.setOnClickListener(v -> {
            myLogI("--- User clicks MOST DOWNLOADED ---");
            query = "";
            if (!checkLangFromSpinner())
                return;
            openLibrivoxResultsTrending();
            FirebaseAnalyticsHelper.tellAnalyticsLibrivoxQuickList(query, selectedLanguageItem.name, "most_download");
        });

        bLibrivoxLastAdded.setOnClickListener(v -> {
            myLogI("--- User clicks LAST ADDED ---");
            query = "";
            if (!checkLangFromSpinner())
                return;
            openLibrivoxResultsLastAdded();
            FirebaseAnalyticsHelper.tellAnalyticsLibrivoxQuickList(query, selectedLanguageItem.name, "last_added");
        });

        buttonByGenre.setOnClickListener(v -> {
            myLogI("--- User clicks BY GENRE ---");
            if (!checkLangFromSpinner())
                return;
            GetLibrivoxFacetListActivity.startForGenres(this, selectedLanguageItem);
            FirebaseAnalyticsHelper.tellAnalyticsLibrivoxQuickList("", selectedLanguageItem.name, "by_genre");
        });

        // --- BY AUTHOR: open facet list activity (no spinner there) ---
        buttonByAuthor.setOnClickListener(v -> {
            myLogI("--- User clicks BY AUTHOR ---");
            if (!checkLangFromSpinner())
                return;
            // TODO
            FirebaseAnalyticsHelper.tellAnalyticsLibrivoxBy("author", selectedLanguageItem.name);
        });

        editTextLibrivox.getSearchButton().setOnClickListener(v -> {
            myLogI("--- User clicks SEARCH ---");
            if (!checkLangFromSpinner())
                return;
            doSearch();
        });
        editTextLibrivox.getEditText().setOnEditorActionListener((v, actionId, event) -> {
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
                editTextLibrivox.getEditText().dismissDropDown();

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
        SettingsHostActivity.start(this, LibrivoxSettingsFragment.class, true, R.string.librivox_settings);
    }

    private void doSearch() {
        query = Tonio.cleanSearchString(editTextLibrivox.getText());
        editTextLibrivox.saveCurrentTextToHistory();
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

}
