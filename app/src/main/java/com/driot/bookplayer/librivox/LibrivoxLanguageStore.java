package com.driot.bookplayer.librivox;

import android.content.Context;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;

import androidx.annotation.RawRes;

import com.driot.bookplayer.helpers.LanguageHelper;
import com.driot.bookplayer.objects.LanguageItem;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class LibrivoxLanguageStore {

    private final Context context;

    public LibrivoxLanguageStore(Context context) {
        this.context = context.getApplicationContext();
    }

    public List<LibrivoxLanguageItem> loadLanguages(@RawRes int rawRes) {
        try {
            InputStream is = context.getResources().openRawResource(rawRes);
            List<RawLang> rawList = new Gson().fromJson(new InputStreamReader(is),
                    new TypeToken<List<RawLang>>() {}.getType());

            List<LibrivoxLanguageItem> result = new ArrayList<>();
            for (RawLang r : rawList) {
                LibrivoxLanguageMapper.Mapping m = LibrivoxLanguageMapper.getMapping(r.lang_en);
                result.add(new LibrivoxLanguageItem(
                        r.lang_en,
                        r.lang_native_alphabet,
                        m.twoLetterCode,
                        m.threeLetterCode,
                        m.flagRes,
                        r.completed
                ));

            }
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // helper class to parse JSON
    private static class RawLang {
        String lang_en;
        String lang_native_alphabet;
        int completed;
        int in_progress;
    }

    public interface OnLanguageSelected {
        void onLanguageSelected(LibrivoxLanguageItem lang);
    }

    public static void setupLanguageSpinner(
            Context context,
            Spinner spinner,
            String selectedLangCode,
            List<LibrivoxLanguageItem> items,
            LibrivoxLanguageStore.OnLanguageSelected callback
    ) {
        LibrivoxLanguageSpinnerAdapter adapter = new LibrivoxLanguageSpinnerAdapter(context, items);
        spinner.setAdapter(adapter);

        int selectedPosition = 0;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).name.equals(selectedLangCode)) {
                selectedPosition = i;
                break;
            }
        }
        spinner.setSelection(selectedPosition);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                LibrivoxLanguageItem selected = (LibrivoxLanguageItem) parent.getItemAtPosition(position);
                callback.onLanguageSelected(selected);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }
}
