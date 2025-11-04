package com.driot.bookplayer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Spinner;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.LanguageHelper;
import com.driot.bookplayer.helpers.ViewHelper;
import com.driot.bookplayer.objects.OngoingTaskHost;
import com.driot.bookplayer.radio.RadioBrowserRepository;
import com.driot.bookplayer.radio.Station;
import com.driot.bookplayer.radio.TagCardAdapter;
import com.driot.bookplayer.radio.TagItem;
import com.driot.bookplayer.settings.ui.RadioSettingsFragment;
import com.driot.bookplayer.utils.log.LoggingActivity;
import com.driot.bookplayer.views.EditTextWithButtons;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Entry screen to browse/search internet radios (Radio Browser).
 * Very close to GetAudiobookActivity: favorites, settings, search, trending.
 */
public class GetRadioActivity extends LoggingActivity {

    public static final String EXTRA_RADIO_STATION_SEARCH_MODE = "EXTRA_RADIO_STATION_SEARCH_MODE";


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

    RecyclerView rvTopTags;
    TagCardAdapter tagAdapter;
    RadioBrowserRepository repo;

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

        rvTopTags = findViewById(R.id.rvTopTags);
        repo = new RadioBrowserRepository(
                this,
                /* discoverMirrors */ false,
                /* log level */ Var.HTTP_LOGGING_INTERCEPTOR_LOG_LEVEL
        );
        // Grid for tags
        int span = getResources().getInteger(R.integer.radio_grid_span); // reuse if you like
        if (span < 2) span = 2;
        GridLayoutManager glm = new GridLayoutManager(this, span);
        rvTopTags.setLayoutManager(glm);
        rvTopTags.addItemDecoration(
                new ViewHelper.SpacesItemDecoration(ViewHelper.dp(this, Var.GRID_LAYOUT_SPACER))
        );

        tagAdapter = new TagCardAdapter(tag -> {
            // Open results with this tag, carrying spinner-selected lang/country
            String lang2    = safeLang(spinnerLang);         // <- 2-letter (we fixed this)
            String country2 = safeCountry(spinnerCountry);   // <- implement same pattern or leave ""
            Intent i = new Intent(this, RadioResultsActivity.class)
                    .putExtra(EXTRA_RADIO_STATION_SEARCH_MODE, "MODE_TAG")
                    .putExtra("query", "")
                    .putExtra("lang", lang2)        // radio-browser expects 2-letter (lowercase ok)
                    .putExtra("country", country2)  // 2-letter country code (e.g., "FR")
                    .putExtra("tag", tag.name);
            startActivity(i);
            FirebaseAnalyticsHelper.tellAnalyticsRadioByTag(tag.name);
        });
        rvTopTags.setAdapter(tagAdapter);

        // Load top 18 tags
        repo.getTopTags(18, new Callback<>() {
            @Override public void onResponse(Call<List<TagItem>> call, Response<List<TagItem>> rsp) {
                if (rsp.isSuccessful() && rsp.body() != null) {
                    tagAdapter.setItems(rsp.body());
                } else {
                    myLogW("getTopTags: empty/unsuccessful");
                }
            }
            @Override public void onFailure(Call<List<TagItem>> call, Throwable t) {
                myLogEE(t, "getTopTags failed");
            }
        });

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
                Pref.get_Audio_Language_Radio(this),
                LanguageHelper.getRadioLanguages(),
                langItem -> Pref.set_Audio_Language_Radio(this, langItem.twoLetterCode),
                false
        );

        // ---- optional: country + tag spinners ----
        // If you have helpers, plug them here. For now we keep them optional:
        // spinnerCountry: entries like "FR", "US", "" (empty = any)
        // spinnerTag: popular tags ("news", "jazz", "talk", "", etc.)
        // If you don’t have adapters yet, leave them empty; we read their .toString() safely.

        buttonTrending.setOnClickListener(v -> {
            myLogI("--- User clicks TRENDING ---");
            // keep your existing lang/country/tag selections if you also filter later
            repo.topVoted(Option.getRadioApiNbResults(), new Callback<>() {
                @Override public void onResponse(Call<List<Station>> call, Response<List<Station>> rsp) {
                    if (rsp.isSuccessful() && rsp.body() != null) {
                        // e.g., open results screen with a “Top voted” header,
                        // or directly set items in a local RecyclerView if you have one here.
                        Intent i = new Intent(getApplicationContext(), RadioResultsActivity.class)
                                .putExtra(EXTRA_RADIO_STATION_SEARCH_MODE, "MODE_TRENDING");
                        startActivity(i);
                        FirebaseAnalyticsHelper.tellAnalyticsRadioTrending("", "", "", "");                    } else {
                        myToastE(getString(R.string.an_error_occurred));
                    }
                }
                @Override public void onFailure(Call<List<Station>> call, Throwable t) {
                    myLogEE(t, "topVoted failed");
                    myToastE(getString(R.string.an_error_occurred));
                }
            });
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
        query   = etRadio.getText();
        lang    = safeSpinnerStr(spinnerLang);
        country = safeSpinnerStr(spinnerCountry);
        tag     = safeSpinnerStr(spinnerTag);

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
