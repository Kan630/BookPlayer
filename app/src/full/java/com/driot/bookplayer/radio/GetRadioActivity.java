package com.driot.bookplayer.radio;

import android.content.Intent;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.ImageButton;

import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.nav.FullActivity;
import com.driot.bookplayer.activities.SettingsHostActivity;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.settings.ui.RadioSettingsFragment;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.views.EditText1lineWithSearch;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Entry screen to browse/search internet radios (Radio Browser).
 * Very close to GetAudiobookActivity: favorites, settings, search, trending.
 */
@AndroidEntryPoint
public class GetRadioActivity extends FullActivity {

    public static final String EXTRA_RADIO_STATION_SEARCH_MODE = "EXTRA_RADIO_STATION_SEARCH_MODE";

    EditText1lineWithSearch etRadio;
    Button bFavorite;
    ImageButton ibFavorite;
    ImageButton ibSettings;
    Button bTopClick, bTopVote, bLastClick, bLastChange;

    String query, lang, country, tag;

    RadioBrowserRepository repo;

    @Override
    protected int getNavSectionId() {
        return R.id.nav_radio;
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_get_radio;
    }

    @Override
    protected boolean enableOngoingTaskOverlay() {
        return true;
    }

    @Override protected boolean isSectionRoot() { return true; }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        repo = new RadioBrowserRepository(
                this,
                /* discoverMirrors */ false,
                /* log level */ Var.HTTP_LOGGING_INTERCEPTOR_LOG_LEVEL);

        bTopClick = findViewById(R.id.bRadioTopClicked);
        bTopVote = findViewById(R.id.bRadioTopVoted);
        bLastClick = findViewById(R.id.bRadioLastClicked);
        bLastChange = findViewById(R.id.bRadioLastChanged);

        etRadio = findViewById(R.id.etRadio);
        // buttonSearch = findViewById(R.id.bRadioSearch); // Removed
        bFavorite = findViewById(R.id.bFavorite);
        ibFavorite = findViewById(R.id.ibFavorite);
        ibSettings = findViewById(R.id.ibSettings);

        // ---- open recyclerviews ----
        bFavorite.setOnClickListener(v -> clickFavorite());
        ibFavorite.setOnClickListener(v -> clickFavorite());
        ibSettings.setOnClickListener(v -> clickSettings());

        findViewById(R.id.bRadioByTag).setOnClickListener(v -> {
            myLogI("---- user clicks BY TAG ---");
            if (!NetworkHelper.getCheckInternetForAction(this))
                return;
            FirebaseAnalyticsHelper.tellAnalyticsRadioBy("tag");
            GetRadioCardListActivity.start(this, GetRadioCardListActivity.MODE_TAG);
        });
        findViewById(R.id.bRadioByCountry).setOnClickListener(v -> {
            myLogI("---- user clicks BY COUNTRY ---");
            if (!NetworkHelper.getCheckInternetForAction(this))
                return;
            FirebaseAnalyticsHelper.tellAnalyticsRadioBy("country");
            GetRadioCardListActivity.start(this, GetRadioCardListActivity.MODE_COUNTRY);
        });
        findViewById(R.id.bRadioByLang).setOnClickListener(v -> {
            myLogI("---- user clicks BY LANGUAGE ---");
            if (!NetworkHelper.getCheckInternetForAction(this))
                return;
            FirebaseAnalyticsHelper.tellAnalyticsRadioBy("lang");
            GetRadioCardListActivity.start(this, GetRadioCardListActivity.MODE_LANGUAGE);
        });

        etRadio.setHistoryKey("radio_search"); // keep histories separate
        etRadio.setCompletionThreshold(1);
        etRadio.setSuggestOnFocus(true);

        bTopClick.setOnClickListener(v -> {
            myLogI("---- user clicks TOP CLICK ---");
            if (!NetworkHelper.getCheckInternetForAction(this))
                return;
            Intent i = new Intent(getApplicationContext(), RadioResultsActivity.class)
                    .putExtra(EXTRA_RADIO_STATION_SEARCH_MODE, "MODE_TOP_CLICK");
            startActivity(i);
            FirebaseAnalyticsHelper.tellAnalyticsRadioBy("topClick");
        });

        bTopVote.setOnClickListener(v -> {
            myLogI("---- user clicks TOP VOTE ---");
            if (!NetworkHelper.getCheckInternetForAction(this))
                return;
            Intent i = new Intent(getApplicationContext(), RadioResultsActivity.class)
                    .putExtra(EXTRA_RADIO_STATION_SEARCH_MODE, "MODE_TOP_VOTE");
            startActivity(i);
            FirebaseAnalyticsHelper.tellAnalyticsRadioBy("topVote");
        });

        bLastClick.setOnClickListener(v -> {
            myLogI("---- user clicks LAST CLICK ---");
            if (!NetworkHelper.getCheckInternetForAction(this))
                return;
            Intent i = new Intent(getApplicationContext(), RadioResultsActivity.class)
                    .putExtra(EXTRA_RADIO_STATION_SEARCH_MODE, "MODE_LAST_CLICK");
            startActivity(i);
            FirebaseAnalyticsHelper.tellAnalyticsRadioBy("lastClick");
        });

        bLastChange.setOnClickListener(v -> {
            myLogI("---- user clicks LAST CHANGE ---");
            if (!NetworkHelper.getCheckInternetForAction(this))
                return;
            Intent i = new Intent(getApplicationContext(), RadioResultsActivity.class)
                    .putExtra(EXTRA_RADIO_STATION_SEARCH_MODE, "MODE_LAST_CHANGE");
            startActivity(i);
            FirebaseAnalyticsHelper.tellAnalyticsRadioBy("lastChange");
        });

        etRadio.getSearchButton().setOnClickListener(v -> {
            myLogI("--- User clicks RADIO SEARCH ---");
            etRadio.saveCurrentTextToHistory();
            doSearch();
        });

        etRadio.getEditText().setOnEditorActionListener((v, actionId, event) -> {
            boolean isEnterKey = event != null
                    && event.getAction() == android.view.KeyEvent.ACTION_DOWN
                    && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER;

            if (actionId == EditorInfo.IME_ACTION_SEARCH || isEnterKey) {
                myLogI("--- User clicks RADIO SEARCH --- (via keyboard)");
                etRadio.saveCurrentTextToHistory();
                doSearch();
                etRadio.getEditText().dismissDropDown();
                return true;
            }
            return false;
        });
    }

    private void clickFavorite() {
        myLogI("--- User clicks RADIO FAVORITES ---");
        // Create this screen like your Librivox favorites (list of saved ApiStation UUIDs)
        Intent intent = new Intent(this, RadioFavoritesActivity.class);
        startActivity(intent);
    }

    private void clickSettings() {
        myLogI("--- User clicks RADIO SETTINGS ---");
        SettingsHostActivity.start(this, RadioSettingsFragment.class, true, R.string.Radio_Settings);
    }

    private void doSearch() {
        if (!NetworkHelper.getCheckInternetForAction(this)) {
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

        Intent intent = new Intent(this, RadioResultsActivity.class)
                .putExtra(EXTRA_RADIO_STATION_SEARCH_MODE, "MODE_SEARCH")
                .putExtra("query", query)
                .putExtra("lang", lang)
                .putExtra("country", country)
                .putExtra("tag", tag);
        startActivity(intent);
        FirebaseAnalyticsHelper.tellAnalyticsRadioSearch(query, lang, country, tag);
    }

}
