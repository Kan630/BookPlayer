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
import com.driot.bookplayer.ebooks.gutendex.GutenbergLanguageItem;
import com.driot.bookplayer.ebooks.gutendex.GutenbergLanguageStore;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.nav.FullActivity;
import com.driot.bookplayer.settings.ui.TtsSettingsFragment;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.views.EditText1lineWithSearch;

import dagger.hilt.android.AndroidEntryPoint;

import java.util.List;

@AndroidEntryPoint
public class GetEbookActivity extends FullActivity {

    Spinner spinnerEbookLang;
    EditText1lineWithSearch editTextEbook;
    Button bEbookMostDownloaded;
    Button bEbookBookshelves;

    String query, lang;

    @Override
    protected int getNavSectionId() {
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
        bEbookMostDownloaded = findViewById(R.id.bEbookMostDownloaded);
        bEbookBookshelves = findViewById(R.id.bEbookBookshelves);

        findViewById(R.id.ibSettings).setOnClickListener(v -> clickSettings());

        editTextEbook.setHistoryKey("ebook_search");
        editTextEbook.setCompletionThreshold(1);
        editTextEbook.setSuggestOnFocus(true);

        refreshLanguageSpinner();

        bEbookMostDownloaded.setOnClickListener(v -> {
            myLogI("--- User clicks MOST DOWNLOADED ---");
            if (!NetworkHelper.isConnected(this)) {
                myToastE(getString(R.string.no_internet_connection));
                return;
            }
            query = "";
            doSearch();
        });

        bEbookBookshelves.setOnClickListener(v -> {
            myLogI("--- User clicks BOOKSHELVES ---");
            GutenbergLanguageItem selected = (GutenbergLanguageItem) spinnerEbookLang.getSelectedItem();
            if (selected == null) {
                myToast(getString(com.driot.bookplayer.R.string.selected_language_error));
                return;
            }
            String selectedLang = getGutendexLanguageCode(selected);
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
        // Get selected language from spinner
        if (!NetworkHelper.isConnected(this)) {
            myToastE(getString(R.string.no_internet_connection));
            return;
        }
        GutenbergLanguageItem selected = (GutenbergLanguageItem) spinnerEbookLang.getSelectedItem();
        if (selected == null) {
            myToast(getString(com.driot.bookplayer.R.string.selected_language_error));
            return;
        }
        lang = getGutendexLanguageCode(selected);
        if (lang == null || lang.isEmpty()) {
            myToast(getString(com.driot.bookplayer.R.string.selected_language_error));
            return;
        }
        FirebaseAnalyticsHelper.tellAnalyticsGutendexSearch(query, lang);

        openEbookResultsActivity();
    }

    /**
     * Get the appropriate language code for Gutendex API.
     * Some languages need code3 instead of code2 for Gutendex API.
     */
    private String getGutendexLanguageCode(GutenbergLanguageItem langItem) {
        if (langItem == null) return null;
        
        // Special case: Scottish Gaelic - Gutendex uses 'gla' (code3) instead of 'gd' (code2)
        if ("gd".equals(langItem.code2) && langItem.code3 != null && !langItem.code3.isEmpty()) {
            return langItem.code3; // Use 'gla' for Scottish Gaelic
        }
        
        // Use code2 if available, otherwise fall back to code3
        return langItem.code2 != null && !langItem.code2.isEmpty() 
                ? langItem.code2 
                : langItem.code3;
    }

    private void openEbookResultsActivity() {
        Intent intent = new Intent(this, EbookResultsActivity.class);
        intent.putExtra("query", query);
        intent.putExtra("lang", lang);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshLanguageSpinner();
    }

    private void refreshLanguageSpinner() {
        if (spinnerEbookLang == null)
            return;
        GutenbergLanguageStore store = new GutenbergLanguageStore(this);
        List<GutenbergLanguageItem> gutenbergLanguages = store.loadLanguages(R.raw.gutenberg_languages);

        GutenbergLanguageStore.setupLanguageSpinner(
                this,
                spinnerEbookLang,
                Pref.get_Audio_Language_Ebook(this),
                gutenbergLanguages,
                langItem -> Pref.set_Audio_Language_Ebook(this, langItem.code2));
    }

    private void clickSettings() {
        myLogI("--- User clicks SETTINGS ---");
        SettingsHostActivity.start(this, TtsSettingsFragment.class, true, R.string.tts_settings);
    }

}
