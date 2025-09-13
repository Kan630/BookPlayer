package com.driot.bookplayer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.Spinner;

import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.helpers.LanguageHelper;
import com.driot.bookplayer.utils.log.LoggingActivity;
import com.driot.bookplayer.views.EditTextWithButtons;

public class GetAudiobookActivity extends LoggingActivity {

    Spinner spinnerLibrivox;
    EditTextWithButtons editTextLibrivox;
    Button buttonTrending;
    Button buttonSearch;

    String query, lang;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_get_audiobook);

        buttonTrending = findViewById(R.id.bLibrivoxTrending);
        spinnerLibrivox = findViewById(R.id.spinnerLibrivox);
        editTextLibrivox = findViewById(R.id.etLibrivox);
        buttonSearch = findViewById(R.id.bLibrivoxSearch);

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
            FirebaseAnalyticsHelper.tellAnalyticsLibrivoxTrending(this, query, lang);
        });

        buttonSearch.setOnClickListener(v -> {
            myLogI("--- User clicks SEARCH ---");
            doSearch();
        });
        // Keyboard "done/search"
        editTextLibrivox.getEditText().setOnEditorActionListener((v, actionId, event) -> {
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

    private void doSearch() {
            query = editTextLibrivox.getText();
            lang = spinnerLibrivox.getSelectedItem().toString().toLowerCase();

            if (lang.isEmpty()) {
                myToast("selected language error");
                return;
            }

            openLibrivoxResultsActivity();

            FirebaseAnalyticsHelper.tellAnalyticsLibrivoxSearch(this, query, lang);
    }

    private void openLibrivoxResultsActivity() {
        Intent intent = new Intent(this, LibrivoxResultsActivity.class);
        intent.putExtra("query", query);
        intent.putExtra("lang", lang);
        startActivity(intent);
    }


}
