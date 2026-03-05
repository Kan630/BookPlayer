package com.driot.bookplayer.radio;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.radio.ApiStation;
import com.driot.bookplayer.utils.log.LoggingRVAdapter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Results adapter for Radio Browser stations, modeled after your Librivox
 * adapter.
 */
public class RadioResultRVAdapter extends LoggingRVAdapter<RecyclerView.ViewHolder> {

    public interface OnActionListener {
        void onPlay(ApiStation apiStation);

        void onFavorite(ApiStation apiStation);
    }

    private final OnActionListener listener;

    private static final int VT_HEADER = 0;
    private static final int VT_ITEM = 1;

    private final List<ApiStation> items = new ArrayList<>();

    // Header data
    private String headerSearch = "";
    private String headerLang = "";
    private String headerCountryTag = "";
    private String headerCount = "";

    // Favorite reflection (UUIDs from VM)
    private final Set<String> favoriteUuids = new HashSet<>();

    private int trackId = 0;
    private String playingRadioStationUuid = null;

    public RadioResultRVAdapter(@NonNull OnActionListener listener) {
        this.listener = listener;
    }

    // --- Header API ---
    /** e.g. search="Jazz", lang="fr", countryTag="FR • chillout" */
    public void setHeader(String search, String lang, String countryTag) {
        this.headerSearch = search != null ? search : "";
        this.headerLang = lang != null ? lang : "";
        this.headerCountryTag = countryTag != null ? countryTag : "";
        notifyItemChanged(0);
    }

    public void setHeaderSearch(String search) {
        this.headerSearch = search != null ? search : "";
        notifyItemChanged(0);
    }

    public void setHeaderLang(String lang) {
        this.headerLang = lang != null ? lang : "";
        notifyItemChanged(0);
    }

    public void setHeaderCountryTag(String countryTag) {
        this.headerCountryTag = countryTag != null ? countryTag : "";
        notifyItemChanged(0);
    }

    public void setHeader(String search) {
        setHeaderSearch(search);
    }

    public void setHeaderCount(String count) {
        this.headerCount = count != null ? count : "";
        notifyItemChanged(0);
    }

    // --- Favorite API (kept local so ApiStation POJO stays API-pure) ---
    public void setFavorites(Set<String> stationUuids) {
        favoriteUuids.clear();
        if (stationUuids != null)
            favoriteUuids.addAll(stationUuids);
        notifyDataSetChanged();
    }

    // --- Items API ---
    public void setItems(List<ApiStation> newItems) {
        items.clear();
        if (newItems != null)
            items.addAll(newItems);
        notifyDataSetChanged();
    }

    public void appendItems(List<ApiStation> newItems) {
        if (newItems != null && !newItems.isEmpty()) {
            int startPosition = items.size() + 1; // +1 for header
            items.addAll(newItems);
            notifyItemRangeInserted(startPosition, newItems.size());
        }
    }

    // --- ViewHolders ---
    public static class HeaderVH extends RecyclerView.ViewHolder {
        final TextView tvSearch, tvLang, tvCountryTag, tvCount;
        final View topOverlayContainer;

        HeaderVH(@NonNull View v) {
            super(v);
            tvSearch = v.findViewById(R.id.tvSearchTerms);
            tvLang = v.findViewById(R.id.tvLanguage);
            tvCountryTag = v.findViewById(R.id.tvCountryTag);
            tvCount = v.findViewById(R.id.tvResultsCount);
            topOverlayContainer = v.findViewById(R.id.topOverlayContainer);
        }
    }

    static class ItemVH extends RecyclerView.ViewHolder {
        ImageView favicon, ivDefaultIcon;
        TextView title, info;
        ImageButton ibFavorite;

