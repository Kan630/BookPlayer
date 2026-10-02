package com.driot.bookplayer.radio;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import androidx.appcompat.content.res.AppCompatResources;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestListener;
import com.driot.bookplayer.helpers.ViewHelper;

public class GlideLoader {

    static boolean isContextAlive(Context context) {
        return ViewHelper.isContextAlive(context);
    }

    public static void load(ImageView view, String url, int replacementResource,
                            RequestListener<Drawable> listener) {
        Context ctx = view.getContext();
        if (!isContextAlive(ctx)) return;
        Drawable placeholder = RadioFaviconHelper.getDefaultFaviconDrawable(ctx);
        Drawable error = replacementResource != 0
                ? AppCompatResources.getDrawable(ctx, replacementResource)
                : placeholder;

        var request = Glide.with(view)
                .load(url)
                .placeholder(placeholder)
                .error(error);

        if (listener != null) request = request.listener(listener);
        request.into(view);
    }

    public static void load(ImageView view, String url, int replacementResource) {
        load(view, url, replacementResource, null);
    }

    public static void clear(ImageView view, int replacementResource) {
        Context ctx = view.getContext();
        if (!isContextAlive(ctx)) return;
        Glide.with(view).clear(view);
        Drawable fallback = replacementResource != 0
                ? AppCompatResources.getDrawable(ctx, replacementResource)
                : RadioFaviconHelper.getDefaultFaviconDrawable(ctx);
        view.setImageDrawable(fallback);
    }
}
