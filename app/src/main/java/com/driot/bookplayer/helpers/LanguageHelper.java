package com.driot.bookplayer.helpers;

import android.content.Context;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;

import com.driot.bookplayer.R;
import com.driot.bookplayer.adapter.LanguageSpinnerAdapter;
import com.driot.bookplayer.objects.LanguageItem;
import com.driot.bookplayer.utils.log.KanLogger;

import java.util.ArrayList;
import java.util.List;

public class LanguageHelper {

    public static final List<LanguageItem> LIBRIVOX_LANGUAGES;
    public static final List<LanguageItem> PODCAST_LANGUAGES;
    public static final List<LanguageItem> APP_LANGUAGES;
    static {
        List<LanguageItem> list = new ArrayList<>();
        // order matches your pref_languages_entries/values
        list.add(new LanguageItem("",   "system", "System default", R.drawable.flag_globe));
        list.add(new LanguageItem("eng","en",     "English",        R.drawable.flag_uk));
        list.add(new LanguageItem("fre","fr",     "Français",       R.drawable.flag_fr));
        list.add(new LanguageItem("spa","es",     "Español",        R.drawable.flag_es));
        list.add(new LanguageItem("deu","de",     "Deutsch",        R.drawable.flag_de));
        list.add(new LanguageItem("ita","it",     "Italiano",       R.drawable.flag_it));
        list.add(new LanguageItem("por","pt",     "Português",      R.drawable.flag_pt));
        list.add(new LanguageItem("ara","ar",     "العربية",        R.drawable.flag_sa));
        list.add(new LanguageItem("rus","ru",     "Русский",        R.drawable.flag_ru));
        list.add(new LanguageItem("zho","zh",     "中文",            R.drawable.flag_cn));
        APP_LANGUAGES = list;
    }

    static {
        List<LanguageItem> baseList  = new ArrayList<>();
        baseList.add(new LanguageItem("eng", "en", "English", R.drawable.flag_uk));
        baseList.add(new LanguageItem("deu", "de", "Deutsch", R.drawable.flag_de));
        baseList.add(new LanguageItem("spa", "es", "Español", R.drawable.flag_es));
        baseList.add(new LanguageItem("fre", "fr", "Français", R.drawable.flag_fr));
        baseList.add(new LanguageItem("por", "pt", "Português", R.drawable.flag_pt));
        baseList.add(new LanguageItem("ita", "it", "Italiano", R.drawable.flag_it));
        baseList.add(new LanguageItem("rus", "ru", "Русский", R.drawable.flag_ru));
        baseList.add(new LanguageItem("zho", "zh", "中文", R.drawable.flag_cn));
        baseList.add(new LanguageItem("ara", "ar", "العربية", R.drawable.flag_sa));
        baseList.add(new LanguageItem("jpn", "ja", "日本語", R.drawable.flag_jp));
        baseList.add(new LanguageItem("hin", "hi", "हिन्दी", R.drawable.flag_in));
        baseList.add(new LanguageItem("ell", "el", "Ελληνικά", R.drawable.flag_gr));
        baseList.add(new LanguageItem("heb", "he", "עברית", R.drawable.flag_il));
        baseList.add(new LanguageItem("swe", "sv", "Svenska", R.drawable.flag_se));
        baseList.add(new LanguageItem("pol", "pl", "Polski", R.drawable.flag_pl));
        baseList.add(new LanguageItem("nld", "nl", "Nederlands", R.drawable.flag_nl));

        List<LanguageItem> librivoxList = new ArrayList<>(baseList );
        librivoxList.add(new LanguageItem("mul", "", "Multiple", R.drawable.flag_globe));
        LIBRIVOX_LANGUAGES = librivoxList ;

        List<LanguageItem> podcastList = new ArrayList<>(baseList );
        podcastList.add(new LanguageItem("tur", "tr", "Türkçe", R.drawable.flag_tr));
        podcastList.add(new LanguageItem("kor", "ko", "한국어", R.drawable.flag_kr));
        podcastList.add(new LanguageItem("ind", "id", "Bahasa Indonesia", R.drawable.flag_id));
        podcastList.add(new LanguageItem("tha", "th", "ไทย", R.drawable.flag_th));
        podcastList.add(new LanguageItem("vie", "vi", "Tiếng Việt", R.drawable.flag_vn));
        podcastList.add(new LanguageItem("ron", "ro", "Română", R.drawable.flag_ro));
        podcastList.add(new LanguageItem("ukr", "uk", "Українська", R.drawable.flag_ua));
        podcastList.add(new LanguageItem("ces", "cs", "Čeština", R.drawable.flag_cz));
        PODCAST_LANGUAGES = podcastList;
    }

    public static List<LanguageItem> getLibrivoxLanguages() {
        return LIBRIVOX_LANGUAGES;
    }

    public static List<LanguageItem> getPodcastLanguages() {
        return PODCAST_LANGUAGES;
    }

    public static List<LanguageItem> getAppLanguages() { return APP_LANGUAGES; }

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

    public static LanguageItem getLanguageForPodcastsByCode(String twoLetterCode) {
        for (LanguageItem item : PODCAST_LANGUAGES) {
            if (item.twoLetterCode.equalsIgnoreCase(twoLetterCode)) {
                return item;
            }
        }
        return null;
    }


    // === LOGGING ===
    // ----------------------- LOG -----------------------
    private static final String TAG = "LanguageHelper";
    private static void myLog(String str) { KanLogger.myLog(TAG, str); }
    private static void myLogD(String str) { KanLogger.myLogD(TAG, str); }
    private static void myLogI(String str) { KanLogger.myLogI(TAG, str); }
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
    private static void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, TAG, str); }
    private static void myToastEE(Throwable t, String str) { KanLogger.myToastEE(t, TAG, str); }
}
