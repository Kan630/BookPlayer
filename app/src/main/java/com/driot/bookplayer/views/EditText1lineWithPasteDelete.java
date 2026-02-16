package com.driot.bookplayer.views;

import android.app.Activity;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ClipData;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatAutoCompleteTextView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.helpers.ViewHelper;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.KanLogger;
import com.driot.bookplayer.utils.log.LoggingLinearLayout;

import java.util.ArrayList;
import java.util.List;

public class EditText1lineWithPasteDelete extends LoggingLinearLayout {

    private AppCompatAutoCompleteTextView editText;

    // --- Knobs (defaults) ---
    private String historyKey = "default_search_history";
    private int maxHistory = 20;
    private int completionThreshold = 1;
    private boolean suggestOnFocus = false;

    private boolean scrollOnFocus = false; // custom to set in the layout

    private ArrayAdapter<String> adapter;

    public EditText1lineWithPasteDelete(Context context) {
        super(context);
        init(context, null);
    }

    public EditText1lineWithPasteDelete(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public EditText1lineWithPasteDelete(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        LayoutInflater.from(context).inflate(R.layout.view_edittext_with_btn_paste_delete, this, true);
        editText = findViewById(R.id.editText); // now an AutoCompleteTextView in XML

        // --- should we scroll down ---
        if (attrs != null) {
            try (TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.EditTextWithButtons)) {
                scrollOnFocus = a.getBoolean(R.styleable.EditTextWithButtons_scrollOnFocus, false);
            }
        }

        // --- Suggestions adapter from history ---
        List<String> history = com.driot.bookplayer.global.Pref.getSearchHistory(historyKey);
        adapter = new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line, new ArrayList<>(history));
        editText.setAdapter(adapter);
        editText.setThreshold(completionThreshold);

        editText.setOnFocusChangeListener((v, hasFocus) -> {
            // KanLogger.myLog("EditText1lineWithPasteDelete", "hasFocus = " + hasFocus + "
            // - scrollOnFocus = " + scrollOnFocus + " - suggestOnFocus = " +
            // suggestOnFocus);

            if (hasFocus && suggestOnFocus && TextUtils.isEmpty(editText.getText())) {
                editText.post(editText::showDropDown);
            }

            if (hasFocus && scrollOnFocus) {
                v.postDelayed(() -> {
                    ScrollView scroll = ((Activity) getContext()).findViewById(R.id.mainScroll);
                    if (scroll == null)
                        KanLogger.myLogE("no ScrollView with id [mainScroll] in xml");
                    if (scroll != null)
                        scroll.fullScroll(View.FOCUS_DOWN);
                }, 300);
            }
        });

        // Enter/IME action can commit to history too (if you use actionSearch)
        editText.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                saveCurrentTextToHistory();
            }
            return false; // let caller handle actual search
        });

        findViewById(R.id.btnPaste).setOnClickListener(v -> ViewHelper.pasteClipboard(context, editText));

        findViewById(R.id.btnClear).setOnClickListener(v -> editText.setText(""));
    }

    // --- Public API ---

    /** Call this when your search actually runs to persist the query. */
    public void saveCurrentTextToHistory() {
        String text = Tonio.cleanSearchString(getText());
        if (text.isEmpty())
            return;

        // Persist
        com.driot.bookplayer.global.Pref.addSearchHistory(historyKey, text, maxHistory);

        // Update adapter (move to front / dedupe)
        adapter.remove(text);
        adapter.insert(text, 0);
        adapter.notifyDataSetChanged();
    }

    /** Programmatically show suggestions. */
    public void showSuggestions() {
        editText.showDropDown();
    }

    public String getText() {
        return editText.getText() == null ? "" : editText.getText().toString();
    }

    public void setText(String text) {
        editText.setText(text);
        if (!TextUtils.isEmpty(text)) {
            editText.setSelection(text.length());
        }
    }

    public AppCompatAutoCompleteTextView getEditText() {
        return editText;
    }

    // --- Knob setters ---

    /**
     * Use a per-screen key to keep histories separate (e.g., "podcast_search",
     * "librivox_search").
     */
    public void setHistoryKey(String key) {
        if (TextUtils.isEmpty(key))
            return;
        this.historyKey = key;
        // reload list for the new key
        List<String> history = com.driot.bookplayer.global.Pref.getSearchHistory(historyKey);
        adapter.clear();
        adapter.addAll(history);
        adapter.notifyDataSetChanged();
    }

    /** Max number of items to keep in history (MRU). */
    public void setMaxHistory(int max) {
        this.maxHistory = Math.max(1, max);
    }

    /** How many characters before suggestions kick in. 1 feels natural. */
    public void setCompletionThreshold(int threshold) {
        this.completionThreshold = Math.max(0, threshold);
        editText.setThreshold(this.completionThreshold);
    }

    /** Show dropdown automatically on focus when field is empty. */
    public void setSuggestOnFocus(boolean suggest) {
        this.suggestOnFocus = suggest;
    }

    /** Clear the stored history for this key. */
    public void clearHistory() {
        com.driot.bookplayer.global.Pref.clearSearchHistory(historyKey);
        adapter.clear();
        adapter.notifyDataSetChanged();
    }
}
