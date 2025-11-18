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
import com.driot.bookplayer.settings.ui.LibrivoxSettingsFragment;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.views.EditText1lineWithPasteDelete;

public class GetAudiobookActivity extends BaseBottomNavActivity {

    Spinner spinnerLibrivox;
    EditText1lineWithPasteDelete editTextLibrivox;
    Button bFavorite;
    ImageButton ibFavorite;
    Button buttonTrending;
    Button buttonSearch;

    String query, lang;


    @Override protected int getNavId() { return R.id.nav_add; }
    @Override protected int getLayoutResId() { return R.layout.activity_get_audiobook; }
    @Override protected boolean enableOngoingTaskOverlay() { return true; }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        buttonTrending = findViewById(R.id.bLibrivoxTrending);
        spinnerLibrivox = findViewById(R.id.spinnerLibrivox);
        editTextLibrivox = findViewById(R.id.etLibrivox);
        buttonSearch = findViewById(R.id.bLibrivoxSearch);
        bFavorite = findViewById(R.id.bFavorite);
        ibFavorite = findViewById(R.id.ibFavorite);

        bFavorite.setOnClickListener(v -> clickFavorite());
        ibFavorite.setOnClickListener(v -> clickFavorite());
        findViewById(R.id.ibSettings).setOnClickListener(v -> clickSettings());

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
            doSearch();
        });
        editTextLibrivox.getEditText().setOnEditorActionListener((v, actionId, event) -> {
            boolean isEnterKey = event != null
                    && event.getAction() == android.view.KeyEvent.ACTION_DOWN
                    && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER;

            if (actionId == EditorInfo.IME_ACTION_SEARCH || isEnterKey) {
                myLogI("--- User clicks SEARCH --- (via keyboard)");

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
        SettingsHostActivity.start(this, LibrivoxSettingsFragment.class, true, R.string.librivox_settings);
    }

    private void doSearch() {
            query = Tonio.cleanSearchString(editTextLibrivox.getText());
            editTextLibrivox.saveCurrentTextToHistory();
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