        ItemVH(@NonNull View itemView) {
            super(itemView);
            favicon = itemView.findViewById(R.id.radio_favicon);
            ivDefaultIcon = itemView.findViewById(R.id.ivDefaultIcon);
            title = itemView.findViewById(R.id.radio_title);
            info = itemView.findViewById(R.id.radio_info); // country • language • tags
            ibFavorite = itemView.findViewById(R.id.ibFavorite);
        }
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? VT_HEADER : VT_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == VT_HEADER) {
            return new HeaderVH(inf.inflate(R.layout.recyclerview_search_header, parent, false));
        } else {
            return new ItemVH(inf.inflate(R.layout.recyclerview_radio_result, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder vh, int position) {
        if (getItemViewType(position) == VT_HEADER) {
            HeaderVH h = (HeaderVH) vh;
            h.tvSearch.setText(headerSearch);
            h.tvSearch.setVisibility(headerSearch.isEmpty() ? View.GONE : View.VISIBLE);

            h.tvLang.setText(headerLang);
            h.tvLang.setVisibility(headerLang.isEmpty() ? View.GONE : View.VISIBLE);

            h.tvCountryTag.setText(headerCountryTag);
            h.tvCountryTag.setVisibility(headerCountryTag.isEmpty() ? View.GONE : View.VISIBLE);

            h.tvCount.setText(headerCount);
            h.tvCount.setVisibility(headerCount.isEmpty() ? View.GONE : View.VISIBLE);
        } else {
            int idx = position - 1;
            if (idx < 0 || idx >= items.size())
                return;

            ApiStation s = items.get(idx);
            ItemVH holder = (ItemVH) vh;

            boolean activated = playingRadioStationUuid.equals(s.stationuuid);
            holder.itemView.setActivated(activated);

            Context context = holder.itemView.getContext();

            // Title
            holder.title.setText(nonNull(s.name));

            // Sub-info: country • language • tags
            holder.info.setText((s.country != null ? s.country
                    : (s.language != null ? s.language : (s.tags != null ? normalizeTags(s.tags) : ""))));

            // Favicon
            holder.favicon.setTag(s.stationuuid);
            if (TextUtils.isEmpty(s.favicon)) {
                holder.ivDefaultIcon.setVisibility(View.VISIBLE);
                Glide.with(holder.favicon).clear(holder.favicon);
                holder.favicon.setImageDrawable(null);
            } else {
                holder.ivDefaultIcon.setVisibility(View.GONE);
                Glide.with(holder.favicon).load(s.favicon)
                        .into(holder.favicon);
            }

            // Favorite tint
            boolean isFav = favoriteUuids.contains(s.stationuuid);
            int tint = ContextCompat.getColor(context, isFav ? R.color.red : android.R.color.white);
            holder.ibFavorite.setColorFilter(tint);

            holder.ibFavorite.setOnClickListener(v -> {
                int p = holder.getBindingAdapterPosition();
                if (p == RecyclerView.NO_POSITION)
                    return;
                listener.onFavorite(s);
            });

            holder.itemView.setOnClickListener(v -> listener.onPlay(s));
        }
    }

    @Override
    public int getItemCount() {
        return items.size() + 1; // header
    }

    // --- Small helpers ---
    private static String nonNull(String s) {
        return s == null ? "" : s;
    }

    private static String emptyIfNull(String s) {
        return s == null ? "" : s.trim();
    }

    private static String normalizeTags(String tagsCsv) {
        if (TextUtils.isEmpty(tagsCsv))
            return "";
        // Keep it short: take first 2–3 relevant tags
        String[] parts = tagsCsv.split(",");
        StringBuilder sb = new StringBuilder();
        int added = 0;
        for (String p : parts) {
            String t = p.trim();
            if (t.isEmpty())
                continue;
            if (added > 0)
                sb.append(", ");
            sb.append(t);
            added++;
            if (added >= 3)
                break;
        }
        return sb.toString();
    }

    private static String joinNonEmpty(String sep, String... xs) {
        StringBuilder sb = new StringBuilder();
        for (String x : xs) {
            if (x == null || x.isEmpty())
                continue;
            if (sb.length() > 0)
                sb.append(sep);
            sb.append(x);
        }
        return sb.toString();
    }

    public void setPlayingRadioStation(int trackId) {
        if (trackId <= 0)
            return;
        if (trackId == this.trackId) {
            notifyDataSetChanged();
        }
        this.trackId = trackId;
    }
}
