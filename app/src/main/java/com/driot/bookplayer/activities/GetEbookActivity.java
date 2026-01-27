package com.driot.bookplayer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.Spinner;

import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.ebooks.gutendex.GetEbookBookshelfListActivity;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.LanguageHelper;
import com.driot.bookplayer.objects.LanguageItem;
import com.driot.bookplayer.settings.ui.TtsSettingsFragment;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.views.EditText1lineWithSearch;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class GetEbookActivity extends BaseBottomNavActivity {

    Spinner spinnerEbookLang;
    EditText1lineWithSearch editTextEbook;
    Button bMostDownloaded;
    Button bBookshelves;

    String query, lang;

    @Override
    protected int getNavId() {
        return R.id.nav_add;
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_get_ebook;
    }

    @Override
    protected boolean enableOngoingTaskOverlay() {
        return true;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        spinnerEbookLang = findViewById(R.id.spinnerEbookLang);
        editTextEbook = findViewById(R.id.etEbook);
        bMostDownloaded = findViewById(R.id.bMostDownloaded);
        bBookshelves = findViewById(R.id.bBookshelves);

        findViewById(R.id.ibSettings).setOnClickListener(v -> clickSettings());

        editTextEbook.setHistoryKey("ebook_search");
        editTextEbook.setCompletionThreshold(1);
        editTextEbook.setSuggestOnFocus(true);

        // Reuse Librivox language pref & list for now (you can make a dedicated one
        // later)
        LanguageHelper.setupLanguageSpinner(
                this,
                spinnerEbookLang,
                Pref.get_Audio_Language_Ebook(this),
                LanguageHelper.getLibrivoxLanguages(),
                langItem -> Pref.set_Audio_Language_Ebook(this, langItem.twoLetterCode),
                false);

        bMostDownloaded.setOnClickListener(v -> {
            myLogI("--- User clicks MOST DOWNLOADED ---");
            query = "";
            doSearch();
        });

        bBookshelves.setOnClickListener(v -> {
            myLogI("--- User clicks BOOKSHELVES ---");
            LanguageItem selected = (LanguageItem) spinnerEbookLang.getSelectedItem();
            String selectedLang = selected != null ? selected.twoLetterCode : "";
            if (selectedLang == null || selectedLang.isEmpty()) {
                myToast(getString(com.driot.bookplayer.R.string.selected_language_error));
                return;
            }
            GetEbookBookshelfListActivity.start(this, selectedLang);
        });

        editTextEbook.getSearchButton().setOnClickListener(v -> {
            myLogI("--- User clicks EBOOK SEARCH ---");
            query = Tonio.cleanSearchString(editTextEbook.getText());
            editTextEbook.saveCurrentTextToHistory();
            doSearch();
        });

        editTextEbook.getEditText().setOnEditorActionListener((v, actionId, event) -> {
            boolean isEnterKey = event != null
                    && event.getAction() == android.view.KeyEvent.ACTION_DOWN
                    && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER;

            if (actionId == EditorInfo.IME_ACTION_SEARCH || isEnterKey) {
                myLogI("--- User clicks EBOOK SEARCH --- (via keyboard)");
                query = Tonio.cleanSearchString(editTextEbook.getText());
                editTextEbook.saveCurrentTextToHistory();
                doSearch();
                editTextEbook.getEditText().dismissDropDown();
                return true;
            }
            return false;
        });
    }

    private void doSearch() {
        // Here we expect spinner entries like "en", "fr", ...
        LanguageItem selected = (LanguageItem) spinnerEbookLang.getSelectedItem();
        lang = selected.twoLetterCode; // "en", "fr", etc.
        if (lang == null)
            lang = "";

        if (lang.isEmpty()) {
            myToast(getString(com.driot.bookplayer.R.string.selected_language_error));
            return;
        }
        FirebaseAnalyticsHelper.tellAnalyticsGutendexSearch(query, lang);

        openEbookResultsActivity();
    }

    private void openEbookResultsActivity() {
        Intent intent = new Intent(this, EbookResultsActivity.class);
        intent.putExtra("query", query);
        intent.putExtra("lang", lang);
        startActivity(intent);
    }

    private void clickSettings() {
        myLogI("--- User clicks SETTINGS ---");
        SettingsHostActivity.start(this, TtsSettingsFragment.class, true, R.string.tts_settings);
    }

}
