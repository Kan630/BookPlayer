package com.driot.bookplayer.radio;

import android.content.Context;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

public class RadioCacheHelper {

    private static final String CACHE_DIR = "radio_cache";

    private static String getFileName(int mode) {
        switch (mode) {
            case GetRadioCardListActivity.MODE_TAG:
                return "tags_cache.json";
            case GetRadioCardListActivity.MODE_COUNTRY:
                return "countries_cache.json";
            case GetRadioCardListActivity.MODE_LANGUAGE:
                return "languages_cache.json";
            default:
                return "unknown_cache.json";
        }
    }

    public static void saveCache(Context context, int mode, List<TagItem> items) {
        try {
            File dir = new File(context.getFilesDir(), CACHE_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File file = new File(dir, getFileName(mode));
            try (FileWriter writer = new FileWriter(file)) {
                new Gson().toJson(items, writer);
            }
            myLogD("Radio cache saved: " + file.getName() + " (" + items.size() + " items)");
        } catch (Exception e) {
            myLogEE(e, "Error saving radio cache");
        }
    }

    public static List<TagItem> loadCache(Context context, int mode) {
        try {
            File file = new File(new File(context.getFilesDir(), CACHE_DIR), getFileName(mode));
            if (!file.exists()) {
                return new ArrayList<>();
            }
            try (FileReader reader = new FileReader(file)) {
                Type listType = new TypeToken<ArrayList<TagItem>>() {
                }.getType();
                List<TagItem> items = new Gson().fromJson(reader, listType);
                if (items != null) {
                    myLogD("Radio cache loaded: " + file.getName() + " (" + items.size() + " items)");
                    return items;
                }
            }
        } catch (Exception e) {
            myLogEE(e, "Error loading radio cache");
        }
        return new ArrayList<>();
    }
}
