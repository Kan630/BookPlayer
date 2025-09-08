package com.driot.bookplayer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Spinner;

import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.helpers.AnalyticsHelper;
import com.driot.bookplayer.objects.LanguageItem;
import com.driot.bookplayer.utils.LanguageHelper;
import com.driot.bookplayer.utils.log.LoggingActivity;
import com.driot.bookplayer.views.EditTextWithButtons;

public class GetPodcastActivity extends LoggingActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_get_podcast);

        ////////////////////////////////
        /// // PODCASTS SEARCH
        ////////////////////////////////
        Spinner spinnerPodcast = findViewById(R.id.spinnerPodcast);
        LanguageHelper.setupLanguageSpinner(
                this,
                spinnerPodcast,
                Pref.get_Audio_Language_Podcast(this),
                LanguageHelper.getPodcastLanguages(),
                lang -> Pref.set_Audio_Language_Podcast(this, lang.twoLetterCode),
                false
        );
        Button buttonPodcastSearch;
        EditTextWithButtons editTextPodcast = findViewById(R.id.etPodcast);
        buttonPodcastSearch = findViewById(R.id.bPodcastSearch);
        buttonPodcastSearch.setOnClickListener(v -> {
            String query = editTextPodcast.getText();
            LanguageItem selectedLang = (LanguageItem) spinnerPodcast.getSelectedItem();
            String lang = selectedLang.getTwoLetterCode().toLowerCase();

            Intent intent = new Intent(this, PodcastSearchResultsActivity.class);
            intent.putExtra("query", query);
            intent.putExtra("lang", lang);
            startActivity(intent);
        });
        Button bFavoritePodcasts = findViewById(R.id.bFavoritePodcasts);
        bFavoritePodcasts.setOnClickListener(v -> {
            myLogI("--- User clicks FAVORITES");
            Intent intent = new Intent(this, PodcastFavoritesActivity.class);
            startActivity(intent);
        });

        Button buttonTrending;
        buttonTrending = findViewById(R.id.bPodcastTrending);
        buttonTrending.setOnClickListener(v -> {
            myLogI("--- User clicks TRENDING");
            String query = "";
            LanguageItem selectedLang = (LanguageItem) spinnerPodcast.getSelectedItem();
            String lang = selectedLang.getTwoLetterCode().toLowerCase();

            Intent intent = new Intent(this, PodcastSearchResultsActivity.class);
            intent.putExtra("query", query);
            intent.putExtra("lang", lang);
            startActivity(intent);
        });

    }
    ////////////////////////////////
    ////////////////////////////////
}
