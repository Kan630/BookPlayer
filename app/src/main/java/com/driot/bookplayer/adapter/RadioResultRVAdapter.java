package com.driot.bookplayer.adapter;

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
import com.driot.bookplayer.radio.Station;
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
        void onPlay(Station station);

        void onFavorite(Station station);
    }

    private final OnActionListener listener;

    private static final int VT_HEADER = 0;
    private static final int VT_ITEM = 1;

    private final List<Station> items = new ArrayList<>();

    // Header data
    private String headerSearch = "";
    private String headerLang = "";
    private String headerCountryTag = "";
    private String headerCount = "";

    // Favorite reflection (UUIDs from VM)
    private final Set<String> favoriteUuids = new HashSet<>();

    @androidx.annotation.Nullable
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
        notifyItemChanged(0); // header
    }

    public void setHeader(String search) {
        this.headerSearch = search != null ? search : "";
        this.headerLang = null;
        this.headerCountryTag = null;
        notifyItemChanged(0); // header
    }

    public void setHeaderCount(String count) {
        this.headerCount = count != null ? count : "";
        notifyItemChanged(0);
    }

    // --- Favorite API (kept local so Station POJO stays API-pure) ---
    public void setFavorites(Set<String> stationUuids) {
        favoriteUuids.clear();
        if (stationUuids != null)
            favoriteUuids.addAll(stationUuids);
        notifyDataSetChanged();
    }

    // --- Items API ---
    public void setItems(List<Station> newItems) {
        items.clear();
        if (newItems != null)
            items.addAll(newItems);
        notifyDataSetChanged();
    }

    public void appendItems(List<Station> newItems) {
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
        ImageView favicon;
        TextView title, info, codec, bitrate;
        ImageButton ibFavorite;

        ItemVH(@NonNull View itemView) {
            super(itemView);
            favicon = itemView.findViewById(R.id.radio_favicon);
            title = itemView.findViewById(R.id.radio_title);
            info = itemView.findViewById(R.id.radio_info); // country • language • tags
            codec = itemView.findViewById(R.id.radio_codec);
            bitrate = itemView.findViewById(R.id.radio_bitrate);
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
            return new HeaderVH(inf.inflate(R.layout.recyclerview_radio_header, parent, false));
        } else {
            return new ItemVH(inf.inflate(R.layout.recyclerview_radio_result, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder vh, int position) {
        if (getItemViewType(position) == VT_HEADER) {
            HeaderVH h = (HeaderVH) vh;
            if (headerSearch == null || headerSearch.isEmpty()) {
                h.tvSearch.setVisibility(View.GONE);
            } else {
                h.tvSearch.setVisibility(View.VISIBLE);
                h.tvSearch.setText(headerSearch);
            }
            if (headerLang == null || headerLang.isEmpty()) {
                h.tvLang.setVisibility(View.GONE);
            } else {
                h.tvLang.setVisibility(View.VISIBLE);
                h.tvLang.setText(headerLang);
            }
            if (headerCountryTag == null || headerCountryTag.isEmpty()) {
                h.tvCountryTag.setVisibility(View.GONE);
            } else {
                h.tvCountryTag.setVisibility(View.VISIBLE);
                h.tvCountryTag.setText(headerCountryTag);
            }
            if (headerCount == null || headerCount.isEmpty()) {
                h.tvCount.setVisibility(View.GONE);
            } else {
                h.tvCount.setVisibility(View.VISIBLE);
                h.tvCount.setText(headerCount);
            }
            // As in your Librivox header, you can attach overlays to h.topOverlayContainer
            // from the Activity
        } else {
            int idx = position - 1;
            if (idx < 0 || idx >= items.size())
                return;

            Station s = items.get(idx);
            ItemVH holder = (ItemVH) vh;

            boolean activated = playingRadioStationUuid != null && playingRadioStationUuid.equals(s.stationuuid);
            holder.itemView.setActivated(activated);

            Context context = holder.itemView.getContext();

            // Title
            holder.title.setText(nonNull(s.name));

            // Sub-info: country • language • tags (single line, ellipsized)
            /*
             * String country = emptyIfNull(s.country);
             * String language = emptyIfNull(s.language);
             * String tags = normalizeTags(s.tags);
             * String info = joinNonEmpty(" • ", country, language, tags);
             * holder.info.setText(info);
             */

            holder.info.setText((s.country != null ? s.country
                    : (s.language != null ? s.language : (s.tags != null ? normalizeTags(s.tags) : ""))));

            // Codec / bitrate
            // holder.codec.setText(nonNull(s.codec));
            // holder.bitrate.setText(s.bitrate > 0 ? s.bitrate + " kbps" : "");
            holder.codec.setVisibility(View.GONE);
            holder.bitrate.setVisibility(View.GONE);

            // Favicon (may be empty)
            holder.favicon.setTag(s.stationuuid); // prevent race
            Glide.with(holder.favicon).load(s.favicon)
                    .placeholder(R.drawable.ic_radio_24px_deportee)
                    .error(R.drawable.ic_radio_24px_deportee)
                    .into(holder.favicon);

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

            // Row click = play (optional: keep it explicit on the play button if you
            // prefer)
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

    public void setPlayingRadioStationUuid(@androidx.annotation.Nullable String uuid) {
        if ((uuid == null) && (this.playingRadioStationUuid == null))
            return;
        if (uuid == null || !uuid.equals(this.playingRadioStationUuid)) {
            notifyDataSetChanged();
        }
        this.playingRadioStationUuid = uuid;
    }
}
