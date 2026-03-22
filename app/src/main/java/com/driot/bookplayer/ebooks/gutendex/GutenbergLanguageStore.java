package com.driot.bookplayer.ebooks.gutendex;

import android.content.Context;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;

import androidx.annotation.Keep;
import androidx.annotation.RawRes;

import com.driot.bookplayer.R;
import com.driot.bookplayer.librivox.LanguageMapper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.text.NumberFormat;
import java.util.Locale;

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

public class GutenbergLanguageStore {
    
    private static final String CACHE_FILENAME = "gutenberg_languages_cache.json";

    private final Context context;

    public GutenbergLanguageStore(Context context) {
        this.context = context.getApplicationContext();
    }

    public List<GutenbergLanguageItem> loadLanguages(@RawRes int rawRes) {
        try {
            List<RawLang> rawList = null;

            // 1) Try to load from cache first
            File cacheFile = new File(context.getFilesDir(), CACHE_FILENAME);
            if (cacheFile.exists()) {
                try (FileInputStream fis = new FileInputStream(cacheFile);
                     InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
                    rawList = new Gson().fromJson(isr, new TypeToken<List<RawLang>>() {
                    }.getType());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // 2) Fallback to raw resource if no cache or error
            if (rawList == null) {
                try (InputStream is = context.getResources().openRawResource(rawRes);
                     InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    rawList = new Gson().fromJson(isr, new TypeToken<List<RawLang>>() {
                    }.getType());
                }
            }

            if (rawList == null)
                return new ArrayList<>();

            List<GutenbergLanguageItem> result = new ArrayList<>();
            for (RawLang r : rawList) {
                LanguageMapper.Mapping m = LanguageMapper.getMapping(r.lang_en);
                // Use mapping if available, otherwise use codes from JSON
                String code2 = (m.twoLetterCode != null && !m.twoLetterCode.isEmpty()) 
                        ? m.twoLetterCode 
                        : (r.code2 != null ? r.code2 : "");
                String code3 = (m.threeLetterCode != null && !m.threeLetterCode.isEmpty()) 
                        ? m.threeLetterCode 
                        : (r.code3 != null ? r.code3 : "");
                // If mapping is fallback (empty codes), use no_flag for unknown languages
                // Otherwise use the mapper's flag (even if it's flag_globe for legitimate cases like multilingual)
                boolean isFallback = (m.twoLetterCode == null || m.twoLetterCode.isEmpty()) 
                        && (m.threeLetterCode == null || m.threeLetterCode.isEmpty());
                int flagRes;
                if (isFallback) {
                    // Unknown language - use no_flag instead of flag_globe
                    flagRes = R.drawable.no_flag;
                } else {
                    // Known language - use mapper's flag (or no_flag if flagRes is 0)
                    flagRes = m.flagRes != 0 ? m.flagRes : R.drawable.no_flag;
                }
                
                String bookCount = r.book_count != null ? r.book_count : "+0";
                
                result.add(new GutenbergLanguageItem(
                        r.lang_en,
                        r.lang_native_alphabet,
                        code2,
                        code3,
                        flagRes,
                        bookCount
                ));
            }
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public void updateLanguageCompletedCount(String code, int count) {
        if (code == null || code.isEmpty())
            return;

        // Load current (possibly cached) list
        List<GutenbergLanguageItem> currentItems = loadLanguages(com.driot.bookplayer.R.raw.gutenberg_languages);
        List<RawLang> rawList = new ArrayList<>();
        boolean modified = false;

        String displayCount;
        if (count >= 0) {
            displayCount = NumberFormat.getNumberInstance(Locale.getDefault()).format(count);
        } else {
            displayCount = "+0";
        }

        for (GutenbergLanguageItem item : currentItems) {
            RawLang r = new RawLang();
            r.lang_en = item.name;
            r.lang_native_alphabet = item.nativeName;
            r.code2 = item.code2;
            r.code3 = item.code3;
            r.book_count = item.bookCount;

            // Match by code2 or code3
            if (code.equalsIgnoreCase(item.code2) || code.equalsIgnoreCase(item.code3)) {
                if (!displayCount.equals(r.book_count)) {
                    r.book_count = displayCount;
                    modified = true;
                }
            }
            rawList.add(r);
        }

        if (modified) {
            saveToCache(rawList);
        }
    }

    private void saveToCache(List<RawLang> list) {
        File cacheFile = new File(context.getFilesDir(), CACHE_FILENAME);
        try (FileOutputStream fos = new FileOutputStream(cacheFile);
             OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            new Gson().toJson(list, osw);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Helper class to parse JSON
    @Keep
    private static class RawLang {
        String lang_en;
        String lang_native_alphabet;
        String code2;
        String code3;
        String book_count; // e.g., "+50", "+0", or number
    }

    public interface OnLanguageSelected {
        void onLanguageSelected(GutenbergLanguageItem lang);
    }

    public static void setupLanguageSpinner(
            Context context,
            Spinner spinner,
            String selectedLangCode,
            List<GutenbergLanguageItem> items,
            GutenbergLanguageStore.OnLanguageSelected callback
    ) {
        GutenbergLanguageSpinnerAdapter adapter = new GutenbergLanguageSpinnerAdapter(context, items);
        spinner.setAdapter(adapter);

        int selectedPosition = 0;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).code2 != null && items.get(i).code2.equals(selectedLangCode)) {
                selectedPosition = i;
                break;
            }
        }
        spinner.setSelection(selectedPosition);

        // Store reference to items list for callback
        final List<GutenbergLanguageItem> itemsRef = items;
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < itemsRef.size()) {
                    GutenbergLanguageItem selected = itemsRef.get(position);
                    callback.onLanguageSelected(selected);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }
}
