package com.driot.bookplayer.librivox;

import android.content.Context;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;

import androidx.annotation.Keep;
import androidx.annotation.RawRes;

import com.driot.bookplayer.utils.log.LoggerHelper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class LibrivoxLanguageStore extends LoggerHelper {

    private final Context context;
    private static final String CACHE_FILENAME = "librivox_languages_cache.json";

    public LibrivoxLanguageStore(Context context) {
        super(LibrivoxLanguageStore.class);
        this.context = context.getApplicationContext();
    }

    public List<LibrivoxLanguageItem> loadLanguages(@RawRes int rawRes) {
        try {
            List<RawLang> rawList = null;

            // 1) Try to load from cache first
            File cacheFile = new File(context.getFilesDir(), CACHE_FILENAME);
            if (cacheFile.exists()) {
                try (FileInputStream fis = new FileInputStream(cacheFile);
                        InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
                    rawList = new Gson().fromJson(isr, new TypeToken<List<RawLang>>() {
                    }.getType());
                    myLogD("loaded from cache");
                } catch (Exception e) {
                    myLogEE(e, "load Languages from cache");
                }
            }

            // 2) Fallback to raw resource if no cache or error
            if (rawList == null) {
                try (InputStream is = context.getResources().openRawResource(rawRes);
                        InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    rawList = new Gson().fromJson(isr, new TypeToken<List<RawLang>>() {
                    }.getType());
                }
                myLogD("loaded from raw");
            }

            if (rawList == null)
                return new ArrayList<>();

            List<LibrivoxLanguageItem> result = new ArrayList<>();
            for (RawLang r : rawList) {
                LanguageMapper.Mapping m = LanguageMapper.getMapping(r.lang_en);
                result.add(new LibrivoxLanguageItem(
                        r.lang_en,
                        r.lang_native_alphabet,
                        m.twoLetterCode,
                        m.threeLetterCode,
                        m.flagRes,
                        r.completed));

            }
            return result;
        } catch (Exception e) {
            myLogEE(e, "loadLanguages");
            return new ArrayList<>();
        }
    }

    public void updateLanguageCompletedCount(String code3, int count) {
        if (code3 == null || code3.isEmpty())
            return;

        // Load current (possibly cached) list
        // Note: we use -1 or similar for rawRes if we only care about cache here,
        // but it's safer to just load the usual way.
        // We need a raw res ID for backup. R.raw.librivox_languages is the standard
        // one.
        List<LibrivoxLanguageItem> currentItems = loadLanguages(com.driot.bookplayer.R.raw.librivox_languages);

        // Convert back to RawLang for saving (to keep it clean)
        List<RawLang> rawList = new ArrayList<>();
        boolean modified = false;

        for (LibrivoxLanguageItem item : currentItems) {
            RawLang r = new RawLang();
            r.lang_en = item.name;
            r.lang_native_alphabet = item.nativeName;
            r.completed = item.completed;

            if (code3.equalsIgnoreCase(item.code3)) {
                if (r.completed != count) {
                    r.completed = count;
                    modified = true;
                }
            }
            rawList.add(r);
        }

        if (modified) {
            myLogD("updating cache with new value : [" + count + "] - for lang [" + code3 + "]");
            saveToCache(rawList);
        }
    }

    private void saveToCache(List<RawLang> list) {
        File cacheFile = new File(context.getFilesDir(), CACHE_FILENAME);
        try (FileOutputStream fos = new FileOutputStream(cacheFile);
                OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            new Gson().toJson(list, osw);
            myLogD("saved to cache");
        } catch (IOException e) {
            myLogEE(e, "saveToCache");
        }
    }

    // helper class to parse JSON
    @Keep
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
            LibrivoxLanguageStore.OnLanguageSelected callback) {
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
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }
}
