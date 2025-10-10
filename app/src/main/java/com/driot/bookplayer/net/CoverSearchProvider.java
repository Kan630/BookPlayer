package com.driot.bookplayer.net;

import android.content.Context;

import com.driot.bookplayer.objects.CoverResult;

import java.util.List;

public interface CoverSearchProvider {
    List<CoverResult> search(Context ctx, String query, int max); // synchronous; call from background
}
