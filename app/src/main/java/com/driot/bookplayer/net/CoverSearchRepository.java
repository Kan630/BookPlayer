package com.driot.bookplayer.net;

import android.content.Context;

import com.driot.bookplayer.objects.CoverResult;

import java.util.ArrayList;
import java.util.List;

public class CoverSearchRepository {
    private final List<CoverSearchProvider> providers = new ArrayList<>();
    public CoverSearchRepository() {
        providers.add(new OpenLibraryProvider());
        providers.add(new GoogleBooksProvider());
    }
    public List<CoverResult> search(Context ctx, String query, int max) {
        ArrayList<CoverResult> all = new ArrayList<>();
        for (CoverSearchProvider p : providers) {
            if (all.size() >= max) break;
            int remaining = max - all.size();
            all.addAll(p.search(ctx, query, remaining));
        }
        return all;
    }
}
