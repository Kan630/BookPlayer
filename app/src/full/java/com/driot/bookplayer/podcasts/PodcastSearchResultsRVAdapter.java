package com.driot.bookplayer.podcasts;

import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.content.res.ColorStateList;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.utils.log.LoggingRVAdapter;

import com.driot.bookplayer.db.Podcast;

import java.util.ArrayList;
import java.util.List;

// Adapter for Podcast (API result)
public class PodcastSearchResultsRVAdapter extends LoggingRVAdapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_ITEM = 1;

    private List<PodcastFeed> items = new ArrayList<>();
    private List<Podcast> favorites = null;
    private List<Podcast> history = null;
    private final OnItemClickListener listener;

    private String headerQuery = "";
    private String headerLang = "";
    private String headerCount = "";

    public interface OnItemClickListener {
        void onItemClick(PodcastFeed item);
    }

    public PodcastSearchResultsRVAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setHeaderInfo(String query, String lang, String count) {
        this.headerQuery = query;
        this.headerLang = lang;
        this.headerCount = count;
        notifyItemChanged(0);
    }

    public void setItems(List<PodcastFeed> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    public void setFavorites(List<Podcast> favorites) {
        this.favorites = favorites;
        notifyDataSetChanged();
    }

    public void setHistory(List<Podcast> history) {
        this.history = history;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.recyclerview_search_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.recyclerview_podcast_result, parent, false);
            return new PodcastViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            HeaderViewHolder h = (HeaderViewHolder) holder;
            h.tvSearchTerms.setText(headerQuery);
            h.tvSearchTerms.setVisibility(headerQuery.isEmpty() ? View.GONE : View.VISIBLE);

            h.tvLanguage.setText(headerLang);
            h.tvLanguage.setVisibility(headerLang.isEmpty() ? View.GONE : View.VISIBLE);

            h.tvResultsCount.setText(headerCount);
            h.tvResultsCount.setVisibility(headerCount.isEmpty() ? View.GONE : View.VISIBLE);

            h.tvCountryTag.setVisibility(View.GONE);
        } else {
            PodcastFeed item = items.get(position - 1); // subtract 1 because of header
            ((PodcastViewHolder) holder).bind(item, listener, favorites, history);
        }
    }

    @Override
    public int getItemCount() {
        return items.size() + 1; // +1 for header
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? VIEW_TYPE_HEADER : VIEW_TYPE_ITEM;
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        final TextView tvSearchTerms, tvLanguage, tvResultsCount, tvCountryTag;

        HeaderViewHolder(View v) {
            super(v);
            tvSearchTerms = v.findViewById(R.id.tvSearchTerms);
            tvLanguage = v.findViewById(R.id.tvLanguage);
            tvResultsCount = v.findViewById(R.id.tvResultsCount);
            tvCountryTag = v.findViewById(R.id.tvCountryTag);
        }
    }

    static class PodcastViewHolder extends RecyclerView.ViewHolder {
        TextView title, desc, folderStats;
        ImageView image;
        View statusContainer;
        ImageView statusIcon;
        TextView statusLabel;

        PodcastViewHolder(View v) {
            super(v);
            title = v.findViewById(R.id.podcast_title);
            desc = v.findViewById(R.id.podcast_desc);
            image = v.findViewById(R.id.podcast_image);
            folderStats = v.findViewById(R.id.podcast_folder_stats);
            statusContainer = v.findViewById(R.id.podcast_autodownload_container);
            statusIcon = v.findViewById(R.id.podcast_autodownload);
            statusLabel = v.findViewById(R.id.podcast_autodownload_label);
        }

        void bind(PodcastFeed item, OnItemClickListener listener, List<Podcast> favorites, List<Podcast> history) {
            title.setText(item.title);
            folderStats.setVisibility(View.GONE);
            if (item.description != null) {
                desc.setText(Html.fromHtml(item.description, Html.FROM_HTML_MODE_LEGACY).toString().trim());
            }
            Glide.with(image.getContext()).load(item.image).into(image);

            // This is a browse/search-results row (not the user's own "My Podcasts" list, where
            // podcast_autodownload_label/_container legitimately show auto-download status - see
            // PodcastFavoritesRVAdapter) - here it's repurposed as a plain "you already know this
            // podcast" indicator: favorite wins over history, neither shows nothing. The text
            // label ("auto") doesn't apply to either case, so it always stays hidden.
            statusLabel.setVisibility(View.GONE);

            boolean isFavorite = containsFeed(favorites, item.id);
            boolean isHistory = !isFavorite && containsFeed(history, item.id);

            if (isFavorite) {
                statusContainer.setVisibility(View.VISIBLE);
                statusIcon.setImageResource(R.drawable.ic_favorite);
                statusIcon.setImageTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(statusIcon.getContext(), android.R.color.holo_red_dark)));
                statusIcon.setContentDescription(statusIcon.getContext().getString(R.string.favorites));
            } else if (isHistory) {
                statusContainer.setVisibility(View.VISIBLE);
                statusIcon.setImageResource(R.drawable.ic_history_24px);
                statusIcon.setImageTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(statusIcon.getContext(), android.R.color.darker_gray)));
                statusIcon.setContentDescription(statusIcon.getContext().getString(R.string.in_history));
            } else {
                statusContainer.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> listener.onItemClick(item));
        }

        private static boolean containsFeed(List<Podcast> podcasts, long feedId) {
            if (podcasts == null) return false;
            for (Podcast p : podcasts) {
                if (p.feedId == feedId) return true;
            }
            return false;
        }
    }
}
