package com.driot.bookplayer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Spinner;

import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.objects.LanguageItem;
import com.driot.bookplayer.helpers.LanguageHelper;
import com.driot.bookplayer.settings.ui.PodcastSettingsFragment;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingActivity;
import com.driot.bookplayer.views.EditTextWithButtons;

public class GetPodcastActivity extends LoggingActivity {

    String query, lang;
    EditTextWithButtons editTextPodcast;
    Button bFavorite;
    ImageButton ibFavorite;
    ImageButton ibSettings;
    Button buttonTrending;
    Button buttonPodcastSearch;
    Spinner spinnerLang;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_get_podcast);
        InsetHelper.apply(this);

        editTextPodcast = findViewById(R.id.etPodcast);
        buttonPodcastSearch = findViewById(R.id.bPodcastSearch);
        bFavorite = findViewById(R.id.bFavorite);
        ibFavorite = findViewById(R.id.ibFavorite);
        ibSettings = findViewById(R.id.ibSettings);
        buttonTrending = findViewById(R.id.bPodcastTrending);

        spinnerLang = findViewById(R.id.spinnerLang);
        LanguageHelper.setupLanguageSpinner(
                this,
                spinnerLang,
                Pref.get_Audio_Language_Podcast(this),
                LanguageHelper.getPodcastLanguages(),
                lang -> Pref.set_Audio_Language_Podcast(this, lang.twoLetterCode),
                false
        );

        bFavorite.setOnClickListener(v -> clickFavorite());
        ibFavorite.setOnClickListener(v -> clickFavorite());
        ibSettings.setOnClickListener(v -> clickSettings());

        editTextPodcast.setHistoryKey("podcast_search"); // keep histories separate
        editTextPodcast.setCompletionThreshold(1);        // suggestions after 1 char
        editTextPodcast.setSuggestOnFocus(true);          // show dropdown on focus if empty

        buttonPodcastSearch.setOnClickListener(v -> {
            myLogI("--- User clicks SEARCH ---");
            query = Tonio.cleanSearchString(editTextPodcast.getText());
            LanguageItem selectedLang = (LanguageItem) spinnerLang.getSelectedItem();
            lang = selectedLang.getTwoLetterCode().toLowerCase();

            Intent intent = new Intent(this, PodcastSearchResultsActivity.class);
            intent.putExtra("query", query);
            intent.putExtra("lang", lang);
            startActivity(intent);
            FirebaseAnalyticsHelper.tellAnalyticsPodcastSearch(query, lang);
        });

        buttonTrending.setOnClickListener(v -> {
            myLogI("--- User clicks TRENDING ---");
            query = "";
            LanguageItem selectedLang = (LanguageItem) spinnerLang.getSelectedItem();
            lang = selectedLang.getTwoLetterCode().toLowerCase();

            Intent intent = new Intent(this, PodcastSearchResultsActivity.class);
            intent.putExtra("query", query);
            intent.putExtra("lang", lang);
            startActivity(intent);
            FirebaseAnalyticsHelper.tellAnalyticsPodcastTrending(query, lang);
        });
        // Keyboard "done/search"
        editTextPodcast.getEditText().setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_ACTION_DONE) {
                doSearch();
                return true;
            }
            return false;
        });

    }
    ////////////////////////////////
    ////////////////////////////////

    private void clickFavorite() {
        myLogI("--- User clicks FAVORITES ---");
        Intent intent = new Intent(this, PodcastFavoritesActivity.class);
        startActivity(intent);
    }

    private void clickSettings() {
        myLogI("--- User clicks SETTINGS ---");
        SettingsHostActivity.start(this, PodcastSettingsFragment.class, true, R.string.podcast_settings);
    }

    private void doSearch() {
        query = editTextPodcast.getText();
        lang = spinnerLang.getSelectedItem().toString().toLowerCase();

        if (lang.isEmpty()) {
            myToast("selected language error");
            return;
        }

        openPodcastResultsActivity();

        FirebaseAnalyticsHelper.tellAnalyticsLibrivoxSearch(query, lang);
    }
    private void openPodcastResultsActivity() {
        Intent intent = new Intent(this, PodcastSearchResultsActivity.class);
        intent.putExtra("query", query);
        intent.putExtra("lang", lang);
        startActivity(intent);
    }

}
