package com.driot.bookplayer.activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.Navigation;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.ebooks.gutendex.GutenbergLanguageItem;
import com.driot.bookplayer.ebooks.gutendex.GutenbergLanguageStore;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.settings.ui.TtsSettingsFragment;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingFragment;
import com.driot.bookplayer.views.EditText1lineWithSearch;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class GetEbookFragment extends LoggingFragment {

    Spinner spinnerEbookLang;
    EditText1lineWithSearch editTextEbook;
    Button bEbookMostDownloaded;
    Button bEbookBookshelves;

    String query, lang;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_get_ebook, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        spinnerEbookLang = view.findViewById(R.id.spinnerEbookLang);
        editTextEbook = view.findViewById(R.id.etEbook);
        bEbookMostDownloaded = view.findViewById(R.id.bEbookMostDownloaded);
        bEbookBookshelves = view.findViewById(R.id.bEbookBookshelves);

        view.findViewById(R.id.ibSettings).setOnClickListener(v -> clickSettings());

        editTextEbook.setHistoryKey("ebook_search");
        editTextEbook.setCompletionThreshold(1);
        editTextEbook.setSuggestOnFocus(true);

        refreshLanguageSpinner();

        bEbookMostDownloaded.setOnClickListener(v -> {
            myLogI("--- User clicks MOST DOWNLOADED ---");
            if (!NetworkHelper.isConnected(requireContext())) {
                myToastE(getString(R.string.no_internet_connection));
                return;
            }
            query = "";
            doSearch(view);
        });

        bEbookBookshelves.setOnClickListener(v -> {
            myLogI("--- User clicks BOOKSHELVES ---");
            GutenbergLanguageItem selected = (GutenbergLanguageItem) spinnerEbookLang.getSelectedItem();
            if (selected == null) {
                myToast(getString(R.string.selected_language_error));
                return;
            }
            String selectedLang = getGutendexLanguageCode(selected);
            if (selectedLang == null || selectedLang.isEmpty()) {
                myToast(getString(R.string.selected_language_error));
                return;
            }
            Bundle args = new Bundle();
            args.putString("lang", selectedLang);
            Navigation.findNavController(view).navigate(R.id.getEbookBookshelfListFragment, args);
        });

        editTextEbook.getSearchButton().setOnClickListener(v -> {
            myLogI("--- User clicks EBOOK SEARCH ---");
            query = Tonio.cleanSearchString(editTextEbook.getText());
            editTextEbook.saveCurrentTextToHistory();
            doSearch(view);
        });

        editTextEbook.getEditText().setOnEditorActionListener((v, actionId, event) -> {
            boolean isEnterKey = event != null
                    && event.getAction() == android.view.KeyEvent.ACTION_DOWN
                    && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER;

            if (actionId == EditorInfo.IME_ACTION_SEARCH || isEnterKey) {
                myLogI("--- User clicks EBOOK SEARCH --- (via keyboard)");
                query = Tonio.cleanSearchString(editTextEbook.getText());
                editTextEbook.saveCurrentTextToHistory();
                doSearch(view);
                editTextEbook.getEditText().dismissDropDown();
                return true;
            }
            return false;
        });
    }

    private void doSearch(View view) {
        // Get selected language from spinner
        if (!NetworkHelper.isConnected(requireContext())) {
            myToastE(getString(R.string.no_internet_connection));
            return;
        }
        GutenbergLanguageItem selected = (GutenbergLanguageItem) spinnerEbookLang.getSelectedItem();
        if (selected == null) {
            myToast(getString(R.string.selected_language_error));
            return;
        }
        lang = getGutendexLanguageCode(selected);
        if (lang == null || lang.isEmpty()) {
            myToast(getString(R.string.selected_language_error));
            return;
        }
        FirebaseAnalyticsHelper.tellAnalyticsGutendexSearch(query, lang);

        openEbookResultsActivity(view);
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

    private void openEbookResultsActivity(View view) {
        Bundle args = new Bundle();
        args.putString("query", query);
        args.putString("lang", lang);
        Navigation.findNavController(view).navigate(R.id.ebookResultsFragment, args);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshLanguageSpinner();
    }

    private void refreshLanguageSpinner() {
        if (spinnerEbookLang == null)
            return;
        GutenbergLanguageStore store = new GutenbergLanguageStore(requireContext());
        java.util.List<GutenbergLanguageItem> gutenbergLanguages = store.loadLanguages(R.raw.gutenberg_languages);

        GutenbergLanguageStore.setupLanguageSpinner(
                requireContext(),
                spinnerEbookLang,
                Pref.get_Audio_Language_Ebook(requireContext()),
                gutenbergLanguages,
                langItem -> Pref.set_Audio_Language_Ebook(requireContext(), langItem.code2));
    }

    private void clickSettings() {
        myLogI("--- User clicks SETTINGS ---");
        SettingsHostActivity.start(requireContext(), TtsSettingsFragment.class, true, R.string.tts_settings);
    }

}
