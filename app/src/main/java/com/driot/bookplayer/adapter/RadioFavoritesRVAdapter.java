package com.driot.bookplayer.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.radio.RadioFavoriteItem;
import com.driot.bookplayer.utils.log.LoggingRVAdapter;

import java.util.ArrayList;
import java.util.List;

public class RadioFavoritesRVAdapter extends LoggingRVAdapter<RecyclerView.ViewHolder> {

    public interface OnActionListener {
        void onPlay(RadioFavoriteItem f);
        void onUnfavorite(RadioFavoriteItem f);
    }

    private static final int VT_HEADER = 0;
    private static final int VT_ITEM   = 1;

    private final OnActionListener listener;
    private final List<RadioFavoriteItem> items = new ArrayList<>();

    public RadioFavoritesRVAdapter(@NonNull OnActionListener l) {
        this.listener = l;
    }

    public void setItems(List<RadioFavoriteItem> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    @Override public int getItemViewType(int position) {
        return position == 0 ? VT_HEADER : VT_ITEM;
    }

    @NonNull @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == VT_HEADER) {
            return new HeaderVH(inf.inflate(R.layout.recyclerview_radio_header, parent, false));
        } else {
            return new ItemVH(inf.inflate(R.layout.recyclerview_radio_result, parent, false));
        }
    }

    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder vh, int position) {
        if (getItemViewType(position) == VT_HEADER) {
            HeaderVH h = (HeaderVH) vh;
            h.tvSearch.setVisibility(View.GONE);
            h.tvLang.setVisibility(View.GONE);
            h.tvCountryTag.setVisibility(View.GONE);
            String resultsCount = items.size() + " " + vh.itemView.getContext().getString(R.string.favorites);
            h.tvCount.setText(resultsCount);
        } else {
            int idx = position - 1;
            RadioFavoriteItem f = items.get(idx);
            ItemVH holder = (ItemVH) vh;

            holder.title.setText(nonNull(f.name));
            holder.info.setText(joinInfo(nonNull(f.country), nonNull(f.language), normalizeTags(f.tags)));
            holder.codec.setText(nonNull(f.codec));
            holder.bitrate.setText(f.bitrate > 0 ? (f.bitrate + " kbps") : "");

            holder.favicon.setTag(f.stationuuid);
            Glide.with(holder.favicon).load(f.favicon)
                    .placeholder(R.drawable.ic_radio_24px_deportee)
                    .error(R.drawable.ic_radio_24px_deportee)
                    .into(holder.favicon);

            holder.ibPlay.setOnClickListener(v -> listener.onPlay(f));
            holder.ibFavorite.setOnClickListener(v -> listener.onUnfavorite(f));
            holder.itemView.setOnClickListener(v -> listener.onPlay(f));
        }
    }

    @Override public int getItemCount() { return items.size() + 1; }

    // ---- VHs ----
    static class HeaderVH extends RecyclerView.ViewHolder {
        final TextView tvSearch, tvLang, tvCountryTag, tvCount;
        HeaderVH(@NonNull View v) {
            super(v);
            tvSearch     = v.findViewById(R.id.tvSearchTerms);
            tvLang       = v.findViewById(R.id.tvLanguage);
            tvCountryTag = v.findViewById(R.id.tvCountryTag);
            tvCount      = v.findViewById(R.id.tvResultsCount);
        }
    }

    static class ItemVH extends RecyclerView.ViewHolder {
        ImageView favicon;
        TextView title, info, codec, bitrate;
        ImageButton ibPlay, ibFavorite;
        ItemVH(@NonNull View v) {
            super(v);
            favicon = v.findViewById(R.id.radio_favicon);
            title   = v.findViewById(R.id.radio_title);
            info    = v.findViewById(R.id.radio_info);
            codec   = v.findViewById(R.id.radio_codec);
            bitrate = v.findViewById(R.id.radio_bitrate);
            ibPlay  = v.findViewById(R.id.ibPlay);
            ibFavorite = v.findViewById(R.id.ibFavorite);
        }
    }

    // ---- helpers ----
    private static String nonNull(String s) { return s == null ? "" : s; }
    private static String normalizeTags(String csv) {
        if (csv == null || csv.trim().isEmpty()) return "";
        String[] parts = csv.split(",");
        StringBuilder sb = new StringBuilder();
        int added = 0;
        for (String p : parts) {
            String t = p.trim();
            if (t.isEmpty()) continue;
            if (added > 0) sb.append(", ");
            sb.append(t);
            added++;
            if (added >= 3) break;
        }
        return sb.toString();
    }
    private static String joinInfo(String... xs) {
        StringBuilder sb = new StringBuilder();
        for (String x : xs) {
            if (x == null || x.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" • ");
            sb.append(x);
        }
        return sb.toString();
    }
}
