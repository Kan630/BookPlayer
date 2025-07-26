package com.driot.bookplayer.utils;

import android.content.Context;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;

import com.driot.bookplayer.R;
import com.driot.bookplayer.adapter.LanguageSpinnerAdapter;
import com.driot.bookplayer.objects.LanguageItem;

import java.util.ArrayList;
import java.util.List;

public class LanguageHelper {

    public static final List<LanguageItem> SUPPORTED_LANGUAGES;

    static {
        List<LanguageItem> list = new ArrayList<>();
        list.add(new LanguageItem("eng", "en", "English", R.drawable.flag_uk));
        list.add(new LanguageItem("deu", "de", "Deutsch", R.drawable.flag_de));
        list.add(new LanguageItem("spa", "es", "Español", R.drawable.flag_es));
        list.add(new LanguageItem("fre", "fr", "Français", R.drawable.flag_fr));
        list.add(new LanguageItem("por", "pt", "Português", R.drawable.flag_pt));
        list.add(new LanguageItem("ita", "it", "Italiano", R.drawable.flag_it));
        list.add(new LanguageItem("rus", "ru", "Русский", R.drawable.flag_ru));
        list.add(new LanguageItem("zho", "zh", "中文", R.drawable.flag_cn));
        list.add(new LanguageItem("ara", "ar", "العربية", R.drawable.flag_sa));
        list.add(new LanguageItem("jpn", "ja", "日本語", R.drawable.flag_jp));
        list.add(new LanguageItem("hin", "hi", "हिन्दी", R.drawable.flag_in));
        list.add(new LanguageItem("ell", "el", "Ελληνικά", R.drawable.flag_gr));
        list.add(new LanguageItem("heb", "he", "עברית", R.drawable.flag_il));
        list.add(new LanguageItem("swe", "sv", "Svenska", R.drawable.flag_se));
        list.add(new LanguageItem("pol", "pl", "Polski", R.drawable.flag_pl));
        list.add(new LanguageItem("nld", "nl", "Nederlands", R.drawable.flag_nl));
        list.add(new LanguageItem("mul", "", "Multiple", R.drawable.flag_globe));
        SUPPORTED_LANGUAGES = list;
    }

    public static List<LanguageItem> getLibrivoxLanguages() {
        return SUPPORTED_LANGUAGES;
    }

    public static List<LanguageItem> getPodcastLanguages() {
        return SUPPORTED_LANGUAGES;
    }

    public interface OnLanguageSelected {
        void onLanguageSelected(LanguageItem lang);
    }

    public static void setupLanguageSpinner(
            Context context,
            Spinner spinner,
            String selectedLangCode,
            List<LanguageItem> items,
            OnLanguageSelected callback,
            boolean useThreeLetterCode
    ) {
        LanguageSpinnerAdapter adapter = new LanguageSpinnerAdapter(context, items);
        spinner.setAdapter(adapter);

        int selectedPosition = 0;
        for (int i = 0; i < items.size(); i++) {
            String code = useThreeLetterCode ? items.get(i).threeLetterCode : items.get(i).twoLetterCode;
            if (code.equals(selectedLangCode)) {
                selectedPosition = i;
                break;
            }
        }
        spinner.setSelection(selectedPosition);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                LanguageItem selected = (LanguageItem) parent.getItemAtPosition(position);
                callback.onLanguageSelected(selected);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    public static LanguageItem getLanguageByCode(String twoLetterCode) {
        for (LanguageItem item : SUPPORTED_LANGUAGES) {
            if (item.twoLetterCode.equalsIgnoreCase(twoLetterCode)) {
                return item;
            }
        }
        return null;
    }

}
