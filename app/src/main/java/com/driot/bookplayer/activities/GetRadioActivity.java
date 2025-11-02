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
import com.driot.bookplayer.helpers.LanguageHelper;
import com.driot.bookplayer.objects.OngoingTaskHost;
import com.driot.bookplayer.settings.ui.RadioSettingsFragment;
import com.driot.bookplayer.utils.log.LoggingActivity;
import com.driot.bookplayer.views.EditTextWithButtons;

/**
 * Entry screen to browse/search internet radios (Radio Browser).
 * Very close to GetAudiobookActivity: favorites, settings, search, trending.
 */
public class GetRadioActivity extends LoggingActivity {

    Spinner spinnerLang;
    Spinner spinnerCountry;   // optional: country filter (2-letter codes like FR/US…)
    Spinner spinnerTag;       // optional: tag/genre (e.g., "jazz", "news", …)

    EditTextWithButtons etRadio;
    Button bFavorite;
    ImageButton ibFavorite;
    ImageButton ibSettings;
    Button buttonTrending;
    Button buttonSearch;

    String query, lang, country, tag;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_get_radio);
        InsetHelper.apply(this);

        OngoingTaskHost.attach(
                this,
                R.id.topOverlayContainer,
                new Intent(this, AddResourceActivity.class)
        );

        // ---- find views ----
        buttonTrending = findViewById(R.id.bRadioTrending);
        spinnerLang    = findViewById(R.id.spinnerRadioLang);
        spinnerCountry = findViewById(R.id.spinnerRadioCountry);
        spinnerTag     = findViewById(R.id.spinnerRadioTag);
        etRadio        = findViewById(R.id.etRadio);
        buttonSearch   = findViewById(R.id.bRadioSearch);
        bFavorite      = findViewById(R.id.bFavorite);
        ibFavorite     = findViewById(R.id.ibFavorite);
        ibSettings     = findViewById(R.id.ibSettings);

        // ---- actions ----
        bFavorite.setOnClickListener(v -> clickFavorite());
        ibFavorite.setOnClickListener(v -> clickFavorite());
        ibSettings.setOnClickListener(v -> clickSettings());

        etRadio.setHistoryKey("radio_search");     // keep histories separate
        etRadio.setCompletionThreshold(1);
        etRadio.setSuggestOnFocus(true);

        // ---- language spinner ----
        // Reuse your LanguageHelper. If you prefer a different list for radios,
        // add LanguageHelper.getRadioLanguages() the same way you did for podcasts/librivox.
        LanguageHelper.setupLanguageSpinner(
                this,
                spinnerLang,
                Pref.get_Audio_Language_Radio(this),       // add this getter in Pref (mirroring Librivox one)
                LanguageHelper.getPodcastLanguages(),       // or getLibrivoxLanguages() / a dedicated list
                langItem -> Pref.set_Audio_Language_Radio(this, langItem.threeLetterCode),
                true
        );

        // ---- optional: country + tag spinners ----
        // If you have helpers, plug them here. For now we keep them optional:
        // spinnerCountry: entries like "FR", "US", "" (empty = any)
        // spinnerTag: popular tags ("news", "jazz", "talk", "", etc.)
        // If you don’t have adapters yet, leave them empty; we read their .toString() safely.

        buttonTrending.setOnClickListener(v -> {
            myLogI("--- User clicks RADIO TRENDING ---");
            query   = "";
            lang    = safeSpinnerStr(spinnerLang);
            country = safeSpinnerStr(spinnerCountry);
            tag     = safeSpinnerStr(spinnerTag);

            if (lang.isEmpty()) {
                myToast(getString(R.string.selected_language_error));
                return;
            }
            openRadioResultsActivity(/*analyticsEvent=*/"trending");
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

        SettingsHostActivity.start(this, RadioSettingsFragment.class, true, R.string.radio_settings);
    }

    private void doSearch() {
        query   = etRadio.getText();
        lang    = safeSpinnerStr(spinnerLang);
        country = safeSpinnerStr(spinnerCountry);
        tag     = safeSpinnerStr(spinnerTag);

        if (lang.isEmpty()) {
            myToast(getString(R.string.selected_language_error));
            return;
        }
        openRadioResultsActivity(/*analyticsEvent=*/"search");
    }

    private void openRadioResultsActivity(String analyticsEvent) {
        Intent intent = new Intent(this, RadioResultsActivity.class);
        intent.putExtra("query", query);
        intent.putExtra("lang", lang);         // e.g., "fr"
        intent.putExtra("country", country);   // e.g., "FR"
        intent.putExtra("tag", tag);           // e.g., "jazz"
        startActivity(intent);

        // Analytics (mirror your Librivox calls)
        if ("trending".equals(analyticsEvent)) {
            //TODO
            //FirebaseAnalyticsHelper.tellAnalyticsRadioTrending(query, lang, country, tag);
        } else if ("search".equals(analyticsEvent)) {
            //TODO
            //FirebaseAnalyticsHelper.tellAnalyticsRadioSearch(query, lang, country, tag);
        }
    }

    private static String safeSpinnerStr(Spinner sp) {
        if (sp == null || sp.getSelectedItem() == null) return "";
        return String.valueOf(sp.getSelectedItem()).trim().toLowerCase();
    }
}
