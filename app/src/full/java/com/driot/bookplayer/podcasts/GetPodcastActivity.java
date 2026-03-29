package com.driot.bookplayer.podcasts;

import android.content.Intent;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Spinner;

import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.SettingsHostActivity;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.nav.FullActivity;
import com.driot.bookplayer.objects.LanguageItem;
import com.driot.bookplayer.helpers.LanguageHelper;
import com.driot.bookplayer.settings.ui.PodcastSettingsFragment;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.views.EditText1lineWithSearch;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class GetPodcastActivity extends FullActivity {

    String query, lang;
    EditText1lineWithSearch editTextPodcast;
    Button bFavorite;
    ImageButton ibFavorite;
    ImageButton ibSettings;
    Button buttonTrending;
    Spinner spinnerLang;

    @Override
    protected int getNavId() {
        return R.id.nav_podcast;
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_get_podcast;
    }

    @Override
    protected boolean enableOngoingTaskOverlay() {
        return true;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        editTextPodcast = findViewById(R.id.etPodcast);
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
                false);

        bFavorite.setOnClickListener(v -> clickFavorite());
        ibFavorite.setOnClickListener(v -> clickFavorite());
        ibSettings.setOnClickListener(v -> clickSettings());

        editTextPodcast.post(() -> { // async because takes ages
            editTextPodcast.setHistoryKey("podcast_search");
            editTextPodcast.setCompletionThreshold(1);
            editTextPodcast.setSuggestOnFocus(true);
        });

        editTextPodcast.getSearchButton().setOnClickListener(v -> {
            myLogI("--- User clicks SEARCH ---");
            doSearch();
            editTextPodcast.saveCurrentTextToHistory();
        });

        buttonTrending.setOnClickListener(v -> {
            myLogI("--- User clicks TRENDING ---");
            if (!NetworkHelper.isConnected(this)) {
                myToastE(getString(R.string.no_internet_connection));
                return;
            }
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
            boolean isEnterKey = event != null
                    && event.getAction() == android.view.KeyEvent.ACTION_DOWN
                    && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER;

            if (actionId == EditorInfo.IME_ACTION_SEARCH || isEnterKey) {
                doSearch();
                editTextPodcast.saveCurrentTextToHistory();
                editTextPodcast.getEditText().dismissDropDown();
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
        SettingsHostActivity.start(this, PodcastSettingsFragment.class, true, R.string.Podcast_Settings);
    }

    private void doSearch() {
        if (!NetworkHelper.isConnected(this)) {
            myToastE(getString(R.string.no_internet_connection));
            return;
        }
        query = Tonio.cleanSearchString(editTextPodcast.getText());
        lang = spinnerLang.getSelectedItem().toString().toLowerCase();

        if (lang.isEmpty()) {
            myToast(getString(com.driot.bookplayer.R.string.selected_language_error));
            return;
        }

        openPodcastResultsActivity();

        FirebaseAnalyticsHelper.tellAnalyticsPodcastSearch(query, lang);
    }

    private void openPodcastResultsActivity() {
        Intent intent = new Intent(this, PodcastSearchResultsActivity.class);
        intent.putExtra("query", query);
        intent.putExtra("lang", lang);
        startActivity(intent);
    }

}
