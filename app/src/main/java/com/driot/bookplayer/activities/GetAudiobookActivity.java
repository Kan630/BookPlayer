package com.driot.bookplayer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.helpers.AnalyticsHelper;
import com.driot.bookplayer.utils.LanguageHelper;
import com.driot.bookplayer.utils.log.LoggingActivity;
import com.driot.bookplayer.views.EditTextWithButtons;

public class GetAudiobookActivity extends LoggingActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_get_audiobook);

        ////////////////////////////////
        /// // LIBRIVOX SEARCH
        ////////////////////////////////
        Spinner spinnerLibrivox = findViewById(R.id.spinnerLibrivox);
        LanguageHelper.setupLanguageSpinner(
                this,
                spinnerLibrivox,
                Pref.get_Audio_Language_Librivox(this),
                LanguageHelper.getLibrivoxLanguages(),
                lang -> Pref.set_Audio_Language_Librivox(this, lang.threeLetterCode),
                true
        );
        EditText editTextQuery;
        Button buttonSearch;
        EditTextWithButtons editTextLibrivox = findViewById(R.id.etLibrivox);
        buttonSearch = findViewById(R.id.bLibrivoxSearch);
        buttonSearch.setOnClickListener(v -> {
            myLogI("--- User clicks SEARCH");
            String query = editTextLibrivox.getText();
            String lang = spinnerLibrivox.getSelectedItem().toString().toLowerCase();

            if (lang.isEmpty()) {
                myToast("selected language error");
                return;
            }

            Intent intent = new Intent(this, LibrivoxResultsActivity.class);
            intent.putExtra("query", query);
            intent.putExtra("lang", lang);
            startActivity(intent);

            AnalyticsHelper.tellAnalyticsLibrivoxSearch(this, query, lang);
        });

        Button buttonTrending;
        buttonTrending = findViewById(R.id.bLibrivoxTrending);
        buttonTrending.setOnClickListener(v -> {
            myLogI("--- User clicks MOST DOWNLOADED");
            String query = "";
            String lang = spinnerLibrivox.getSelectedItem().toString().toLowerCase();

            if (lang.isEmpty()) {
                myToast("selected language error");
                return;
            }

            Intent intent = new Intent(this, LibrivoxResultsActivity.class);
            intent.putExtra("query", query);
            intent.putExtra("lang", lang);
            startActivity(intent);

            AnalyticsHelper.tellAnalyticsLibrivoxSearch(this, query, lang);
        });


        ////////////////////////////////
        ////////////////////////////////

    }
}
