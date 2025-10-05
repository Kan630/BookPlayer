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
import com.driot.bookplayer.utils.log.LoggingActivity;
import com.driot.bookplayer.views.EditTextWithButtons;

public class GetAudiobookActivity extends LoggingActivity {

    Spinner spinnerLibrivox;
    EditTextWithButtons editTextLibrivox;
    Button bFavorite;
    ImageButton ibFavorite;
    ImageButton ibSettings;
    Button buttonTrending;
    Button buttonSearch;

    String query, lang;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_get_audiobook);
        InsetHelper.apply(this);

        OngoingTaskHost.attach(
                this,
                R.id.topOverlayContainer,
                new Intent(this, AddResourceActivity.class)
        );

        buttonTrending = findViewById(R.id.bLibrivoxTrending);
        spinnerLibrivox = findViewById(R.id.spinnerLibrivox);
        editTextLibrivox = findViewById(R.id.etLibrivox);
        buttonSearch = findViewById(R.id.bLibrivoxSearch);
        bFavorite = findViewById(R.id.bFavorite);
        ibFavorite = findViewById(R.id.ibFavorite);
        ibSettings = findViewById(R.id.ibSettings);

        bFavorite.setOnClickListener(v -> clickFavorite());
        ibFavorite.setOnClickListener(v -> clickFavorite());
        ibSettings.setOnClickListener(v -> clickSettings());

        editTextLibrivox.setHistoryKey("librivox_search"); // keep histories separate
        editTextLibrivox.setCompletionThreshold(1);        // suggestions after 1 char
        editTextLibrivox.setSuggestOnFocus(true);          // show dropdown on focus if empty

        LanguageHelper.setupLanguageSpinner(
                this,
                spinnerLibrivox,
                Pref.get_Audio_Language_Librivox(this),
                LanguageHelper.getLibrivoxLanguages(),
                lang -> Pref.set_Audio_Language_Librivox(this, lang.threeLetterCode),
                true
        );

        buttonTrending.setOnClickListener(v -> {
            myLogI("--- User clicks TRENDING ---");
            query = "";
            lang = spinnerLibrivox.getSelectedItem().toString().toLowerCase();
            if (lang.isEmpty()) {
                myToast("selected language error");
                return;
            }

            openLibrivoxResultsActivity();
            FirebaseAnalyticsHelper.tellAnalyticsLibrivoxTrending(query, lang);
        });

        buttonSearch.setOnClickListener(v -> {
            myLogI("--- User clicks SEARCH ---");
            editTextLibrivox.saveCurrentTextToHistory();
            doSearch();
        });
        editTextLibrivox.getEditText().setOnEditorActionListener((v, actionId, event) -> {
            boolean isEnterKey = event != null
                    && event.getAction() == android.view.KeyEvent.ACTION_DOWN
                    && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER;

            if (actionId == EditorInfo.IME_ACTION_SEARCH || isEnterKey) {
                myLogI("--- User clicks SEARCH --- (via keyboard)");

                // 1) persist the query to MRU history
                editTextLibrivox.saveCurrentTextToHistory();

                // 2) run your existing search
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
        Intent intent = new Intent(this, LibrivoxSettingsActivity.class);
        startActivity(intent);
    }

    private void doSearch() {
            query = editTextLibrivox.getText();
            lang = spinnerLibrivox.getSelectedItem().toString().toLowerCase();

            if (lang.isEmpty()) {
                myToast("selected language error");
                return;
            }

            openLibrivoxResultsActivity();

            FirebaseAnalyticsHelper.tellAnalyticsLibrivoxSearch(query, lang);
    }

    private void openLibrivoxResultsActivity() {
        Intent intent = new Intent(this, LibrivoxResultsActivity.class);
        intent.putExtra("query", query);
        intent.putExtra("lang", lang);
        startActivity(intent);
    }


}
