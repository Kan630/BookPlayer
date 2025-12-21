package com.driot.bookplayer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Spinner;

import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.radio.RadioBrowserRepository;
import com.driot.bookplayer.radio.Station;
import com.driot.bookplayer.radio.TagCardAdapter;
import com.driot.bookplayer.settings.ui.RadioSettingsFragment;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.views.EditText1lineWithPasteDelete;

import java.util.List;
import java.util.concurrent.Executors;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Entry screen to browse/search internet radios (Radio Browser).
 * Very close to GetAudiobookActivity: favorites, settings, search, trending.
 */
@AndroidEntryPoint
public class GetRadioActivity extends BaseBottomNavActivity {

    public static final String EXTRA_RADIO_STATION_SEARCH_MODE = "EXTRA_RADIO_STATION_SEARCH_MODE";


    Spinner spinnerLang;
    Spinner spinnerCountry;   // optional: country filter (2-letter codes like FR/US…)
    Spinner spinnerTag;       // optional: tag/genre (e.g., "jazz", "news", …)

    EditText1lineWithPasteDelete etRadio;
    Button bFavorite;
    ImageButton ibFavorite;
    ImageButton ibSettings;
    Button bTopClick, bTopVote, bLastClick, bLastChange;
    Button buttonSearch;

    String query, lang, country, tag;

    TagCardAdapter tagAdapter;
    RadioBrowserRepository repo;

    @Override protected int getNavId() { return R.id.nav_radio; }
    @Override protected int getLayoutResId() { return R.layout.activity_get_radio; }
    @Override protected boolean enableOngoingTaskOverlay() { return true; }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        repo = new RadioBrowserRepository(
                this,
                /* discoverMirrors */ false,
                /* log level */ Var.HTTP_LOGGING_INTERCEPTOR_LOG_LEVEL
        );

        bTopClick      = findViewById(R.id.bTopClick);
        bTopVote       = findViewById(R.id.bTopVote);
        bLastClick     = findViewById(R.id.blastClick);
        bLastChange    = findViewById(R.id.blastChange);

        etRadio        = findViewById(R.id.etRadio);
        buttonSearch   = findViewById(R.id.bRadioSearch);
        bFavorite      = findViewById(R.id.bFavorite);
        ibFavorite     = findViewById(R.id.ibFavorite);
        ibSettings     = findViewById(R.id.ibSettings);

        // ---- open recyclerviews ----
        bFavorite.setOnClickListener(v -> clickFavorite());
        ibFavorite.setOnClickListener(v -> clickFavorite());
        ibSettings.setOnClickListener(v -> clickSettings());

        findViewById(R.id.bByTag).setOnClickListener(v -> {
            myLogI("---- user clicks BY TAG ---");
            FirebaseAnalyticsHelper.tellAnalyticsRadioBy("tag");
            GetRadioCardListActivity.start(this, GetRadioCardListActivity.MODE_TAG);
        });
        findViewById(R.id.bByCountry).setOnClickListener(v -> {
            myLogI("---- user clicks BY COUNTRY ---");
            FirebaseAnalyticsHelper.tellAnalyticsRadioBy("country");
            GetRadioCardListActivity.start(this, GetRadioCardListActivity.MODE_COUNTRY);
        });
        findViewById(R.id.bByLanguage).setOnClickListener(v -> {
            myLogI("---- user clicks BY LANGUAGE ---");
            FirebaseAnalyticsHelper.tellAnalyticsRadioBy("lang");
            GetRadioCardListActivity.start(this, GetRadioCardListActivity.MODE_LANGUAGE);
        });

        etRadio.setHistoryKey("radio_search");     // keep histories separate
        etRadio.setCompletionThreshold(1);
        etRadio.setSuggestOnFocus(true);

