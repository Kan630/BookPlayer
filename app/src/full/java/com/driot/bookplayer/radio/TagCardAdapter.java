package com.driot.bookplayer.radio;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.helpers.LanguageHelper;
import com.driot.bookplayer.utils.log.KanLogger;

import java.util.ArrayList;
import java.util.List;

public class TagCardAdapter extends RecyclerView.Adapter<TagCardAdapter.VH> {

    public interface OnClick { void onTagClick(TagItem t); }

    private final List<TagItem> items = new ArrayList<>();
    private final OnClick cb;

    public TagCardAdapter(OnClick cb) { this.cb = cb; }

    public void setItems(List<TagItem> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int vtype) {
        View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_tag_card, p, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        TagItem t = items.get(pos);
//NAME
        String name = "---";
        if (t.name != null && !t.name.isEmpty()) {
            name = t.name.substring(0, 1).toUpperCase() + t.name.substring(1);
        }
        h.tvName.setText(name);
//COUNT
        h.tvCount.setText(String.valueOf(t.stationcount));
//FLAG
        Integer flagResId = null;
        //KanLogger.myLog("tagitem - name=" + t.name + "- iso_3166_1=" + t.iso_3166_1 + "- iso_639=" + t.iso_639);
        if (t.iso_3166_1 != null) {
            flagResId = getFlagResId(h.ivFlag.getContext(), t.iso_3166_1, "country");
        } else if (t.iso_639 != null) {
            flagResId = getFlagResId(h.ivFlag.getContext(), t.iso_639, "language");
        } else if ("american english".equals(t.name)) {
            flagResId = getFlagResId(h.ivFlag.getContext(), "us", "country");
        } else if ("brazilian portuguese".equals(t.name)) {
            flagResId = getFlagResId(h.ivFlag.getContext(), "br", "country");
        } else if ("português  brasil".equals(t.name)) {
            flagResId = getFlagResId(h.ivFlag.getContext(), "br", "country");
        } else if ("español internacional".equals(t.name)) {
            flagResId = getFlagResId(h.ivFlag.getContext(), "es", "country");
        } else if ("español argentina".equals(t.name)) {
            flagResId = getFlagResId(h.ivFlag.getContext(), "ar", "country");
        } else if ("español mexico".equals(t.name)) {
            flagResId = getFlagResId(h.ivFlag.getContext(), "mx", "country");
        } else if ("english uk".equals(t.name)) {
            flagResId = getFlagResId(h.ivFlag.getContext(), "uk", "country");
        } else if ("cantonese".equals(t.name)) {
            flagResId = getFlagResId(h.ivFlag.getContext(), "cn", "country");
        } else if ("british english".equals(t.name)) {
            flagResId = getFlagResId(h.ivFlag.getContext(), "uk", "country");
        } else if ("filipino".equals(t.name)) {
            flagResId = getFlagResId(h.ivFlag.getContext(), "ph", "country");
        } else if ("punjabi".equals(t.name)) {
            flagResId = getFlagResId(h.ivFlag.getContext(), "in", "country");
        } else if (t.name.contains("castellan")) {
            flagResId = getFlagResId(h.ivFlag.getContext(), "in", "country");
        } else if (t.name.toLowerCase().contains("breton")) {
            flagResId = getFlagResId(h.ivFlag.getContext(), "in", "country");
        }
        if (flagResId != null && flagResId != 0) {
            Glide.with(h.ivFlag.getContext()).load(flagResId).into(h.ivFlag);
            h.ivFlag.setVisibility(View.VISIBLE);
        } else {
            h.ivFlag.setVisibility(View.GONE);
        }
//CLICK
        h.card.setOnClickListener(v -> { if (cb != null) cb.onTagClick(t); });

    }

    @Override public int getItemCount() { return items.size(); }

    static final class VH extends RecyclerView.ViewHolder {
        final View card;
        final TextView tvName;
        final TextView tvCount;
        final ImageView ivFlag;
        VH(@NonNull View v) {
            super(v);
            card = v.findViewById(R.id.card);
            tvName  = v.findViewById(R.id.tvTagName);
            tvCount = v.findViewById(R.id.tvTagCount);
            ivFlag  = v.findViewById(R.id.ivFlag);
        }
    }

    private static int getFlagResId(Context context, String code2, String codeType) {
        if (code2 == null) return 0;
        if (codeType.equals("language")) {
            String countryCode = LanguageHelper.getCountryForLanguage(code2);
            if (countryCode == null) return 0;
            return context.getResources().getIdentifier("flag_" + countryCode.toLowerCase(), "drawable", context.getPackageName());
        } else if (codeType.equals("country")) {
            return context.getResources().getIdentifier("flag_" + code2.toLowerCase(), "drawable", context.getPackageName());
        } else {
            return 0;
        }
    }
}