        bTopClick.setOnClickListener(v -> {
            myLogI("---- user clicks TOP CLICK ---");
            Intent i = new Intent(getApplicationContext(), RadioResultsActivity.class)
                    .putExtra(EXTRA_RADIO_STATION_SEARCH_MODE, "MODE_TOP_CLICK");
            startActivity(i);
            FirebaseAnalyticsHelper.tellAnalyticsRadioBy("topClick");
        });

        bTopVote.setOnClickListener(v -> {
            myLogI("---- user clicks TOP VOTE ---");
            Intent i = new Intent(getApplicationContext(), RadioResultsActivity.class)
                    .putExtra(EXTRA_RADIO_STATION_SEARCH_MODE, "MODE_TOP_VOTE");
            startActivity(i);
            FirebaseAnalyticsHelper.tellAnalyticsRadioBy("topVote");
        });

        bLastClick.setOnClickListener(v -> {
            myLogI("---- user clicks LAST CLICK ---");
            Intent i = new Intent(getApplicationContext(), RadioResultsActivity.class)
                    .putExtra(EXTRA_RADIO_STATION_SEARCH_MODE, "MODE_LAST_CLICK");
            startActivity(i);
            FirebaseAnalyticsHelper.tellAnalyticsRadioBy("lastClick");
        });

        bLastChange.setOnClickListener(v -> {
            myLogI("---- user clicks LAST CHANGE ---");
            Intent i = new Intent(getApplicationContext(), RadioResultsActivity.class)
                    .putExtra(EXTRA_RADIO_STATION_SEARCH_MODE, "MODE_LAST_CHANGE");
            startActivity(i);
            FirebaseAnalyticsHelper.tellAnalyticsRadioBy("lastChange");
        });

        buttonSearch.setOnClickListener(v -> {
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
        // Create this screen like your Librivox favorites (list of saved Station UUIDs)
        Intent intent = new Intent(this, RadioFavoritesActivity.class);
        startActivity(intent);
    }

    private void clickSettings() {
        myLogI("--- User clicks RADIO SETTINGS ---");
        // Reuse your SettingsHostActivity with a RadioSettingsFragment (build like LibrivoxSettingsFragment)

        SettingsHostActivity.start(this, RadioSettingsFragment.class, true, R.string.Radio_Settings);
    }

    private void doSearch() {
        query   = Tonio.cleanSearchString(etRadio.getText());
        /*
        lang    = safeSpinnerStr(spinnerLang);
        country = safeSpinnerStr(spinnerCountry);
        tag     = safeSpinnerStr(spinnerTag);
         */
        lang    = null;
        country = null;
        tag     = null;

        Intent intent = new Intent(this, RadioResultsActivity.class)
                .putExtra(EXTRA_RADIO_STATION_SEARCH_MODE, "MODE_SEARCH")
                .putExtra("query", query)
                .putExtra("lang", lang)
                .putExtra("country", country)
                .putExtra("tag", tag);
        startActivity(intent);
        FirebaseAnalyticsHelper.tellAnalyticsRadioSearch(query, lang, country, tag);
    }

    private static String safeSpinnerStr(Spinner sp) {
        if (sp == null || sp.getSelectedItem() == null) return "";
        return String.valueOf(sp.getSelectedItem()).trim().toLowerCase();
    }
    /** 2-letter language from LanguageItem. */
    private static String safeLang(Spinner sp) {
        Object it = (sp == null) ? null : sp.getSelectedItem();
        if (it instanceof com.driot.bookplayer.objects.LanguageItem) {
            return ((com.driot.bookplayer.objects.LanguageItem) it).twoLetterCode; // "de", "fr", ...
        }
        return "";
    }

    /** If you use a real CountryItem model, mirror safeLang; else keep "" until you wire one. */
    private static String safeCountry(Spinner sp) {
        if (sp == null || sp.getSelectedItem() == null) return "";
        String s = String.valueOf(sp.getSelectedItem()).trim();
        // Expect values like "FR", "US" or "" – normalize to upper
        return s.length() == 2 ? s.toUpperCase() : "";
    }


}
